package dev.launcherfork.data.exbo

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.isRegularFile

/** One file inside the launcher's `/torrentData` catalog. [sha1] is the 20-byte digest of the whole file. */
internal data class ExboTorrentFile(val path: String, val size: Long, val sha1: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is ExboTorrentFile && path == other.path && size == other.size && sha1.contentEquals(other.sha1)

    override fun hashCode(): Int = path.hashCode()
}

/** File catalog the working launcher reads from `/torrentData` (not a `.torrent` bencode blob). */
internal data class ExboTorrent(val name: String, val pieceLength: Int, val files: List<ExboTorrentFile>)

internal class ExboSyncStopped : RuntimeException()

/**
 * Reads the catalog the official client stores as `<id>.torrent.bin`:
 * 8 skipped bytes, a comment, tracker URLs, one ignored string, then piece length, name, files and piece hashes.
 */
internal fun parseExboTorrent(bytes: ByteArray): ExboTorrent {
    val plain = inflateCatalog(bytes)
    DataInputStream(ByteArrayInputStream(plain)).use { input ->
        if (input.skipBytes(8) != 8) throw IOException("short catalog")
        input.readUTF()
        val trackers = input.readInt()
        if (trackers !in 0..10_000) throw IOException("bad catalog")
        repeat(trackers) { input.readUTF() }
        input.readUTF()
        val pieceLength = input.readInt()
        if (pieceLength <= 0) throw IOException("bad catalog")
        val name = input.readUTF()
        val count = input.readInt()
        if (count !in 0..500_000) throw IOException("bad catalog")
        val files = ArrayList<ExboTorrentFile>(count)
        repeat(count) {
            val path = input.readUTF()
            val size = input.readLong()
            if (size < 0) throw IOException("bad catalog")
            val hash = ByteArray(20)
            input.readFully(hash)
            files += ExboTorrentFile(path, size, hash)
        }
        val pieces = input.readInt()
        if (pieces !in 0..5_000_000) throw IOException("bad catalog")
        repeat(pieces) { input.skipBytes(20) }
        return ExboTorrent(name, pieceLength, files)
    }
}

/** `/torrentData` is sometimes a gzip stream, with or without a 4-byte prefix. The cached `.torrent.bin` is plain. */
internal fun inflateCatalog(bytes: ByteArray): ByteArray {
    val start = when {
        bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte() -> 0
        bytes.size >= 6 && bytes[4] == 0x1f.toByte() && bytes[5] == 0x8b.toByte() -> 4
        else -> return bytes
    }
    return GZIPInputStream(ByteArrayInputStream(bytes, start, bytes.size - start)).use { it.readBytes() }
}

internal fun writeExboTorrent(torrent: ExboTorrent): ByteArray {
    val buffer = java.io.ByteArrayOutputStream()
    DataOutputStream(buffer).use { out ->
        out.writeLong(0)
        out.writeUTF("test")
        out.writeInt(0)
        out.writeUTF("")
        out.writeInt(torrent.pieceLength)
        out.writeUTF(torrent.name)
        out.writeInt(torrent.files.size)
        for (file in torrent.files) {
            out.writeUTF(file.path)
            out.writeLong(file.size)
            out.write(file.sha1)
        }
        out.writeInt(0)
    }
    return buffer.toByteArray()
}

internal fun exboSha1(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(bytes)

/** Web-seed URL. [withName] prefixes the torrent name the way libtorrent's url-list does for a multi-file catalog. */
internal fun exboSeedFileUrl(seed: String, torrentName: String, filePath: String, withName: Boolean): String {
    val suffix = if (withName && torrentName.isNotBlank()) "$torrentName/$filePath" else filePath
    val encoded = suffix.split('/').filter { it.isNotEmpty() }
        .joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20") }
    val base = seed.trim()
    return if (base.endsWith("/")) base + encoded else "$base/$encoded"
}

/**
 * Brings [directory] in line with [torrent]. Files that already match are left in place.
 * A path that starts with an exclusion is kept when it exists and is not created when it does not.
 * [open] must return the file bytes for a web-seed URL. A wrong body is discarded and the next URL is tried.
 * Throws [ExboSyncStopped] when [cancelled] becomes true, [IOException] when a required file cannot be fetched.
 */
internal fun syncClientFiles(
    directory: Path,
    torrent: ExboTorrent,
    seeds: List<String>,
    exclusions: List<String>,
    open: (String) -> InputStream,
    cancelled: () -> Boolean = { false },
    onProgress: (done: Long, total: Long, perSecond: Long) -> Unit = { _, _, _ -> },
) {
    val jobs = ArrayList<Pair<ExboTorrentFile, Path>>(torrent.files.size)
    for (file in torrent.files) {
        val dest = resolveInside(directory, file.path) ?: continue
        jobs += file to dest
    }
    val total = jobs.sumOf { it.first.size }.coerceAtLeast(1L)
    var done = 0L
    var inflight = 0L
    var stamp = System.nanoTime()
    var window = 0L
    fun report(rate: Long = 0) {
        onProgress((done + inflight).coerceAtMost(total), total, rate)
    }
    fun tick(n: Int) {
        inflight += n
        window += n
        val now = System.nanoTime()
        val elapsed = now - stamp
        if (elapsed >= 200_000_000L) {
            val rate = window * 1_000_000_000L / elapsed
            stamp = now
            window = 0
            report(rate)
        }
    }
    var withName: Boolean? = null
    for ((file, dest) in jobs) {
        if (cancelled()) throw ExboSyncStopped()
        if (exboPathExcluded(file.path, exclusions)) {
            done += file.size
            report()
            continue
        }
        inflight = 0
        if (dest.isRegularFile() && Files.size(dest) == file.size && fileHash(dest, cancelled, ::tick).contentEquals(file.sha1)) {
            done += file.size
            inflight = 0
            report()
            continue
        }
        inflight = 0
        if (seeds.isEmpty()) throw IOException("no seeds")
        val part = dest.resolveSibling(dest.fileName.toString() + ".part")
        var wrote = false
        val patterns = if (withName != null) listOf(withName!!) else listOf(false, true)
        for (seed in seeds) {
            for (named in patterns) {
                if (cancelled()) throw ExboSyncStopped()
                val url = exboSeedFileUrl(seed, torrent.name, file.path.replace('\\', '/'), named)
                try {
                    val got = open(url).use { input ->
                        Files.createDirectories(dest.parent)
                        hashCopy(input, part, file.size, cancelled, ::tick)
                    }
                    if (!got.contentEquals(file.sha1) || Files.size(part) != file.size) {
                        Files.deleteIfExists(part)
                        continue
                    }
                    moveReplacing(part, dest)
                    withName = named
                    wrote = true
                    break
                } catch (_: ExboSyncStopped) {
                    Files.deleteIfExists(part)
                    throw ExboSyncStopped()
                } catch (_: IOException) {
                    Files.deleteIfExists(part)
                }
            }
            if (wrote) break
        }
        if (!wrote) {
            Files.deleteIfExists(part)
            throw IOException("file mismatch")
        }
        done += file.size
        inflight = 0
        report()
    }
}

private fun moveReplacing(part: Path, dest: Path) {
    try {
        Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING)
    }
}

internal fun exboOpenSeed(url: String): InputStream {
    val conn = (URI(url).toURL().openConnection() as HttpURLConnection)
    conn.connectTimeout = 15_000
    conn.readTimeout = 60_000
    conn.instanceFollowRedirects = true
    val code = conn.responseCode
    if (code !in 200..299) {
        conn.disconnect()
        throw IOException("http $code")
    }
    return conn.inputStream
}

private fun fileHash(file: Path, cancelled: () -> Boolean, onBytes: (Int) -> Unit): ByteArray {
    val digest = MessageDigest.getInstance("SHA-1")
    Files.newInputStream(file).use { input ->
        val buf = ByteArray(1024 * 256)
        while (true) {
            if (cancelled()) throw ExboSyncStopped()
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
            onBytes(n)
        }
    }
    return digest.digest()
}

private fun hashCopy(
    input: InputStream,
    dest: Path,
    expected: Long,
    cancelled: () -> Boolean,
    onBytes: (Int) -> Unit,
): ByteArray {
    val digest = MessageDigest.getInstance("SHA-1")
    var left = expected
    Files.newOutputStream(dest).use { output ->
        val buf = ByteArray(1024 * 256)
        while (left > 0) {
            if (cancelled()) throw ExboSyncStopped()
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw IOException("short body")
            output.write(buf, 0, n)
            digest.update(buf, 0, n)
            left -= n
            onBytes(n)
        }
    }
    return digest.digest()
}

internal fun exboPathExcluded(path: String, exclusions: List<String>): Boolean {
    val normalized = path.replace('\\', '/').trimStart('/')
    return exclusions.any { raw ->
        val prefix = raw.replace('\\', '/').trim().trimStart('/')
        prefix.isNotEmpty() && (normalized == prefix.removeSuffix("/") || normalized.startsWith(prefix) ||
            normalized.startsWith(prefix.removeSuffix("/") + "/"))
    }
}

private fun resolveInside(root: Path, relative: String): Path? {
    val normalized = relative.replace('\\', '/').trim()
    if (normalized.isEmpty() || normalized.startsWith("/") || normalized.contains(':')) return null
    val parts = normalized.split('/')
    if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
    val base = root.toAbsolutePath().normalize()
    val target = parts.fold(base) { acc, part -> acc.resolve(part) }.normalize()
    return target.takeIf { it.startsWith(base) }
}

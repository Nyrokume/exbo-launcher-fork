package dev.launcherfork.data.exbo

import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.TorrentStatus
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Opens the official `/torrentData` catalog with the launcher's own bencode (so the info-hash matches the swarm)
 * and fetches the missing pieces into an existing client folder. The 32-bit DLL next to the installed launcher
 * is never loaded; the session uses the 64-bit jlibtorrent on this process's classpath.
 */
internal data class ExboDecodedTorrent(val info: TorrentInfo, val paths: List<String>)

internal fun decodeExboTorrent(catalog: ByteArray, seeds: List<String>, launcherJar: Path): ExboDecodedTorrent {
    ensureExboTorrentNative()
    if (!Files.isRegularFile(launcherJar)) throw IOException("no launcher")
    val plain = inflateCatalog(catalog)
    val copy = Files.createTempFile("exbo-torrent-", ".jar")
    try {
        Files.copy(launcherJar, copy, StandardCopyOption.REPLACE_EXISTING)
        val parent = ExboDecodedTorrent::class.java.classLoader
        URLClassLoader(arrayOf(copy.toUri().toURL()), parent).use { loader ->
            val metaClass = loader.loadClass("ru.justagod.torrent.meta.TorrentMeta")
            val meta = metaClass.getDeclaredConstructor().newInstance()
            DataInputStream(ByteArrayInputStream(plain)).use { input ->
                invoke(metaClass.getMethod("read", DataInputStream::class.java), meta, input)
            }
            invoke(metaClass.getMethod("setHttpSeeds", java.util.List::class.java), meta, seeds)
            val info = invoke(metaClass.getMethod("toLTInfo"), meta) as TorrentInfo
            val theirInfo = invoke(metaClass.getMethod("getInfo"), meta) ?: throw IOException("bad catalog")
            val files = invoke(theirInfo.javaClass.getMethod("getFiles"), theirInfo) as List<*>
            val paths = files.map { file ->
                invoke(file!!.javaClass.getMethod("getPath"), file) as String
            }
            if (!info.isValid || info.numFiles() != paths.size) throw IOException("bad catalog")
            return ExboDecodedTorrent(info, paths)
        }
    } finally {
        runCatching { Files.deleteIfExists(copy) }
    }
}

/**
 * Points each torrent entry at its real relative path. A path covered by [exclusions] (and `parts/`) is parked
 * under `parts/` and ignored, so an update does not overwrite configs or saves.
 * Returns one priority per file, in catalog order.
 */
internal fun mapClientFiles(info: TorrentInfo, paths: List<String>, exclusions: List<String>): Array<Priority> {
    if (info.numFiles() != paths.size) throw IOException("bad catalog")
    val storage = info.files()
    val priorities = Priority.array(Priority.NORMAL, paths.size)
    val soft = exclusions + "parts/"
    paths.forEachIndexed { index, raw ->
        val path = raw.replace('\\', '/')
        if (!safeRelative(path)) throw IOException("bad path")
        val excluded = exboPathExcluded(path, soft)
        storage.renameFile(index, if (excluded) "parts/$path" else path)
        if (excluded) priorities[index] = Priority.IGNORE
    }
    return priorities
}

/**
 * Downloads [catalog] into [clientDir]. Existing matching files are kept. Throws [ExboSyncStopped] when
 * [cancelled] becomes true or the worker is interrupted, and [IOException] when the swarm cannot be opened.
 * Does not write the version file and does not delete the client folder.
 */
internal fun syncClientTorrent(
    catalog: ByteArray,
    seeds: List<String>,
    exclusions: List<String>,
    clientDir: Path,
    launcherJar: Path,
    cancelled: () -> Boolean = { false },
    onProgress: (done: Long, total: Long, perSecond: Long, checking: Boolean) -> Unit = { _, _, _, _ -> },
) {
    val decoded = decodeExboTorrent(catalog, seeds, launcherJar)
    val priorities = mapClientFiles(decoded.info, decoded.paths, exclusions)
    val session = SessionManager()
    var handle: com.frostwire.jlibtorrent.TorrentHandle? = null
    try {
        session.start()
        session.download(decoded.info, clientDir.toFile(), null, priorities, emptyList<com.frostwire.jlibtorrent.TcpEndpoint>())
        val hash = decoded.info.infoHash()
        val deadline = System.nanoTime() + 60_000_000_000L
        while (handle == null || !handle.isValid) {
            park(200, cancelled)
            handle = session.find(hash)
            if ((handle == null || !handle.isValid) && System.nanoTime() > deadline) throw IOException("torrent")
        }
        while (true) {
            park(400, cancelled)
            val status = handle.status()
            val checking = status.state() == TorrentStatus.State.CHECKING_FILES ||
                status.state() == TorrentStatus.State.CHECKING_RESUME_DATA
            val wanted = status.totalWanted()
            val total = if (wanted > 0) wanted else 1L
            val done = if (checking) {
                (status.progress().toDouble() * total).toLong().coerceIn(0L, total)
            } else {
                status.totalWantedDone().coerceIn(0L, total)
            }
            onProgress(done, total, status.downloadRate().toLong(), checking)
            if (status.isFinished || status.isSeeding) return
            if (status.errorCode().isError()) throw IOException("torrent")
        }
    } finally {
        val current = handle
        if (current != null && current.isValid) runCatching { session.remove(current) }
        runCatching { session.stop() }
    }
}

private fun safeRelative(path: String): Boolean {
    if (path.isEmpty() || path.startsWith("/") || path.contains(':')) return false
    return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
}

private fun park(millis: Long, cancelled: () -> Boolean) {
    if (cancelled() || Thread.currentThread().isInterrupted) throw ExboSyncStopped()
    try {
        Thread.sleep(millis)
    } catch (_: InterruptedException) {
        throw ExboSyncStopped()
    }
}

private fun invoke(method: java.lang.reflect.Method, target: Any?, vararg args: Any?): Any? {
    try {
        return method.invoke(target, *args)
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    }
}

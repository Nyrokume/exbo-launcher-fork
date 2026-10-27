package dev.launcherfork.data.exbo

import dev.launcherfork.domain.Progress
import dev.launcherfork.domain.UpdatePhase
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Delete refused because the path is not a client directory. Nothing was removed. */
internal class UnsafeClientDelete : IOException("unsafe_path")

/**
 * Stable [dev.launcherfork.domain.ProgressListener.onFailed] code. Never a path, URL, or exception message.
 */
internal fun clientOperationFailure(error: Throwable): String = when (error) {
    is UnsafeClientDelete -> "unsafe_path"
    is ExboTransportException -> "no_session"
    is IOException -> when (error.message) {
        "no client" -> "no_client"
        "no seeds" -> "no_seeds"
        "file mismatch" -> "file_mismatch"
        "no catalog" -> "no_catalog"
        else -> "io"
    }
    else -> "error"
}

/**
 * Checks the client at [directory] against [catalog] and downloads only the files that do not match.
 * Exclusions and paths outside the directory are left untouched, the same way [syncClientFiles] does.
 * Throws [IOException] with a stable message (`no client`, `no catalog`, `no seeds`, `file mismatch`)
 * and [ExboSyncStopped] when [cancelled] becomes true. Does not write `runtime/<id>.hash`.
 */
internal fun verifyClientFiles(
    directory: Path,
    catalog: ByteArray,
    seeds: List<String>,
    exclusions: List<String>,
    open: (String) -> InputStream,
    cancelled: () -> Boolean = { false },
    onPhase: (UpdatePhase) -> Unit = {},
    onProgress: (Progress) -> Unit = {},
) {
    if (!clientFilesPresent(directory)) throw IOException("no client")
    val torrent = try {
        parseExboTorrent(catalog)
    } catch (_: IOException) {
        throw IOException("no catalog")
    }
    if (torrent.files.isEmpty()) throw IOException("no catalog")
    onPhase(UpdatePhase.CHECKING)
    var fetching = false
    syncClientFiles(
        directory = directory,
        torrent = torrent,
        seeds = seeds,
        exclusions = exclusions,
        open = { url ->
            if (!fetching) {
                fetching = true
                onPhase(UpdatePhase.DOWNLOADING)
            }
            open(url)
        },
        cancelled = cancelled,
    ) { done, total, rate ->
        val fraction = if (total <= 0L) 0.0 else done.toDouble() / total.toDouble()
        onProgress(Progress(fraction.coerceIn(0.0, 1.0), done, total, rate))
    }
}

/**
 * Deletes [clientDir] and everything strictly inside it.
 * Refuses the EXBO root, `runtime`, `launcher`, `java`, `runtime/logs`, the user home, a filesystem root,
 * a symlink, and any folder that would contain those. A mapped install outside the EXBO tree is allowed
 * when it is not an ancestor of the bootstrap. Does not remove `runtime/<id>.hash` or any other file
 * outside [clientDir]. Throws [UnsafeClientDelete] before the first delete when the path is refused,
 * and [ExboSyncStopped] when [cancelled] becomes true.
 */
internal fun deleteClientDirectory(
    clientDir: Path,
    exboRoot: Path,
    cancelled: () -> Boolean = { false },
    home: Path = Path.of(System.getProperty("user.home")),
    onProgress: (Double) -> Unit = {},
) {
    val exbo = existingReal(exboRoot)
    val homeDir = existingReal(home)
    val lexical = clientDir.toAbsolutePath().normalize()
    ensureDeletableClient(lexical, exbo, homeDir)
    if (!Files.exists(lexical, LinkOption.NOFOLLOW_LINKS)) {
        onProgress(1.0)
        return
    }
    if (Files.isSymbolicLink(lexical) || !Files.isDirectory(lexical, LinkOption.NOFOLLOW_LINKS)) {
        throw UnsafeClientDelete()
    }
    val root = lexical.toRealPath()
    if (root != lexical) ensureDeletableClient(root, exbo, homeDir)
    wipeTree(root, cancelled, onProgress)
}

internal fun ensureDeletableClient(clientDir: Path, exboRoot: Path, home: Path) {
    val root = clientDir.toAbsolutePath().normalize()
    val exbo = exboRoot.toAbsolutePath().normalize()
    val homeDir = home.toAbsolutePath().normalize()
    if (root.nameCount < 2) throw UnsafeClientDelete()
    if (root == homeDir || homeDir.startsWith(root)) throw UnsafeClientDelete()
    val runtime = exbo.resolve("runtime")
    val protected = listOf(
        exbo,
        runtime,
        exbo.resolve("launcher"),
        exbo.resolve("java"),
        runtime.resolve("logs"),
    )
    if (protected.any { it == root || it.startsWith(root) }) throw UnsafeClientDelete()
    if (!root.startsWith(exbo)) return
    if (!root.startsWith(runtime)) throw UnsafeClientDelete()
    val relative = runtime.relativize(root)
    if (relative.nameCount != 1) throw UnsafeClientDelete()
    val id = relative.getName(0).toString()
    if (!CLIENT_DIR_ID.matches(id) || id.lowercase() in RESERVED_RUNTIME_NAMES) throw UnsafeClientDelete()
}

private fun existingReal(path: Path): Path {
    val normalized = path.toAbsolutePath().normalize()
    if (!Files.exists(normalized)) return normalized
    return try {
        normalized.toRealPath()
    } catch (_: IOException) {
        normalized
    }
}

private fun wipeTree(root: Path, cancelled: () -> Boolean, onProgress: (Double) -> Unit) {
    val base = root.toAbsolutePath().normalize()
    var total = 0
    var stop = false
    Files.walkFileTree(base, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (stopRequested(cancelled)) {
                stop = true
                return FileVisitResult.TERMINATE
            }
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (stopRequested(cancelled)) {
                stop = true
                return FileVisitResult.TERMINATE
            }
            total++
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = throw exc
    })
    if (stop) throw ExboSyncStopped()
    var done = 0
    fun report() {
        val fraction = if (total == 0) 0.0 else done.toDouble() / total.toDouble()
        onProgress(fraction.coerceIn(0.0, 1.0))
    }
    Files.walkFileTree(base, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (stopRequested(cancelled)) {
                stop = true
                return FileVisitResult.TERMINATE
            }
            ensureInside(base, file)
            Files.deleteIfExists(file)
            done++
            report()
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            if (exc != null) throw exc
            val current = dir.toAbsolutePath().normalize()
            if (current == base) return FileVisitResult.CONTINUE
            if (stopRequested(cancelled)) {
                stop = true
                return FileVisitResult.TERMINATE
            }
            ensureInside(base, current)
            Files.deleteIfExists(current)
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = throw exc
    })
    if (stop) throw ExboSyncStopped()
    Files.deleteIfExists(base)
    onProgress(1.0)
}

private fun ensureInside(root: Path, path: Path) {
    val target = path.toAbsolutePath().normalize()
    if (target != root && !target.startsWith(root)) throw UnsafeClientDelete()
}

private fun stopRequested(cancelled: () -> Boolean): Boolean =
    cancelled() || Thread.currentThread().isInterrupted

/** Same shape as [ExboClients]'s id check. `logs` and `clients` are runtime files, not a game. */
private val CLIENT_DIR_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
private val RESERVED_RUNTIME_NAMES = setOf("logs", "clients", "launcher", "java")

package dev.launcherfork.data.exbo

import dev.launcherfork.domain.ExboPaths
import java.io.DataInputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Read-only view of the official `%APPDATA%/EXBO/runtime/clients` file:
 * a big-endian count, then that many `DataInputStream.readUTF` id/path pairs.
 * This code never writes the file. An id that is absent falls back to `runtime/<id>`.
 */
internal object ExboClients {
    private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")

    fun clientsFile(root: Path = ExboPaths.roamingRoot()): Path = root.resolve("runtime").resolve("clients")

    fun runtimeDir(root: Path = ExboPaths.roamingRoot()): Path = root.resolve("runtime")

    fun directory(
        id: String,
        clientsFile: Path = clientsFile(),
        runtime: Path = runtimeDir(),
    ): Path {
        val fallback = runtime.resolve(if (SAFE_ID.matches(id)) id else "invalid")
        if (!SAFE_ID.matches(id)) return fallback
        val mapped = readClientsLocations(clientsFile)[id]?.takeIf { it.isNotBlank() } ?: return fallback
        return Path.of(mapped)
    }

    /** First mapped directory, or the runtime folder when the file has no rows. */
    fun preferredDirectory(clientsFile: Path = clientsFile(), runtime: Path = runtimeDir()): Path {
        val mapped = readClientsLocations(clientsFile).values.firstOrNull { it.isNotBlank() }
        return if (mapped != null) Path.of(mapped) else runtime
    }

    /**
     * Official `DownloadManager.saveTorrentHash`: `<runtime>/<id>.hash` holds the catalog version
     * of a finished client. `<client>/version` is a file that ships inside the game.
     */
    fun installedHashFile(id: String, runtime: Path = runtimeDir()): Path? {
        if (!SAFE_ID.matches(id)) return null
        return runtime.resolve("$id.hash")
    }
}

/** Catalog version recorded for [id], or null when the official hash file is missing. */
internal fun readInstalledHash(id: String, runtime: Path = ExboClients.runtimeDir()): String? {
    val file = ExboClients.installedHashFile(id, runtime) ?: return null
    if (!Files.isRegularFile(file)) return null
    return try {
        Files.readString(file)
    } catch (_: IOException) {
        null
    }
}

/** Writes the catalog version the way `saveTorrentHash` does. Returns false for an unsafe id or a blank version. */
internal fun writeInstalledHash(id: String, version: String, runtime: Path): Boolean {
    val file = ExboClients.installedHashFile(id, runtime) ?: return false
    val text = version.trim()
    if (text.isEmpty()) return false
    Files.createDirectories(file.parent)
    val part = file.resolveSibling("${file.fileName}.part")
    Files.writeString(part, text)
    try {
        Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(part, file, StandardCopyOption.REPLACE_EXISTING)
    }
    return true
}

internal fun readClientsLocations(file: Path): Map<String, String> {
    val source = when {
        Files.isRegularFile(file) -> file
        Files.isRegularFile(siblingOld(file)) -> siblingOld(file)
        else -> return emptyMap()
    }
    return try {
        DataInputStream(Files.newInputStream(source)).use { input ->
            val count = input.readInt()
            if (count < 0 || count > 10_000) return emptyMap()
            buildMap {
                repeat(count) {
                    val id = input.readUTF()
                    val path = input.readUTF()
                    if (id.isNotEmpty()) put(id, path)
                }
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun siblingOld(file: Path): Path = file.resolveSibling("${file.fileName}.old")

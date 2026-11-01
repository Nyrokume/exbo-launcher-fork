package dev.launcherfork.data.exbo

import dev.launcherfork.domain.ExboPaths
import dev.launcherfork.domain.Server
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
        val locations = readClientsLocations(clientsFile)
        val resolved = resolveId(id, locations, runtime)
        val fallback = runtime.resolve(if (SAFE_ID.matches(resolved)) resolved else "invalid")
        if (!SAFE_ID.matches(resolved)) return fallback
        val mapped = locations[resolved]?.takeIf { it.isNotBlank() } ?: return fallback
        return Path.of(mapped)
    }

    /**
     * Official EU maps the game as `stalcraft_global`. `/listServers` still sends `stalcraft`.
     * A leftover `runtime/stalcraft` next to that mapping is not the client.
     */
    fun resolveId(
        id: String,
        locations: Map<String, String>,
        runtime: Path,
    ): String {
        val mapped = resolveMappedId(id, locations)
        if (mapped != id || id in locations) return mapped
        val hashed = (listOf(id) + clientAliases(id)).filter { Files.isRegularFile(runtime.resolve("$it.hash")) }
        if (hashed.isNotEmpty()) {
            return hashed.maxBy { Files.getLastModifiedTime(runtime.resolve("$it.hash")).toMillis() }
        }
        return id
    }

    fun resolveMappedId(id: String, locations: Map<String, String>): String {
        if (!SAFE_ID.matches(id)) return id
        if (id in locations) return id
        return clientAliases(id).firstOrNull { it in locations } ?: id
    }

    fun resolveId(id: String, clientsFile: Path = clientsFile(), runtime: Path = runtimeDir()): String =
        resolveId(id, readClientsLocations(clientsFile), runtime)

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

/** A client directory counts as installed when it contains the game's `classes.jar`. */
internal fun clientFilesPresent(directory: Path): Boolean =
    Files.isDirectory(directory) && Files.isRegularFile(directory.resolve("classes.jar"))

/**
 * Disk truth behind ИГРАТЬ / Обновить. [present] is [clientFilesPresent] in the directory
 * [ExboClients.directory] already maps. [current] is that install whose `runtime/<id>.hash` matches
 * [remoteVersion]. A missing or different hash is still installed: the button says Обновить.
 * [dev.launcherfork.domain.Server.installed] is not read.
 */
internal data class DiskInstall(val present: Boolean, val current: Boolean)

internal fun diskInstall(directory: Path, remoteVersion: String?, installedHash: String?): DiskInstall {
    val present = clientFilesPresent(directory)
    if (!present) return DiskInstall(present = false, current = false)
    val needsUpdate = installedClientNeedsUpdate(remoteVersion, installedHash, clientPresent = true)
    return DiskInstall(present = true, current = !needsUpdate)
}

internal fun clientAliases(id: String): List<String> = when (id) {
    "stalcraft" -> listOf("stalcraft_global")
    "stalcraft_global" -> listOf("stalcraft")
    else -> emptyList()
}

/**
 * One card per install. When `/listServers` sends both `stalcraft` and `stalcraft_global`,
 * keep the id the official `clients` file already maps.
 */
internal fun collapseAliasedServers(
    servers: List<Server>,
    locations: Map<String, String>,
    runtime: Path,
): List<Server> {
    val kept = linkedMapOf<String, Server>()
    for (server in servers) {
        val canonical = ExboClients.resolveId(server.id, locations, runtime)
        val existing = kept[canonical]
        if (existing == null) {
            kept[canonical] = server
            continue
        }
        val preferNew = (server.id == canonical && existing.id != canonical) ||
            (server.id in locations && existing.id !in locations)
        if (preferNew) kept[canonical] = server
    }
    return kept.values.toList()
}

/** Catalog version recorded for [id], or null when the official hash file is missing. */
internal fun readInstalledHash(id: String, runtime: Path = ExboClients.runtimeDir()): String? {
    val resolved = ExboClients.resolveId(id, runtime.resolve("clients"), runtime)
    val file = ExboClients.installedHashFile(resolved, runtime) ?: return null
    if (!Files.isRegularFile(file)) return null
    return try {
        Files.readString(file)
    } catch (_: IOException) {
        null
    }
}

/** Writes the catalog version the way `saveTorrentHash` does. Returns false for an unsafe id or a blank version. */
internal fun writeInstalledHash(id: String, version: String, runtime: Path): Boolean {
    val locations = readClientsLocations(runtime.resolve("clients"))
    val resolved = ExboClients.resolveMappedId(id, locations)
    val file = ExboClients.installedHashFile(resolved, runtime) ?: return false
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

/** Newest official main-client hash in [root], or null when that install has no hash yet. */
internal fun mainClientTouchedAt(root: Path): Long? {
    val runtime = ExboClients.runtimeDir(root)
    val locations = readClientsLocations(ExboClients.clientsFile(root))
    return listOf("stalcraft", "stalcraft_global").mapNotNull { raw ->
        val id = ExboClients.resolveId(raw, locations, runtime)
        val file = ExboClients.installedHashFile(id, runtime) ?: return@mapNotNull null
        if (!Files.isRegularFile(file)) return@mapNotNull null
        Files.getLastModifiedTime(file).toMillis()
    }.maxOrNull()
}

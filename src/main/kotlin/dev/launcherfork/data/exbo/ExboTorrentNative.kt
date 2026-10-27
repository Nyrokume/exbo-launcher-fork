package dev.launcherfork.data.exbo

import dev.launcherfork.domain.ExboPaths
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Points jlibtorrent at the 64-bit DLL shipped with this launcher before its classes load their native library.
 * The DLL next to the installed EXBO launcher is 32-bit and is left untouched.
 *
 * jlibtorrent calls `System.load` on `jlibtorrent.jni.path` from its static initializer. The file used to be
 * whatever that property already named, or a temp copy kept when it was merely larger than 100 KB. The property
 * is set here only after the bytes match the DLL pinned below, and a path that does not match fails closed.
 */
private object ExboTorrentNativeMarker

internal const val JLIBTORRENT_JNI_PROPERTY = "jlibtorrent.jni.path"
internal const val JLIBTORRENT_LIBRARY_NAME = "jlibtorrent-1.2.19.0.dll"

/** SHA-256 of `lib/x86_64/jlibtorrent-1.2.19.0.dll` inside `jlibtorrent-windows-1.2.19.0.jar`. */
internal const val JLIBTORRENT_SHA256 = "8963273dd3c073e90acc92c83018213c57eab4af173a16be9d9cdefd4eea1744"

internal fun exboTorrentNativeCache(exboRoot: Path = ExboPaths.roamingRoot()): Path =
    exboRoot.resolve("launcher-fork").resolve("native")

internal fun bundledExboTorrentLibrary(): ByteArray {
    val stream = ExboTorrentNativeMarker::class.java.classLoader
        .getResourceAsStream("lib/x86_64/$JLIBTORRENT_LIBRARY_NAME")
        ?: throw IOException("no torrent library")
    return stream.use { it.readBytes() }
}

internal fun torrentNativeMatches(path: Path): Boolean {
    if (Files.isSymbolicLink(path)) return false
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false
    return try {
        exboSha256Hex(path).equals(JLIBTORRENT_SHA256, ignoreCase = true)
    } catch (_: IOException) {
        false
    }
}

/**
 * Makes [JLIBTORRENT_JNI_PROPERTY] name a regular file whose SHA-256 is [JLIBTORRENT_SHA256].
 * [libraryBytes] defaults to the DLL on the classpath. A configured path that fails the check is cleared
 * and the call throws [TORRENT_INTEGRITY_ERROR] without loading the library.
 */
internal fun ensureExboTorrentNative(
    cacheDir: Path = exboTorrentNativeCache(),
    libraryBytes: ByteArray? = null,
) {
    val configured = System.getProperty(JLIBTORRENT_JNI_PROPERTY)
    if (!configured.isNullOrBlank()) {
        val path = jniPathOrNull(configured)
        if (path != null && torrentNativeMatches(path)) return
        System.clearProperty(JLIBTORRENT_JNI_PROPERTY)
        throw IOException(TORRENT_INTEGRITY_ERROR)
    }
    val bytes = libraryBytes ?: bundledExboTorrentLibrary()
    if (!exboSha256Hex(bytes).equals(JLIBTORRENT_SHA256, ignoreCase = true)) {
        throw IOException(TORRENT_INTEGRITY_ERROR)
    }
    val dest = installVerifiedNative(cacheDir, bytes)
    retireLegacyTorrentTemp(dest)
    System.setProperty(JLIBTORRENT_JNI_PROPERTY, dest.toAbsolutePath().toString())
}

private fun installVerifiedNative(cacheDir: Path, bytes: ByteArray): Path {
    val parent = cacheDir.parent
    if ((parent != null && Files.isSymbolicLink(parent)) || Files.isSymbolicLink(cacheDir)) {
        throw IOException(TORRENT_INTEGRITY_ERROR)
    }
    Files.createDirectories(cacheDir)
    val rootReal = cacheDir.toRealPath()
    val dest = cacheDir.resolve(JLIBTORRENT_LIBRARY_NAME).toAbsolutePath().normalize()
    if (!dest.parent.toRealPath().startsWith(rootReal)) throw IOException(TORRENT_INTEGRITY_ERROR)
    if (Files.isSymbolicLink(dest)) Files.delete(dest)
    if (Files.isDirectory(dest)) deleteTree(dest)
    if (torrentNativeMatches(dest)) return dest
    val part = cacheDir.resolve("$JLIBTORRENT_LIBRARY_NAME.part").toAbsolutePath().normalize()
    if (Files.isSymbolicLink(part)) Files.delete(part)
    Files.write(part, bytes)
    if (realPathEscapes(rootReal, part) || !torrentNativeMatches(part)) {
        Files.deleteIfExists(part)
        throw IOException(TORRENT_INTEGRITY_ERROR)
    }
    try {
        Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING)
    }
    if (Files.isSymbolicLink(dest) || realPathEscapes(rootReal, dest) || !torrentNativeMatches(dest)) {
        if (dest.toAbsolutePath().normalize().startsWith(cacheDir.toAbsolutePath().normalize())) {
            Files.deleteIfExists(dest)
        }
        throw IOException(TORRENT_INTEGRITY_ERROR)
    }
    return dest
}

private fun jniPathOrNull(configured: String): Path? =
    try {
        Path.of(configured)
    } catch (_: InvalidPathException) {
        null
    }

private fun realPathEscapes(rootReal: Path, path: Path): Boolean =
    try {
        !path.toRealPath().startsWith(rootReal)
    } catch (_: IOException) {
        true
    }

private fun retireLegacyTorrentTemp(keep: Path) {
    val legacy = Path.of(System.getProperty("java.io.tmpdir"), "launcher-fork", JLIBTORRENT_LIBRARY_NAME)
    if (legacy.toAbsolutePath().normalize() == keep.toAbsolutePath().normalize()) return
    try {
        if (Files.isSymbolicLink(legacy) || Files.isRegularFile(legacy, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(legacy)
        }
    } catch (_: IOException) {
        // The property is already aimed at the checked file. The old temp copy is not loaded from here.
    }
}

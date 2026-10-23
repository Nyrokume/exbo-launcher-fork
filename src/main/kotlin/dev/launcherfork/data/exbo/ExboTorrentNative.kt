package dev.launcherfork.data.exbo

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Points jlibtorrent at the 64-bit DLL shipped with this launcher before its classes load their native library.
 * The DLL next to the installed EXBO launcher is 32-bit and is left untouched.
 */
private object ExboTorrentNativeMarker

internal fun ensureExboTorrentNative() {
    val configured = System.getProperty("jlibtorrent.jni.path")
    if (!configured.isNullOrBlank() && Files.isRegularFile(Path.of(configured))) return
    val name = "jlibtorrent-1.2.19.0.dll"
    val dest = Path.of(System.getProperty("java.io.tmpdir"), "launcher-fork", name)
    if (!Files.isRegularFile(dest) || Files.size(dest) < 100_000) {
        val stream = ExboTorrentNativeMarker::class.java.classLoader.getResourceAsStream("lib/x86_64/$name")
            ?: throw IOException("no torrent library")
        Files.createDirectories(dest.parent)
        val part = dest.resolveSibling("$name.part")
        stream.use { input ->
            Files.newOutputStream(part).use { output -> input.copyTo(output) }
        }
        try {
            Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING)
        }
    }
    System.setProperty("jlibtorrent.jni.path", dest.toAbsolutePath().toString())
}

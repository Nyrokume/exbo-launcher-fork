package dev.launcherfork

import org.opentest4j.TestAbortedException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates a symbolic link, or skips the test when this Windows account is not allowed to.
 * A normal user without Developer Mode gets "privilege not held"; that is the OS, not the launcher.
 */
fun symLink(link: Path, target: Path): Path = try {
    Files.createSymbolicLink(link, target)
} catch (e: IOException) {
        if (!symbolicLinksPermitted) throw TestAbortedException("symbolic links are not permitted for this account")
    throw e
}

private val symbolicLinksPermitted: Boolean by lazy {
    val dir = Files.createTempDirectory("symlink-probe")
    try {
        Files.writeString(dir.resolve("t"), "x")
        Files.createSymbolicLink(dir.resolve("l"), dir.resolve("t"))
        true
    } catch (_: IOException) {
        false
    } finally {
        dir.toFile().deleteRecursively()
    }
}

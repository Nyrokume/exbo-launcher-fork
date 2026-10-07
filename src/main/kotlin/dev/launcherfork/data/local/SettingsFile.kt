package dev.launcherfork.data.local

import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * `~/.launcher-fork/settings.properties`: a few independent keys (`appearance`, `animations`, `wallpaper.last`). Each writer updates
 * its own key and keeps the others (read, change, write a temp file next to it, move it over atomically), so a crash
 * mid-write never leaves a truncated file. Read / write errors are logged, never thrown (everything kept here is cosmetic).
 */
object SettingsFile {
    const val COMMENT = "launcher-fork settings: appearance = dark | light, animations = on | off, wallpaper.last = number of the last wallpaper"

    /** All keys of [file]; empty when it is missing or unreadable. */
    fun load(file: Path): Properties = Properties().apply {
        try {
            if (Files.isRegularFile(file)) Files.newBufferedReader(file).use { load(it) }
        } catch (e: IOException) {
            System.err.println("[launcher-fork] WARNING: cannot read $file: ${e.message}")
        } catch (e: IllegalArgumentException) { // malformed \\uXXXX escape
            System.err.println("[launcher-fork] WARNING: cannot read $file: ${e.message}")
        }
    }

    fun read(file: Path, key: String): String? = load(file).getProperty(key)

    /** Sets [key] to [value] and keeps every other key of [file]. */
    fun write(file: Path, key: String, value: String) {
        var tmp: Path? = null
        try {
            val target = file.toAbsolutePath()
            val dir = requireNotNull(target.parent) { "no parent directory for $target" }
            Files.createDirectories(dir)
            val props = load(target)
            props.setProperty(key, value)
            tmp = Files.createTempFile(dir, "${target.fileName}.", ".tmp")
            Files.newBufferedWriter(tmp).use { props.store(it, COMMENT) }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            }
            tmp = null
        } catch (e: IOException) {
            System.err.println("[launcher-fork] WARNING: cannot save $key to $file: ${e.message}")
        } catch (e: IllegalArgumentException) {
            System.err.println("[launcher-fork] WARNING: cannot save $key to $file: ${e.message}")
        } finally {
            tmp?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }
}

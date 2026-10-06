package dev.launcherfork.data.local

import dev.launcherfork.domain.Appearance
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.SettingsStore
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * Settings store that keeps ONE field on disk: [LauncherSettings.appearance] («Тема: Тёмная / Белая»), so the chosen
 * scheme survives a restart. Everything else stays in [delegate] (the fork's session-only in-memory store). The file is
 * a tiny properties file (`appearance=light`), written (atomically) only when the appearance changes; a missing, unreadable or
 * unknown value means [Appearance.DARK]. I/O errors are logged, never thrown (the theme is cosmetic).
 * LauncherApp uses `~/.launcher-fork/settings.properties` (-Dlauncher.settingsFile=<path> to move it).
 */
class AppearanceFileStore(private val delegate: SettingsStore, private val file: Path) : SettingsStore {
    init {
        val stored = read(file)
        if (stored != null && stored != delegate.load().appearance) delegate.save(delegate.load().copy(appearance = stored))
    }

    override fun load(): LauncherSettings = delegate.load()

    override fun save(settings: LauncherSettings) {
        val old = delegate.load().appearance
        delegate.save(settings)
        if (settings.appearance != old) write(file, settings.appearance)
    }

    companion object {
        const val KEY = "appearance"

        fun read(file: Path): Appearance? = try {
            if (!Files.isRegularFile(file)) null
            else Properties().apply { Files.newBufferedReader(file).use { load(it) } }.getProperty(KEY)
                ?.trim()?.uppercase()?.let { v -> Appearance.entries.firstOrNull { it.name == v } }
        } catch (e: IOException) {
            System.err.println("[launcher-fork] WARNING: cannot read $file: ${e.message}")
            null
        } catch (e: IllegalArgumentException) {
            null
        }

        /**
         * Writes a temp file next to [file], then moves it over [file] atomically (plain replace where the file system
         * has no atomic move), so a crash mid-write never leaves a truncated file. Errors are logged, the temp removed.
         */
        fun write(file: Path, appearance: Appearance) {
            var tmp: Path? = null
            try {
                val target = file.toAbsolutePath()
                val dir = requireNotNull(target.parent) { "no parent directory for $target" }
                Files.createDirectories(dir)
                val props = Properties()
                props.setProperty(KEY, appearance.name.lowercase())
                tmp = Files.createTempFile(dir, "${target.fileName}.", ".tmp")
                Files.newBufferedWriter(tmp).use { props.store(it, "launcher-fork: colour scheme (dark | light)") }
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
                }
                tmp = null
            } catch (e: IOException) {
                System.err.println("[launcher-fork] WARNING: cannot save the theme to $file: ${e.message}")
            } catch (e: IllegalArgumentException) {
                System.err.println("[launcher-fork] WARNING: cannot save the theme to $file: ${e.message}")
            } finally {
                tmp?.let { runCatching { Files.deleteIfExists(it) } }
            }
        }
    }
}

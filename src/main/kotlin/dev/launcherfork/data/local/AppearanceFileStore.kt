package dev.launcherfork.data.local

import dev.launcherfork.domain.Appearance
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.SettingsStore
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Settings store that keeps ONE field on disk: [LauncherSettings.appearance] («Тема: Тёмная / Белая»), so the chosen
 * scheme survives a restart. Everything else stays in [delegate] (the fork's session-only in-memory store). The file is
 * a tiny properties file (`appearance=light`), written only when the appearance changes; a missing, unreadable or
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

        fun write(file: Path, appearance: Appearance) {
            try {
                file.toAbsolutePath().parent?.let { Files.createDirectories(it) }
                val props = Properties()
                props.setProperty(KEY, appearance.name.lowercase())
                Files.newBufferedWriter(file).use { props.store(it, "launcher-fork: colour scheme (dark | light)") }
            } catch (e: IOException) {
                System.err.println("[launcher-fork] WARNING: cannot save the theme to $file: ${e.message}")
            }
        }
    }
}

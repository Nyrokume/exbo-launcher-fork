package dev.launcherfork.data.local

import dev.launcherfork.domain.Appearance
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.SettingsStore
import java.nio.file.Path

/**
 * Settings store that keeps ONE field on disk: [LauncherSettings.appearance] («Тема: Тёмная / Белая»), so the chosen
 * scheme survives a restart. Everything else stays in [delegate] (the fork's session-only in-memory store). The file is
 * a tiny properties file (`appearance=light`, shared with `wallpaper.last`), written (atomically) only when the appearance changes; a missing, unreadable or
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

        fun read(file: Path): Appearance? = SettingsFile.read(file, KEY)
            ?.trim()?.uppercase()?.let { v -> Appearance.entries.firstOrNull { it.name == v } }

        /**
         * Updates `appearance` in [file] and keeps its other keys (`wallpaper.last`): [SettingsFile] writes a temp file
         * next to it and moves it over atomically, so a crash mid-write never leaves a truncated file. Errors are logged.
         */
        fun write(file: Path, appearance: Appearance) = SettingsFile.write(file, KEY, appearance.name.lowercase())
    }
}

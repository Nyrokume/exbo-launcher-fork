package dev.launcherfork.data.local

import dev.launcherfork.domain.Appearance
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.SettingsStore
import dev.launcherfork.ui.motion.Motion
import java.nio.file.Path

/**
 * Settings store that keeps TWO cosmetic fields on disk: [LauncherSettings.appearance] («Тема: Тёмная / Белая») and
 * [LauncherSettings.animations] («Анимации», key `animations=on|off`), so they survive a restart. Everything else stays
 * in [delegate] (the fork's session-only in-memory store). The file is a tiny properties file (`appearance=light`,
 * `animations=off`, shared with `wallpaper.last`); a key is written (atomically) only when its field changes; a missing,
 * unreadable or unknown value means [Appearance.DARK] / animations on. I/O errors are logged, never thrown.
 * LauncherApp uses `~/.launcher-fork/settings.properties` (-Dlauncher.settingsFile=<path> to move it).
 */
class AppearanceFileStore(private val delegate: SettingsStore, private val file: Path) : SettingsStore {
    init {
        val stored = read(file)
        if (stored != null && stored != delegate.load().appearance) delegate.save(delegate.load().copy(appearance = stored))
        val animations = readAnimations(file)
        if (animations != null && animations != delegate.load().animations) delegate.save(delegate.load().copy(animations = animations))
    }

    override fun load(): LauncherSettings = delegate.load()

    override fun save(settings: LauncherSettings) {
        val old = delegate.load()
        delegate.save(settings)
        if (settings.appearance != old.appearance) write(file, settings.appearance)
        if (settings.animations != old.animations) SettingsFile.write(file, ANIMATIONS_KEY, Motion.settingValue(settings.animations))
    }

    companion object {
        const val KEY = "appearance"
        const val ANIMATIONS_KEY = Motion.SETTINGS_KEY

        /** `animations=on|off` of [file]; null when missing or not on / off (the default, on, applies). */
        fun readAnimations(file: Path): Boolean? = Motion.parse(SettingsFile.read(file, ANIMATIONS_KEY))

        fun read(file: Path): Appearance? = SettingsFile.read(file, KEY)
            ?.trim()?.uppercase()?.let { v -> Appearance.entries.firstOrNull { it.name == v } }

        /**
         * Updates `appearance` in [file] and keeps its other keys (`wallpaper.last`): [SettingsFile] writes a temp file
         * next to it and moves it over atomically, so a crash mid-write never leaves a truncated file. Errors are logged.
         */
        fun write(file: Path, appearance: Appearance) = SettingsFile.write(file, KEY, appearance.name.lowercase())
    }
}

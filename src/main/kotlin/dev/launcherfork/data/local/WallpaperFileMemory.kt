package dev.launcherfork.data.local

import dev.launcherfork.wallpaper.WallpaperMemory
import java.nio.file.Path

/**
 * The previous launch's wallpaper as `wallpaper.last=N` in settings.properties ([SettingsFile]).
 * A missing or non-numeric value means "unknown" (any wallpaper may come next).
 */
class WallpaperFileMemory(private val file: Path) : WallpaperMemory {
    override fun last(): Int? = SettingsFile.read(file, KEY)?.trim()?.toIntOrNull()

    override fun remember(number: Int) = SettingsFile.write(file, KEY, number.toString())

    companion object {
        const val KEY = "wallpaper.last"
    }
}

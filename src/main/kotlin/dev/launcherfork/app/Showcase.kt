package dev.launcherfork.app

import java.io.File

/**
 * Showcase mode (docs/SHOWCASE.md): `-Dlauncher.showcase=true` runs the launcher with mock data only and auto-plays a
 * looping presentation of the redesign ([ShowcaseTour]) with the animations on. It keeps NOTHING: no settings file (an
 * in-memory store; -Dlauncher.settingsFile is ignored), no `wallpaper.last`, no theme / appearance written anywhere.
 *
 * Properties: `launcher.showcase.size=WxH` (main window, 1366x800 by default), `launcher.showcase.captions=true|false`
 * (small step caption in the title strip, on by default), `launcher.showcase.stills=<dir>` (one clean PNG per step of
 * the first loop, without the caption), `launcher.showcase.loops=N` (exit after N loops; 0 = forever, the default).
 */
data class Showcase(
    val width: Double = DEFAULT_WIDTH,
    val height: Double = DEFAULT_HEIGHT,
    val captions: Boolean = true,
    val stillsDir: File? = null,
    val loops: Int = 0,
) {
    companion object {
        const val PROPERTY = "launcher.showcase"
        const val SIZE_PROPERTY = "launcher.showcase.size"
        const val CAPTIONS_PROPERTY = "launcher.showcase.captions"
        const val STILLS_PROPERTY = "launcher.showcase.stills"
        const val LOOPS_PROPERTY = "launcher.showcase.loops"
        const val DEFAULT_WIDTH = 1366.0
        const val DEFAULT_HEIGHT = 800.0
        /** The main window's own minimum (Theme.WINDOW_MIN_*): a smaller size is raised to it. */
        private const val MIN_WIDTH = 900.0
        private const val MIN_HEIGHT = 580.0

        /** "1920x1080" (also "1920X1080", "1920*1080") -> 1920.0 to 1080.0; null when not a size. */
        fun parseSize(value: String?): Pair<Double, Double>? {
            val m = Regex("""^\s*(\d{3,5})\s*[xX×*]\s*(\d{3,5})\s*$""").matchEntire(value ?: return null) ?: return null
            return m.groupValues[1].toDouble().coerceAtLeast(MIN_WIDTH) to m.groupValues[2].toDouble().coerceAtLeast(MIN_HEIGHT)
        }

        /** The showcase asked for by system properties, null when it is off. */
        fun fromProperties(props: (String) -> String? = System::getProperty): Showcase? {
            if (props(PROPERTY)?.trim()?.lowercase() !in setOf("true", "on", "yes", "1")) return null
            val size = parseSize(props(SIZE_PROPERTY))
            return Showcase(
                width = size?.first ?: DEFAULT_WIDTH,
                height = size?.second ?: DEFAULT_HEIGHT,
                captions = props(CAPTIONS_PROPERTY)?.trim()?.lowercase() !in setOf("false", "off", "no", "0"),
                stillsDir = props(STILLS_PROPERTY)?.takeIf { it.isNotBlank() }?.let(::File),
                loops = props(LOOPS_PROPERTY)?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            )
        }
    }
}

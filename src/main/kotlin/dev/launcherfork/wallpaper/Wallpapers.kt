package dev.launcherfork.wallpaper

import java.net.URL
import kotlin.random.Random

/**
 * One bundled wallpaper: a row of `ui/wallpapers/wallpapers.txt`. [number] is 1-based and is the same everywhere: file
 * `0N.jpg`, `-Dlauncher.wallpaper=N`, `wallpaper.last=N` in settings.properties. [focusX] / [focusY] (0..1 of the
 * picture) is the point that cover-fit keeps in view when the window crops the picture ([CoverFit]). [bright] marks a
 * light picture (overcast sky, daylight): the window then uses the theme's stronger scrim (`-wallpaper-scrim-bright`
 * in dark themes, a lighter one in the white scheme), so dark pictures are not dimmed more than they need.
 */
data class Wallpaper(
    val number: Int,
    val resource: String,
    val focusX: Double = 0.5,
    val focusY: Double = 0.5,
    val title: String = "",
    val bright: Boolean = false,
)

/** Where the previous launch's wallpaper is kept (LauncherApp: `wallpaper.last` in settings.properties). */
interface WallpaperMemory {
    /** Number of the previous launch's wallpaper; null when unknown (no file, missing or corrupt value). */
    fun last(): Int?
    fun remember(number: Int)
}

/**
 * Main-window wallpapers (the original EXBO launcher draws game art behind the whole window): a fixed list read from a
 * manifest (works inside the jar, no directory scanning), a random pick per launch that never repeats the previous
 * launch, and ONE holder ([current]) so every window of a launch shows the same picture.
 *
 * Use: LauncherApp calls [choose] once at start, before any window exists; the main window asks
 * `WallpaperLayer.forCurrent(...)` (ui/components) for its background node. The login window stays solid
 * (see docs/design/STYLE-ZONE.md, «Обои»).
 */
object Wallpapers {
    /** `-Dlauncher.wallpaper=N` (1..11): always this wallpaper (screenshots, reproducible checks). */
    const val PROPERTY = "launcher.wallpaper"
    const val ROOT = "/dev/launcherfork/ui/wallpapers/"
    const val MANIFEST = ROOT + "wallpapers.txt"
    /** Wallpaper of the screenshot tour (docs/screenshots) when -Dlauncher.wallpaper is not given: 03, watchtower, red sky. */
    const val TOUR_DEFAULT = 3

    /** The bundled wallpapers, in manifest order. Empty if the manifest is missing (then no wallpaper is shown). */
    val all: List<Wallpaper> by lazy {
        Wallpapers::class.java.getResource(MANIFEST)?.readText()?.let(::parseManifest).orEmpty()
    }

    /**
     * Manifest rows: `<file> [focusX focusY] [bright|dark] [title...]`; `#` starts a comment, blank lines are skipped.
     * The number is the row's position (1-based), so the list order is the numbering.
     */
    fun parseManifest(text: String): List<Wallpaper> = text.lineSequence()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .mapIndexed { i, line ->
            val parts = line.split(Regex("""\s+"""))
            val fx = parts.getOrNull(1)?.toDoubleOrNull()
            val fy = parts.getOrNull(2)?.toDoubleOrNull()
            var next = if (fx != null && fy != null) 3 else 1
            val tone = parts.getOrNull(next)?.takeIf { it == "bright" || it == "dark" }
            if (tone != null) next++
            Wallpaper(
                number = i + 1,
                resource = ROOT + parts[0],
                focusX = (fx ?: 0.5).coerceIn(0.0, 1.0),
                focusY = (fy ?: 0.5).coerceIn(0.0, 1.0),
                title = parts.drop(next).joinToString(" "),
                bright = tone == "bright",
            )
        }.toList()

    fun url(wallpaper: Wallpaper): URL? = Wallpapers::class.java.getResource(wallpaper.resource)

    /**
     * Random wallpaper number in 1..[count], never [previous] when there is a choice (count > 1). A [previous] outside
     * 1..count (missing or corrupt setting) is ignored. Uniform over the allowed numbers.
     */
    fun pick(count: Int, previous: Int?, random: Random): Int {
        require(count > 0) { "no wallpapers" }
        val prev = previous?.takeIf { count > 1 && it in 1..count } ?: return random.nextInt(count) + 1
        val n = random.nextInt(count - 1) + 1 // 1..count-1, then skip over prev
        return if (n >= prev) n + 1 else n
    }

    /** Value of -Dlauncher.wallpaper as a valid number (1..[count]), else null. */
    fun forcedNumber(value: String? = System.getProperty(PROPERTY), count: Int = all.size): Int? =
        value?.trim()?.toIntOrNull()?.takeIf { it in 1..count }

    /** The wallpaper of this launch; null until [choose] ran (tests) or when nothing is bundled. */
    @Volatile
    var current: Wallpaper? = null
        private set

    /**
     * Chooses this launch's wallpaper ONCE: [forced] if given, else a random one that differs from [memory]'s last;
     * remembers a random choice in [memory] (a forced one is not saved). Later calls return the same wallpaper and change nothing (the background never
     * changes after login, the login and main windows share it).
     */
    @Synchronized
    fun choose(
        memory: WallpaperMemory?,
        random: Random = Random.Default,
        forced: Int? = forcedNumber(),
        list: List<Wallpaper> = all,
    ): Wallpaper? {
        current?.let { return it }
        if (list.isEmpty()) return null
        val fixed = forced?.takeIf { it in 1..list.size }
        val chosen = list[(fixed ?: pick(list.size, memory?.last(), random)) - 1]
        // a forced choice (-Dlauncher.wallpaper=N) is not saved: wallpaper.last keeps the user's real last wallpaper
        if (fixed == null) runCatching { memory?.remember(chosen.number) }
            .onFailure { System.err.println("[launcher-fork] WARNING: cannot remember the wallpaper: $it") }
        current = chosen
        System.err.println("[launcher-fork] wallpaper: %02d %s".format(chosen.number, chosen.title))
        return chosen
    }

    /** Tests only: forget the launch's choice. */
    internal fun resetForTests() {
        current = null
    }
}

package dev.launcherfork

import dev.launcherfork.theme.Theme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.1 contrast helpers shared by [SettingsContrastTest] (tokens against tokens) and [WallpaperContrastTest]
 * (theme text over the scrimmed wallpaper pixels): sRGB colours with alpha, source-over compositing, relative
 * luminance, contrast ratio and the looked-up colour tokens of every theme stack.
 */
object ContrastKit {
    /** Stylesheet stack per theme, same order as Theme.stylesheets. */
    val themes = mapOf(
        "exbo" to listOf("theme.css"),
        "shadcn" to listOf("theme.css", "theme-shadcn.css"),
        "zone" to listOf("theme.css", "theme-zone.css"),
    )

    data class Rgba(val r: Double, val g: Double, val b: Double, val a: Double = 1.0) {
        fun over(bg: Rgba) = Rgba(r * a + bg.r * (1 - a), g * a + bg.g * (1 - a), b * a + bg.b * (1 - a))
        fun luminance(): Double {
            fun ch(c: Double) = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)
        }

        companion object {
            fun ofArgb(argb: Int) = Rgba(((argb shr 16) and 0xff) / 255.0, ((argb shr 8) and 0xff) / 255.0, (argb and 0xff) / 255.0,
                ((argb ushr 24) and 0xff) / 255.0)
        }
    }

    fun parseColor(v: String): Rgba {
        val s = v.trim()
        Regex("""#([0-9a-fA-F]{6})""").matchEntire(s)?.let { m ->
            val h = m.groupValues[1]
            return Rgba(h.substring(0, 2).toInt(16) / 255.0, h.substring(2, 4).toInt(16) / 255.0, h.substring(4, 6).toInt(16) / 255.0)
        }
        Regex("""#([0-9a-fA-F]{3})""").matchEntire(s)?.let { m ->
            val h = m.groupValues[1].map { "$it$it".toInt(16) / 255.0 }
            return Rgba(h[0], h[1], h[2])
        }
        Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([\d.]+)\s*)?\)""").matchEntire(s)?.let { m ->
            val (r, g, b) = m.groupValues.drop(1).take(3).map { it.toInt() / 255.0 }
            return Rgba(r, g, b, m.groupValues[4].ifEmpty { "1" }.toDouble())
        }
        error("unsupported color '$v'")
    }

    /** Looked-up tokens of a theme: theme.css first, the theme file overrides it (same order as Theme.apply). */
    fun tokens(theme: String): Map<String, String> = themes.getValue(theme).fold(mutableMapOf()) { acc, file ->
        val css = Theme.resource("style/$file").readText().replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        val root = Regex("""\.root\s*\{(.*?)}""", RegexOption.DOT_MATCHES_ALL).find(css)!!.groupValues[1]
        Regex("""^\s*(-[a-z][a-z0-9-]*)\s*:\s*([^;]+);""", RegexOption.MULTILINE).findAll(root)
            .filterNot { it.groupValues[1].startsWith("-fx-") }
            .forEach { acc[it.groupValues[1]] = it.groupValues[2].trim() }
        acc
    }

    /** Opaque color of [token]; a translucent one is composed over [bg] (itself opaque). */
    fun color(t: Map<String, String>, token: String, bg: Rgba? = null): Rgba {
        val c = parseColor(t[token] ?: error("token $token missing"))
        return if (c.a < 1.0) c.over(bg ?: error("$token is translucent, needs a background")) else c
    }

    fun ratio(a: Rgba, b: Rgba): Double {
        val la = a.luminance(); val lb = b.luminance()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** layout.css without comments, and one property of an exact selector rule in it. */
    val layoutCss: String by lazy {
        Theme.resource("style/layout.css").readText().replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
    }

    fun layoutProp(selector: String, p: String): String {
        val rule = Regex("""(?m)^\Q$selector\E\s*\{([^}]*)}""").find(layoutCss)?.groupValues?.get(1) ?: error("no rule '$selector' in layout.css")
        return Regex("""(?:^|;|\s)\Q$p\E\s*:\s*([^;]+);""").find(rule)?.groupValues?.get(1)?.trim() ?: error("$selector has no $p")
    }
}

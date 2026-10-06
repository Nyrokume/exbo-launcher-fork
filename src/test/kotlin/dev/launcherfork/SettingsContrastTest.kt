package dev.launcherfork

import dev.launcherfork.theme.Theme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Readability of the settings page, from the stylesheets alone (no FX): the text and background tokens of every theme
 * (theme.css + theme-shadcn.css / theme-zone.css; all themes are dark, there is no light variant) must meet WCAG 2.1 AA:
 * 4.5:1 for normal text (row labels, RAM summary and captions, inactive segment labels, KB/s), 3:1 for the large group
 * headings and for non-text parts that carry state (RAM segments, switch off-state; WCAG 1.4.11). layout.css is checked
 * too, so the settings selectors really paint with the tokens measured here.
 */
class SettingsContrastTest {
    private val themes = mapOf("exbo" to listOf("theme.css"), "shadcn" to listOf("theme.css", "theme-shadcn.css"),
        "zone" to listOf("theme.css", "theme-zone.css"))

    private data class Rgba(val r: Double, val g: Double, val b: Double, val a: Double = 1.0) {
        fun over(bg: Rgba) = Rgba(r * a + bg.r * (1 - a), g * a + bg.g * (1 - a), b * a + bg.b * (1 - a))
        fun luminance(): Double {
            fun ch(c: Double) = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)
        }
    }

    private fun parseColor(v: String): Rgba {
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
    private fun tokens(theme: String): Map<String, String> = themes.getValue(theme).fold(mutableMapOf()) { acc, file ->
        val css = Theme.resource("style/$file").readText().replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        val root = Regex("""\.root\s*\{(.*?)}""", RegexOption.DOT_MATCHES_ALL).find(css)!!.groupValues[1]
        Regex("""^\s*(-[a-z][a-z0-9-]*)\s*:\s*([^;]+);""", RegexOption.MULTILINE).findAll(root)
            .filterNot { it.groupValues[1].startsWith("-fx-") }
            .forEach { acc[it.groupValues[1]] = it.groupValues[2].trim() }
        acc
    }

    /** Opaque color of [token]; a translucent one is composed over [bg] (itself opaque). */
    private fun color(t: Map<String, String>, token: String, bg: Rgba? = null): Rgba {
        val c = parseColor(t[token] ?: error("token $token missing"))
        return if (c.a < 1.0) c.over(bg ?: error("$token is translucent, needs a background")) else c
    }

    private fun ratio(a: Rgba, b: Rgba): Double {
        val la = a.luminance(); val lb = b.luminance()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** (what, foreground token, background token, minimum ratio) */
    private val pairs = listOf(
        Triple("group heading (large, Bebas / Europe 17-21 px)", "-settings-heading" to "-background", 3.0),
        Triple("group heading, stricter: also readable as normal text", "-settings-heading" to "-background", 4.5),
        Triple("row label / RAM summary", "-settings-label" to "-card", 4.5),
        Triple("inactive segment label", "-settings-muted" to "-input", 4.5),
        Triple("KB/s unit (in the limit field)", "-settings-muted" to "-input", 4.5),
        Triple("RAM min / max caption", "-settings-muted" to "-card", 4.5),
        Triple("selected segment label", "-primary-foreground" to "-segment-selected", 4.5),
        Triple("hovered segment label", "-foreground" to "-tile-hover", 4.5),
        Triple("speed limit value", "-input-foreground" to "-input", 4.5),
        Triple("RAM segment: selected (non-text)", "-ram-filled" to "-card", 3.0),
        Triple("RAM segment: available (non-text)", "-ram-free" to "-card", 3.0),
        Triple("switch off: knob (non-text)", "-switch-off-knob" to "-input", 3.0),
        Triple("switch off: track ring (non-text)", "-switch-off-border" to "-card", 3.0),
    )

    @TestFactory
    fun settingsTextMeetsWcagAaInEveryTheme(): List<DynamicTest> = themes.keys.flatMap { theme ->
        val t = tokens(theme)
        pairs.map { (what, tokens, minRatio) ->
            DynamicTest.dynamicTest("$theme: $what") {
                val bg = color(t, tokens.second, color(t, "-background"))
                val fg = color(t, tokens.first, bg)
                val r = ratio(fg, bg)
                assertTrue(r >= minRatio, "$theme: $what ${tokens.first} on ${tokens.second} = ${"%.2f".format(r)}:1 < $minRatio:1")
            }
        }
    }

    @Test
    fun ramStatesAreDistinct() = themes.keys.forEach { theme ->
        val t = tokens(theme)
        val card = color(t, "-card")
        val free = color(t, "-ram-free", card); val reserve = color(t, "-ram-reserve", card); val filled = color(t, "-ram-filled", card)
        assertTrue(free != reserve && free != filled && reserve != filled, "$theme: RAM states share a color")
        // the reserve is quieter than the available segments (and drawn as an outline, see layout.css)
        assertTrue(ratio(reserve, card) < ratio(free, card), "$theme: reserve not darker than the available segments")
    }

    /** layout.css paints the settings page with exactly the tokens measured above. */
    @Test
    fun settingsSelectorsUseTheMeasuredTokens() {
        val css = Theme.resource("style/layout.css").readText().replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        fun rule(selector: String): String {
            val m = Regex("""(?m)^\Q$selector\E\s*\{([^}]*)}""").find(css) ?: error("no rule '$selector' in layout.css")
            return m.groupValues[1]
        }
        fun prop(selector: String, p: String) =
            Regex("""(?:^|;|\s)\Q$p\E\s*:\s*([^;]+);""").find(rule(selector))?.groupValues?.get(1)?.trim() ?: error("$selector has no $p")
        assertEquals("-settings-heading", prop(".settings-group-label", "-fx-text-fill"))
        assertEquals("-settings-label", prop(".settings-label", "-fx-text-fill"))
        assertEquals("-settings-label", prop(".ram-summary", "-fx-text-fill"))
        assertEquals("-settings-muted", prop(".ram-scale-label", "-fx-text-fill"))
        assertEquals("-settings-muted", prop(".segment", "-fx-text-fill"))
        assertEquals("-settings-muted", prop(".limit-unit", "-fx-text-fill"))
        assertEquals("-input", prop(".segmented", "-fx-background-color"))
        assertEquals("-ram-free", prop(".ram-segment", "-fx-background-color"))
        assertEquals("-ram-filled", prop(".ram-segment:filled", "-fx-background-color"))
        assertTrue(prop(".ram-segment:reserve", "-fx-background-color").startsWith("-ram-reserve"))
        assertEquals("-switch-off-knob", prop(".switch > .box > .mark", "-fx-background-color"))
        assertTrue(prop(".switch > .box", "-fx-background-color").startsWith("-switch-off-border"))
        assertTrue(prop(".segment:selected, .segment:selected:hover", "-fx-background-color") == "-segment-selected")
        // readable sizes: row labels 14 px, segments 13 px, headings larger than the rows
        assertEquals("14", prop(".settings-label", "-fx-font-size"))
        assertEquals("13", prop(".segment", "-fx-font-size"))
        assertTrue(prop(".settings-group-label", "-fx-font-size").toDouble() >= 17.0)
    }
}

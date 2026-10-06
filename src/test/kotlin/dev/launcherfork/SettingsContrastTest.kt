package dev.launcherfork

import dev.launcherfork.theme.Theme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Readability of the settings page, from the stylesheets alone (no FX): the text and background tokens of every theme
 * (theme.css + theme-shadcn.css / theme-zone.css, and the white scheme theme-zone-light.css on top of the zone theme;
 * the ratio is symmetric, so dark-on-light is measured the same way) must meet WCAG 2.1 AA:
 * 4.5:1 for normal text (row labels, RAM summary and captions, inactive segment labels, KB/s), 3:1 for the large group
 * headings and for non-text parts that carry state (RAM segments, switch off-state; WCAG 1.4.11). layout.css is checked
 * too, so the settings selectors really paint with the tokens measured here.
 */
class SettingsContrastTest {
    private val themes = mapOf("exbo" to listOf("theme.css"), "shadcn" to listOf("theme.css", "theme-shadcn.css"),
        "zone" to listOf("theme.css", "theme-zone.css"),
        // the white scheme (settings «Тема: Белая»): same stack as Theme.stylesheets("zone", LIGHT, ...)
        "zone-light" to listOf("theme.css", "theme-zone.css", "theme-zone-light.css"))

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
        Triple("inactive segment label", "-segment-text" to "-input", 4.5),
        Triple("KB/s unit (in the limit field)", "-settings-muted" to "-input", 4.5),
        Triple("RAM min / max caption", "-settings-muted" to "-card", 4.5),
        Triple("RAM note «Мало памяти останется системе»", "-settings-warning" to "-card", 4.5),
        Triple("selected segment label", "-primary-foreground" to "-segment-selected", 4.5),
        Triple("hovered segment label", "-settings-label" to "-tile-hover", 4.5),
        Triple("speed limit value", "-input-foreground" to "-input", 4.5),
        Triple("RAM segment: selected (non-text)", "-ram-filled" to "-card", 3.0),
        Triple("RAM segment: available (non-text)", "-ram-free" to "-card", 3.0),
        Triple("switch on: track (non-text)", "-switch-on" to "-card", 3.0),
        Triple("switch on: knob (non-text)", "-primary-foreground" to "-switch-on", 3.0),
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
        val free = color(t, "-ram-free", card); val filled = color(t, "-ram-filled", card)
        assertTrue(free != filled, "$theme: RAM states share a color")
        // the selected amount is the loudest part of the bar: more contrast than the available (grey) segments
        assertTrue(ratio(filled, card) > ratio(free, card),
            "$theme: selected ${"%.2f".format(ratio(filled, card))}:1 not above available ${"%.2f".format(ratio(free, card))}:1")
    }

    /**
     * Segmented controls («Закрывать лаунчер», «Peer-to-peer», «Тема», «Язык»): the inactive options are bright text on
     * the control (>= 7:1, AAA, also when hovered), the selected one is the theme accent filling the whole part with
     * -primary-foreground on it (>= 4.5:1). In zone, zone-light and exbo that is white text on the accent (site red /
     * exbo blue); shadcn is monochrome (near-white accent, dark text). Figures are printed for the PR.
     */
    @Test
    fun segmentedControlsAreReadableInEveryTheme() = themes.keys.forEach { theme ->
        val t = tokens(theme)
        val input = color(t, "-input"); val hover = color(t, "-tile-hover", input)
        val inactive = ratio(color(t, "-segment-text"), input)
        val hovered = ratio(color(t, "-settings-label"), hover)
        val selected = ratio(color(t, "-primary-foreground"), color(t, "-segment-selected"))
        println("segmented $theme: inactive ${"%.2f".format(inactive)}:1, hovered ${"%.2f".format(hovered)}:1, selected ${"%.2f".format(selected)}:1")
        assertTrue(inactive >= 7.0, "$theme: inactive segment text ${"%.2f".format(inactive)}:1 < 7:1")
        assertTrue(hovered >= 7.0, "$theme: hovered segment text ${"%.2f".format(hovered)}:1 < 7:1")
        assertTrue(selected >= 4.5, "$theme: selected segment text ${"%.2f".format(selected)}:1 < 4.5:1")
        // inactive text close to the row labels: brighter than the muted captions (dark) / darker (white scheme)
        assertTrue(ratio(color(t, "-segment-text"), input) > ratio(color(t, "-settings-muted"), input), "$theme: inactive segments as dim as captions")
        if (theme != "shadcn") assertEquals(parseColor("#ffffff"), color(t, "-primary-foreground"), "$theme: selected text not white")
        if (theme.startsWith("zone")) assertEquals(parseColor("#e91932"), color(t, "-segment-selected"), "$theme: selected part not the site red")
        else assertEquals(color(t, "-primary"), color(t, "-segment-selected"), "$theme: selected part not the theme primary")
    }

    /** (what, foreground token, background token, minimum ratio): main window and pre-main (login / code) window. */
    private val windowPairs = listOf(
        Triple("main: headings, game name", "-foreground" to "-background", 4.5),
        Triple("main: text on cards / menus", "-foreground" to "-card", 4.5),
        Triple("main: card text", "-card-foreground" to "-card", 4.5),
        Triple("main: sidebar text and logo", "-foreground" to "-sidebar", 4.5),
        Triple("main: muted labels, hints", "-muted-foreground" to "-background", 4.5),
        Triple("main: muted text on cards", "-muted-foreground" to "-card", 4.5),
        Triple("main: header links", "-text-nav" to "-background", 4.5),
        Triple("main: status text in the pill", "-text-status" to "-input", 4.5),
        Triple("main: red menu rows", "-destructive-text" to "-card", 4.5),
        Triple("main / login: ИГРАТЬ, ВОЙТИ (white on red)", "-primary-foreground" to "-primary", 4.5),
        Triple("main: hovered tile text", "-foreground" to "-tile-hover", 4.5),
        Triple("window glyphs (non-text)", "-titlebar-foreground" to "-titlebar", 3.0),
        Triple("icons (non-text)", "-icon" to "-background", 3.0),
        Triple("login: form text", "-foreground" to "-card-description", 4.5),
        Triple("login: hints, labels", "-muted-foreground" to "-card-description", 4.5),
        Triple("login: input text", "-input-foreground" to "-input", 4.5),
        Triple("login: red links", "-primary" to "-card-description", 4.5),
        Triple("registration: selected combo row (red text on card)", "-primary" to "-card", 4.5),
        Triple("login: error text", "-error" to "-card-description", 4.5),
    )

    /**
     * Known gaps of the DARK zone theme (rules owned by other branches, not touched here): -text-status on -input
     * 2.98:1 and -error on -card-description 4.41:1 predate the white scheme; red TEXT in -primary (login links in
     * zone-auth.css, the selected registration combo row) is the site's --red-color #e91932: 4.24:1 on
     * -card-description, 3.93:1 on -card (no red can carry white text >= 4.5:1 AND be >= 4.5:1 text on near-black;
     * the fix is -destructive-text #ff3b52 in those rules). Self-checking: [knownDarkZoneGapsAreStillGaps] fails as soon
     * as a listed pair passes (or no longer exists), so a stale entry is removed when the fix lands.
     */
    private val knownDarkZoneGaps = setOf("-text-status" to "-input", "-error" to "-card-description",
        "-primary" to "-card-description", "-primary" to "-card")

    /** Every exception above is a real, still failing window pair of the dark zone theme; otherwise delete it. */
    @TestFactory
    fun knownDarkZoneGapsAreStillGaps(): List<DynamicTest> = knownDarkZoneGaps.map { gap ->
        DynamicTest.dynamicTest("zone: known gap ${gap.first} on ${gap.second}") {
            val pair = windowPairs.filter { it.second == gap }
            assertTrue(pair.isNotEmpty(), "known gap ${gap.first} on ${gap.second} is not a checked window pair: remove it")
            val t = tokens("zone")
            pair.forEach { (what, tokens, minRatio) ->
                val bg = color(t, tokens.second, color(t, "-background"))
                val r = ratio(color(t, tokens.first, bg), bg)
                println("zone known gap: $what ${tokens.first} on ${tokens.second} = ${"%.2f".format(r)}:1 (needs $minRatio:1)")
                assertTrue(r < minRatio, "stale exception: zone $what ${tokens.first} on ${tokens.second} = ${"%.2f".format(r)}:1 " +
                    "now meets $minRatio:1, remove it from knownDarkZoneGaps")
            }
        }
    }

    /**
     * Main and login window text in the zone look, dark and white. The exbo / shadcn dev themes keep the original
     * colours (some original greys, e.g. exbo -text-link, are below AA) and are not part of this check.
     */
    @TestFactory
    fun mainAndLoginTextMeetWcagAaInTheZoneSchemes(): List<DynamicTest> = listOf("zone", "zone-light").flatMap { theme ->
        val t = tokens(theme)
        windowPairs.filterNot { theme == "zone" && it.second in knownDarkZoneGaps }.map { (what, tokens, minRatio) ->
            DynamicTest.dynamicTest("$theme: $what") {
                val bg = color(t, tokens.second, color(t, "-background"))
                val fg = color(t, tokens.first, bg)
                val r = ratio(fg, bg)
                assertTrue(r >= minRatio, "$theme: $what ${tokens.first} on ${tokens.second} = ${"%.2f".format(r)}:1 < $minRatio:1")
            }
        }
    }

    /** The zone accent is the red of stalzone.com (--red-color #E91932) on every red fill, dark and white. */
    @Test
    fun zoneUsesTheSiteRed() {
        val site = parseColor("#e91932")
        for (theme in listOf("zone", "zone-light")) {
            val t = tokens(theme)
            for (tok in listOf("-segment-selected", "-ram-filled", "-switch-on", "-ring", "-window-close"))
                assertEquals(site, parseColor(t.getValue(tok)), "$theme: $tok is not the site red")
        }
        assertEquals(site, parseColor(tokens("zone").getValue("-primary")), "zone: -primary is not the site red")
        assertEquals(parseColor("#ff3b52"), parseColor(tokens("zone").getValue("-primary-hover")), "zone: site hover red")
    }

    /** The white scheme really is light: light surfaces, dark text, no translucent white left over from the dark tokens. */
    @Test
    fun whiteSchemeIsLight() {
        val t = tokens("zone-light")
        val white = color(t, "-background")
        assertTrue(white.luminance() > 0.95, "background not white")
        for (surface in listOf("-card", "-sidebar", "-input", "-card-description", "-titlebar", "-tile"))
            assertTrue(color(t, surface).luminance() > 0.85, "$surface is not light")
        for (text in listOf("-foreground", "-settings-heading", "-settings-label", "-input-foreground"))
            assertTrue(color(t, text).luminance() < 0.02, "$text is not dark")
        // hover / fill / overlay tokens are black-with-alpha here (white-with-alpha would vanish on white)
        for (tok in listOf("-accent", "-fill-subtle", "-fill-strong", "-divider-light", "-scrim", "-overlay", "-shadow")) {
            val c = parseColor(t.getValue(tok))
            assertTrue(c.a < 1.0 && c.luminance() < 0.01, "$tok should be translucent black, is ${t[tok]}")
        }
        // the switch knob (white, -primary-foreground) on the red ON track, the off knob on the white track
        assertTrue(ratio(color(t, "-primary-foreground"), color(t, "-switch-on")) >= 4.5)
        assertTrue(ratio(color(t, "-switch-off-knob"), color(t, "-input")) >= 3.0)
    }

    /**
     * Game banner on the main screen (main/game_banner.fxml): text selector of layout.css -> (token it must paint with,
     * minimum ratio on the content background it sits on, -background; the centre slot and the banner are transparent).
     * Normal text >= 4.5:1, the large display title >= 3:1. The banner has no online line (the count lives in the games
     * list, see OnlineCountTest).
     */
    private val bannerText = listOf(
        Triple(".game-banner-title", "-foreground", 3.0),
        Triple(".game-banner-lead", "-foreground", 4.5),
        Triple(".game-banner-subheading", "-foreground", 4.5),
        Triple(".game-banner-body", "-secondary-foreground", 4.5),
        Triple(".game-banner-closing", "-foreground", 4.5),
    )

    /**
     * Banner text in EVERY theme of [themes] (exbo, shadcn, zone here): a theme added to that map (e.g. zone-light when
     * the white scheme is merged) is checked by this test without further changes. exbo / shadcn draw the game art
     * behind the content; its left / bottom fade is -background, so -background is the reference there too.
     */
    @TestFactory
    fun gameBannerTextIsReadable(): List<DynamicTest> {
        val css = Theme.resource("style/layout.css").readText().replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        fun prop(selector: String, p: String): String {
            val rule = Regex("""(?m)^\Q$selector\E\s*\{([^}]*)}""").find(css)?.groupValues?.get(1) ?: error("no rule '$selector' in layout.css")
            return Regex("""(?:^|;|\s)\Q$p\E\s*:\s*([^;]+);""").find(rule)?.groupValues?.get(1)?.trim() ?: error("$selector has no $p")
        }
        val selectors = DynamicTest.dynamicTest("layout.css: banner selectors use the measured tokens") {
            bannerText.forEach { (selector, token, _) -> assertEquals(token, prop(selector, "-fx-text-fill"), "$selector text fill") }
            assertFalse(css.contains(".game-banner-online"), "layout.css: leftover rules of the removed banner online line")
            // the banner itself is transparent: the text really sits on -background
            assertTrue(prop(".game-banner-scroll, .game-banner-scroll > .viewport, .game-banner-scroll:focused", "-fx-background-color") == "transparent")
        }
        return listOf(selectors) + themes.keys.flatMap { theme ->
            val t = tokens(theme)
            val bg = color(t, "-background")
            bannerText.map { (selector, token, minRatio) ->
                DynamicTest.dynamicTest("$theme: banner $selector") {
                    val r = ratio(color(t, token, bg), bg)
                    println("banner $theme $selector $token on -background = ${"%.2f".format(r)}:1")
                    assertTrue(r >= minRatio, "$theme: banner $selector $token on -background = ${"%.2f".format(r)}:1 < $minRatio:1")
                }
            }
        }
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
        assertEquals("-settings-warning", prop(".ram-warning", "-fx-text-fill"))
        assertEquals("-segment-text", prop(".segment", "-fx-text-fill"))
        assertEquals("-settings-muted", prop(".limit-unit", "-fx-text-fill"))
        assertEquals("-input", prop(".segmented", "-fx-background-color"))
        assertEquals("-ram-free", prop(".ram-segment", "-fx-background-color"))
        assertEquals("-ram-filled", prop(".ram-segment:filled", "-fx-background-color"))
        assertTrue(Regex("""(?m)^\.ram-segment:reserve""").find(css) == null, "dead reserve rule left in layout.css")
        assertEquals("-switch-off-knob", prop(".switch > .box > .mark", "-fx-background-color"))
        assertTrue(prop(".switch > .box", "-fx-background-color").startsWith("-switch-off-border"))
        assertEquals("-switch-on, -switch-on", prop(".switch:selected > .box", "-fx-background-color"))
        assertEquals("-primary-foreground", prop(".switch:selected > .box > .mark", "-fx-background-color"))
        assertEquals("-segment-selected", prop(".segment:selected, .segment:selected:hover, .segment:selected:focus-visible", "-fx-background-color"))
        assertEquals("-primary-foreground", prop(".segment:selected, .segment:selected:hover, .segment:selected:focus-visible", "-fx-text-fill"))
        assertEquals("-tile-hover", prop(".segment:hover, .segment:focus-visible", "-fx-background-color"))
        assertEquals("-settings-label", prop(".segment:hover, .segment:focus-visible", "-fx-text-fill"))
        // the segmented box: like the limit fields (1 px -border), parts edge to edge, no inset pill
        assertEquals("-border", prop(".segmented", "-fx-border-color"))
        assertEquals("1", prop(".segmented", "-fx-border-width"))
        assertEquals("0", prop(".segmented", "-fx-padding"))
        assertEquals("0", prop(".segmented", "-fx-spacing"))
        assertEquals("transparent", prop(".segment", "-fx-background-color"))
        val segHeight = prop(".segment", "-fx-pref-height").toDouble()
        assertTrue(segHeight in 26.0..28.0, "segment height $segHeight not 26..28 px")
        val sidePad = prop(".segment", "-fx-padding").split(Regex("""\s+""")).map { it.toDouble() }
        assertTrue(sidePad[1] in 12.0..14.0 && sidePad[3] == sidePad[1] && sidePad[0] == 0.0, "segment padding $sidePad")
        // readable sizes: row labels and segments 14 px, headings larger than the rows
        assertEquals("14", prop(".settings-label", "-fx-font-size"))
        assertEquals("14", prop(".segment", "-fx-font-size"))
        assertTrue(prop(".settings-group-label", "-fx-font-size").toDouble() >= 17.0)
    }
}

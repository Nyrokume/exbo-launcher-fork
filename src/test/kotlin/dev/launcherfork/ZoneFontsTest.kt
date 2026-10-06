package dev.launcherfork

import dev.launcherfork.theme.Theme
import javafx.scene.text.Font
import javafx.scene.text.Text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fonts of the zone theme: every family named in theme-zone.css / zone-auth.css must come from a bundled TTF whose
 * internal family name matches the CSS name exactly (a mismatch silently falls back to the system font) and that
 * covers Cyrillic.
 */
class ZoneFontsTest {
    private val fontDir = "/dev/launcherfork/ui/style/font/"
    private val sheets = listOf("theme-zone.css", "zone-auth.css")
    private val cyrillic = (0x410..0x44F).map { it.toChar() }.joinToString("") + "Ёё"

    /** Family names used by the zone stylesheets (-fx-font-family and @font-face font-family). */
    private fun cssFamilies(): Set<String> = sheets.flatMap { sheet ->
        val css = Theme.resource("style/$sheet").readText()
        Regex("""(?:-fx-)?font-family:\s*"([^"]+)"""").findAll(css).map { it.groupValues[1] }.toList()
    }.toSet()

    /** JavaFX family name -> bundled TTF file, from Font.loadFont on each file. */
    private fun bundledFamilies(): Map<String, String> = FxTestSupport.onFx {
        listOf("BebasNeue-Regular.ttf", "Roboto-Light.ttf", "Roboto-Regular.ttf", "Roboto-Medium.ttf").associate { file ->
            val font = javaClass.getResourceAsStream(fontDir + file).use { requireNotNull(it) { "missing $file" }; Font.loadFont(it, 12.0) }
            assertNotNull(font, "Font.loadFont returned null for $file")
            font!!.family to file
        }
    }

    @Test
    fun zoneStylesheetsUseBundledFamilies() {
        FxTestSupport.start()
        val bundled = bundledFamilies()
        val families = cssFamilies()
        assertTrue("Bebas Neue Regular" in families && "Roboto Medium" in families, "$families")
        FxTestSupport.onFx {
            Theme.zoneFonts
            families.forEach { name ->
                assertTrue(name in bundled, "CSS family \"$name\" has no bundled TTF (bundled: ${bundled.keys})")
                // exact internal name: an unknown family would come back as the system font
                assertEquals(name, Font.font(name, 20.0).family, "Font.font(\"$name\") fell back to another family")
            }
        }
    }

    @Test
    fun zoneFontsCoverCyrillic() {
        FxTestSupport.start()
        val bundled = bundledFamilies()
        cssFamilies().forEach { name ->
            val file = bundled.getValue(name)
            val awt = javaClass.getResourceAsStream(fontDir + file).use { java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, it) }
            assertEquals(-1, awt.canDisplayUpTo(cyrillic), "$file ($name) misses Cyrillic at index ${awt.canDisplayUpTo(cyrillic)}")
        }
        FxTestSupport.onFx {
            val sample = "ДОБРО ПОЖАЛОВАТЬ, ВОЙТИ"
            fun width(font: Font) = Text(sample).apply { this.font = font }.layoutBounds.width
            val bebas = width(Font.font("Bebas Neue Regular", 40.0))
            assertTrue(bebas > 0.0)
            // tall condensed Bebas is clearly narrower than the default sans fallback
            assertNotEquals(width(Font.font("System", 40.0)), bebas, 1.0)
        }
    }

    @Test
    fun authPanelFontFaceFileExists() {
        val css = Theme.resource("style/zone-auth.css").readText()
        Regex("""src:\s*url\("([^"]+)"\)""").findAll(css).forEach { m ->
            assertNotNull(javaClass.getResource("/dev/launcherfork/ui/style/" + m.groupValues[1]), "@font-face file ${m.groupValues[1]}")
        }
    }
}

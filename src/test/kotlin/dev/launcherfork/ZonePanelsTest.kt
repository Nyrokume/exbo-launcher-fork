package dev.launcherfork

import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.screens.auth.InitialAuthController
import javafx.css.CssParser
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.effect.ColorAdjust
import javafx.scene.effect.GaussianBlur
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Zone-style overlay panels. Besides the checks, renders each panel over a blurred main screen, the way the
 * overlay slot will show it, into build/zone-preview/ (or -Dzone.preview=<dir>) for design review.
 */
class ZonePanelsTest {
    private val previewDir = File(System.getProperty("zone.preview", "build/zone-preview"))

    @Test
    fun zoneStylesheetParses() {
        CssParser.errorsProperty().clear()
        CssParser().parse(dev.launcherfork.theme.Theme.resource("style/zone-auth.css"))
        assertTrue(CssParser.errorsProperty().isEmpty(), "CSS errors: ${CssParser.errorsProperty().map { it.message }}")
    }

    @Test
    fun passwordEyeTogglesPlainTextTwin() = FxTestSupport.onFx {
        val loaded = Fxml.load<InitialAuthController>(FxTestSupport.context("ru"), "auth_panel.fxml")
        val c = loaded.controller
        c.passwordField.text = "secret"
        assertEquals("secret", c.passwordVisibleField!!.text)
        assertFalse(c.passwordVisibleField!!.isVisible)
        c.passwordToggle!!.isSelected = true
        assertTrue(c.passwordVisibleField!!.isVisible)
        assertFalse(c.passwordField.isVisible)
        c.passwordVisibleField!!.text = "changed"
        assertEquals("changed", c.passwordField.text)
    }

    @Test
    fun renderPreviews() {
        listOf("ru", "en").forEach { lang -> render("auth_panel.fxml", "auth_panel_$lang.png", lang) }
        render("auth_panel.fxml", "auth_panel_error.png", "ru") { c ->
            (c as InitialAuthController).loginField.text = ""
            c.onEnter()
        }
    }

    /** Real single window (StageNavigator + window.fxml), auth overlay on top, zone theme forced. */
    private fun render(@Suppress("UNUSED_PARAMETER") path: String, file: String, lang: String, prepare: (Any) -> Unit = {}) = FxTestSupport.onFx {
        val stage = javafx.stage.Stage(javafx.stage.StageStyle.UNDECORATED)
        val nav = dev.launcherfork.app.StageNavigator(stage, dev.launcherfork.app.AppContext.mock(authDelayMs = 0), lang)
        nav.showAuth()
        val scene = stage.scene
        // preview always in the zone theme, whatever -Dlauncher.theme the test JVM got
        dev.launcherfork.theme.Theme.zoneFonts
        val zoneSheet = dev.launcherfork.theme.Theme.stylesheet("theme-zone.css")
        if (zoneSheet !in scene.stylesheets) scene.stylesheets.add(1, zoneSheet)
        val host = scene.root
        host.applyCss(); host.layout()
        prepare(nav.overlayController!!)
        host.applyCss(); host.layout()
        val img = scene.snapshot(null)
        val w = img.width.toInt(); val h = img.height.toInt()
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val reader = img.pixelReader
        for (y in 0 until h) for (x in 0 until w) out.setRGB(x, y, reader.getArgb(x, y))
        previewDir.mkdirs()
        ImageIO.write(out, "png", File(previewDir, file))
    }
}

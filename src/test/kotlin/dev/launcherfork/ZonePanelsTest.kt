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

    private fun render(path: String, file: String, lang: String, prepare: (Any) -> Unit = {}) = FxTestSupport.onFx {
        val ctx = FxTestSupport.context(lang)
        val main = Fxml.load<Any?>(ctx, "main.fxml").root
        // wrapper carries the blur: main.css may style the main root's own -fx-effect
        val content = javafx.scene.Group(main).apply {
            effect = GaussianBlur(18.0).apply { input = ColorAdjust(0.0, -0.2, -0.35, 0.0) }
            scaleX = 1024.0 / 886.0; scaleY = 640.0 / 550.0
        }
        val panel = Fxml.load<Any>(ctx, path)
        val host = StackPane(content, Region().apply { style = "-fx-background-color: rgba(0,0,0,0.35);" }, panel.root)
        StackPane.setAlignment(panel.root, Pos.CENTER)
        val scene = Scene(host, 1024.0, 640.0, Color.BLACK)
        dev.launcherfork.theme.Theme.apply(scene, lang)
        // preview always in the zone theme, whatever -Dlauncher.theme the test JVM got
        dev.launcherfork.theme.Theme.zoneFonts
        val zoneSheet = dev.launcherfork.theme.Theme.stylesheet("theme-zone.css")
        if (zoneSheet !in scene.stylesheets) scene.stylesheets.add(1, zoneSheet)
        host.applyCss(); host.layout()
        prepare(panel.controller)
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

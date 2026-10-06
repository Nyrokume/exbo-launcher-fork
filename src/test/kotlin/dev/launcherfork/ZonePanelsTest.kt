package dev.launcherfork

import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.screens.auth.InitialAuthController
import javafx.css.CssParser
import javafx.scene.Scene
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Zone login panel (stalzone.com/login). Behaviour checks; the render test also writes PNGs of the panel at the
 * login window size into build/zone-preview/ (or -Dzone.preview=<dir>) for design review.
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
    fun eyeIsKeyboardReachableAndLabelled() = FxTestSupport.onFx {
        val ctx = FxTestSupport.context("ru")
        val c = Fxml.load<InitialAuthController>(ctx, "auth_panel.fxml").controller
        val eye = c.passwordToggle!!
        assertTrue(eye.isFocusTraversable)
        assertEquals(ctx.t("fork.zone.show_password"), eye.accessibleText)
        eye.isSelected = true
        assertEquals(ctx.t("fork.zone.hide_password"), eye.accessibleText)
    }

    @Test
    fun enterIsDisabledUntilBothFieldsAreFilled() = FxTestSupport.onFx {
        val c = Fxml.load<InitialAuthController>(FxTestSupport.context("ru"), "auth_panel.fxml").controller
        c.loginField.text = ""; c.passwordField.text = ""
        assertTrue(c.enterButton.isDisabled)
        c.loginField.text = "player"
        assertTrue(c.enterButton.isDisabled)
        c.passwordField.text = "secret"
        assertFalse(c.enterButton.isDisabled)
    }

    @Test
    fun errorPaintsFieldsUntilEdited() = FxTestSupport.onFx {
        val c = Fxml.load<InitialAuthController>(FxTestSupport.context("ru"), "auth_panel.fxml").controller
        c.loginField.text = ""; c.passwordField.text = "x"
        c.onEnter() // empty login -> error
        assertTrue(c.errorFirst.text.isNotEmpty())
        assertTrue(c.errorFirst.isManaged)
        assertTrue(c.loginField.pseudoClassStates.any { it.pseudoClassName == "error" })
        c.loginField.text = "p"
        assertFalse(c.loginField.pseudoClassStates.any { it.pseudoClassName == "error" })
        assertFalse(c.errorSecond.isManaged)
    }

    @Test
    fun legacyLoginKeepsBrightnessHover() = FxTestSupport.onFx {
        // initial_auth.fxml has no cssHover flag: no disable binding, legacy behaviour untouched
        val c = Fxml.load<InitialAuthController>(FxTestSupport.context("ru"), "initial_auth.fxml").controller
        assertEquals(null, c.cssHover)
        assertFalse(c.enterButton.disableProperty().isBound)
    }

    @Test
    fun renderPreviews() {
        listOf("ru", "en").forEach { lang -> render("auth_panel_$lang.png", lang) }
        render("auth_panel_filled.png", "ru") { c ->
            c.loginField.text = "player"; c.passwordField.text = "secret"; c.autoAuthCheckBox.isSelected = true
        }
        render("auth_panel_error.png", "ru") { c ->
            c.loginField.text = ""; c.passwordField.text = "x"; c.onEnter()
        }
    }

    /** The panel alone at the login window's content size (460x588 under LR's 32 px title bar), zone theme forced. */
    private fun render(file: String, lang: String, prepare: (InitialAuthController) -> Unit = {}) = FxTestSupport.onFx {
        val panel = Fxml.load<InitialAuthController>(FxTestSupport.context(lang), "auth_panel.fxml")
        val scene = Scene(panel.root, 460.0, 588.0, Color.BLACK)
        dev.launcherfork.theme.Theme.apply(scene, lang)
        // preview always in the zone theme, whatever -Dlauncher.theme the test JVM got
        dev.launcherfork.theme.Theme.zoneFonts
        val zoneSheet = dev.launcherfork.theme.Theme.stylesheet("theme-zone.css")
        if (zoneSheet !in scene.stylesheets) scene.stylesheets.add(1, zoneSheet)
        val host = scene.root
        host.applyCss(); host.layout()
        prepare(panel.controller)
        host.applyCss(); host.layout()
        // the panel must fill the window and keep every control inside it
        assertEquals(460.0, host.layoutBounds.width, 0.5)
        val bottom = panel.controller.registerButton.localToScene(panel.controller.registerButton.layoutBounds).maxY
        assertTrue(bottom <= 588.0, "footer is cut off: maxY=$bottom")
        val img = scene.snapshot(null)
        val w = img.width.toInt(); val h = img.height.toInt()
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val reader = img.pixelReader
        for (y in 0 until h) for (x in 0 until w) out.setRGB(x, y, reader.getArgb(x, y))
        assertNotEquals(out.getRGB(10, 10), out.getRGB(10, h - 10), "top and bottom blocks should differ")
        previewDir.mkdirs()
        ImageIO.write(out, "png", File(previewDir, file))
    }
}

package dev.launcherfork

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.components.BulletPasswordFieldSkin
import dev.launcherfork.ui.screens.auth.InitialAuthController
import dev.launcherfork.ui.screens.auth.RegisterController
import javafx.css.CssParser
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.scene.control.ToggleButton
import javafx.scene.control.skin.TextFieldSkin
import javafx.scene.paint.Color
import javafx.scene.text.Text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Password mask (theme.css `.password-field { -fx-skin }` -> [BulletPasswordFieldSkin]): small U+2022 bullets instead
 * of the oversized U+25CF discs in every password field of every theme; caret/hit-testing stay 1:1 with the text and
 * the eye toggle still reveals the plain text. Writes filled-password previews to build/zone-preview/.
 */
class PasswordMaskTest {
    private val previewDir = File(System.getProperty("zone.preview", "build/zone-preview"))

    private class Form(val scene: Scene, val password: PasswordField, val plain: TextField?, val eye: ToggleButton?)

    /** On the FX thread: [fxml] in a scene styled like StageNavigator does, with the [theme] overrides forced (Theme.themeName is per JVM). */
    private fun load(fxml: String, theme: String, lang: String = "ru"): Form {
        val loaded = Fxml.load<Any>(FxTestSupport.context(lang), fxml)
        val scene = Scene(loaded.root, Theme.AUTH_PANEL_WIDTH, Theme.AUTH_PANEL_HEIGHT, Color.BLACK)
        Theme.apply(scene, lang)
        listOf("theme-shadcn.css", "theme-zone.css").map(Theme::stylesheet).forEach { scene.stylesheets.remove(it) }
        if (theme != "exbo") {
            if (theme == "zone") Theme.zoneFonts
            scene.stylesheets.add(1, Theme.stylesheet("theme-$theme.css"))
        }
        loaded.root.applyCss(); loaded.root.layout()
        return when (val c = loaded.controller) {
            is InitialAuthController -> Form(scene, c.passwordField, c.passwordVisibleField, c.passwordToggle)
            is RegisterController -> Form(scene, c.passwordField, c.passwordVisibleField, c.passwordToggle)
            else -> error("unexpected controller $c")
        }
    }

    private fun texts(n: Node): List<Text> = when (n) {
        is Text -> listOf(n)
        is Parent -> n.childrenUnmodifiable.flatMap(::texts)
        else -> emptyList()
    }

    @Test
    fun themeSheetParses() {
        CssParser.errorsProperty().clear()
        CssParser().parse(Theme.resource("style/theme.css"))
        assertTrue(CssParser.errorsProperty().isEmpty(), "CSS errors: ${CssParser.errorsProperty().map { it.message }}")
    }

    @Test
    fun everyPasswordFieldMasksWithSmallBullets() {
        val forms = listOf("auth_panel.fxml", "register_panel.fxml", "initial_auth.fxml")
        for (theme in listOf("exbo", "shadcn", "zone")) for (fxml in forms) FxTestSupport.onFx {
            val f = load(fxml, theme)
            val where = "$fxml/$theme"
            val skin = f.password.skin
            assertTrue(skin is BulletPasswordFieldSkin, "$where: skin is ${skin?.javaClass?.name}")
            // the dots are drawn in the bundled mask font at the field's size, the prompt keeps the field font
            val maskNode = (skin as BulletPasswordFieldSkin).maskTextNode()!!
            assertEquals("Launcher Mask", maskNode.font.family, where)
            assertEquals(f.password.font.size, maskNode.font.size, 0.01, where)
            f.password.text = "zone26"
            f.scene.root.applyCss(); f.scene.root.layout()
            val shown = texts(f.password).map { it.text.orEmpty() }
            assertTrue("\u2022".repeat(6) in shown, "$where: mask nodes $shown")
            assertEquals('\u2022', BulletPasswordFieldSkin.MASK)
            assertEquals("\u2022".repeat(6), maskNode.text, where)
            assertFalse(shown.any { "zone26" in it || '\u25CF' in it }, "$where: plain text or U+25CF in $shown")
        }
    }

    @Test
    fun caretAndHitTestStayOnCharacters() = FxTestSupport.onFx {
        val f = load("auth_panel.fxml", "zone")
        val pw = "abc12345"
        f.password.text = pw
        f.scene.root.applyCss(); f.scene.root.layout()
        val skin = f.password.skin as TextFieldSkin
        val bounds = pw.indices.map { skin.getCharacterBounds(it) }
        val widths = bounds.map { it.width }
        // same glyph for every char: equal widths and an even pitch, 0.625 em of the field font (10 px at 16 px)
        widths.forEach { assertEquals(widths[0], it, 0.01) }
        val pitch = bounds.zipWithNext().map { (a, b) -> b.minX - a.minX }
        pitch.forEach { assertEquals(0.625 * f.password.font.size, it, 0.05, "bullet pitch $pitch") }
        // clicking the left half of bullet i puts the caret before char i
        pw.indices.forEach { i ->
            val b = bounds[i]
            val hit = skin.getIndex(b.minX + b.width * 0.25, b.minY + b.height / 2)
            assertEquals(i, hit.insertionIndex, "hit test at bullet $i")
        }
        f.password.selectRange(2, 5)
        assertEquals("c12", f.password.selectedText)
    }

    @Test
    fun promptAndDotsSitOnTheFieldFontBaseline() {
        // the eye's plain-text twin has the same style classes and the stock TextFieldSkin: same baselines expected
        for (theme in listOf("exbo", "shadcn", "zone")) for (fxml in listOf("auth_panel.fxml", "register_panel.fxml")) FxTestSupport.onFx {
            val f = load(fxml, theme)
            val plain = f.plain!!
            fun baseline(field: TextField, text: String) = texts(field).first { it.text == text }.y
            f.password.text = ""
            f.scene.root.applyCss(); f.scene.root.layout()
            assertEquals(baseline(plain, plain.promptText), baseline(f.password, f.password.promptText), 0.01, "$fxml/$theme prompt")
            f.password.text = "secret"
            f.scene.root.applyCss(); f.scene.root.layout()
            assertEquals(baseline(plain, "secret"), baseline(f.password, "\u2022".repeat(6)), 0.01, "$fxml/$theme text")
        }
    }

    @Test
    fun eyeStillRevealsAndHidesThePassword() {
        for (fxml in listOf("auth_panel.fxml", "register_panel.fxml")) FxTestSupport.onFx {
            val f = load(fxml, "zone")
            f.password.text = "secret"
            f.eye!!.isSelected = true
            assertTrue(f.plain!!.isVisible && !f.password.isVisible)
            assertEquals("secret", f.plain.text)
            f.eye.isSelected = false
            assertTrue(f.password.isVisible && !f.plain.isVisible)
            f.scene.root.applyCss(); f.scene.root.layout()
            assertTrue(f.password.skin is BulletPasswordFieldSkin)
            assertTrue("\u2022".repeat(6) in texts(f.password).map { it.text.orEmpty() })
        }
    }

    @Test
    fun renderFilledPasswordPreviews() {
        FxTestSupport.onFx {
            val login = load("auth_panel.fxml", "zone")
            (login.scene.root.lookup("#zoneLogin") as? TextField)?.text = "player"
            login.password.text = "secret"
            save(login.scene, "password_login_zone.png")
            val reg = load("register_panel.fxml", "zone")
            reg.password.text = "zone2026"
            save(reg.scene, "password_register_zone.png")
            listOf("exbo", "shadcn").forEach { theme ->
                val legacy = load("initial_auth.fxml", theme)
                legacy.password.text = "secret"
                save(legacy.scene, "password_legacy_$theme.png")
            }
        }
    }

    /** The real separate login window (title strip + panel host) with a typed password, zone theme forced. */
    @Test
    fun renderLoginWindowWithPassword() {
        val app = dev.launcherfork.app.AppContext.mock(speed = 50.0, authDelayMs = 0)
        val nav = FxTestSupport.onFx {
            val stage = javafx.stage.Stage(javafx.stage.StageStyle.TRANSPARENT)
            dev.launcherfork.app.StageNavigator(stage, app, "ru").also { it.exit = {}; it.showAuth(); stage.show() }
        }
        assertTrue(FxTestSupport.waitFx(10_000) { nav.panelController is InitialAuthController })
        FxTestSupport.onFx {
            val c = nav.panelController as InitialAuthController
            val scene = nav.authStage.scene
            Theme.zoneFonts
            val zone = Theme.stylesheet("theme-zone.css")
            if (zone !in scene.stylesheets) scene.stylesheets.add(1, zone)
            c.loginField.text = "player"
            c.passwordField.text = "secret"
            scene.root.applyCss(); scene.root.layout()
            assertTrue(c.passwordField.skin is BulletPasswordFieldSkin)
            save(scene, "password_login_window_zone.png")
        }
        FxTestSupport.onFx { nav.close() }
        app.shutdown()
    }

    private fun save(scene: Scene, file: String) {
        scene.root.applyCss(); scene.root.layout()
        val img = scene.snapshot(null)
        val out = BufferedImage(img.width.toInt(), img.height.toInt(), BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until out.height) for (x in 0 until out.width) out.setRGB(x, y, img.pixelReader.getArgb(x, y))
        previewDir.mkdirs()
        ImageIO.write(out, "png", File(previewDir, file))
    }
}

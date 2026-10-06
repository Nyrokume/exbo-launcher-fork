package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.screens.auth.AgeGateController
import dev.launcherfork.ui.screens.auth.InitialAuthController
import dev.launcherfork.ui.screens.auth.RegisterController
import dev.launcherfork.ui.screens.auth.TotpAuthController
import javafx.css.CssParser
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

/**
 * Registration and authenticator-code panels in the login window: they fit 460x588 (also with an error shown), the real
 * window walks login -> age gate -> form -> login with a notice, "totp" opens the authenticator panel. Writes PNG
 * previews to build/zone-preview/ (or -Dzone.preview=<dir>) for design review.
 */
class ZoneStepsTest {
    private val previewDir = File(System.getProperty("zone.preview", "build/zone-preview"))

    @Test
    fun stylesheetParses() {
        CssParser.errorsProperty().clear()
        CssParser().parse(Theme.resource("style/zone-steps.css"))
        CssParser().parse(Theme.resource("style/zone-totp.css"))
        assertTrue(CssParser.errorsProperty().isEmpty(), "CSS errors: ${CssParser.errorsProperty().map { it.message }}")
    }

    private fun <C> show(path: String, lang: String = "ru", prepare: (C) -> Unit = {}): Pair<C, Scene> = FxTestSupport.onFx {
        val loaded = Fxml.load<C>(recordingContext(RecordingNavigator(), lang), path)
        val scene = Scene(loaded.root, Theme.AUTH_PANEL_WIDTH, Theme.AUTH_PANEL_HEIGHT, Color.BLACK)
        Theme.apply(scene, lang)
        Theme.zoneFonts
        val zone = Theme.stylesheet("theme-zone.css")
        if (zone !in scene.stylesheets) scene.stylesheets.add(1, zone)
        loaded.root.applyCss(); loaded.root.layout()
        prepare(loaded.controller)
        loaded.root.applyCss(); loaded.root.layout()
        loaded.controller to scene
    }

    /** Every visible leaf control lies inside the panel with [margin] px of air on the right and at the bottom. */
    private fun assertFits(scene: Scene, name: String, margin: Double = 16.0) = FxTestSupport.onFx {
        fun walk(n: Node) {
            if (!n.isVisible) return
            if (n is javafx.scene.control.Control || n is javafx.scene.text.Text) {
                val b = n.localToScene(n.layoutBounds)
                assertTrue(b.maxX <= scene.width - margin + 0.5, "$name: ${n.styleClass} sticks out to the right (${b.maxX})")
                assertTrue(b.maxY <= scene.height - margin + 0.5, "$name: ${n.styleClass} sticks out at the bottom (${b.maxY})")
            }
            if (n is Parent && n !is javafx.scene.control.Control) n.childrenUnmodifiable.forEach(::walk)
        }
        walk(scene.root)
    }

    private fun save(scene: Scene, file: String) = FxTestSupport.onFx {
        previewDir.mkdirs()
        val img = scene.snapshot(null)
        val out = BufferedImage(img.width.toInt(), img.height.toInt(), BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until out.height) for (x in 0 until out.width) out.setRGB(x, y, img.pixelReader.getArgb(x, y))
        ImageIO.write(out, "png", File(previewDir, file))
    }

    @Test
    fun ageGateFitsWithError() {
        val (_, empty) = show<AgeGateController>("age_gate_panel.fxml")
        assertFits(empty, "age gate"); save(empty, "age_gate.png")
        val (_, error) = show<AgeGateController>("age_gate_panel.fxml") { c ->
            c.dayBox.value = 1; c.monthBox.value = 1; c.yearBox.value = LocalDate.now().year - 10; c.onContinue()
        }
        assertFits(error, "age gate + error"); save(error, "age_gate_error.png")
        val (_, en) = show<AgeGateController>("age_gate_panel.fxml", "en")
        assertFits(en, "age gate en"); save(en, "age_gate_en.png")
    }

    @Test
    fun registerFormFitsWithErrorInBothLanguages() {
        listOf("ru", "en").forEach { lang ->
            val (_, empty) = show<RegisterController>("register_panel.fxml", lang)
            assertFits(empty, "register $lang"); save(empty, "register_$lang.png")
            val (_, error) = show<RegisterController>("register_panel.fxml", lang) { c ->
                c.emailField.text = "player@mail"; c.usernameField.text = "pl"; c.passwordField.text = "123"
                c.newsletterCheckBox.isSelected = true; c.licenseCheckBox.isSelected = true; c.onCreate()
            }
            assertFits(error, "register $lang + error"); save(error, "register_error_$lang.png")
        }
    }

    @Test
    fun totpPanelFits() {
        val (_, totp) = show<TotpAuthController>("totp_auth_panel.fxml") { c ->
            c.codeInput.setCode("558966"); c.errorFirst.text = "Неправильный код аутентификации!"
        }
        assertFits(totp, "totp + toast"); save(totp, "totp_error.png")
        val (_, totpEn) = show<TotpAuthController>("totp_auth_panel.fxml", "en")
        assertFits(totpEn, "totp en")
    }

    @Test
    fun realWindowRegistrationRoundTrip() {
        val app = AppContext.mock(speed = 50.0, authDelayMs = 0)
        val nav = FxTestSupport.onFx {
            val stage = Stage(StageStyle.TRANSPARENT)
            StageNavigator(stage, app, "ru").also { it.exit = {}; it.showAuth(); stage.show() }
        }
        FxTestSupport.onFx { (nav.panelController as InitialAuthController).registerButton.fire() }
        assertTrue(FxTestSupport.waitFx { nav.panelController is AgeGateController })
        FxTestSupport.onFx {
            assertEquals(Theme.AUTH_WINDOW_WIDTH, nav.authStage.width, 0.5) // same fixed window
            val gate = nav.panelController as AgeGateController
            gate.dayBox.value = 26; gate.monthBox.value = 7; gate.yearBox.value = 2000
            gate.onContinue()
        }
        assertTrue(FxTestSupport.waitFx { nav.panelController is RegisterController })
        FxTestSupport.onFx {
            val form = nav.panelController as RegisterController
            assertEquals(LocalDate.of(2000, 7, 26), form.birthDate)
            form.onBack() // back keeps the date
        }
        assertTrue(FxTestSupport.waitFx { nav.panelController is AgeGateController })
        FxTestSupport.onFx {
            val gate = nav.panelController as AgeGateController
            assertEquals(LocalDate.of(2000, 7, 26), gate.birthDate)
            gate.onContinue()
        }
        assertTrue(FxTestSupport.waitFx { nav.panelController is RegisterController })
        FxTestSupport.onFx {
            val form = nav.panelController as RegisterController
            form.emailField.text = "stalker@mail.ru"; form.usernameField.text = "stalker_1"
            form.passwordField.text = "zone2026!"; form.licenseCheckBox.isSelected = true
            form.onCreate()
        }
        assertTrue(FxTestSupport.waitFx(5_000) { nav.panelController is InitialAuthController })
        FxTestSupport.onFx {
            val login = nav.panelController as InitialAuthController
            assertEquals("stalker_1", login.loginField.text)
            assertTrue(login.noticeLabel!!.text.contains("stalker_1"))
            assertTrue(login.noticeLabel!!.isVisible)
        }
        save(nav.authStage.scene, "login_after_register.png")
        FxTestSupport.onFx {
            // the totp login opens the authenticator panel in the same window
            val login = nav.panelController as InitialAuthController
            login.passwordField.text = "x"; login.loginField.text = "totp"; login.onEnter()
        }
        assertTrue(FxTestSupport.waitFx(5_000) { nav.panelController is TotpAuthController })
        FxTestSupport.onFx { nav.close() }
        app.shutdown()
    }
}

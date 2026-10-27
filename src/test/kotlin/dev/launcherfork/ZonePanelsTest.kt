package dev.launcherfork

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.screens.auth.InitialAuthController
import javafx.css.CssParser
import javafx.scene.Scene
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun enterInAFieldDoesNotBypassTheGreyButton() = FxTestSupport.onFx {
        val c = Fxml.load<InitialAuthController>(FxTestSupport.context("ru"), "auth_panel.fxml").controller
        c.loginField.text = ""; c.passwordField.text = "x"
        c.onEnter() // Enter in the password field with an empty login
        assertTrue(c.errorFirst.text.isNullOrEmpty(), "no validation error, the button is grey")
        assertFalse(c.loginField.pseudoClassStates.any { it.pseudoClassName == "error" })
    }

    @Test
    fun errorPaintsFieldsUntilEdited() {
        val c = FxTestSupport.onFx {
            Fxml.load<InitialAuthController>(FxTestSupport.context("ru"), "auth_panel.fxml").controller.also {
                it.loginField.text = "player"; it.passwordField.text = "wrong"; it.onEnter() // mock: wrong credentials
            }
        }
        assertTrue(FxTestSupport.waitFx { !c.errorFirst.text.isNullOrEmpty() }, "server error never shown")
        FxTestSupport.onFx {
            assertTrue(c.errorFirst.isManaged)
            assertTrue(c.loginField.pseudoClassStates.any { it.pseudoClassName == "error" })
            assertTrue(c.passwordField.pseudoClassStates.any { it.pseudoClassName == "error" })
            c.passwordField.text = "wrong2"
            assertFalse(c.loginField.pseudoClassStates.any { it.pseudoClassName == "error" })
            assertFalse(c.errorSecond.isManaged)
        }
    }

    @Test
    fun socialLoginIsHidden() = FxTestSupport.onFx {
        // Steam / Epic Games login hidden on the user's request (2026-10-27): the row is invisible and unmanaged, so the
        // form collapses; the nodes stay for the controller. No Google tile either.
        val panel = Fxml.load<InitialAuthController>(FxTestSupport.context("ru"), "auth_panel.fxml")
        val scene = Scene(panel.root, Theme.AUTH_PANEL_WIDTH, Theme.AUTH_PANEL_HEIGHT, Color.BLACK)
        Theme.apply(scene, "ru")
        scene.root.applyCss(); scene.root.layout()
        val row = scene.root.lookup(".zone-social-row")!!
        assertFalse(row.isVisible, "social row is shown")
        assertFalse(row.isManaged, "social row still takes space")
        assertFalse(generateSequence(panel.controller.enterSteamButton as javafx.scene.Node) { it.parent }.all { it.isVisible })
        assertTrue(scene.root.lookup("#zoneGoogle") == null, "Google tile is back")
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
        render("auth_panel_error.png", "ru", ready = { !it.errorFirst.text.isNullOrEmpty() }) { c ->
            c.loginField.text = "player"; c.passwordField.text = "wrong"; c.onEnter()
        }
    }

    /** The real separate login window (StageNavigator: title strip + PanelHost), snapshot of the whole scene. */
    @Test
    fun renderLoginWindow() {
        val app = dev.launcherfork.app.AppContext.mock(speed = 50.0, authDelayMs = 0)
        val nav = FxTestSupport.onFx {
            val stage = javafx.stage.Stage(javafx.stage.StageStyle.TRANSPARENT)
            dev.launcherfork.app.StageNavigator(stage, app, "ru").also { it.exit = {}; it.showAuth(); stage.show() }
        }
        assertTrue(FxTestSupport.waitFx(10_000) { nav.panelController is InitialAuthController })
        // focus lands in the first empty field (login is pre-filled -> password), not on a social tile
        assertTrue(FxTestSupport.waitFx(5_000) {
            val c = nav.panelController as InitialAuthController
            val expected = if (c.loginField.text.isNullOrEmpty()) c.loginField else c.passwordField
            nav.authStage.scene.focusOwner === expected
        }, "initial focus is not in the form")
        listOf("auth_window_ru.png" to false, "auth_window_error.png" to true).forEach { (file, error) ->
            val c = nav.panelController as InitialAuthController
            if (error) {
                FxTestSupport.onFx { c.loginField.text = "player"; c.passwordField.text = "wrong"; c.onEnter() }
                assertTrue(FxTestSupport.waitFx { !c.errorFirst.text.isNullOrEmpty() }, "server error never shown")
            }
            FxTestSupport.onFx {
                val scene = nav.authStage.scene
                val zoneSheet = dev.launcherfork.theme.Theme.stylesheet("theme-zone.css")
                if (zoneSheet !in scene.stylesheets) scene.stylesheets.add(1, zoneSheet)
            }
            Thread.sleep(500) // the window refits to the zone-styled content on the next layout pulse
            FxTestSupport.onFx {
                val scene = nav.authStage.scene
                scene.root.applyCss(); scene.root.layout()
                val bottom = c.registerButton.localToScene(c.registerButton.layoutBounds).maxY
                assertTrue(bottom <= scene.height - 20, "footer too close to the window edge: maxY=$bottom of ${scene.height}")
                // no hole above ВОЙТИ (social row hidden): it follows remember / restore; footer near the bottom
                val rowBottom = c.autoAuthCheckBox.localToScene(c.autoAuthCheckBox.layoutBounds).maxY
                val enterTop = c.enterButton.localToScene(c.enterButton.layoutBounds).minY
                assertTrue(enterTop - rowBottom <= 48.0, "gap above ВОЙТИ: ${enterTop - rowBottom}")
                assertTrue(bottom >= scene.height - 40, "empty strip under the footer: maxY=$bottom of ${scene.height}")
                // compact login window (user 2026-10-27): fits its content, below the full pre-main height
                assertTrue(nav.authStage.height < dev.launcherfork.theme.Theme.AUTH_WINDOW_HEIGHT, "login window height ${nav.authStage.height}")
                // the top card shows the EXBO logo instead of a heading; the title strip drops its small copy
                val logo = scene.root.lookup(".zone-top .zone-login-logo")
                assertTrue(logo is dev.launcherfork.ui.components.LogoMark && logo.isVisible, "no EXBO logo in the top card")
                assertTrue(scene.root.lookupAll(".zone-top .zone-title").isEmpty(), "heading left in the top card")
                assertFalse(scene.root.lookup("#authLogo").isVisible, "second logo in the title strip")
                // the disabled ВОЙТИ keeps a readable label (it took the window colour and vanished in the white theme)
                val off = javafx.css.PseudoClass.getPseudoClass("disabled")
                c.enterButton.pseudoClassStateChanged(off, true); c.enterButton.applyCss()
                val txt = c.enterButton.textFill as javafx.scene.paint.Color
                val btnBg = c.enterButton.background.fills.first().fill as javafx.scene.paint.Color
                c.enterButton.pseudoClassStateChanged(off, c.enterButton.isDisabled); c.enterButton.applyCss()
                assertTrue(contrast(txt, btnBg) >= 3.0, "disabled ВОЙТИ label contrast ${contrast(txt, btnBg)}")
                // red links («Восстановить пароль», «Создать») are AA text on the form black
                val formBg = (scene.root.lookup(".zone-bottom") as javafx.scene.layout.Region).background.fills.first().fill as javafx.scene.paint.Color
                val links = scene.root.lookupAll(".zone-link-accent") + scene.root.lookupAll(".zone-link-underline")
                assertTrue(links.isNotEmpty(), "no red links on the login card")
                for (l in links.filterIsInstance<javafx.scene.control.Labeled>()) {
                    val k = contrast(l.textFill as javafx.scene.paint.Color, formBg)
                    assertTrue(k >= 4.5, "red link «${l.text}» contrast $k")
                }
                // centred, on the same black as the form (no grey card behind the logo)
                val lb = logo.localToScene(logo.layoutBounds)
                assertEquals(scene.width / 2, (lb.minX + lb.maxX) / 2, 1.0, "logo not centred")
                val top = scene.root.lookup(".zone-top") as javafx.scene.layout.Region
                val form = scene.root.lookup(".zone-bottom") as javafx.scene.layout.Region
                assertEquals(form.background.fills.first().fill, top.background.fills.first().fill, "logo block differs from the form colour")
                val img = scene.snapshot(null)
                val out = BufferedImage(img.width.toInt(), img.height.toInt(), BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until out.height) for (x in 0 until out.width) out.setRGB(x, y, img.pixelReader.getArgb(x, y))
                previewDir.mkdirs()
                ImageIO.write(out, "png", File(previewDir, file))
            }
        }
        FxTestSupport.onFx { nav.close() }
        app.shutdown()
    }

    @Test
    fun renderMailCodeWindow() {
        val app = dev.launcherfork.app.AppContext.mock(speed = 50.0, authDelayMs = 0, mock2fa = dev.launcherfork.data.mock.MockSecondFactor.EMAIL)
        val nav = FxTestSupport.onFx {
            val stage = javafx.stage.Stage(javafx.stage.StageStyle.TRANSPARENT)
            dev.launcherfork.app.StageNavigator(stage, app, "ru").also { it.exit = {}; it.showAuth(); stage.show() }
        }
        assertTrue(FxTestSupport.waitFx(10_000) { nav.panelController is InitialAuthController })
        val loginHeight = FxTestSupport.onFx {
            val c = nav.panelController as InitialAuthController
            c.loginField.text = "player"; c.passwordField.text = "secret"; c.onEnter()
            nav.authStage.height
        }
        assertTrue(FxTestSupport.waitFx(10_000) { nav.panelController is dev.launcherfork.ui.screens.auth.MailAuthController }, "mail code never shown")
        listOf("auth_window_mail.png" to false, "auth_window_mail_error.png" to true).forEach { (file, error) ->
            val c = nav.panelController as dev.launcherfork.ui.screens.auth.MailAuthController
            if (error) {
                FxTestSupport.onFx { c.errorFirst.text = "Неверный код" }
            }
            FxTestSupport.onFx {
                val scene = nav.authStage.scene
                val zoneSheet = dev.launcherfork.theme.Theme.stylesheet("theme-zone.css")
                if (zoneSheet !in scene.stylesheets) scene.stylesheets.add(1, zoneSheet)
            }
            Thread.sleep(500) // the compact window refits to the zone-styled content on the next layout pulse
            FxTestSupport.onFx {
                val scene = nav.authStage.scene
                scene.root.applyCss(); scene.root.layout()
                val h = nav.authStage.height
                // compact: idle lower than the login window; the error toast grows it, within the full pre-main height
                if (!error) assertTrue(h < loginHeight, "code window ($h) should be lower than the login window ($loginHeight)")
                if (!error) assertTrue(h <= 460.0, "code window too tall: $h")
                assertTrue(h <= dev.launcherfork.theme.Theme.AUTH_WINDOW_HEIGHT, "code window too tall: $h")
                assertTrue(scene.root.lookup("#authLogo").isVisible, "title strip logo missing on the code window")
                val bottom = c.backButton!!.let { it.localToScene(it.layoutBounds) }.maxY
                assertTrue(bottom <= scene.height - 16 && bottom >= scene.height - 44, "footer at $bottom of ${scene.height}")
                if (error) assertTrue(c.toast!!.let { !it.isVisible || it.localToScene(it.layoutBounds).maxY <= c.enterButton.localToScene(c.enterButton.layoutBounds).minY }, "error toast clipped / overlaps ПОДТВЕРДИТЬ")
                if (error) assertTrue(c.errorFirst.localToScene(c.errorFirst.layoutBounds).maxY < c.enterButton.localToScene(c.enterButton.layoutBounds).minY)
                val img = scene.snapshot(null)
                val out = BufferedImage(img.width.toInt(), img.height.toInt(), BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until out.height) for (x in 0 until out.width) out.setRGB(x, y, img.pixelReader.getArgb(x, y))
                previewDir.mkdirs()
                ImageIO.write(out, "png", File(previewDir, file))
            }
        }
        FxTestSupport.onFx { nav.close() }
        app.shutdown()
    }

    /** The panel alone at the login window's content size (460x588 under LR's 32 px title bar), zone theme forced. */
    private fun render(
        file: String,
        lang: String,
        ready: (InitialAuthController) -> Boolean = { true },
        prepare: (InitialAuthController) -> Unit = {},
    ) {
        val panel = FxTestSupport.onFx {
            val panel = Fxml.load<InitialAuthController>(FxTestSupport.context(lang), "auth_panel.fxml")
            val scene = Scene(panel.root, Theme.AUTH_PANEL_WIDTH, Theme.AUTH_PANEL_HEIGHT, Color.BLACK)
            dev.launcherfork.theme.Theme.apply(scene, lang)
            // preview always in the zone theme, whatever -Dlauncher.theme the test JVM got
            dev.launcherfork.theme.Theme.zoneFonts
            val zoneSheet = dev.launcherfork.theme.Theme.stylesheet("theme-zone.css")
            if (zoneSheet !in scene.stylesheets) scene.stylesheets.add(1, zoneSheet)
            scene.root.applyCss(); scene.root.layout()
            prepare(panel.controller)
            panel
        }
        assertTrue(FxTestSupport.waitFx { ready(panel.controller) }, "$file: state never reached")
        snapshot(file, panel.controller.registerButton.scene, panel.controller)
    }

    private fun snapshot(file: String, scene: Scene, c: InitialAuthController) = FxTestSupport.onFx {
        val host = scene.root
        host.applyCss(); host.layout()
        // the panel must fill the window and keep every control inside it
        assertEquals(460.0, host.layoutBounds.width, 0.5)
        val bottom = c.registerButton.localToScene(c.registerButton.layoutBounds).maxY
        // at least 20 px of air under "Создать", also with an error line shown
        assertTrue(bottom <= 568.0, "footer too close to the window edge: maxY=$bottom")
        val img = scene.snapshot(null)
        val w = img.width.toInt(); val h = img.height.toInt()
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val reader = img.pixelReader
        for (y in 0 until h) for (x in 0 until w) out.setRGB(x, y, reader.getArgb(x, y))
        // one monotone black: the logo block and the form share a colour (user, 2026-10-07)
        // the compact panel is shorter than this scene: sample the form just under the footer, not the scene bottom
        val formY = (bottom + 8).toInt().coerceAtMost(h - 1)
        assertEquals(out.getRGB(10, 10), out.getRGB(10, formY), "logo block and form should be the same black")
        previewDir.mkdirs()
        ImageIO.write(out, "png", File(previewDir, file))
    }

    /** WCAG contrast of [fg] (alpha blended over [bg]) against [bg]. */
    private fun contrast(fg: javafx.scene.paint.Color, bg: javafx.scene.paint.Color): Double {
        fun mix(f: Double, b: Double) = f * fg.opacity + b * (1 - fg.opacity)
        fun lin(c: Double) = if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        fun lum(r: Double, g: Double, b: Double) = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
        val a = lum(mix(fg.red, bg.red), mix(fg.green, bg.green), mix(fg.blue, bg.blue))
        val b = lum(bg.red, bg.green, bg.blue)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}

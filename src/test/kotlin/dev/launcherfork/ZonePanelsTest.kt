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
    fun socialTilesAreSteamAndEpicOnly() = FxTestSupport.onFx {
        // Google auth is not supported: only Steam and Epic Games tiles, 44x44 with 10 px spacing, right-aligned in the form
        // row under remember / restore (user's sketch), the top card keeps only the title
        val panel = Fxml.load<InitialAuthController>(FxTestSupport.context("ru"), "auth_panel.fxml")
        val scene = Scene(panel.root, 460.0, 588.0, Color.BLACK)
        dev.launcherfork.theme.Theme.apply(scene, "ru")
        val zoneSheet = dev.launcherfork.theme.Theme.stylesheet("theme-zone.css")
        if (zoneSheet !in scene.stylesheets) scene.stylesheets.add(1, zoneSheet)
        scene.root.applyCss(); scene.root.layout()
        val tiles = scene.root.lookupAll(".zone-social").map { it as javafx.scene.control.Button }
        assertEquals(listOf("Steam", "Epic Games"), tiles.map { it.accessibleText })
        assertTrue(scene.root.lookup("#zoneGoogle") == null, "Google tile is back")
        assertTrue(scene.root.lookupAll(".icon-google").isEmpty())
        // Tab order: Steam -> Epic (document order, both traversable), Steam is the controller's enterSteamButton
        assertTrue(tiles[0] === panel.controller.enterSteamButton)
        assertTrue(tiles.all { it.isFocusTraversable })
        val row = tiles[0].parent as javafx.scene.layout.HBox
        assertEquals(tiles, row.children.toList())
        tiles.forEach { assertEquals(44.0, it.width, 0.5); assertEquals(44.0, it.height, 0.5) }
        assertEquals(44.0 + 10.0, tiles[1].layoutX - tiles[0].layoutX, 0.5)
        val c = panel.controller
        val form = c.enterButton.localToScene(c.enterButton.layoutBounds)
        val epic = tiles[1].localToScene(tiles[1].layoutBounds)
        val steam = tiles[0].localToScene(tiles[0].layoutBounds)
        assertEquals(form.maxX, epic.maxX, 1.0, "tiles end at the form's right edge")
        assertTrue(steam.minY > c.autoAuthCheckBox.localToScene(c.autoAuthCheckBox.layoutBounds).maxY, "tiles under remember me")
        assertTrue(steam.maxY < form.minY, "tiles above ВОЙТИ")
        assertTrue(scene.root.lookupAll(".zone-top .zone-social").isEmpty(), "tiles left in the top card")
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
            Thread.sleep(500) // the compact window refits to the zone-styled content on the next layout pulse
            FxTestSupport.onFx {
                val scene = nav.authStage.scene
                scene.root.applyCss(); scene.root.layout()
                val bottom = c.registerButton.localToScene(c.registerButton.layoutBounds).maxY
                assertTrue(bottom <= scene.height - 20, "footer too close to the window edge: maxY=$bottom of ${scene.height}")
                // no hole above ВОЙТИ, no empty strip under the footer: the compact window fits its content
                val socialsBottom = c.enterSteamButton.localToScene(c.enterSteamButton.layoutBounds).maxY
                val enterTop = c.enterButton.localToScene(c.enterButton.layoutBounds).minY
                assertTrue(enterTop - socialsBottom <= 40.0, "gap above ВОЙТИ: ${enterTop - socialsBottom}")
                assertTrue(bottom >= scene.height - 40, "empty strip under the footer: maxY=$bottom of ${scene.height}")
                assertTrue(nav.authStage.height < dev.launcherfork.theme.Theme.AUTH_WINDOW_HEIGHT, "login window not compact: ${nav.authStage.height}")
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
                assertTrue(h < loginHeight, "code window ($h) should be lower than the login window ($loginHeight)")
                assertTrue(h <= 460.0, "code window too tall: $h")
                val bottom = c.backButton!!.let { it.localToScene(it.layoutBounds) }.maxY
                assertTrue(bottom <= scene.height - 16 && bottom >= scene.height - 44, "footer at $bottom of ${scene.height}")
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
            val scene = Scene(panel.root, 460.0, 588.0, Color.BLACK)
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
        assertNotEquals(out.getRGB(10, 10), out.getRGB(10, h - 10), "top and bottom blocks should differ")
        previewDir.mkdirs()
        ImageIO.write(out, "png", File(previewDir, file))
    }
}

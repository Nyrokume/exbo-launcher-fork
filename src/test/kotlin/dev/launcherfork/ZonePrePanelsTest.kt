package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.Navigator
import dev.launcherfork.i18n.Strings
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.screens.auth.MailAuthController
import dev.launcherfork.ui.screens.update.LauncherUpdateController
import javafx.scene.Scene
import javafx.scene.control.ProgressIndicator
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Zone e-mail code and update panels (same look as auth_panel.fxml). Behaviour checks plus PNGs of the real
 * pre-main window into build/zone-preview/ for design review.
 */
class ZonePrePanelsTest {
    private val previewDir = File(System.getProperty("zone.preview", "build/zone-preview"))

    private fun mail(nav: Navigator = NoopNavigator): MailAuthController {
        val app = AppContext.mock(language = "ru", speed = 50.0, authDelayMs = 0)
        val ctx = UiContext(app.services, nav, Strings.bundle("ru"), "ru", app.clientStates, app.devMode)
        return Fxml.load<MailAuthController>(ctx, "mail_auth_panel.fxml").controller
    }

    @Test
    fun confirmIsGreyUntilAllDigitsAndEnterDoesNotBypassIt() = FxTestSupport.onFx {
        val c = mail()
        assertEquals(true, c.cssHover)
        assertTrue(c.enterButton.isDisabled)
        c.codeField.text = "12345"
        assertTrue(c.enterButton.isDisabled)
        c.enter() // Enter in the field with 5 digits
        assertTrue(c.errorFirst.text.isNullOrEmpty())
        c.codeField.text = "123456"
        assertFalse(c.enterButton.isDisabled)
        c.codeField.text = "12a"
        assertEquals("123456", c.codeField.text, "digits only, max 6")
    }

    @Test
    fun serverErrorPaintsTheCodeFieldUntilEdited() {
        // fresh mock without a pending login answers SERVER_ERROR
        val c = FxTestSupport.onFx { mail().also { it.codeField.text = "123456"; it.enter() } }
        assertTrue(FxTestSupport.waitFx { !c.errorFirst.text.isNullOrEmpty() })
        FxTestSupport.onFx {
            assertTrue(c.codeField.pseudoClassStates.any { it.pseudoClassName == "error" })
            assertTrue(c.errorFirst.isManaged)
            assertFalse(c.errorSecond.isManaged)
            c.codeField.text = "12345"
            assertFalse(c.codeField.pseudoClassStates.any { it.pseudoClassName == "error" })
        }
    }

    @Test
    fun backReturnsToLogin() = FxTestSupport.onFx {
        var shown = false
        val nav = object : Navigator by NoopNavigator { override fun showAuth() { shown = true } }
        val c = mail(nav)
        assertNotNull(c.backButton)
        c.backButton!!.fire()
        assertTrue(shown)
    }

    @Test
    fun legacyMailPanelUntouched() = FxTestSupport.onFx {
        val c = Fxml.load<MailAuthController>(FxTestSupport.context("ru"), "mail_auth.fxml").controller
        assertEquals(null, c.cssHover)
        assertFalse(c.enterButton.disableProperty().isBound)
    }

    @Test
    fun updateBarGoesFromIndeterminateToFullAndFinishes() {
        val c = FxTestSupport.onFx { Fxml.load<LauncherUpdateController>(FxTestSupport.context("ru"), "update_panel.fxml").controller }
        var done = false
        var sawProgress = false
        FxTestSupport.onFx {
            val bar = requireNotNull(c.progressBar)
            assertEquals(ProgressIndicator.INDETERMINATE_PROGRESS, bar.progress)
            bar.progressProperty().addListener { _, _, v -> if (v.toDouble() > 0) sawProgress = true }
            c.start { done = true }
            assertFalse(c.rollingBar.isVisible, "the bar replaces the spinner")
        }
        assertTrue(FxTestSupport.waitFx(15_000) { done }, "update never finished")
        assertTrue(sawProgress, "bar never showed determinate progress")
    }

    @Test
    fun renderPreWindowPanels() {
        val app = AppContext.mock(speed = 50.0, authDelayMs = 0)
        val nav = FxTestSupport.onFx {
            val stage = Stage(StageStyle.TRANSPARENT)
            StageNavigator(stage, app, "ru").also { it.exit = {}; it.showLauncherUpdate {}; stage.show() }
        }
        // update panel, frozen mid-download for the picture
        assertTrue(FxTestSupport.waitFx { nav.panelController is LauncherUpdateController })
        FxTestSupport.onFx {
            val c = nav.panelController as LauncherUpdateController
            c.stop()
            c.progressBar!!.progress = 0.42
            c.statusLabel.text = "Загрузка обновления 4.2 MB / 10 MB"
        }
        snap(nav, "update_window_ru.png")

        FxTestSupport.onFx { nav.showMailAuth() }
        assertTrue(FxTestSupport.waitFx { nav.panelController is MailAuthController })
        assertTrue(FxTestSupport.waitFx {
            val c = nav.panelController as MailAuthController
            nav.authStage.scene.focusOwner === c.codeField
        }, "focus is not in the code field")
        snap(nav, "mail_window_ru.png")
        FxTestSupport.onFx { (nav.panelController as MailAuthController).codeField.text = "123456" }
        snap(nav, "mail_window_filled.png")
        FxTestSupport.onFx { nav.close() }
        app.shutdown()
    }

    private fun snap(nav: StageNavigator, file: String) = FxTestSupport.onFx {
        val scene: Scene = nav.authStage.scene
        val zoneSheet = Theme.stylesheet("theme-zone.css")
        if (zoneSheet !in scene.stylesheets) scene.stylesheets.add(1, zoneSheet)
        scene.root.applyCss(); scene.root.layout()
        val img = scene.snapshot(null)
        val out = BufferedImage(img.width.toInt(), img.height.toInt(), BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until out.height) for (x in 0 until out.width) out.setRGB(x, y, img.pixelReader.getArgb(x, y))
        previewDir.mkdirs()
        ImageIO.write(out, "png", File(previewDir, file))
    }
}

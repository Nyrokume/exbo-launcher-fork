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

    private fun MailAuthController.cells() = requireNotNull(codeInput) { "mail_auth_panel.fxml has no CodeInput" }

    @Test
    fun nineCellsAndConfirmIsGreyUntilAllAreFilled() = FxTestSupport.onFx {
        val c = mail()
        val cells = c.cells()
        assertEquals(MailAuthController.CODE_LENGTH, cells.cells.size)
        assertEquals(9, MailAuthController.CODE_LENGTH)
        assertEquals("Цифра 1 из 9", cells.cells.first().accessibleText)
        assertTrue(c.enterButton.isDisabled)
        cells.setCode("26841924")
        assertTrue(c.enterButton.isDisabled)
        c.enter() // Enter in a cell with 8 digits
        assertTrue(c.errorFirst.text.isNullOrEmpty())
        cells.cells[8].replaceSelection("9")
        assertEquals("268419249", c.code)
        assertFalse(c.enterButton.isDisabled)
    }

    @Test
    fun pastedCodeFillsEveryCell() = FxTestSupport.onFx {
        val c = mail()
        val cells = c.cells()
        cells.cells[4].replaceSelection("268 419 249\n") // pasted into a middle cell, with spaces and a newline
        assertEquals("268419249", c.code, "a whole code fills all cells from the first")
        assertTrue(cells.cells.all { it.text.length == 1 })
        assertFalse(c.enterButton.isDisabled)
        cells.clear()
        cells.cells[0].replaceSelection("26-84-19-24-99-1")
        assertEquals("268419249", c.code, "extra digits are dropped")
        cells.cells[3].replaceSelection("x")
        assertEquals("268419249", c.code, "letters change nothing")
    }

    @Test
    fun serverErrorPaintsTheCellsUntilEdited() {
        // fresh mock without a pending login answers SERVER_ERROR
        val c = FxTestSupport.onFx { mail().also { it.cells().setCode("268419249"); it.enter() } }
        assertTrue(FxTestSupport.waitFx { !c.errorFirst.text.isNullOrEmpty() })
        FxTestSupport.onFx {
            val cells = c.cells()
            assertTrue(cells.cells.all { cell -> cell.pseudoClassStates.any { it.pseudoClassName == "error" } })
            assertTrue(c.errorFirst.isManaged)
            assertFalse(c.errorSecond.isManaged)
            cells.cells[8].replaceSelection("1")
            assertTrue(c.errorFirst.text.isNullOrEmpty())
            assertFalse(cells.cells.any { cell -> cell.pseudoClassStates.any { it.pseudoClassName == "error" } })
        }
    }

    @Test
    fun nineDigitCodeSignsInAfterTheEmailStep() {
        val app = AppContext.mock(language = "ru", speed = 50.0, authDelayMs = 0)
        assertEquals(dev.launcherfork.domain.AuthResult.CodeRequired, app.services.auth.login("2fa", "x", false))
        val ctx = UiContext(app.services, NoopNavigator, Strings.bundle("ru"), "ru", app.clientStates, app.devMode)
        val c = FxTestSupport.onFx {
            Fxml.load<MailAuthController>(ctx, "mail_auth_panel.fxml").controller.also { it.cells().setCode("268419249"); it.enter() }
        }
        assertTrue(FxTestSupport.waitFx { app.services.auth.currentSession != null }, "the 9-digit code was not accepted")
        FxTestSupport.onFx { assertTrue(c.errorFirst.text.isNullOrEmpty()) }
        app.shutdown()
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
    fun legacyMailPanelKeepsItsTextField() = FxTestSupport.onFx {
        val c = Fxml.load<MailAuthController>(FxTestSupport.context("ru"), "mail_auth.fxml").controller
        assertEquals(null, c.codeInput)
        val field = requireNotNull(c.codeField)
        assertFalse(c.enterButton.disableProperty().isBound)
        field.replaceSelection("268 419 249 1")
        assertEquals("268419249", field.text, "digits only, cut to nine")
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
            nav.authStage.scene.focusOwner === c.codeInput!!.cells.first()
        }, "focus is not in the first code cell")
        snap(nav, "mail_window_ru.png")
        FxTestSupport.onFx {
            val c = nav.panelController as MailAuthController
            c.codeInput!!.setCode("268419249")
            val scene = nav.authStage.scene
            scene.root.applyCss(); scene.root.layout()
            // all nine cells inside the 460 px window, with the form's side padding
            val first = c.codeInput!!.cells.first().let { it.localToScene(it.layoutBounds) }
            val last = c.codeInput!!.cells.last().let { it.localToScene(it.layoutBounds) }
            assertTrue(first.minX >= 20.0 && last.maxX <= scene.width - 20.0, "cells overflow: ${first.minX}..${last.maxX} of ${scene.width}")
            val button = c.enterButton.localToScene(c.enterButton.layoutBounds)
            assertEquals(button.minX, first.minX, 1.0, "cells start where ПОДТВЕРДИТЬ starts")
            // like the login panel: no hole between the cells and ПОДТВЕРДИТЬ, spare height goes to the top card
            assertTrue(button.minY - first.maxY <= 48.0, "gap above ПОДТВЕРДИТЬ: ${button.minY - first.maxY}")
        }
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

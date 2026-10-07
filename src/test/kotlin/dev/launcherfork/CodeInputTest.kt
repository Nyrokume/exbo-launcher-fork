package dev.launcherfork

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.components.CodeInput
import dev.launcherfork.ui.screens.auth.TotpAuthController
import javafx.scene.Scene
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CodeInput digit cells (cell count is a parameter) and the authenticator-app panel built on them: 6 cells, paste,
 * keys, the mock's wrong code (000000) -> toast. The e-mail code screen is not part of this branch.
 */
class CodeInputTest {
    private fun cells(n: Int) = FxTestSupport.onFx { CodeInput().apply { cellCount = n } }

    private fun CodeInput.key(index: Int, code: KeyCode) =
        cells[index].fireEvent(KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))

    @Test
    fun typingFillsCellsAndAdvances() {
        val c = cells(6)
        FxTestSupport.onFx {
            assertEquals(6, c.cells.size)
            assertTrue("cells-6" in c.styleClass)
            var completed = ""
            c.onComplete = { completed = it }
            var at = 0
            "55896".forEach { d -> at = c.input(at, d.toString()) }
            assertEquals("55896", c.code)
            assertEquals(5, at, "focus moved to the 6th cell")
            assertFalse(c.isComplete)
            c.cells[5].replaceText(0, 0, "x") // letters are ignored (typed keys go through replaceText)
            assertEquals("55896", c.code)
            c.cells[5].replaceText(0, 0, "6")
            assertEquals("558966", c.code)
            assertTrue(c.isComplete)
            assertEquals("558966", completed)
            c.cells[0].replaceText(0, 1, "1") // typing into a filled cell replaces its digit
            assertEquals("158966", c.code)
            assertEquals(listOf("1", "5", "8", "9", "6", "6"), c.cells.map { it.text })
        }
    }

    @Test
    fun pasteOfAWholeCodeFillsAllCellsFromAnyCell() {
        val c = cells(TotpAuthController.CODE_LENGTH)
        FxTestSupport.onFx {
            assertEquals(6, c.cells.size)
            c.cells[4].replaceSelection("558966") // what Ctrl+V does
            assertEquals("558966", c.code)
            assertTrue(c.isComplete)
            c.clear()
            assertEquals("", c.code)
            c.cells[0].replaceSelection(" 558-966\n") // spaces, dashes and newlines are dropped
            assertEquals("558966", c.code)
            c.clear()
            c.input(2, "12")          // a partial paste fills from that cell
            assertEquals(listOf("", "", "1", "2", "", ""), c.cells.map { it.text })
            c.input(4, "98765")       // overflow is cut at the last cell
            assertEquals(listOf("9", "8"), c.cells.takeLast(2).map { it.text })
            c.setCode("12345678")     // longer than the code: the first 6 digits
            assertEquals("123456", c.code)
        }
    }

    @Test
    fun cellCountIsAParameter() {
        val c = cells(8)
        FxTestSupport.onFx {
            assertEquals(8, c.cells.size)
            assertTrue("cells-8" in c.styleClass)
            c.cellCount = 4
            assertEquals(4, c.cells.size)
            assertFalse("cells-8" in c.styleClass)
            c.setCode("1234")
            assertTrue(c.isComplete)
        }
    }

    @Test
    fun backspaceClearsThenGoesBack() {
        val c = cells(6)
        FxTestSupport.onFx {
            c.setCode("123")
            c.key(2, KeyCode.BACK_SPACE)       // filled cell: cleared, stays
            assertEquals("12", c.code)
            c.key(2, KeyCode.BACK_SPACE)       // empty cell: previous one is cleared
            assertEquals("1", c.code)
            assertEquals(listOf("1", "", "", "", "", ""), c.cells.map { it.text })
            c.key(0, KeyCode.DELETE)
            assertEquals("", c.code)
            c.key(0, KeyCode.BACK_SPACE)       // first cell, nothing to go back to
            assertEquals("", c.code)
        }
    }

    @Test
    fun errorPaintsCellsUntilEdited() {
        val c = cells(6)
        FxTestSupport.onFx {
            c.setCode("000000")
            c.error = true
            assertTrue(c.cells.all { cell -> cell.pseudoClassStates.any { it.pseudoClassName == "error" } })
            c.key(5, KeyCode.BACK_SPACE)
            assertFalse(c.cells.any { cell -> cell.pseudoClassStates.any { it.pseudoClassName == "error" } })
        }
    }

    /** Puts a panel into a 460x588 scene (the login window's panel area) with the zone theme, laid out. */
    private fun <C> panel(path: String, nav: RecordingNavigator, lang: String = "ru"): Pair<C, Scene> = FxTestSupport.onFx {
        val loaded = Fxml.load<C>(recordingContext(nav, lang), path)
        val scene = Scene(loaded.root, Theme.AUTH_PANEL_WIDTH, Theme.AUTH_PANEL_HEIGHT, Color.BLACK)
        Theme.apply(scene, lang)
        val zone = Theme.stylesheet("theme-zone.css")
        if (zone !in scene.stylesheets) scene.stylesheets.add(1, zone)
        loaded.root.applyCss(); loaded.root.layout()
        loaded.controller to scene
    }

    @Test
    fun totpCellsFitTheLoginWindow() {
        val (c, scene) = panel<TotpAuthController>("totp_auth_panel.fxml", RecordingNavigator())
        FxTestSupport.onFx {
            val input = c.codeInput
            assertEquals(6, input.cells.size)
            val first = input.cells.first().localToScene(input.cells.first().layoutBounds)
            val last = input.cells.last().localToScene(input.cells.last().layoutBounds)
            assertTrue(first.minX >= 20.0, "left edge ${first.minX}")
            assertTrue(last.maxX <= scene.width - 20.0, "cells overflow: right edge ${last.maxX} of ${scene.width}")
            // 6 cells are narrower than the form — keep the row centered under ОТПРАВИТЬ
            val button = c.enterButton.localToScene(c.enterButton.layoutBounds)
            val cellsMid = (first.minX + last.maxX) / 2
            val buttonMid = (button.minX + button.maxX) / 2
            assertEquals(buttonMid, cellsMid, 2.0, "cells not centered under ОТПРАВИТЬ")
        }
    }

    @Test
    fun totpButtonIsGreyUntilAllSixDigits() {
        val (c, _) = panel<TotpAuthController>("totp_auth_panel.fxml", RecordingNavigator())
        FxTestSupport.onFx {
            c.codeInput.setCode("12345")
            assertTrue(c.enterButton.isDisabled)
            c.enter() // Enter in a cell does not bypass the grey button
            assertTrue(c.errorFirst.text.isNullOrEmpty())
            c.codeInput.input(5, "6")
            assertFalse(c.enterButton.isDisabled)
        }
    }

    @Test
    fun totpPanelShowsToastForWrongCode() {
        val nav = RecordingNavigator()
        val ctx = recordingContext(nav)
        assertEquals(dev.launcherfork.domain.AuthResult.TotpRequired, ctx.services.auth.login("totp", "x", false))
        val c = FxTestSupport.onFx { Fxml.load<TotpAuthController>(ctx, "totp_auth_panel.fxml").controller }
        FxTestSupport.onFx {
            assertEquals(6, c.codeInput.cells.size)
            assertFalse(c.isToastShown)
            assertTrue(c.enterButton.isDisabled)
            c.codeInput.setCode("000000")
            c.enter()
        }
        assertTrue(FxTestSupport.waitFx { c.isToastShown })
        FxTestSupport.onFx {
            assertEquals("Неправильный код аутентификации!", c.errorFirst.text)
            val title = c.toast.lookupAll(".zone-toast-title").single() as javafx.scene.control.Label
            assertEquals("ОШИБКА ПРИ ПОДТВЕРЖДЕНИИ", title.text)
            assertTrue(c.codeInput.error)
            c.toastClose.fire()
            assertFalse(c.isToastShown)
            c.codeInput.setCode("558966")
            c.enter()
        }
        assertTrue(FxTestSupport.waitFx { "main" in nav.calls })
    }

    @Test
    fun totpGoesBackToLoginByLinkAndEsc() {
        val nav = RecordingNavigator()
        FxTestSupport.onFx {
            val c = Fxml.load<TotpAuthController>(recordingContext(nav), "totp_auth_panel.fxml").controller
            c.backButton.fire()
            c.codeInput.cells[0].fireEvent(KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false))
        }
        assertEquals(listOf("auth", "auth"), nav.calls.toList())
    }
}

package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.ui.components.InputBlock
import dev.launcherfork.ui.components.SegmentedBar
import dev.launcherfork.ui.screens.window.WindowController
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * In-window dialogs of the MAIN window (overlay layer and the modal layers of main.fxml): they block mouse AND
 * keyboard of the screen below (nothing there can take focus), the blurred content is cached, focus moves into
 * the dialog. Plus the SegmentedBar component.
 */
class OverlayNavigatorTest {
    private fun window(nav: StageNavigator): WindowController {
        val f = StageNavigator::class.java.getDeclaredField("windowController").apply { isAccessible = true }
        return f.get(nav) as WindowController
    }

    private fun isInside(node: Node?, parent: Node): Boolean {
        var n = node
        while (n != null) {
            if (n === parent) return true
            n = n.parent
        }
        return false
    }

    /** Main window on screen (Monocle) with the main screen loaded. */
    private fun mainOnScreen(app: AppContext): StageNavigator = FxTestSupport.onFx {
        val nav = StageNavigator(Stage(StageStyle.UNDECORATED), app, "ru")
        nav.exit = {}
        nav.showMain()
        nav.mainStage.show()
        nav
    }

    @Test
    fun overlayBlocksKeyboardFocusAndCachesContent() {
        val app = AppContext.mock(authDelayMs = 0)
        val nav = mainOnScreen(app)
        FxTestSupport.onFx {
            val w = window(nav)
            val scene = nav.mainStage.scene
            val underneath = nav.mainController!!.defaultStatusPaneController.downloadButton
            underneath.requestFocus()
            assertSame(underneath, scene.focusOwner, "sanity: the play button can take focus without a dialog")

            val ok = Button("OK")
            nav.showOverlay(VBox(ok))
            assertTrue(nav.isOverlayShown)
            assertTrue(w.contentHost.isDisabled, "content must be disabled (no keyboard) under a dialog")
            assertTrue(w.contentHost.isCache, "blurred content must be cached")
            assertNotNull(w.contentHost.effect)
            assertSame(ok, scene.focusOwner, "focus must move into the dialog")

            // nothing inside the content host can take focus while the dialog is up
            w.contentHost.lookupAll("*").forEach { it.requestFocus() }
            assertFalse(isInside(scene.focusOwner, w.contentHost), "focus leaked under the dialog: ${scene.focusOwner}")
            assertNull(InputBlock.firstFocusable(w.contentHost), "keyboard traversal must find nothing under the dialog")

            nav.hideOverlay()
            assertFalse(w.contentHost.isDisabled)
            assertFalse(w.contentHost.isCache)
            assertNull(w.contentHost.effect)
            underneath.requestFocus()
            assertSame(underneath, scene.focusOwner)
            nav.close()
        }
        app.shutdown()
    }

    @Test
    fun mainModalsBlockKeyboardToo() {
        val app = AppContext.mock(authDelayMs = 0)
        val nav = mainOnScreen(app)
        FxTestSupport.onFx {
            val main = nav.mainController!!
            val scene = nav.mainStage.scene
            main.logoutPaneController.show()
            assertTrue(main.layout.isDisabled, "screen under a modal must be disabled")
            assertTrue(main.layout.isCache)
            assertTrue(isInside(scene.focusOwner, main.logoutPaneController.layer), "focus must be in the modal: ${scene.focusOwner}")
            main.layout.lookupAll("*").forEach { it.requestFocus() }
            assertFalse(isInside(scene.focusOwner, main.layout), "focus leaked under the modal")
            main.logoutPaneController.hide()
            assertFalse(main.layout.isDisabled)
            assertFalse(main.layout.isCache)
            nav.close()
        }
        app.shutdown()
    }

    @Test
    fun segmentedBarSnapsToSegments() {
        FxTestSupport.onFx {
            val bar = SegmentedBar().apply { segments = 9 }
            bar.value = 0.5
            assertEquals(5, bar.activeCount) // 4.5 rounds up
            assertEquals(5.0 / 9, bar.value, 1e-9)
            bar.value = 0.0
            assertEquals(1, bar.activeCount) // never empty
            bar.value = 2.0
            assertEquals(9, bar.activeCount)
            assertEquals(9, bar.children.count { "active" in it.styleClass })
        }
    }
}

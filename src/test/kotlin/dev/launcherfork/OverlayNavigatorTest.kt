package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.ui.components.SegmentedBar
import dev.launcherfork.ui.screens.auth.InitialAuthController
import dev.launcherfork.ui.screens.auth.MailAuthController
import dev.launcherfork.ui.screens.update.LauncherUpdateController
import dev.launcherfork.ui.screens.window.WindowController
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** One window: update and auth are overlays over the blurred, input-blocked main screen. */
class OverlayNavigatorTest {
    private fun window(nav: StageNavigator): WindowController {
        val f = StageNavigator::class.java.getDeclaredField("windowController").apply { isAccessible = true }
        return f.get(nav) as WindowController
    }

    @Test
    fun startupShowsUpdateThenAuthOverMainAndLoginHidesOverlay() {
        val app = AppContext.mock(speed = 50.0, authDelayMs = 0)
        val nav = FxTestSupport.onFx {
            val stage = Stage(StageStyle.UNDECORATED)
            StageNavigator(stage, app, "ru").also { it.showLauncherUpdate(afterUpdate = it::showAuth) }
        }
        FxTestSupport.onFx {
            assertNotNull(nav.mainController, "main screen must be built under the update overlay")
            assertTrue(nav.overlayController is LauncherUpdateController || nav.overlayController is InitialAuthController)
            val w = window(nav)
            assertTrue(w.overlayLayer.isVisible)
            assertTrue(w.contentHost.isMouseTransparent, "content must not take input under an overlay")
            assertNotNull(w.contentHost.effect, "content must be blurred under an overlay")
        }
        assertTrue(FxTestSupport.waitFx(10_000) { nav.overlayController is InitialAuthController }, "update overlay never finished")

        FxTestSupport.onFx { nav.showMailAuth() }
        FxTestSupport.onFx { assertTrue(nav.overlayController is MailAuthController) }

        FxTestSupport.onFx { nav.showMain() }
        FxTestSupport.onFx {
            val w = window(nav)
            assertNull(nav.overlayController)
            assertFalse(w.overlayLayer.isVisible)
            assertFalse(w.contentHost.isMouseTransparent)
            assertNull(w.contentHost.effect)
            assertEquals(1, w.contentHost.children.size)
        }
        app.shutdown()
    }

    @Test
    fun legacyPanelsLoseTheirOwnWindowButtons() {
        val app = AppContext.mock(authDelayMs = 0)
        FxTestSupport.onFx {
            val nav = StageNavigator(Stage(StageStyle.UNDECORATED), app, "ru")
            nav.showAuth()
            val auth = nav.overlayController as InitialAuthController
            assertFalse(auth.closeButton.isVisible)
            assertFalse(auth.hideButton.isVisible)
        }
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

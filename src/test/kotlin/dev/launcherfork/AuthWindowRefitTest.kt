package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.ui.screens.auth.InitialAuthController
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Fixed 460×620 auth window: an idle panel with a focused, blinking field must not spam refits.
 * An error line uses the panel's internal VGrow (logo shrinks) — stage height stays [Theme.AUTH_WINDOW_HEIGHT].
 */
class AuthWindowRefitTest {

    @Test
    fun idlePanelDoesNotSpamRefit() {
        val nav = FxTestSupport.onFx {
            StageNavigator(Stage(StageStyle.TRANSPARENT), AppContext.mock(authDelayMs = 0), "ru")
                .also { it.exit = {}; it.showAuth(); it.authStage.show() }
        }
        try {
            fun fitted() = FxTestSupport.waitFx {
                val stage = nav.authStage
                val panel = stage.scene.lookup("#authPanelHost")
                panel != null && abs(stage.height - expectedHeight(nav)) < 1.0
            }
            assertTrue(FxTestSupport.waitFx(10_000) { nav.panelController is InitialAuthController })
            assertTrue(fitted(), "the login window did not fit its panel")
            assertEquals(dev.launcherfork.theme.Theme.AUTH_WINDOW_HEIGHT, FxTestSupport.onFx { nav.authStage.height }, 0.5)
            FxTestSupport.onFx { (nav.panelController as InitialAuthController).loginField.requestFocus() }
            Thread.sleep(300)
            val idleStart = FxTestSupport.onFx { nav.authRefitChecks }
            Thread.sleep(1_600) // three caret blinks
            assertEquals(idleStart, FxTestSupport.onFx { nav.authRefitChecks }, "an idle panel must not refit the window")

            val before = FxTestSupport.onFx { nav.authStage.height }
            FxTestSupport.onFx { (nav.panelController as InitialAuthController).onRestore() } // shows an error line
            assertTrue(FxTestSupport.waitFx { (nav.panelController as InitialAuthController).errorFirst.isVisible })
            assertEquals(before, FxTestSupport.onFx { nav.authStage.height }, 0.5, "fixed auth window must not grow on error")
            assertTrue(fitted(), "the window does not fit the panel with the error line")
        } finally {
            FxTestSupport.onFx { nav.close() }
        }
    }

    private fun expectedHeight(nav: StageNavigator): Double {
        val host = nav.authStage.scene.lookup("#authPanelHost") as javafx.scene.Parent
        val panel = host.childrenUnmodifiable.firstOrNull() as? javafx.scene.layout.Region
        return dev.launcherfork.ui.screens.window.AuthWindowController.fittedWindowHeight(panel)
    }
}

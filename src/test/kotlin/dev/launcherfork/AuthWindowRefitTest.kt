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
 * The compact pre-main window follows its panel's height only when the panel's content changes (StageNavigator
 * watches the panel's layout requests): an idle panel with a focused, blinking field runs no refit at all, while an
 * error line appearing still grows the window.
 */
class AuthWindowRefitTest {

    @Test
    fun refitRunsOnContentChangesOnly() {
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
            FxTestSupport.onFx { (nav.panelController as InitialAuthController).loginField.requestFocus() }
            Thread.sleep(300)
            val idleStart = FxTestSupport.onFx { nav.authRefitChecks }
            Thread.sleep(1_600) // three caret blinks
            assertEquals(idleStart, FxTestSupport.onFx { nav.authRefitChecks }, "an idle panel must not refit the window")

            val before = FxTestSupport.onFx { nav.authStage.height }
            FxTestSupport.onFx { (nav.panelController as InitialAuthController).onRestore() } // shows an error line
            assertTrue(FxTestSupport.waitFx { nav.authStage.height > before + 1.0 }, "the error line did not grow the window")
            assertTrue(fitted(), "the window does not fit the panel with the error line")
            assertTrue(FxTestSupport.onFx { nav.authRefitChecks } > idleStart)
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

package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.screens.main.MainController
import dev.launcherfork.ui.state.ClientState
import javafx.event.Event
import javafx.scene.Scene
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainScreenSessionTest {
    private fun loadMain(app: AppContext): MainController = FxTestSupport.onFx {
        val loaded = Fxml.load<MainController>(FxTestSupport.context(app = app), "main.fxml")
        Scene(loaded.root) // attaches the key filter
        loaded.controller
    }

    @Test
    fun downloadSurvivesMainSceneRebuild() {
        val app = AppContext(AppContext.mock(speed = 0.05, authDelayMs = 0).services)
        val first = loadMain(app)
        val server = first.servers.first { !app.services.updates.isInstalled(it) }
        FxTestSupport.onFx { app.clientStates.startDownload(server) }

        // what a language switch does: dispose the old controller, build a new scene
        FxTestSupport.onFx { first.dispose() }
        val second = loadMain(app)
        FxTestSupport.onFx {
            assertSame(app.clientStates, second.store)
            assertTrue(second.store.state(server).get() is ClientState.Downloading, "download was paused by the rebuild")
        }
        app.shutdown()
    }

    private fun press(main: MainController, code: KeyCode) = FxTestSupport.onFx {
        Event.fireEvent(main.root, KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
    }

    @Test
    fun devHotkeysAreOffByDefault() {
        val main = loadMain(AppContext.mock(authDelayMs = 0))
        press(main, KeyCode.F1)
        press(main, KeyCode.F2)
        FxTestSupport.onFx {
            assertFalse(main.updatePaneController.isShowing)
            assertFalse(main.licensePaneController.isShowing)
        }
    }

    @Test
    fun devHotkeysWorkInDevMode() {
        val main = loadMain(AppContext.mock(authDelayMs = 0, devMode = true))
        press(main, KeyCode.F1)
        FxTestSupport.onFx { assertTrue(main.updatePaneController.isShowing) }
        press(main, KeyCode.ESCAPE)
        FxTestSupport.onFx { assertFalse(main.updatePaneController.isShowing) }
    }
}

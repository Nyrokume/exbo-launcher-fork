package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.app.StageNavigator.ActiveWindow
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.components.WindowFrame
import dev.launcherfork.ui.screens.auth.InitialAuthController
import dev.launcherfork.ui.screens.auth.MailAuthController
import dev.launcherfork.ui.screens.main.MainController
import dev.launcherfork.ui.screens.update.LauncherUpdateController
import dev.launcherfork.ui.screens.window.AuthWindowController
import dev.launcherfork.ui.screens.window.WindowController
import javafx.geometry.Rectangle2D
import javafx.scene.layout.Region
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
 * Two windows as in the original launcher: start -> update -> auth (-> mail code) in the separate pre-main window,
 * login success closes it and opens the main window; switch account / logout dispose the main screen and come
 * back to the auth window on the same screen; closing either window exits the app.
 */
class AuthWindowTest {
    private fun authWindow(nav: StageNavigator) = field<AuthWindowController>(nav, "authWindow")
    private fun mainWindow(nav: StageNavigator) = field<WindowController>(nav, "windowController")

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(nav: StageNavigator, name: String): T =
        StageNavigator::class.java.getDeclaredField(name).apply { isAccessible = true }.get(nav) as T

    private class Live(val app: AppContext, val nav: StageNavigator) {
        var exited = false
    }

    /** App as LauncherApp starts it: pre-main window on screen with the update panel, then auth. */
    private fun start(): Live {
        val app = AppContext.mock(speed = 50.0, authDelayMs = 0)
        val live = FxTestSupport.onFx {
            val stage = Stage(StageStyle.TRANSPARENT)
            val nav = StageNavigator(stage, app, "ru")
            Live(app, nav).also { l ->
                nav.exit = { l.exited = true }
                nav.showLauncherUpdate(afterUpdate = nav::showAuth)
                stage.show()
            }
        }
        assertTrue(FxTestSupport.waitFx(10_000) { live.nav.panelController is InitialAuthController }, "update never finished")
        return live
    }

    private fun login(live: Live, user: String = "player") {
        FxTestSupport.onFx {
            val auth = live.nav.panelController as InitialAuthController
            auth.loginField.text = user
            auth.passwordField.text = "secret"
            auth.onEnter()
        }
    }

    private fun loggedIn(): Live {
        val live = start()
        login(live)
        assertTrue(FxTestSupport.waitFx(10_000) { live.nav.activeWindow == ActiveWindow.MAIN }, "login never reached main")
        return live
    }

    @Test
    fun startupUsesSeparateAuthWindowThenMainWindow() {
        val app = AppContext.mock(speed = 50.0, authDelayMs = 0)
        val nav = FxTestSupport.onFx {
            val stage = Stage(StageStyle.TRANSPARENT)
            StageNavigator(stage, app, "ru").also {
                it.exit = {}
                it.showLauncherUpdate(afterUpdate = it::showAuth)
                stage.show()
            }
        }
        FxTestSupport.onFx {
            assertTrue(nav.panelController is LauncherUpdateController || nav.panelController is InitialAuthController)
            assertEquals(ActiveWindow.PRE_MAIN, nav.activeWindow)
            assertNull(nav.mainController, "no main screen (and no blur) behind the auth window")
            assertTrue(nav.authStage.isShowing)
            assertFalse(nav.mainStage.isShowing)
            assertFalse(nav.authStage.isResizable)
            assertEquals(Theme.AUTH_WINDOW_WIDTH, nav.authStage.scene.width, 0.5)
            assertEquals(authWindow(nav).fittedWindowHeight(), nav.authStage.height, 0.5)
        }
        assertTrue(FxTestSupport.waitFx(10_000) { nav.panelController is InitialAuthController })
        assertTrue(FxTestSupport.waitFx(5_000) { kotlin.math.abs(nav.authStage.scene.height - authWindow(nav).fittedWindowHeight()) < 0.5 }, "scene never took the compact height")
        FxTestSupport.onFx {
            val host = authWindow(nav).panelHost
            nav.authStage.scene.root.applyCss(); nav.authStage.scene.root.layout()
            assertEquals(Theme.AUTH_PANEL_WIDTH, host.width, 0.5)
            // compact login panel: the window is as tall as its content (title strip + panel), not the full 620
            val fitted = authWindow(nav).fittedWindowHeight()
            assertTrue(fitted < Theme.AUTH_WINDOW_HEIGHT && fitted >= Theme.AUTH_WINDOW_MIN_HEIGHT, "login window height $fitted")
            assertEquals(fitted, nav.authStage.height, 0.5)
            assertEquals(fitted - Theme.AUTH_TITLEBAR_HEIGHT, host.height, 0.5)
            assertNotNull(authWindow(nav).panel as? Region)
            assertSame(nav.authStage.scene, (nav.panelController as InitialAuthController).loginField.scene, "panel lives in the auth window")
            // legacy chrome hidden: the window strip has its own minimize / close
            val auth = nav.panelController as InitialAuthController
            assertFalse(auth.closeButton.isVisible)
            assertFalse(auth.hideButton.isVisible)
            nav.close()
        }
        app.shutdown()
    }

    @Test
    fun panelWithoutFixedSizeFillsTheHost() {
        // contract for auth_panel.fxml (redesign/auth-zone): min 420x560, pref 460x588, no max -> fills the host
        FxTestSupport.onFx {
            val nav = StageNavigator(Stage(StageStyle.TRANSPARENT), AppContext.mock(authDelayMs = 0), "ru")
            val panel = javafx.scene.layout.StackPane().apply { setMinSize(420.0, 560.0); setPrefSize(460.0, 588.0) }
            authWindow(nav).setPanel(panel)
            nav.authStage.scene.root.applyCss(); nav.authStage.scene.root.layout()
            assertEquals(Theme.AUTH_PANEL_WIDTH, panel.width, 0.5)
            assertEquals(Theme.AUTH_PANEL_HEIGHT, panel.height, 0.5)
            assertEquals(Theme.AUTH_TITLEBAR_HEIGHT, panel.localToScene(0.0, 0.0).y, 0.5)
        }
    }

    @Test
    fun panelAskingForMoreThanTheHostStillFitsBelowTheTitleStrip() {
        // content of a real panel may compute a min height a bit above 588: the host sizes it like a Scene root
        FxTestSupport.onFx {
            val nav = StageNavigator(Stage(StageStyle.TRANSPARENT), AppContext.mock(authDelayMs = 0), "ru")
            val panel = javafx.scene.layout.StackPane().apply { setMinSize(420.0, 610.0); setPrefSize(460.0, 610.0) }
            authWindow(nav).setPanel(panel)
            nav.authStage.scene.root.applyCss(); nav.authStage.scene.root.layout()
            assertEquals(Theme.AUTH_PANEL_HEIGHT, panel.height, 0.5)
            assertEquals(Theme.AUTH_TITLEBAR_HEIGHT, panel.localToScene(0.0, 0.0).y, 0.5, "panel must not cover the title strip")
            assertEquals(Theme.AUTH_TITLEBAR_HEIGHT, authWindow(nav).authTitleBar.height, 0.5)
        }
    }

    @Test
    fun fixedSizePanelIsCentered() {
        FxTestSupport.onFx {
            val nav = StageNavigator(Stage(StageStyle.TRANSPARENT), AppContext.mock(authDelayMs = 0), "ru")
            val panel = javafx.scene.layout.Pane().apply { setMinSize(379.0, 490.0); setPrefSize(379.0, 490.0); setMaxSize(379.0, 490.0) }
            authWindow(nav).setPanel(panel)
            nav.authStage.scene.root.applyCss(); nav.authStage.scene.root.layout()
            assertEquals(379.0, panel.width, 0.5)
            assertEquals((Theme.AUTH_PANEL_WIDTH - 379) / 2, panel.localToScene(0.0, 0.0).x, 1.0)
        }
    }

    @Test
    fun loginClosesAuthWindowAndOpensMain() {
        val live = loggedIn()
        FxTestSupport.onFx {
            val nav = live.nav
            assertTrue(nav.mainStage.isShowing, "main window must be open")
            assertFalse(nav.authStage.isShowing, "auth window must be closed")
            assertNull(nav.panelController)
            assertNotNull(nav.mainController)
            assertEquals("PLAYER", nav.mainController!!.profileBarController.accountName.text)
            assertSame(nav.mainStage, nav.window)
            nav.close()
        }
        live.app.shutdown()
    }

    @Test
    fun mailCodeStaysInTheAuthWindow() {
        val live = start()
        login(live, user = "2fa")
        assertTrue(FxTestSupport.waitFx(10_000) { live.nav.panelController is MailAuthController })
        FxTestSupport.onFx {
            assertEquals(ActiveWindow.PRE_MAIN, live.nav.activeWindow)
            assertTrue(live.nav.authStage.isShowing)
            assertFalse(live.nav.mainStage.isShowing)
            live.nav.close()
        }
        live.app.shutdown()
    }

    /** Fires [action] in the main window and checks the main screen was disposed BEFORE the auth panel showed. */
    private fun assertBackToAuth(action: (MainController) -> Unit) {
        val live = loggedIn()
        FxTestSupport.onFx {
            val nav = live.nav
            val main = nav.mainController!!
            var disposedBeforeAuth: Boolean? = null
            nav.onScreenChanged = { name -> if (name == "auth") disposedBeforeAuth = main.isDisposed }
            action(main)
            assertEquals(true, disposedBeforeAuth, "MainController.dispose() must run before the auth window shows")
            assertEquals(ActiveWindow.PRE_MAIN, nav.activeWindow)
            assertTrue(nav.panelController is InitialAuthController)
            assertNull(nav.mainController)
            assertTrue(nav.authStage.isShowing)
            assertFalse(nav.mainStage.isShowing)
            assertNull(live.app.services.auth.currentSession)
            nav.close()
        }
        live.app.shutdown()
    }

    @Test
    fun switchAccountDisposesMainAndOpensAuthWindow() = assertBackToAuth { main ->
        main.profileBarController.loginButton.fire()
        assertTrue(main.profileBarController.isMenuShown)
        main.profileBarController.switchAccount.fire()
    }

    @Test
    fun logoutDisposesMainAndOpensAuthWindow() = assertBackToAuth { main ->
        main.profileBarController.loginButton.fire()
        main.profileBarController.logout.fire()
        assertTrue(main.logoutPaneController.isShowing, "log out asks for confirmation")
        main.logoutPaneController.logoutButtonRight.fire()
    }

    @Test
    fun authWindowIsCenteredOnTheScreenOfTheMainWindow() {
        val live = loggedIn()
        FxTestSupport.onFx {
            val nav = live.nav
            nav.mainStage.x = 40.0
            nav.mainStage.y = 30.0
            val mainBounds = Rectangle2D(nav.mainStage.x, nav.mainStage.y, nav.mainStage.width, nav.mainStage.height)
            nav.mainController!!.profileBarController.switchAccount.fire()
            val (x, y) = WindowFrame.centeredIn(WindowFrame.screenBoundsOf(mainBounds), Theme.AUTH_WINDOW_WIDTH, authWindow(nav).fittedWindowHeight())
            assertEquals(x, nav.authStage.x, 0.5)
            assertEquals(y, nav.authStage.y, 0.5)
            nav.close()
        }
        live.app.shutdown()
    }

    @Test
    fun mainWindowOpensAtDefaultSizeThenKeepsTheUserSizeAndMaximize() {
        val live = start()
        val authBounds = FxTestSupport.onFx {
            Rectangle2D(live.nav.authStage.x, live.nav.authStage.y, live.nav.authStage.width, live.nav.authStage.height)
        }
        login(live)
        assertTrue(FxTestSupport.waitFx(10_000) { live.nav.activeWindow == ActiveWindow.MAIN })
        FxTestSupport.onFx {
            val nav = live.nav
            // first time: default size, centered on the screen of the auth window; actions target the main slot
            assertEquals(Theme.WINDOW_WIDTH, nav.mainStage.width, 0.5)
            assertEquals(Theme.WINDOW_HEIGHT, nav.mainStage.height, 0.5)
            val (x, y) = WindowFrame.centeredIn(WindowFrame.screenBoundsOf(authBounds), Theme.WINDOW_WIDTH, Theme.WINDOW_HEIGHT)
            assertEquals(x, nav.mainStage.x, 0.5)
            assertEquals(y, nav.mainStage.y, 0.5)
            assertSame(nav.mainStage, nav.window)
            // the user resizes the main window, then switches account
            nav.mainStage.width = 900.0
            nav.mainStage.height = 600.0
            nav.mainController!!.profileBarController.switchAccount.fire()
            assertSame(nav.authStage, nav.window)
            assertEquals(Theme.AUTH_WINDOW_WIDTH, nav.authStage.width, 0.5)
            assertEquals(authWindow(nav).fittedWindowHeight(), nav.authStage.height, 0.5)
        }
        login(live)
        assertTrue(FxTestSupport.waitFx(10_000) { live.nav.activeWindow == ActiveWindow.MAIN })
        val maximized = FxTestSupport.onFx {
            val nav = live.nav
            assertEquals(900.0, nav.mainStage.width, 0.5, "second time the main window keeps the user's size")
            assertEquals(600.0, nav.mainStage.height, 0.5)
            nav.toggleMaximize()
            assertTrue(nav.isMaximized)
            val bounds = Rectangle2D(nav.mainStage.x, nav.mainStage.y, nav.mainStage.width, nav.mainStage.height)
            nav.mainController!!.profileBarController.switchAccount.fire()
            bounds
        }
        login(live)
        assertTrue(FxTestSupport.waitFx(10_000) { live.nav.activeWindow == ActiveWindow.MAIN })
        FxTestSupport.onFx {
            val nav = live.nav
            assertTrue(nav.isMaximized, "a maximized main window stays maximized")
            assertEquals(maximized, Rectangle2D(nav.mainStage.x, nav.mainStage.y, nav.mainStage.width, nav.mainStage.height))
            nav.minimize()
            assertTrue(nav.mainStage.isIconified, "minimize targets the active (main) window")
            assertFalse(nav.authStage.isIconified)
            nav.close()
        }
        live.app.shutdown()
    }

    @Test
    fun centeringMathUsesTheGivenScreen() {
        // a second monitor right of a 1920x1080 one
        val (x, y) = WindowFrame.centeredIn(Rectangle2D(1920.0, 0.0, 2560.0, 1400.0), 460.0, 620.0)
        assertEquals(1920.0 + (2560 - 460) / 2, x, 1e-9)
        assertEquals((1400.0 - 620) / 2, y, 1e-9)
    }

    @Test
    fun closingTheAuthWindowExitsTheApp() {
        val live = start()
        FxTestSupport.onFx {
            authWindow(live.nav).authCloseButton.fire()
            assertTrue(live.exited, "close (×) must exit the app")
            assertFalse(live.nav.authStage.isShowing)
            assertFalse(live.nav.mainStage.isShowing, "no hidden main window may stay behind")
        }
        live.app.shutdown()
    }

    @Test
    fun closingTheMainWindowExitsTheApp() {
        val live = loggedIn()
        FxTestSupport.onFx {
            val main = live.nav.mainController!!
            mainWindow(live.nav).closeButton.fire()
            assertTrue(live.exited)
            assertTrue(main.isDisposed)
            assertFalse(live.nav.authStage.isShowing)
            assertFalse(live.nav.mainStage.isShowing)
        }
        live.app.shutdown()
    }
}

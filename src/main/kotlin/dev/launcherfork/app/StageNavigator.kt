package dev.launcherfork.app

import dev.launcherfork.i18n.Strings
import dev.launcherfork.i18n.text
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Disposable
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.Loaded
import dev.launcherfork.ui.Navigator
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.UpdatePanel
import dev.launcherfork.ui.components.WindowFrame
import dev.launcherfork.ui.screens.auth.AgeGateController
import dev.launcherfork.ui.screens.auth.InitialAuthController
import dev.launcherfork.ui.screens.auth.RegisterController
import dev.launcherfork.ui.screens.main.MainController
import dev.launcherfork.ui.screens.window.AuthWindowController
import dev.launcherfork.ui.screens.window.WindowController
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.geometry.Rectangle2D
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.stage.Stage
import javafx.stage.StageStyle
import javafx.stage.Window
import java.time.LocalDate

/**
 * TWO windows, as in the original launcher (docs/design/WINDOWS.md):
 *  - the PRE-MAIN window ([stage], the primary stage): undecorated, not resizable, 460x620, auth_window.fxml
 *    frame; shows the update, auth and second-factor panels (e-mail code, authenticator code) one after another,
 *    and the registration steps (age gate -> form -> back to auth with a notice);
 *  - the MAIN window ([mainStage]): undecorated, resizable, 1024x640, window.fxml frame with main.fxml.
 * Login success closes the pre-main window and opens the main one; logout / account switch disposes the main
 * screen, closes the main window and opens the pre-main window with the auth panel, centered on the same screen.
 *
 * Panels are looked up by file name: the new panels (update_panel.fxml, auth_panel.fxml, mail_auth_panel.fxml)
 * win when they exist, otherwise the legacy 379x490 FXMLs are used. Adding the new files needs no change here.
 * The main window keeps an overlay layer for in-window dialogs ([showOverlay]).
 *
 * Windows are only shown / hidden once the app has shown one of them (LauncherApp shows [stage] at start);
 * before that (tests) only the state ([activeWindow], controllers) changes.
 */
class StageNavigator(private val stage: Stage, private val app: AppContext, private var lang: String) : Navigator {
    enum class ActiveWindow { PRE_MAIN, MAIN }

    private var ctx = newContext()

    /** The pre-main window (update / auth / mail code). */
    val authStage: Stage get() = stage
    /** The main window, created here. */
    val mainStage: Stage = Stage(StageStyle.UNDECORATED)

    /** A window paired with its own frame, so the auth and main frames cannot be mixed up (plain class: no `copy()`, slots compare by identity). */
    private class WindowSlot(val stage: Stage, val frame: WindowFrame = WindowFrame(stage)) {
        val isShowing: Boolean get() = stage.isShowing
    }

    private val authSlot = WindowSlot(stage)
    private val mainSlot = WindowSlot(mainStage)
    private val authWindow: AuthWindowController
    private val windowController: WindowController
    private val authScene: Scene
    private val mainScene: Scene
    private var mainShownOnce = false

    var activeWindow = ActiveWindow.PRE_MAIN
        private set
    /** Controller of the main screen (null while the pre-main window is active). */
    var mainController: MainController? = null
        private set
    /** Controller of the panel in the pre-main window (update / auth / mail code), null while main is active. */
    var panelController: Any? = null
        private set
    /** Controller of an in-window dialog shown in the main window's overlay layer (if any). */
    var overlayController: Any? = null
        private set
    /** What the user currently sees: overlay dialog, else the panel or the main screen of the active window. */
    val currentController: Any?
        get() = overlayController ?: if (activeWindow == ActiveWindow.PRE_MAIN) panelController else mainController
    var onScreenChanged: (String) -> Unit = {}
    /** How [close] ends the app; tests replace it so the FX toolkit survives. */
    var exit: () -> Unit = { Platform.exit() }

    override val window: Window get() = slotOf(activeWindow).stage

    init {
        // pre-main window
        val auth = Fxml.load<AuthWindowController>(ctx, "auth_window.fxml")
        authWindow = auth.controller
        authScene = Scene(auth.root, Theme.AUTH_WINDOW_WIDTH, Theme.AUTH_WINDOW_HEIGHT, Color.TRANSPARENT)
        Theme.apply(authScene, lang)
        authSlot.frame.installDrag(authWindow.authDragArea, maximizable = false)
        stage.scene = authScene
        stage.isResizable = false
        stage.title = ctx.strings.text("title.auth")

        // main window
        val main = Fxml.load<WindowController>(ctx, "window.fxml")
        windowController = main.controller
        mainScene = Scene(main.root, Theme.WINDOW_WIDTH, Theme.WINDOW_HEIGHT)
        Theme.apply(mainScene, lang)
        mainSlot.frame.installDrag(windowController.dragArea)
        mainSlot.frame.installResize(mainScene) { windowController.windowButtons }
        mainSlot.frame.maximizedProperty.addListener { _, _, max -> windowController.setMaximized(max) }
        mainStage.isResizable = true
        mainStage.minWidth = Theme.WINDOW_MIN_WIDTH
        mainStage.minHeight = Theme.WINDOW_MIN_HEIGHT
        mainStage.scene = mainScene
        mainStage.title = ctx.strings.text("title.launcher")
        Bindings.bindContent(mainStage.icons, stage.icons)
        // closing the main window by the OS (Alt+F4) ends the app like the close button
        mainStage.setOnCloseRequest { close() }
        stage.setOnCloseRequest { close() }
        // compact panels: the window follows the panel's content height (an error line appearing grows it downwards)
        authScene.addPostLayoutPulseListener { fitAuthHeight(recenter = false) }
    }

    private fun newContext() = UiContext(app.services, this, Strings.bundle(lang), lang, app.clientStates, app.devMode)

    override fun showLauncherUpdate(afterUpdate: () -> Unit) {
        val loaded = showPanel("update", "update_panel.fxml", "update.fxml", "title.update")
        val panel = loaded.controller as? UpdatePanel
        if (panel != null) panel.start(afterUpdate) else afterUpdate()
    }

    override fun showAuth() {
        showPanel("auth", "auth_panel.fxml", "initial_auth.fxml", "title.auth")
    }

    override fun showMailAuth() {
        showPanel("mail_auth", "mail_auth_panel.fxml", "mail_auth.fxml", "title.auth.email")
    }

    override fun showTotpAuth() {
        showPanel("totp_auth", "totp_auth_panel.fxml", "totp_auth_panel.fxml", "title.auth.totp")
    }

    override fun showAgeGate(birthDate: LocalDate?) {
        val gate = showPanel("age_gate", "age_gate_panel.fxml", "age_gate_panel.fxml", "fork.title.register").controller
        if (birthDate != null) (gate as? AgeGateController)?.preset(birthDate)
    }

    override fun showRegister(birthDate: LocalDate) {
        val form = showPanel("register", "register_panel.fxml", "register_panel.fxml", "fork.title.register").controller
        (form as? RegisterController)?.birthDate = birthDate
    }

    override fun showRegistered(login: String) {
        showAuth()
        (panelController as? InitialAuthController)?.showRegistered(login)
    }

    /** Closes the pre-main window and opens the main window with a fresh main screen (new session). */
    override fun showMain() {
        disposePanel()
        loadMain()
        mainStage.title = ctx.strings.text("title.launcher")
        switchTo(ActiveWindow.MAIN)
        onScreenChanged("main")
    }

    override fun switchLanguage(lang: String) {
        if (lang == this.lang) return
        this.lang = lang
        ctx = newContext()
        Theme.apply(mainScene, lang)
        Theme.apply(authScene, lang)
        // rebuild the main screen in the new language and keep the settings page open
        Platform.runLater {
            if (activeWindow != ActiveWindow.MAIN) return@runLater
            loadMain()
            mainController?.setSettingsVisible(true)
            onScreenChanged("main")
        }
    }

    override fun minimize() {
        slotOf(activeWindow).stage.isIconified = true
    }

    override fun toggleMaximize() {
        if (activeWindow == ActiveWindow.MAIN) mainSlot.frame.toggleMaximize()
    }

    val isMaximized: Boolean get() = mainSlot.frame.isMaximized

    /** Close button of either window: no hidden window stays behind, the app exits. */
    override fun close() {
        mainController?.dispose()
        disposePanel()
        mainStage.hide()
        stage.hide()
        exit()
    }

    // ---- in-window dialogs of the main window ----

    /** Shows [node] over the blurred, input-blocked main screen (mouse and keyboard, see WindowController). */
    fun showOverlay(node: Node, controller: Any? = null) {
        (overlayController as? Disposable)?.dispose()
        overlayController = controller
        windowController.showOverlay(node)
    }

    fun hideOverlay() {
        (overlayController as? Disposable)?.dispose()
        overlayController = null
        windowController.hideOverlay()
    }

    val isOverlayShown: Boolean get() = windowController.isOverlayShown

    // ---- internals ----

    private fun loadMain() {
        // leaving the old main screen: stop its listeners (downloads keep running in the session store)
        mainController?.dispose()
        val loaded = Fxml.load<MainController>(ctx, "main.fxml")
        windowController.setContent(loaded.root)
        mainController = loaded.controller
    }

    /** Leaving the main window: dispose the main screen (and any dialog) BEFORE the pre-main window shows up. */
    private fun disposeMain() {
        if (overlayController != null || isOverlayShown) hideOverlay()
        mainController?.dispose()
        mainController = null
        windowController.setContent(javafx.scene.layout.Region())
    }

    private fun disposePanel() {
        (panelController as? Disposable)?.dispose()
        panelController = null
        authWindow.clear()
    }

    private fun showPanel(name: String, panel: String, legacy: String, titleKey: String): Loaded<Any?> {
        disposeMain()
        (panelController as? Disposable)?.dispose()
        val isNew = Theme::class.java.getResource(Theme.UI_ROOT + panel) != null
        val loaded = Fxml.load<Any?>(ctx, if (isNew) panel else legacy)
        if (!isNew) hideLegacyChrome(loaded.root)
        panelController = loaded.controller
        authWindow.setPanel(loaded.root)
        stage.title = ctx.strings.text(titleKey)
        switchTo(ActiveWindow.PRE_MAIN)
        fitAuthHeight(recenter = true)
        onScreenChanged(name)
        return loaded
    }

    private fun slotOf(window: ActiveWindow): WindowSlot = if (window == ActiveWindow.MAIN) mainSlot else authSlot

    /**
     * Makes [target] the active window. Once the app is on screen the target window is shown FIRST (so the app
     * never has zero windows and does not exit implicitly), centered on the screen where the other window was,
     * then the other window is hidden.
     */
    private fun switchTo(target: ActiveWindow) {
        val previous = activeWindow
        activeWindow = target
        if (!authSlot.isShowing && !mainSlot.isShowing) return
        val show = slotOf(target)
        val hide = if (show === mainSlot) authSlot else mainSlot
        if (previous != target || !show.isShowing) {
            place(show, from = if (hide.isShowing) hide.frame.bounds else show.frame.bounds)
            show.stage.show()
            if (show === mainSlot) mainShownOnce = true
        }
        if (hide.isShowing) hide.stage.hide()
    }

    /**
     * Sizes [slot] and centers it on the screen of [from]: the auth window at its fixed size, the main window at
     * the default size the first time, then at the user's size; a maximized main window is left as it is.
     */
    private fun place(slot: WindowSlot, from: Rectangle2D) {
        val (w, h) = when {
            slot === authSlot -> Theme.AUTH_WINDOW_WIDTH to authWindow.fittedWindowHeight()
            !mainShownOnce -> Theme.WINDOW_WIDTH to Theme.WINDOW_HEIGHT
            slot.frame.isMaximized -> return
            else -> slot.stage.width to slot.stage.height
        }
        slot.stage.width = w
        slot.stage.height = h
        slot.frame.centerOnScreenOf(from, w, h)
    }

    /**
     * Resizes the shown pre-main window to [AuthWindowController.fittedWindowHeight]: a new panel keeps the window's
     * vertical center ([recenter]), a change inside the same panel (error line) keeps its top edge.
     */
    private fun fitAuthHeight(recenter: Boolean) {
        if (activeWindow != ActiveWindow.PRE_MAIN || !authSlot.isShowing) return
        val h = authWindow.fittedWindowHeight()
        val old = stage.height
        if (kotlin.math.abs(h - old) < 0.5) return
        stage.height = h
        if (recenter) stage.y += (old - h) / 2
        // never past the screen edge (small monitors, a growing error line near the bottom)
        val screen = WindowFrame.screenBoundsOf(Rectangle2D(stage.x, stage.y, stage.width, h))
        stage.y = stage.y.coerceIn(screen.minY, (screen.maxY - h).coerceAtLeast(screen.minY))
    }

    /** The legacy auth/update FXMLs were whole windows: their own close/minimize buttons and top strip go away. */
    private fun hideLegacyChrome(root: Parent) {
        listOf("#close", "#hide", "#header").forEach { sel -> root.lookupAll(sel).forEach { it.isVisible = false } }
    }
}

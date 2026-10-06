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
import dev.launcherfork.ui.screens.main.MainController
import dev.launcherfork.ui.screens.window.WindowController
import javafx.application.Platform
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage
import javafx.stage.Window

/**
 * ONE undecorated, resizable window for the whole app. window.fxml is the frame; main.fxml lives in its
 * content host; update / auth / mail-code screens are overlay panels above the blurred main screen.
 *
 * Overlay panels are looked up by file name: the new panels (update_panel.fxml, auth_panel.fxml,
 * mail_auth_panel.fxml) win when they exist, otherwise the legacy 379x490 FXMLs are used. Adding the new
 * files needs no change here (docs/design/OVERLAY.md).
 */
class StageNavigator(private val stage: Stage, private val app: AppContext, private var lang: String) : Navigator {
    private var ctx = newContext()
    private val frame = WindowFrame(stage)
    private val windowController: WindowController
    private val scene: Scene

    /** Controller of the main screen and of the overlay panel currently shown (the screenshot tour uses them). */
    var mainController: MainController? = null
        private set
    var overlayController: Any? = null
        private set
    /** Overlay controller if an overlay is shown, else the main controller. */
    val currentController: Any? get() = overlayController ?: mainController
    var onScreenChanged: (String) -> Unit = {}

    override val window: Window get() = stage

    init {
        val loaded = Fxml.load<WindowController>(ctx, "window.fxml")
        windowController = loaded.controller
        scene = Scene(loaded.root, Theme.WINDOW_WIDTH, Theme.WINDOW_HEIGHT)
        Theme.apply(scene, lang)
        frame.installDrag(windowController.dragArea)
        frame.installResize(scene)
        frame.maximizedProperty.addListener { _, _, max -> windowController.setMaximized(max) }
        stage.minWidth = Theme.WINDOW_MIN_WIDTH
        stage.minHeight = Theme.WINDOW_MIN_HEIGHT
        stage.scene = scene
        stage.title = ctx.strings.text("title.launcher")
    }

    private fun newContext() = UiContext(app.services, this, Strings.bundle(lang), lang, app.clientStates, app.devMode)

    override fun showLauncherUpdate(afterUpdate: () -> Unit) {
        val loaded = showOverlay("update", "update_panel.fxml", "update.fxml", "title.update")
        val panel = loaded.controller as? UpdatePanel
        if (panel != null) panel.start(afterUpdate) else afterUpdate()
    }

    override fun showAuth() {
        showOverlay("auth", "auth_panel.fxml", "initial_auth.fxml", "title.auth")
    }

    override fun showMailAuth() {
        showOverlay("mail_auth", "mail_auth_panel.fxml", "mail_auth.fxml", "title.auth.email")
    }

    override fun showMain() {
        hideOverlay()
        loadMain() // rebuilt so that the new session (login name, servers) is picked up
        stage.title = ctx.strings.text("title.launcher")
        onScreenChanged("main")
    }

    override fun switchLanguage(lang: String) {
        if (lang == this.lang) return
        this.lang = lang
        ctx = newContext()
        Theme.apply(scene, lang)
        // rebuild the main screen in the new language and keep the settings page open
        Platform.runLater {
            loadMain()
            mainController?.setSettingsVisible(true)
            onScreenChanged("main")
        }
    }

    override fun minimize() {
        stage.isIconified = true
    }

    override fun toggleMaximize() = frame.toggleMaximize()

    val isMaximized: Boolean get() = frame.isMaximized

    override fun close() {
        Platform.exit()
    }

    fun hideOverlay() {
        (overlayController as? Disposable)?.dispose()
        overlayController = null
        windowController.hideOverlay()
    }

    val isOverlayShown: Boolean get() = windowController.isOverlayShown

    private fun ensureMain() {
        if (mainController == null) loadMain()
    }

    private fun loadMain() {
        // leaving the old main screen: stop its listeners (downloads keep running in the session store)
        mainController?.dispose()
        val loaded = Fxml.load<MainController>(ctx, "main.fxml")
        windowController.setContent(loaded.root)
        mainController = loaded.controller
    }

    private fun showOverlay(name: String, panel: String, legacy: String, titleKey: String): Loaded<Any?> {
        ensureMain()
        (overlayController as? Disposable)?.dispose()
        val isNew = Theme::class.java.getResource(Theme.UI_ROOT + panel) != null
        val loaded = Fxml.load<Any?>(ctx, if (isNew) panel else legacy)
        if (!isNew) hideLegacyChrome(loaded.root)
        overlayController = loaded.controller
        windowController.showOverlay(loaded.root)
        stage.title = ctx.strings.text(titleKey)
        onScreenChanged(name)
        return loaded
    }

    /** The legacy auth/update FXMLs were whole windows: their own close/minimize buttons and top strip go away. */
    private fun hideLegacyChrome(root: Parent) {
        listOf("#close", "#hide", "#header").forEach { sel -> root.lookupAll(sel).forEach { it.isVisible = false } }
    }
}

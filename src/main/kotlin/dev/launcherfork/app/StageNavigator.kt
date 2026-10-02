package dev.launcherfork.app

import dev.launcherfork.i18n.Strings
import dev.launcherfork.i18n.text
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Disposable
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.Navigator
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.WindowChrome
import dev.launcherfork.ui.screens.main.MainController
import dev.launcherfork.ui.screens.update.LauncherUpdateController
import javafx.application.Platform
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.paint.Color
import javafx.stage.Stage
import javafx.stage.Window

/** Single transparent undecorated Stage; each screen swaps the Scene and resizes the window. */
class StageNavigator(private val stage: Stage, private val app: AppContext, private var lang: String) : Navigator {
    private var ctx = newContext()

    /** Controller of the current screen (used by the screenshot tour). */
    var currentController: Any? = null
        private set
    var onScreenChanged: (String) -> Unit = {}

    override val window: Window get() = stage

    private fun newContext() = UiContext(app.services, this, Strings.bundle(lang), lang, app.clientStates, app.devMode)

    override fun showLauncherUpdate(afterUpdate: () -> Unit) {
        val loaded = Fxml.load<LauncherUpdateController>(ctx, "update.fxml")
        setScene("update", loaded.root, loaded.controller, Theme.AUTH_WIDTH, Theme.AUTH_HEIGHT, "title.update") { Theme.AUTH_DRAG_POLYGON }
        loaded.controller.start(afterUpdate)
    }

    override fun showAuth() {
        val loaded = Fxml.load<Any>(ctx, "initial_auth.fxml")
        setScene("auth", loaded.root, loaded.controller, Theme.AUTH_WIDTH, Theme.AUTH_HEIGHT, "title.auth") { Theme.AUTH_DRAG_POLYGON }
    }

    override fun showMailAuth() {
        val loaded = Fxml.load<Any>(ctx, "mail_auth.fxml")
        setScene("mail_auth", loaded.root, loaded.controller, Theme.AUTH_WIDTH, Theme.AUTH_HEIGHT, "title.auth.email") { Theme.AUTH_DRAG_POLYGON }
    }

    override fun showMain() {
        val loaded = Fxml.load<MainController>(ctx, "main.fxml")
        setScene("main", loaded.root, loaded.controller, Theme.MAIN_WIDTH, Theme.MAIN_HEIGHT, "title.launcher", Theme::mainDragPolygon)
    }

    override fun switchLanguage(lang: String) {
        if (lang == this.lang) return
        this.lang = lang
        ctx = newContext()
        // rebuild the main window in the new language and keep the settings page open
        Platform.runLater {
            showMain()
            (currentController as? MainController)?.setSettingsVisible(true)
        }
    }

    override fun minimize() {
        stage.isIconified = true
    }

    override fun close() {
        Platform.exit()
    }

    private fun setScene(
        name: String,
        root: Parent,
        controller: Any?,
        width: Double,
        height: Double,
        titleKey: String,
        polygon: (Double) -> List<Pair<Double, Double>>,
    ) {
        // leaving the screen: stop its timers / detach from session state (downloads keep running)
        (currentController as? Disposable)?.dispose()
        val oldCenterX = if (stage.isShowing) stage.x + stage.width / 2 else Double.NaN
        val oldY = stage.y
        val scene = Scene(root, width, height, Color.TRANSPARENT)
        Theme.apply(scene, lang)
        WindowChrome.installDrag(scene, polygon)
        stage.scene = scene
        stage.title = ctx.strings.text(titleKey)
        stage.sizeToScene()
        if (oldCenterX.isNaN()) stage.centerOnScreen() else {
            stage.x = oldCenterX - width / 2
            stage.y = oldY
        }
        currentController = controller
        onScreenChanged(name)
    }
}

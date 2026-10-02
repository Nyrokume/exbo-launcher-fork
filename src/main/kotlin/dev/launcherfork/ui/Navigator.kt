package dev.launcherfork.ui

import javafx.scene.Scene
import javafx.stage.Window

/** Screen switching. Implemented by app/StageNavigator; tests use a no-op implementation. */
interface Navigator {
    /** update.fxml: fake launcher self-update, then [afterUpdate]. */
    fun showLauncherUpdate(afterUpdate: () -> Unit = ::showAuth)
    fun showAuth()
    fun showMailAuth()
    fun showMain()
    fun switchLanguage(lang: String)
    fun minimize()
    fun close()
    val window: Window?
    /** Called by screens after their root is attached, e.g. to install window dragging. */
    fun onSceneReady(scene: Scene) {}
}

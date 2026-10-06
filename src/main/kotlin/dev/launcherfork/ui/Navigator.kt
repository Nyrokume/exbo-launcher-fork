package dev.launcherfork.ui

import javafx.stage.Window

/**
 * Screen switching inside the ONE launcher window. Implemented by app/StageNavigator; tests use a no-op one.
 * Update / auth / mail-code screens are overlay panels on top of the main screen (see docs/design/OVERLAY.md).
 */
interface Navigator {
    /** Update overlay (update_panel.fxml or legacy update.fxml): fake self-update, then [afterUpdate]. */
    fun showLauncherUpdate(afterUpdate: () -> Unit = ::showAuth)
    /** Auth overlay (auth_panel.fxml or legacy initial_auth.fxml) over the blurred main screen. */
    fun showAuth()
    /** E-mail code overlay (mail_auth_panel.fxml or legacy mail_auth.fxml). */
    fun showMailAuth()
    /** Hides any overlay and (re)builds the main screen for the current session. */
    fun showMain()
    fun switchLanguage(lang: String)
    fun minimize()
    fun toggleMaximize() {}
    fun close()
    val window: Window?
}

/** Implemented by the controller of an update overlay panel: start the (fake) update and call [done] when finished. */
fun interface UpdatePanel {
    fun start(done: () -> Unit)
}

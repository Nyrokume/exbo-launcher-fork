package dev.launcherfork.ui

import javafx.stage.Window

/**
 * Screen switching between the two launcher windows. Implemented by app/StageNavigator; tests use a no-op one.
 * Update / auth / mail-code panels live in the separate pre-main window, the main screen in the main window
 * (see docs/design/WINDOWS.md).
 */
interface Navigator {
    /** Update panel in the pre-main window (update_panel.fxml or legacy update.fxml): fake self-update, then [afterUpdate]. */
    fun showLauncherUpdate(afterUpdate: () -> Unit = ::showAuth)
    /** Auth panel (auth_panel.fxml or legacy initial_auth.fxml) in the pre-main window; the main window closes. */
    fun showAuth()
    /** E-mail code panel (mail_auth_panel.fxml or legacy mail_auth.fxml) in the pre-main window. */
    fun showMailAuth()
    /** Closes the pre-main window and opens the main window with a main screen built for the current session. */
    fun showMain()
    fun switchLanguage(lang: String)
    fun minimize()
    fun toggleMaximize() {}
    fun close()
    val window: Window?
}

/** Implemented by the controller of an update panel: start the (fake) update and call [done] when finished. */
fun interface UpdatePanel {
    fun start(done: () -> Unit)
}

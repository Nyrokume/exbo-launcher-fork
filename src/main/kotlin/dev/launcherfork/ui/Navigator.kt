package dev.launcherfork.ui

import dev.launcherfork.domain.Appearance
import java.time.LocalDate
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
    /** Authenticator-app (TOTP) code panel (totp_auth_panel.fxml) in the pre-main window. */
    fun showTotpAuth() = Unit
    /** Registration step 1: age gate (age_gate_panel.fxml); [birthDate] preselects the dropdowns (back from step 2). */
    fun showAgeGate(birthDate: LocalDate? = null) = Unit
    /** Registration step 2: the form (register_panel.fxml) for a user born on [birthDate]. */
    fun showRegister(birthDate: LocalDate) = Unit
    /** Back to the auth panel after a registration: [login] pre-filled and a success notice shown. */
    fun showRegistered(login: String) = showAuth()
    /** Closes the pre-main window and opens the main window with a main screen built for the current session. */
    fun showMain()
    fun switchLanguage(lang: String)
    /** Re-styles every launcher window (main and pre-main) with the dark or the white scheme, live. */
    fun switchAppearance(appearance: Appearance) = Unit
    fun minimize()
    fun toggleMaximize() {}
    fun close()
    val window: Window?
}

/** Implemented by the controller of an update panel: start the (fake) update and call [done] when finished. */
fun interface UpdatePanel {
    fun start(done: () -> Unit)
}

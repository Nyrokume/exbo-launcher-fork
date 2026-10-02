package dev.launcherfork.ui

import dev.launcherfork.domain.Services
import dev.launcherfork.i18n.text
import dev.launcherfork.ui.state.ClientStateStore
import java.util.ResourceBundle

/** What every controller receives (via the FXML controller factory). Only interfaces + session UI state, no mocks. */
class UiContext(
    val services: Services,
    val navigator: Navigator,
    val strings: ResourceBundle,
    val lang: String,
    /** Session-scoped (owned by AppContext): survives scene rebuilds such as a language switch. */
    val clientStates: ClientStateStore,
    /** -Dlauncher.dev=true: developer shortcuts (F1-F7 open modals). */
    val devMode: Boolean = false,
) {
    fun t(key: String, vararg args: Any): String = strings.text(key, *args)
}

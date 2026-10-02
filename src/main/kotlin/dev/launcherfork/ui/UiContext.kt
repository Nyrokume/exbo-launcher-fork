package dev.launcherfork.ui

import dev.launcherfork.domain.Services
import dev.launcherfork.i18n.text
import java.util.ResourceBundle

/** What every controller receives (via the FXML controller factory). Only interfaces, no mocks. */
class UiContext(
    val services: Services,
    val navigator: Navigator,
    val strings: ResourceBundle,
    val lang: String,
) {
    fun t(key: String, vararg args: Any): String = strings.text(key, *args)
}

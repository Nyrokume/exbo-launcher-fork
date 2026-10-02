package dev.launcherfork.ui.screens.auth

import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.background
import dev.launcherfork.ui.components.brightenOnHover
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.control.TextFormatter

/** mail_auth.fxml: one-time e-mail code (second factor). */
class MailAuthController(ctx: UiContext) : AuthScreenBase(ctx) {
    @FXML lateinit var codeField: TextField
    @FXML lateinit var enterButton: Button

    @FXML
    fun initialize() {
        initChrome()
        enterButton.brightenOnHover()
        // replaces the original custom NumberTextField
        codeField.textFormatter = TextFormatter<String> { c ->
            if (c.controlNewText.length <= 9 && c.controlNewText.all(Char::isDigit)) c else null
        }
    }

    @FXML
    fun enter() {
        val code = codeField.text.trim()
        if (code.isEmpty()) return showError(ctx.t("auth.error.empty_code"))
        showError(null)
        setBusy(true)
        background({ ctx.services.auth.confirmCode(code) }, ::handle)
    }
}

package dev.launcherfork.ui.screens.auth

import dev.launcherfork.domain.AuthError
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.background
import dev.launcherfork.theme.Theme
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
        // shadcn theme has CSS :hover states; the brightness effect would wash out the light primary button
        if (!Theme.isShadcn) enterButton.brightenOnHover()
        // replaces the original custom NumberTextField; the code is exactly CODE_LENGTH digits
        codeField.textFormatter = TextFormatter<String> { c ->
            if (c.controlNewText.length <= CODE_LENGTH && c.controlNewText.all(Char::isDigit)) c else null
        }
    }

    @FXML
    fun enter() {
        val code = codeField.text.trim()
        if (code.isEmpty()) return showError(AuthError.EMPTY_CODE)
        showError(null)
        setBusy(true)
        background({ ctx.services.auth.confirmCode(code) }, onResult = ::handle)
    }

    companion object {
        /** Length of the e-mail confirmation code. */
        const val CODE_LENGTH = 6
    }
}

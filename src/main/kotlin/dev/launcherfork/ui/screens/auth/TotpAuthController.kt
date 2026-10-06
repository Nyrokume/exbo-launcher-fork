package dev.launcherfork.ui.screens.auth

import dev.launcherfork.domain.AuthError
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.CodeInput
import dev.launcherfork.ui.components.background
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.layout.VBox

/**
 * totp_auth_panel.fxml: code of an authenticator app ([CODE_LENGTH] digit cells of [CodeInput]), after the
 * stalzone.com 2FA step. ОТПРАВИТЬ stays grey until every cell is filled (Enter in a cell = the button, it cannot
 * bypass the grey state), Esc / "Вернуться ко входу" = login panel. Errors appear in the site's toast
 * ("ОШИБКА ПРИ ПОДТВЕРЖДЕНИИ" / "Неправильный код аутентификации!") instead of a line of text, the cells turn red;
 * the toast closes with its X or as soon as the code is edited.
 *
 * Self-contained on purpose: the e-mail code screen (mail_auth_panel.fxml / MailAuthController) is owned by the
 * redesign/panels-zone branch and is not touched here.
 */
class TotpAuthController(ctx: UiContext) : AuthScreenBase(ctx) {
    @FXML lateinit var codeInput: CodeInput
    @FXML lateinit var enterButton: Button
    @FXML lateinit var backButton: Button
    @FXML lateinit var toast: VBox
    @FXML lateinit var toastClose: Button

    override fun errorText(error: AuthError): String =
        if (error == AuthError.WRONG_CODE) ctx.t("fork.totp.wrong_code") else super.errorText(error)

    @FXML
    fun initialize() {
        initChrome()
        codeInput.cellCount = CODE_LENGTH
        enterButton.disableProperty().bind(codeInput.completeProperty.not())
        errorFirst.textProperty().addListener { _, _, text -> codeInput.error = !text.isNullOrEmpty() }
        // editing the code hides the error (CodeInput drops :error itself)
        codeInput.codeProperty.addListener { _, _, _ -> if (!errorFirst.text.isNullOrEmpty()) showError(null) }
        codeInput.cells.forEach { it.setOnAction { enter() } }
        codeInput.sceneProperty().addListener { _, _, scene -> if (scene != null) Platform.runLater { codeInput.focusFirstEmpty() } }
        onEscape(::onBack)

        toast.visibleProperty().bind(errorFirst.textProperty().isNotEmpty)
        errorSecond.managedProperty().bind(errorSecond.textProperty().isNotEmpty)
        toastClose.accessibleText = ctx.t("fork.toast.close")
    }

    @FXML
    fun enter() {
        if (enterButton.isDisabled) return
        val code = codeInput.code
        if (code.isEmpty()) return showError(AuthError.EMPTY_CODE)
        showError(null)
        setBusy(true)
        background({ ctx.services.auth.confirmTotp(code) }, onResult = ::handle)
    }

    @FXML fun onBack() = ctx.navigator.showAuth()

    /** True while the error toast is on screen. */
    val isToastShown: Boolean get() = toast.isVisible

    @FXML fun onCloseToast() = showError(null)

    companion object {
        /** Digits of an authenticator-app (TOTP) code. */
        const val CODE_LENGTH = 6
    }
}

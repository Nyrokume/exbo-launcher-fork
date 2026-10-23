package dev.launcherfork.ui.screens.auth

import dev.launcherfork.domain.AuthError
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.CodeInput
import dev.launcherfork.ui.components.background
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.layout.HBox

/**
 * totp_auth_panel.fxml: authenticator-app code — same layout contract as mail_auth_panel.fxml
 * (top card absorbs spare height; dense bottom: label → cells → toast → ПОДТВЕРДИТЬ → footer).
 */
class TotpAuthController(ctx: UiContext) : AuthScreenBase(ctx) {
    @FXML lateinit var codeInput: CodeInput
    @FXML lateinit var enterButton: Button
    @FXML lateinit var backButton: Button
    @FXML lateinit var toast: HBox
    @FXML lateinit var toastClose: Button

    override fun errorText(error: AuthError): String =
        if (error == AuthError.WRONG_CODE) ctx.t("fork.totp.wrong_code") else super.errorText(error)

    @FXML
    fun initialize() {
        initChrome()
        codeInput.strings = ctx.strings
        codeInput.cellCount = CODE_LENGTH
        codeInput.alignment = Pos.CENTER
        enterButton.disableProperty().bind(codeInput.completeProperty.not())
        errorFirst.textProperty().addListener { _, _, text -> codeInput.error = !text.isNullOrEmpty() }
        codeInput.codeProperty.addListener { _, _, _ -> if (!errorFirst.text.isNullOrEmpty()) showError(null) }
        codeInput.cells.forEach { it.setOnAction { enter() } }
        codeInput.sceneProperty().addListener { _, _, scene -> if (scene != null) Platform.runLater { codeInput.focusFirstEmpty() } }
        onEscape(::onBack)

        toast.visibleProperty().bind(errorFirst.textProperty().isNotEmpty)
        toast.managedProperty().bind(toast.visibleProperty())
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

    val isToastShown: Boolean get() = toast.isVisible

    @FXML fun onCloseToast() = showError(null)

    companion object {
        const val CODE_LENGTH = 6
    }
}

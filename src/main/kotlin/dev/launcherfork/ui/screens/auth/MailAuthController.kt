package dev.launcherfork.ui.screens.auth

import dev.launcherfork.domain.AuthError
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.background
import dev.launcherfork.ui.components.brightenOnHover
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.css.PseudoClass
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.control.TextFormatter

/** mail_auth.fxml / mail_auth_panel.fxml: one-time e-mail code (second factor). */
class MailAuthController(ctx: UiContext) : AuthScreenBase(ctx) {
    @FXML lateinit var codeField: TextField
    @FXML lateinit var enterButton: Button
    /** Only in mail_auth_panel.fxml: "Вернуться ко входу". */
    @FXML var backButton: Button? = null
    /** `<Boolean fx:id="cssHover" fx:value="true"/>` in the zone panel: hover from CSS, zone form behaviour. */
    @FXML var cssHover: Boolean? = null

    @FXML
    fun initialize() {
        initChrome()
        // replaces the original custom NumberTextField; the code is exactly CODE_LENGTH digits
        codeField.textFormatter = TextFormatter<String> { c ->
            if (c.controlNewText.length <= CODE_LENGTH && c.controlNewText.all(Char::isDigit)) c else null
        }
        if (cssHover != true) enterButton.brightenOnHover() else wireZoneForm()
    }

    /** Zone panel: ПОДТВЕРДИТЬ is grey until all digits are typed, an error paints the field until it is edited. */
    private fun wireZoneForm() {
        enterButton.disableProperty().bind(Bindings.length(codeField.textProperty()).isNotEqualTo(CODE_LENGTH))
        errorFirst.textProperty().addListener { _, _, text -> codeField.pseudoClassStateChanged(ERROR, !text.isNullOrEmpty()) }
        codeField.textProperty().addListener { _, _, _ -> codeField.pseudoClassStateChanged(ERROR, false) }
        listOf(errorFirst, errorSecond).forEach { it.managedProperty().bind(it.textProperty().isNotEmpty) }
        codeField.sceneProperty().addListener { _, _, scene -> if (scene != null) Platform.runLater { codeField.requestFocus() } }
    }

    @FXML
    fun enter() {
        // zone panel: Enter in the field must not bypass the grey button
        if (enterButton.disableProperty().isBound && enterButton.isDisabled) return
        val code = codeField.text.trim()
        if (code.isEmpty()) return showError(AuthError.EMPTY_CODE)
        showError(null)
        setBusy(true)
        background({ ctx.services.auth.confirmCode(code) }, onResult = ::handle)
    }

    @FXML fun onBack() = ctx.navigator.showAuth()

    companion object {
        /** Length of the e-mail confirmation code. */
        const val CODE_LENGTH = 6
        private val ERROR: PseudoClass = PseudoClass.getPseudoClass("error")
    }
}

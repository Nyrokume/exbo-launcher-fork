package dev.launcherfork.ui.screens.auth

import dev.launcherfork.domain.AuthError
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.CodeInput
import dev.launcherfork.ui.components.background
import dev.launcherfork.ui.components.brightenOnHover
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.control.TextFormatter
import javafx.scene.layout.HBox

/**
 * mail_auth.fxml / mail_auth_panel.fxml: one-time e-mail code (second factor), [CODE_LENGTH] digits.
 *  - legacy mail_auth.fxml: one text field ([codeField]);
 *  - zone mail_auth_panel.fxml: same layout as totp_auth_panel — digit cells, compact error toast, ПОДТВЕРДИТЬ
 *    grey until complete; spare window height goes to the top card (no empty mid-form gap).
 */
class MailAuthController(ctx: UiContext) : AuthScreenBase(ctx) {
    /** Only in mail_auth.fxml. */
    @FXML var codeField: TextField? = null
    /** Only in mail_auth_panel.fxml. */
    @FXML var codeInput: CodeInput? = null
    @FXML lateinit var enterButton: Button
    /** Only in mail_auth_panel.fxml. */
    @FXML var backButton: Button? = null
    /** Zone panel: compact error toast (same chrome as TOTP / register). */
    @FXML var toast: HBox? = null
    @FXML var toastClose: Button? = null

    val code: String get() = codeInput?.code ?: codeField?.text?.trim().orEmpty()

    /** True while the zone toast is on screen (false on the legacy single-field panel). */
    val isToastShown: Boolean get() = toast?.isVisible == true

    @FXML
    fun initialize() {
        initChrome()
        codeField?.let { field ->
            field.textFormatter = TextFormatter<String> { c ->
                if (c.isContentChange) {
                    val room = CODE_LENGTH - (c.controlText.length - (c.rangeEnd - c.rangeStart))
                    c.text = c.text.filter(Char::isDigit).take(room.coerceAtLeast(0))
                    c.caretPosition = c.rangeStart + c.text.length
                    c.anchor = c.caretPosition
                }
                c
            }
            enterButton.brightenOnHover()
        }
        codeInput?.let(::wireCells)
    }

    private fun wireCells(cells: CodeInput) {
        cells.strings = ctx.strings
        cells.cellCount = CODE_LENGTH
        cells.alignment = Pos.CENTER
        cells.cells.forEach { it.setOnAction { enter() } }
        enterButton.disableProperty().bind(cells.completeProperty.not())
        errorFirst.textProperty().addListener { _, _, text -> cells.error = !text.isNullOrEmpty() }
        cells.codeProperty.addListener { _, _, _ -> if (!errorFirst.text.isNullOrEmpty()) showError(null) }
        errorSecond.managedProperty().bind(errorSecond.textProperty().isNotEmpty)
        cells.sceneProperty().addListener { _, _, scene -> if (scene != null) Platform.runLater { cells.focusFirstEmpty() } }
        onEscape(::onBack)

        toast?.let { t ->
            t.visibleProperty().bind(errorFirst.textProperty().isNotEmpty)
            t.managedProperty().bind(t.visibleProperty())
        }
        toastClose?.accessibleText = ctx.t("fork.toast.close")
    }

    @FXML
    fun enter() {
        if (enterButton.disableProperty().isBound && enterButton.isDisabled) return
        val code = code
        if (code.isEmpty()) return showError(AuthError.EMPTY_CODE)
        showError(null)
        setBusy(true)
        background({ ctx.services.auth.confirmCode(code) }, onResult = ::handle)
    }

    @FXML fun onBack() = ctx.navigator.showAuth()

    @FXML fun onCloseToast() = showError(null)

    companion object {
        const val CODE_LENGTH = 9
    }
}

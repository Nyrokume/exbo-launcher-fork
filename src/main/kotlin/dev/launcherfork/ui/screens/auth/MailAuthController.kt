package dev.launcherfork.ui.screens.auth

import dev.launcherfork.domain.AuthError
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.CodeInput
import dev.launcherfork.ui.components.background
import dev.launcherfork.ui.components.brightenOnHover
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.control.TextFormatter

/**
 * mail_auth.fxml / mail_auth_panel.fxml: one-time e-mail code (second factor), [CODE_LENGTH] digits ("268419249").
 *  - legacy mail_auth.fxml: one text field ([codeField]); typed or pasted text keeps only its digits, cut to what fits;
 *  - zone mail_auth_panel.fxml: [CODE_LENGTH] digit cells of the shared [CodeInput] (same component as the
 *    authenticator screen); a pasted code fills every cell, ПОДТВЕРДИТЬ is grey until all cells are filled and Enter
 *    cannot bypass it, an error paints the cells until the code is edited, Esc / "Вернуться ко входу" = login panel.
 */
class MailAuthController(ctx: UiContext) : AuthScreenBase(ctx) {
    /** Only in mail_auth.fxml. */
    @FXML var codeField: TextField? = null
    /** Only in mail_auth_panel.fxml. */
    @FXML var codeInput: CodeInput? = null
    @FXML lateinit var enterButton: Button
    /** Only in mail_auth_panel.fxml: "Вернуться ко входу". */
    @FXML var backButton: Button? = null

    /** The code as typed so far, from whichever input the panel has. */
    val code: String get() = codeInput?.code ?: codeField?.text?.trim().orEmpty()

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
        cells.cellCount = CODE_LENGTH
        cells.cells.forEachIndexed { i, cell ->
            cell.accessibleText = ctx.t("fork.zone.code_cell").format(i + 1, CODE_LENGTH)
            cell.setOnAction { enter() }
        }
        enterButton.disableProperty().bind(cells.completeProperty.not())
        errorFirst.textProperty().addListener { _, _, text -> cells.error = !text.isNullOrEmpty() }
        cells.codeProperty.addListener { _, _, _ -> if (!errorFirst.text.isNullOrEmpty()) showError(null) }
        listOf(errorFirst, errorSecond).forEach { it.managedProperty().bind(it.textProperty().isNotEmpty) }
        cells.sceneProperty().addListener { _, _, scene -> if (scene != null) Platform.runLater { cells.focusFirstEmpty() } }
        onEscape(::onBack)
    }

    @FXML
    fun enter() {
        // zone panel: Enter in a cell must not bypass the grey button
        if (enterButton.disableProperty().isBound && enterButton.isDisabled) return
        val code = code
        if (code.isEmpty()) return showError(AuthError.EMPTY_CODE)
        showError(null)
        setBusy(true)
        background({ ctx.services.auth.confirmCode(code) }, onResult = ::handle)
    }

    @FXML fun onBack() = ctx.navigator.showAuth()

    companion object {
        /** Length of the e-mail confirmation code (e.g. 268419249). */
        const val CODE_LENGTH = 9
    }
}

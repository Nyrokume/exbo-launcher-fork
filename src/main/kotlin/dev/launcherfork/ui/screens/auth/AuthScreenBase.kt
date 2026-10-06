package dev.launcherfork.ui.screens.auth

import dev.launcherfork.domain.AuthError
import dev.launcherfork.domain.AuthResult
import dev.launcherfork.ui.Messages
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.WindowChrome
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane

/**
 * Common fx:ids of the 379x490 auth windows (same names in every original auth FXML); the new 460x588 panels of the
 * login window keep them (hidden title-bar nodes) so every pre-main panel shares this base.
 */
abstract class AuthScreenBase(protected val ctx: UiContext) {
    @FXML lateinit var root: Pane
    @FXML lateinit var header: ImageView
    @FXML lateinit var closeButton: Button
    @FXML lateinit var hideButton: Button
    @FXML lateinit var rollingBar: ProgressIndicator
    @FXML lateinit var errorFirst: Label
    @FXML lateinit var errorSecond: Label

    protected fun initChrome() {
        WindowChrome.wireButtons(ctx.navigator, closeButton, hideButton)
        showError(null)
    }

    protected fun showError(first: String?, second: String? = null) {
        errorFirst.text = first ?: ""
        errorSecond.text = second ?: ""
    }

    protected fun setBusy(busy: Boolean) {
        rollingBar.isVisible = busy
        root.isDisable = busy
    }

    protected fun showError(error: AuthError) = showError(errorText(error))

    /** Localized text of [error]; panels with their own wording (authenticator toast) override it. */
    protected open fun errorText(error: AuthError): String = ctx.t(Messages.key(error))

    /** Esc anywhere in the panel runs [back] (registration steps, code panels: same as their back link). */
    protected fun onEscape(back: () -> Unit) {
        root.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED) { e ->
            if (e.code == javafx.scene.input.KeyCode.ESCAPE && !root.isDisable) { back(); e.consume() }
        }
    }

    /** Outcome of a background auth call. Always unlocks the form; a thrown exception becomes SERVER_ERROR. */
    protected fun handle(outcome: Result<AuthResult>) {
        setBusy(false)
        when (val result = outcome.getOrElse { AuthResult.Failure(AuthError.SERVER_ERROR) }) {
            is AuthResult.Success -> ctx.navigator.showMain()
            AuthResult.CodeRequired -> ctx.navigator.showMailAuth()
            AuthResult.TotpRequired -> ctx.navigator.showTotpAuth()
            is AuthResult.Failure -> showError(result.error)
        }
    }
}

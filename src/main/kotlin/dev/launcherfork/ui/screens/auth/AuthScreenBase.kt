package dev.launcherfork.ui.screens.auth

import dev.launcherfork.domain.AuthResult
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.WindowChrome
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane

/** Common fx:ids of the 379x490 auth windows (same names in every original auth FXML). */
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

    protected fun handle(result: AuthResult) {
        setBusy(false)
        when (result) {
            is AuthResult.Success -> ctx.navigator.showMain()
            AuthResult.CodeRequired -> ctx.navigator.showMailAuth()
            is AuthResult.Failure -> showError(ctx.t(result.messageKey))
        }
    }
}

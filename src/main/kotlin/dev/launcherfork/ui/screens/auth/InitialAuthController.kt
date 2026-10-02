package dev.launcherfork.ui.screens.auth

import dev.launcherfork.domain.AuthError
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.background
import dev.launcherfork.ui.components.brightenOnHover
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip

/** initial_auth.fxml: login + password. Uses only [dev.launcherfork.domain.AuthService]. */
class InitialAuthController(ctx: UiContext) : AuthScreenBase(ctx) {
    @FXML lateinit var loginField: TextField
    @FXML lateinit var passwordField: PasswordField
    @FXML lateinit var enterButton: Button
    @FXML lateinit var autoAuthCheckBox: CheckBox
    @FXML lateinit var registerButton: Button
    @FXML lateinit var restoreButton: Button
    @FXML lateinit var enterSteamButton: Button

    @FXML
    fun initialize() {
        initChrome()
        loginField.text = ctx.services.auth.savedLogin ?: ""
        autoAuthCheckBox.isSelected = ctx.services.auth.savedLogin != null
        listOf(enterButton, registerButton, restoreButton, enterSteamButton).forEach { it.brightenOnHover() }
        enterButton.tooltip = Tooltip(ctx.t("fork.hint"))
    }

    @FXML
    fun onEnter() {
        val login = loginField.text.trim()
        val password = passwordField.text
        when {
            login.isEmpty() -> return showError(AuthError.EMPTY_LOGIN)
            password.isEmpty() -> return showError(AuthError.EMPTY_PASSWORD)
        }
        showError(null)
        setBusy(true)
        val remember = autoAuthCheckBox.isSelected
        background({ ctx.services.auth.login(login, password, remember) }, onResult = ::handle)
    }

    @FXML fun onAutoAuth() = Unit

    @FXML
    fun onLabelAutoAuth() {
        autoAuthCheckBox.isSelected = !autoAuthCheckBox.isSelected
    }

    @FXML fun onRegister() = showError(ctx.t("fork.not_available"))
    @FXML fun onRestore() = showError(ctx.t("fork.not_available"))
    @FXML fun onEnterSteam() = showError(ctx.t("fork.not_available"))
}

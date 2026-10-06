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
import javafx.scene.control.ToggleButton
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
    /** Only in auth_panel.fxml (zone style): plain-text twin of [passwordField] shown by the eye toggle. */
    @FXML var passwordVisibleField: TextField? = null
    @FXML var passwordToggle: ToggleButton? = null

    @FXML
    fun initialize() {
        initChrome()
        loginField.text = ctx.services.auth.savedLogin ?: ""
        autoAuthCheckBox.isSelected = ctx.services.auth.savedLogin != null
        wirePasswordToggle()
        // the zone panel has CSS :hover states instead of the brightness effect
        if (!root.styleClass.contains("zone-panel")) {
            listOf(enterButton, registerButton, restoreButton, enterSteamButton).forEach { it.brightenOnHover() }
        }
        enterButton.tooltip = Tooltip(ctx.t("fork.hint"))
    }

    private fun wirePasswordToggle() {
        val plain = passwordVisibleField ?: return
        val toggle = passwordToggle ?: return
        plain.textProperty().bindBidirectional(passwordField.textProperty())
        plain.visibleProperty().bind(toggle.selectedProperty())
        passwordField.visibleProperty().bind(toggle.selectedProperty().not())
        toggle.selectedProperty().addListener { _, _, shown ->
            val target = if (shown) plain else passwordField
            target.requestFocus()
            target.positionCaret(target.text.length)
        }
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

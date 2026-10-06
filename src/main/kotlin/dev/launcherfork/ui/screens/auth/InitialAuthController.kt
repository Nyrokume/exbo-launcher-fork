package dev.launcherfork.ui.screens.auth

import dev.launcherfork.domain.AuthError
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.background
import dev.launcherfork.ui.components.brightenOnHover
import javafx.css.PseudoClass
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
    /** `<Boolean fx:id="cssHover" fx:value="true"/>` in the FXML: hover comes from CSS, skip the brightness effect. */
    @FXML var cssHover: Boolean? = null

    @FXML
    fun initialize() {
        initChrome()
        loginField.text = ctx.services.auth.savedLogin ?: ""
        autoAuthCheckBox.isSelected = ctx.services.auth.savedLogin != null
        wirePasswordToggle()
        if (cssHover != true) {
            listOf(enterButton, registerButton, restoreButton, enterSteamButton).forEach { it.brightenOnHover() }
        } else {
            wireZoneForm()
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
        fun label(shown: Boolean) = ctx.t(if (shown) "fork.zone.hide_password" else "fork.zone.show_password")
        toggle.accessibleText = label(toggle.isSelected)
        toggle.tooltip = Tooltip(toggle.accessibleText)
        toggle.selectedProperty().addListener { _, _, shown ->
            toggle.accessibleText = label(shown)
            toggle.tooltip.text = toggle.accessibleText
        }
    }

    /**
     * stalzone.com/login behaviour: ВОЙТИ stays disabled (grey) while a field is empty, an error paints the
     * fields red (:error) until the user edits them, and empty error labels take no space.
     */
    private fun wireZoneForm() {
        enterButton.disableProperty().bind(
            loginField.textProperty().isEmpty.or(passwordField.textProperty().isEmpty)
        )
        val fields = listOfNotNull(loginField, passwordField, passwordVisibleField)
        errorFirst.textProperty().addListener { _, _, text ->
            fields.forEach { it.pseudoClassStateChanged(ERROR, !text.isNullOrEmpty()) }
        }
        fields.forEach { f -> f.textProperty().addListener { _, _, _ -> fields.forEach { it.pseudoClassStateChanged(ERROR, false) } } }
        listOf(errorFirst, errorSecond).forEach { it.managedProperty().bind(it.textProperty().isNotEmpty) }
        // as on the site: the cursor starts in the first empty field, not on a social tile
        loginField.sceneProperty().addListener { _, _, scene ->
            if (scene != null) javafx.application.Platform.runLater {
                (if (loginField.text.isNullOrEmpty()) loginField else passwordField).requestFocus()
            }
        }
    }

    @FXML
    fun onEnter() {
        // zone form: Enter in a field must not bypass the grey ВОЙТИ (as on stalzone.com/login)
        if (enterButton.disableProperty().isBound && enterButton.isDisabled) return
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
    @FXML fun onEnterEpic() = showError(ctx.t("fork.not_available"))

    private companion object {
        val ERROR: PseudoClass = PseudoClass.getPseudoClass("error")
    }
}

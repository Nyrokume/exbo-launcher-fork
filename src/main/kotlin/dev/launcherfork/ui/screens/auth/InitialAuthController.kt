package dev.launcherfork.ui.screens.auth

import dev.launcherfork.domain.AuthError
import dev.launcherfork.domain.ExternalLinks
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.background
import dev.launcherfork.ui.components.PasswordReveal
import dev.launcherfork.ui.components.brightenOnHover
import javafx.css.PseudoClass
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
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
    /** Only in auth_panel.fxml: success notice after a registration ("Аккаунт создан…"), hidden while empty. */
    @FXML var noticeLabel: Label? = null
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
        if (ctx.liveBackend) {
            (root.lookup("#zoneEpic") as? Button)?.apply {
                isDisable = true
                tooltip = Tooltip(ctx.t("fork.live.epic"))
            }
        }
    }

    private fun wirePasswordToggle() {
        val plain = passwordVisibleField ?: return
        val toggle = passwordToggle ?: return
        PasswordReveal.wire(passwordField, plain, toggle) { shown ->
            ctx.t(if (shown) "fork.zone.hide_password" else "fork.zone.show_password")
        }
    }

    /**
     * Back from a successful registration (StageNavigator.showRegistered): the new [login] in the login field, an
     * empty password (focused) and the success notice above the remember row, until the next login attempt.
     */
    fun showRegistered(login: String) {
        loginField.text = login
        passwordField.text = ""
        showError(null)
        noticeLabel?.text = ctx.t("fork.reg.success", login)
        passwordField.requestFocus()
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
        listOfNotNull(errorFirst, errorSecond, noticeLabel).forEach { it.managedProperty().bind(it.textProperty().isNotEmpty) }
        noticeLabel?.let { notice ->
            notice.visibleProperty().bind(notice.textProperty().isNotEmpty)
            // hide the success banner once the user starts entering the password (login stays prefilled)
            listOfNotNull(passwordField, passwordVisibleField).forEach { f ->
                f.textProperty().addListener { _, _, text -> if (!text.isNullOrEmpty()) notice.text = "" }
            }
        }
        // as on the site: the cursor starts in the first empty field, not on a social tile
        loginField.sceneProperty().addListener { _, _, scene ->
            if (scene != null) javafx.application.Platform.runLater {
                (if (loginField.text.isNullOrEmpty()) loginField else passwordField).requestFocus()
            }
        }
    }

    fun explainNewPlace() = showError(ctx.t("auth.error.new_place"))

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
        noticeLabel?.text = ""
        setBusy(true)
        val remember = autoAuthCheckBox.isSelected
        background({ ctx.services.auth.login(login, password, remember) }, onResult = ::handle)
    }

    @FXML fun onAutoAuth() = Unit

    @FXML
    fun onLabelAutoAuth() {
        autoAuthCheckBox.isSelected = !autoAuthCheckBox.isSelected
    }

    /** "Создать" (zone) / "Регистрация" (legacy): in-app age gate, including when EXBO is live. */
    @FXML fun onRegister() {
        ctx.navigator.showAgeGate()
    }

    @FXML fun onRestore() {
        if (ctx.liveBackend) ctx.services.links.open(ExternalLinks.RESTORE)
        else showError(ctx.t("fork.not_available"))
    }

    /** Steam auth in the official client needs a local socket. This fork leaves it unavailable. */
    @FXML fun onEnterSteam() = showError(ctx.t("fork.not_available"))
    @FXML fun onEnterEpic() = showError(ctx.t("fork.not_available"))

    private companion object {
        val ERROR: PseudoClass = PseudoClass.getPseudoClass("error")
    }
}

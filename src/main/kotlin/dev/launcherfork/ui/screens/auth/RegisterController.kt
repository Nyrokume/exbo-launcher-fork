package dev.launcherfork.ui.screens.auth

import dev.launcherfork.domain.RegisterError
import dev.launcherfork.domain.RegisterResult
import dev.launcherfork.domain.RegistrationField
import dev.launcherfork.domain.RegistrationRequest
import dev.launcherfork.domain.RegistrationRules
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.PasswordReveal
import dev.launcherfork.ui.components.background
import javafx.css.PseudoClass
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Hyperlink
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.scene.control.ToggleButton
import javafx.scene.layout.HBox
import javafx.scene.text.TextFlow
import java.time.LocalDate

/**
 * register_panel.fxml, registration step 2 (stalzone.com/register form):
 *  - СОЗДАТЬ АККАУНТ is grey until e-mail, username and password are filled and the license box is ticked;
 *  - on click the formats are checked ([RegistrationRules]); the first problem is shown in the compact toast
 *    (same style as TOTP) and every invalid field turns red until it is edited;
 *  - then [dev.launcherfork.domain.AuthService.register] runs in the background (spinner, form locked); success
 *    returns to the login panel with the username filled in and a notice, "taken" errors paint their field;
 *  - back link / Esc -> age gate with the date kept; "Войти" -> login.
 */
class RegisterController(ctx: UiContext) : AuthScreenBase(ctx) {
    @FXML lateinit var emailField: TextField
    @FXML lateinit var usernameField: TextField
    @FXML lateinit var passwordField: PasswordField
    @FXML lateinit var passwordVisibleField: TextField
    @FXML lateinit var passwordToggle: ToggleButton
    @FXML lateinit var newsletterCheckBox: CheckBox
    @FXML lateinit var newsletterText: TextFlow
    @FXML lateinit var licenseCheckBox: CheckBox
    @FXML lateinit var licenseText: TextFlow
    @FXML lateinit var licenseLink: Hyperlink
    @FXML lateinit var privacyLink: Hyperlink
    @FXML lateinit var createButton: Button
    @FXML lateinit var backButton: Button
    @FXML lateinit var loginLink: Button
    @FXML lateinit var toast: HBox
    @FXML lateinit var toastClose: Button

    /** Date from the age gate (StageNavigator.showRegister). Without it the form sends the user back there. */
    var birthDate: LocalDate? = null

    @FXML
    fun initialize() {
        initChrome()
        PasswordReveal.wire(passwordField, passwordVisibleField, passwordToggle) { shown ->
            ctx.t(if (shown) "fork.zone.hide_password" else "fork.zone.show_password")
        }
        createButton.disableProperty().bind(
            emailField.textProperty().isEmpty
                .or(usernameField.textProperty().isEmpty)
                .or(passwordField.textProperty().isEmpty)
                .or(licenseCheckBox.selectedProperty().not())
        )
        // a field stays red until it is edited; any edit hides the error line
        fieldsOf(RegistrationField.EMAIL, RegistrationField.USERNAME, RegistrationField.PASSWORD).flatten().forEach { f ->
            (f as TextField).textProperty().addListener { _, _, _ ->
                markField(f, false)
                if (f === passwordVisibleField) markField(passwordField, false)
                if (f === passwordField) markField(passwordVisibleField, false)
                showError(null)
            }
        }
        licenseCheckBox.selectedProperty().addListener { _, _, _ -> markField(licenseCheckBox, false); showError(null) }
        // clicking the text next to a box ticks it (except the links)
        toggleOnTextClick(newsletterText, newsletterCheckBox)
        toggleOnTextClick(licenseText, licenseCheckBox)
        listOf(errorFirst, errorSecond).forEach { it.managedProperty().bind(it.textProperty().isNotEmpty) }
        toast.visibleProperty().bind(errorFirst.textProperty().isNotEmpty.or(errorSecond.textProperty().isNotEmpty))
        toast.managedProperty().bind(toast.visibleProperty())
        toastClose.accessibleText = ctx.t("fork.toast.close")
        onEscape(::onBack)
    }

    @FXML fun onCloseToast() = showError(null)

    private fun toggleOnTextClick(text: TextFlow, box: CheckBox) {
        text.setOnMouseClicked { e ->
            var n = e.target as? Node
            while (n != null && n !== text) {
                if (n is Hyperlink) return@setOnMouseClicked
                n = n.parent
            }
            box.isSelected = !box.isSelected
        }
    }

    private fun fieldsOf(vararg fields: RegistrationField): List<List<Node>> = fields.map {
        when (it) {
            RegistrationField.EMAIL -> listOf(emailField)
            RegistrationField.USERNAME -> listOf(usernameField)
            RegistrationField.PASSWORD -> listOf(passwordField, passwordVisibleField)
            RegistrationField.LICENSE -> listOf(licenseCheckBox)
        }
    }

    private fun markField(node: Node, error: Boolean) = node.pseudoClassStateChanged(ERROR, error)

    /** True while [node] is painted as invalid (tests). */
    fun isMarked(node: Node): Boolean = node.pseudoClassStates.contains(ERROR)

    private fun message(field: RegistrationField): String = when (field) {
        RegistrationField.EMAIL -> ctx.t("fork.reg.error.email")
        RegistrationField.USERNAME -> ctx.t("fork.reg.error.username", RegistrationRules.USERNAME_MIN, RegistrationRules.USERNAME_MAX)
        RegistrationField.PASSWORD -> ctx.t("fork.reg.error.password", RegistrationRules.PASSWORD_MIN)
        RegistrationField.LICENSE -> ctx.t("fork.reg.error.license")
    }

    @FXML
    fun onCreate() {
        if (createButton.isDisabled) return // Enter in a field must not bypass the grey button
        val email = emailField.text.trim()
        val username = usernameField.text.trim()
        val password = passwordField.text
        val invalid = RegistrationRules.validate(email, username, password, licenseCheckBox.isSelected)
        if (invalid.isNotEmpty()) {
            fieldsOf(*invalid.toTypedArray()).flatten().forEach { markField(it, true) }
            return showError(message(invalid.first()))
        }
        val birth = birthDate ?: return ctx.navigator.showAgeGate()
        showError(null)
        setBusy(true)
        val request = RegistrationRequest(email, username, password, birth, newsletterCheckBox.isSelected)
        background({ ctx.services.auth.register(request) }) { outcome ->
            setBusy(false)
            when (val r = outcome.getOrElse { RegisterResult.Failure(RegisterError.SERVER_ERROR) }) {
                is RegisterResult.Success -> ctx.navigator.showRegistered(r.login)
                is RegisterResult.Failure -> fail(r.error)
            }
        }
    }

    private fun fail(error: RegisterError) {
        when (error) {
            RegisterError.USERNAME_TAKEN -> { markField(usernameField, true); showError(ctx.t("fork.reg.error.username_taken")) }
            RegisterError.EMAIL_TAKEN -> { markField(emailField, true); showError(ctx.t("fork.reg.error.email_taken")) }
            RegisterError.UNDER_AGE -> showError(ctx.t("fork.age.too_young", RegistrationRules.MIN_AGE))
            RegisterError.SERVER_ERROR -> showError(ctx.t("auth.error.server_error"))
        }
    }

    /** License / privacy links: no browser in the fork, a grey note instead. */
    @FXML fun onDocLink() = showError(null, ctx.t("fork.not_available"))

    @FXML fun onBack() = ctx.navigator.showAgeGate(birthDate)

    @FXML fun onLogin() = ctx.navigator.showAuth()

    private companion object {
        val ERROR: PseudoClass = PseudoClass.getPseudoClass("error")
    }
}

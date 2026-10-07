package dev.launcherfork.app

import dev.launcherfork.domain.Progress
import dev.launcherfork.ui.screens.auth.AgeGateController
import dev.launcherfork.ui.screens.auth.InitialAuthController
import dev.launcherfork.ui.screens.auth.RegisterController
import dev.launcherfork.ui.screens.main.MainController
import dev.launcherfork.ui.state.ClientState
import javafx.animation.PauseTransition
import javafx.util.Duration
import java.time.LocalDate

/**
 * What the automatic tours do on screen, shared by the screenshot tour ([ScreenshotTour]) and the showcase
 * ([ShowcaseTour]): sign in with the mock account, open the menus, show settings, put the selected game into a sample
 * download state, leave to the auth window, open the registration steps. Everything goes through the navigator and the
 * controllers' public API (the login / registration panels are only navigated to, never changed). Mock data only.
 */
class TourActions(private val nav: StageNavigator) {
    val main: MainController get() = requireNotNull(nav.mainController) { "the main window is not shown" }

    /** Signs in on the auth panel with the mock account (any login / password works in the mock). */
    fun signIn(login: String = MOCK_LOGIN, password: String = MOCK_PASSWORD) {
        val auth = nav.panelController as InitialAuthController
        auth.loginField.text = login
        auth.passwordField.text = password
        auth.onEnter()
    }

    fun showProfileMenu() = main.profileBarController.showMenu()
    fun showCommunityMenu() = main.headerController.showCommunityMenu()
    fun hideMenus() = main.hideMenus()

    fun showSettings(visible: Boolean) {
        main.hideMenus()
        main.setSettingsVisible(visible)
    }

    /** Re-selects the selected game: the banner text rises in again (the mock has one game). */
    fun reselectGame() {
        val game = main.selected ?: return
        main.serversListController.select(game)
    }

    fun setClientState(state: ClientState) = main.run { store.state(requireNotNull(selected)).set(state) }

    /** Sample download of a 10 GB client at 24 MB/s, [fraction] done (fixed numbers: same picture every run). */
    fun downloading(fraction: Double) = setClientState(
        ClientState.Downloading(Progress(fraction, (SAMPLE_TOTAL * fraction).toLong(), SAMPLE_TOTAL, SAMPLE_SPEED)),
    )

    fun paused(fraction: Double) = setClientState(ClientState.Paused(fraction, SAMPLE_TOTAL))
    fun ready() = setClientState(ClientState.Ready)

    /** Profile menu «Выйти» + confirm: the only menu path back to the separate auth window (session ends; «Сменить аккаунт» is gone). Showcase uses [showAuth] instead. */
    fun logoutToAuth() {
        main.profileBarController.loginButton.fire()
        main.profileBarController.logout.fire()
        main.logoutPaneController.logoutButtonRight.fire()
    }

    fun showAuth() = nav.showAuth()
    fun showAgeGate() = nav.showAgeGate()
    fun showRegister(birthDate: LocalDate = SAMPLE_BIRTH_DATE) = nav.showRegister(birthDate)

    /** Fills the age-gate dropdowns with [birthDate] (registration step 1, visible pick). */
    fun fillAgeGate(birthDate: LocalDate = SAMPLE_BIRTH_DATE) {
        val gate = nav.panelController as AgeGateController
        gate.preset(birthDate)
    }

    /** Continues from a filled age gate into the registration form. */
    fun continueAgeGate() {
        (nav.panelController as AgeGateController).onContinue()
    }

    /** Fills the registration form with sample mock data (no submit: showcase stays on the form). */
    fun fillRegister(
        email: String = SAMPLE_EMAIL,
        username: String = SAMPLE_USERNAME,
        password: String = MOCK_PASSWORD,
    ) {
        val form = nav.panelController as RegisterController
        form.emailField.text = email
        form.usernameField.text = username
        form.passwordField.text = password
        form.newsletterCheckBox.isSelected = true
        form.licenseCheckBox.isSelected = true
    }

    /** «⋯» menu next to ИГРАТЬ. */
    fun showGameActions() = main.defaultStatusPaneController.gameActionsController.show()
    fun hideGameActions() = main.defaultStatusPaneController.gameActionsController.hide()

    /** Profile → «Выйти» opens the confirm pane; [cancelLogout] dismisses it. */
    fun showLogoutConfirm() {
        showProfileMenu()
        main.profileBarController.logout.fire()
    }

    fun cancelLogout() = main.logoutPaneController.logoutButtonLeft.fire()

    companion object {
        const val MOCK_LOGIN = "DementiyRezak"
        const val MOCK_PASSWORD = "secret"
        const val SAMPLE_EMAIL = "stalker@mail.ru"
        const val SAMPLE_USERNAME = "DementiyRezak"
        const val SAMPLE_TOTAL = 10_737_418_240L
        const val SAMPLE_SPEED = 25_165_824L
        val SAMPLE_BIRTH_DATE: LocalDate = LocalDate.of(2000, 7, 26)
    }
}

/**
 * Steps run one after another, each [step] after its delay (seconds) from the end of the previous one, on the FX
 * thread (PauseTransition). A failing step is logged and the queue goes on. [cancel] drops the rest.
 */
class StepQueue {
    private val steps = ArrayDeque<Pair<Double, () -> Unit>>()
    private var pending: PauseTransition? = null

    fun step(delaySec: Double, action: () -> Unit) = steps.addLast(delaySec to action)

    val isEmpty: Boolean get() = steps.isEmpty()

    fun run() {
        val (delay, action) = steps.removeFirstOrNull() ?: return
        pending = later(delay) {
            try { action() } catch (e: Exception) { e.printStackTrace() }
            run()
        }
    }

    fun cancel() {
        steps.clear()
        pending?.stop()
        pending = null
    }

    companion object {
        fun later(sec: Double, action: () -> Unit): PauseTransition =
            PauseTransition(Duration.seconds(sec)).apply { setOnFinished { action() } }.also { it.play() }
    }
}

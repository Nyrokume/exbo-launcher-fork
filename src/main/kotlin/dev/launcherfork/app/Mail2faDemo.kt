package dev.launcherfork.app

import dev.launcherfork.data.mock.MockAuthService
import dev.launcherfork.ui.screens.auth.MailAuthController
import javafx.application.Platform

/**
 * Dev demo: e-mail 2FA panel — wrong code (`000000000` → «Неверный код!»), then a correct 9-digit code → main.
 * Start with `-Dlauncher.demo=mail2fa` (login `2fa` always opens the mail panel).
 */
class Mail2faDemo(private val nav: StageNavigator) {
    private val queue = StepQueue()

    fun install() {
        val act = TourActions(nav)
        val mail = { nav.panelController as MailAuthController }
        queue.step(0.8) {
            System.err.println("[launcher-fork] mail2fa demo: login → code panel")
            act.signIn(login = "2fa", password = TourActions.MOCK_PASSWORD)
        }
        queue.step(1.4) {
            check(nav.panelController is MailAuthController) { "expected mail panel, got ${nav.panelController}" }
            System.err.println("[launcher-fork] mail2fa demo: wrong code ${MockAuthService.WRONG_EMAIL_CODE}")
            mail().codeInput!!.setCode(MockAuthService.WRONG_EMAIL_CODE)
            mail().enter()
        }
        queue.step(4.0) {
            System.err.println("[launcher-fork] mail2fa demo: correct code 268419249")
            mail().codeInput!!.setCode("268419249")
            mail().enter()
        }
        queue.step(1.5) {
            System.err.println("[launcher-fork] mail2fa demo: main window")
        }
        Platform.runLater { queue.run() }
    }
}

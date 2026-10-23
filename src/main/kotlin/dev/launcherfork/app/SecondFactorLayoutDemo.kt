package dev.launcherfork.app

import dev.launcherfork.data.mock.MockAuthService
import dev.launcherfork.ui.screens.auth.MailAuthController
import dev.launcherfork.ui.screens.auth.TotpAuthController
import javafx.application.Platform

/**
 * Dev demo for layout checks: e-mail 2FA (idle → error), then Google Authenticator (idle → error).
 * Stays on the TOTP error screen. Start with `-Dlauncher.demo=2fa`.
 *
 * Navigation goes through [StageNavigator] (not controller back-links) so panel swaps stay reliable;
 * error states use the mock's rejected codes after a real login so toast / field chrome matches production.
 */
class SecondFactorLayoutDemo(private val nav: StageNavigator) {
    private val queue = StepQueue()

    fun install() {
        val act = TourActions(nav)

        // --- e-mail ---
        queue.step(0.6) {
            System.err.println("[launcher-fork] 2fa layout: login → e-mail panel")
            act.signIn(login = "2fa", password = TourActions.MOCK_PASSWORD)
        }
        queue.step(1.8) {
            val mail = requireMail()
            System.err.println("[launcher-fork] 2fa layout: hold mail idle")
            mail.codeInput?.clear()
        }
        queue.step(4.0) {
            val mail = requireMail()
            System.err.println("[launcher-fork] 2fa layout: mail error ${MockAuthService.WRONG_EMAIL_CODE}")
            mail.codeInput!!.setCode(MockAuthService.WRONG_EMAIL_CODE)
            mail.enter()
        }
        queue.step(2.0) {
            val mail = requireMail()
            check(mail.isToastShown) { "mail toast not shown: '${mail.errorFirst.text}'" }
            System.err.println("[launcher-fork] 2fa layout: hold mail error «${mail.errorFirst.text}»")
        }
        queue.step(5.0) {
            System.err.println("[launcher-fork] 2fa layout: → Google Authenticator")
            nav.showAuth()
        }

        // --- TOTP ---
        queue.step(0.8) {
            act.signIn(login = "totp", password = TourActions.MOCK_PASSWORD)
        }
        queue.step(1.8) {
            val totp = requireTotp()
            System.err.println("[launcher-fork] 2fa layout: hold totp idle")
            totp.codeInput.clear()
        }
        queue.step(4.0) {
            val totp = requireTotp()
            System.err.println("[launcher-fork] 2fa layout: totp error ${MockAuthService.WRONG_TOTP_CODE}")
            totp.codeInput.setCode(MockAuthService.WRONG_TOTP_CODE)
            totp.enter()
        }
        queue.step(2.0) {
            val totp = requireTotp()
            check(totp.isToastShown) { "totp toast not shown" }
            System.err.println("[launcher-fork] 2fa layout: hold totp error (stays here)")
        }

        Platform.runLater { queue.run() }
    }

    private fun requireMail(): MailAuthController =
        nav.panelController as? MailAuthController
            ?: error("expected mail panel, got ${nav.panelController}")

    private fun requireTotp(): TotpAuthController =
        nav.panelController as? TotpAuthController
            ?: error("expected totp panel, got ${nav.panelController}")
}

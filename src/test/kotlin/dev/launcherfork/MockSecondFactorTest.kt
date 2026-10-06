package dev.launcherfork

import dev.launcherfork.app.StageNavigator.ActiveWindow
import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.data.mock.MockAuthService
import dev.launcherfork.data.mock.MockSecondFactor
import dev.launcherfork.domain.AuthError
import dev.launcherfork.domain.AuthResult
import dev.launcherfork.ui.screens.auth.InitialAuthController
import dev.launcherfork.ui.screens.auth.MailAuthController
import dev.launcherfork.ui.screens.auth.TotpAuthController
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * «При авторизации вызывай рандомно один из методов 2FA почта или google auth»: after a successful password login the
 * mock asks for a random second factor per attempt (9-digit e-mail code or 6-digit authenticator code), with an
 * injected Random and the -Dlauncher.mock2fa=random|email|totp|none override; the routing goes to the existing panels.
 */
class MockSecondFactorTest {
    private fun auth(mode: MockSecondFactor, random: Random = Random(1)) = MockAuthService(delayMs = 0, secondFactor = mode, random = random)

    private fun success(r: AuthResult) = assertTrue(r is AuthResult.Success, "expected success, got $r")

    @Test
    fun propertyValuesAreParsedWithRandomAsTheDefault() {
        assertEquals(MockSecondFactor.RANDOM, MockSecondFactor.parse(null))
        assertEquals(MockSecondFactor.RANDOM, MockSecondFactor.parse(""))
        assertEquals(MockSecondFactor.RANDOM, MockSecondFactor.parse("random"))
        assertEquals(MockSecondFactor.EMAIL, MockSecondFactor.parse("email"))
        assertEquals(MockSecondFactor.EMAIL, MockSecondFactor.parse(" EMAIL "))
        assertEquals(MockSecondFactor.TOTP, MockSecondFactor.parse("totp"))
        assertEquals(MockSecondFactor.NONE, MockSecondFactor.parse("none"))
        assertEquals(MockSecondFactor.RANDOM, MockSecondFactor.parse("sms"), "unknown values fall back to the default")
        assertEquals(MockSecondFactor.RANDOM, MockSecondFactor.fromSystemProperties { null })
        assertEquals(MockSecondFactor.TOTP, MockSecondFactor.fromSystemProperties { if (it == MockSecondFactor.PROPERTY) "totp" else null })
        assertEquals("launcher.mock2fa", MockSecondFactor.PROPERTY)
    }

    @Test
    fun randomModeReachesBothFactorsAndEachOneCompletes() {
        val seen = mutableSetOf<AuthResult>()
        val a = auth(MockSecondFactor.RANDOM, Random(42))
        repeat(40) {
            val r = a.login("player", "secret", false)
            seen += r
            when (r) {
                AuthResult.CodeRequired -> success(a.confirmCode("268419249"))
                AuthResult.TotpRequired -> success(a.confirmTotp("123456"))
                else -> error("random mode must always ask for a second factor, got $r")
            }
        }
        assertEquals(setOf(AuthResult.CodeRequired, AuthResult.TotpRequired), seen, "both branches reachable")
    }

    @Test
    fun theInjectedRandomDecidesPerAttempt() {
        val picks = ArrayDeque(listOf(false, true, true, false))
        val scripted = object : Random() {
            override fun nextBits(bitCount: Int) = 0
            override fun nextBoolean() = picks.removeFirst()
        }
        val a = auth(MockSecondFactor.RANDOM, scripted)
        assertEquals(AuthResult.CodeRequired, a.login("player", "secret", false)) // false -> e-mail
        assertEquals(AuthResult.TotpRequired, a.login("player", "secret", false)) // a new attempt re-rolls
        success(a.confirmTotp("123456"))
        assertEquals(AuthResult.TotpRequired, a.login("player", "secret", false))
        // the factor that was not asked is refused: the code must go to the panel that was shown
        assertEquals(AuthResult.Failure(AuthError.SERVER_ERROR), a.confirmCode("268419249"))
        assertEquals(AuthResult.CodeRequired, a.login("player", "secret", false))
    }

    @Test
    fun overridesAreRespected() {
        val email = auth(MockSecondFactor.EMAIL)
        val totp = auth(MockSecondFactor.TOTP)
        val none = auth(MockSecondFactor.NONE)
        repeat(10) {
            assertEquals(AuthResult.CodeRequired, email.login("player", "secret", false))
            assertEquals(AuthResult.TotpRequired, totp.login("player", "secret", false))
            success(none.login("player", "secret", false))
        }
        // the same code rules as before: 9 digits for the e-mail, 6 for the app, 000000 is the app's wrong code
        assertEquals(AuthResult.Failure(AuthError.WRONG_CODE), email.confirmCode("123456"))
        success(email.confirmCode("268419249"))
        assertEquals(AuthResult.Failure(AuthError.WRONG_CODE), totp.confirmTotp(MockAuthService.WRONG_TOTP_CODE))
        success(totp.confirmTotp("654321"))
    }

    @Test
    fun theOldRulesStillWinInEveryMode() {
        MockSecondFactor.entries.forEach { mode ->
            val a = auth(mode)
            assertEquals(AuthResult.Failure(AuthError.WRONG_CREDENTIALS), a.login("player", "wrong", false), "$mode: wrong password")
            assertEquals(AuthResult.CodeRequired, a.login("2fa", "x", false), "$mode: login 2fa")
            assertEquals(AuthResult.TotpRequired, a.login("totp", "x", false), "$mode: login totp")
        }
        // the default constructor (tests, embedding) keeps the old behaviour: plain logins pass without a second factor
        success(MockAuthService(delayMs = 0).login("player", "secret", false))
        success(AppContext.mock(authDelayMs = 0).services.auth.login("player", "secret", false))
    }

    private fun loginRoutesTo(mode: MockSecondFactor, expected: Class<*>) {
        val app = AppContext.mock(speed = 50.0, authDelayMs = 0, mock2fa = mode)
        val nav = FxTestSupport.onFx {
            StageNavigator(Stage(StageStyle.TRANSPARENT), app, "ru").also { it.exit = {}; it.showAuth(); it.authStage.show() }
        }
        try {
            assertTrue(FxTestSupport.waitFx(10_000) { nav.panelController is InitialAuthController })
            FxTestSupport.onFx {
                val a = nav.panelController as InitialAuthController
                a.loginField.text = "player"; a.passwordField.text = "secret"; a.onEnter()
            }
            assertTrue(FxTestSupport.waitFx(10_000) { expected.isInstance(nav.panelController) || nav.activeWindow == ActiveWindow.MAIN },
                "$mode: login went nowhere")
            FxTestSupport.onFx {
                if (expected == ActiveWindow::class.java) assertEquals(ActiveWindow.MAIN, nav.activeWindow)
                else assertTrue(expected.isInstance(nav.panelController), "$mode: shows ${nav.panelController}")
            }
        } finally {
            FxTestSupport.onFx { nav.close() }
            app.shutdown()
        }
    }

    @Test
    fun loginRoutesToTheChosenPanel() {
        loginRoutesTo(MockSecondFactor.EMAIL, MailAuthController::class.java)
        loginRoutesTo(MockSecondFactor.TOTP, TotpAuthController::class.java)
        loginRoutesTo(MockSecondFactor.NONE, ActiveWindow::class.java)
    }
}

package dev.launcherfork.data.mock

import dev.launcherfork.domain.AuthError
import dev.launcherfork.domain.AuthResult
import dev.launcherfork.domain.AuthService
import dev.launcherfork.domain.RegisterError
import dev.launcherfork.domain.RegisterResult
import dev.launcherfork.domain.RegistrationRequest
import dev.launcherfork.domain.RegistrationRules
import dev.launcherfork.domain.UserSession
import java.time.LocalDate
import kotlin.random.Random

/**
 * Fake auth, no network:
 *  - password "wrong" -> wrong credentials error
 *  - login "2fa" -> e-mail code screen, any 9-digit code is accepted
 *  - login "totp" -> authenticator-app screen: any 6-digit code is accepted except 000000
 *  - any other non-empty login/password -> the second factor chosen by [secondFactor]: [MockSecondFactor.RANDOM]
 *    picks the e-mail code or the authenticator app per login attempt with [random] (the app's default,
 *    `-Dlauncher.mock2fa`), [MockSecondFactor.EMAIL] / [MockSecondFactor.TOTP] always that one,
 *    [MockSecondFactor.NONE] (the constructor default: tests, the tour) -> success straight away
 *  - register: username "taken" -> "name taken", e-mail "taken@…" -> "e-mail taken", anything else -> success
 */
class MockAuthService(
    private val delayMs: Long = 500,
    private val secondFactor: MockSecondFactor = MockSecondFactor.NONE,
    private val random: Random = Random.Default,
) : AuthService {
    private enum class Factor { EMAIL, TOTP }

    @Volatile private var pending: Pair<String, Factor>? = null
    @Volatile override var currentSession: UserSession? = null
        private set
    @Volatile override var savedLogin: String? = "player"
        private set

    override fun login(login: String, password: String, remember: Boolean): AuthResult {
        pause()
        if (password == "wrong") return AuthResult.Failure(AuthError.WRONG_CREDENTIALS)
        if (remember) savedLogin = login
        return when {
            login.equals("2fa", ignoreCase = true) -> { pending = login to Factor.EMAIL; AuthResult.CodeRequired }
            login.equals("totp", ignoreCase = true) -> { pending = login to Factor.TOTP; AuthResult.TotpRequired }
            else -> when (pickFactor()) {
                Factor.EMAIL -> { pending = login to Factor.EMAIL; AuthResult.CodeRequired }
                Factor.TOTP -> { pending = login to Factor.TOTP; AuthResult.TotpRequired }
                null -> success(login)
            }
        }
    }

    /** Second factor of this attempt (null = none); RANDOM re-rolls on every login. */
    private fun pickFactor(): Factor? = when (secondFactor) {
        MockSecondFactor.NONE -> null
        MockSecondFactor.EMAIL -> Factor.EMAIL
        MockSecondFactor.TOTP -> Factor.TOTP
        MockSecondFactor.RANDOM -> if (random.nextBoolean()) Factor.TOTP else Factor.EMAIL
    }

    override fun confirmCode(code: String): AuthResult = confirm(code, Factor.EMAIL, CODE_LENGTH, wrong = null)

    override fun confirmTotp(code: String): AuthResult = confirm(code, Factor.TOTP, TOTP_CODE_LENGTH, WRONG_TOTP_CODE)

    private fun confirm(code: String, factor: Factor, length: Int, wrong: String?): AuthResult {
        pause()
        val (login, expected) = pending ?: return AuthResult.Failure(AuthError.SERVER_ERROR)
        if (expected != factor) return AuthResult.Failure(AuthError.SERVER_ERROR)
        if (code.length != length || !code.all(Char::isDigit) || code == wrong) return AuthResult.Failure(AuthError.WRONG_CODE)
        pending = null
        return success(login)
    }

    override fun register(request: RegistrationRequest): RegisterResult {
        pause()
        if (!RegistrationRules.isOldEnough(request.birthDate, LocalDate.now())) return RegisterResult.Failure(RegisterError.UNDER_AGE)
        if (request.username.equals(TAKEN, ignoreCase = true)) return RegisterResult.Failure(RegisterError.USERNAME_TAKEN)
        if (request.email.substringBefore('@').equals(TAKEN, ignoreCase = true)) return RegisterResult.Failure(RegisterError.EMAIL_TAKEN)
        return RegisterResult.Success(request.username)
    }

    override fun logout() {
        currentSession = null
    }

    private fun success(login: String): AuthResult {
        pending = null // a finished sign-in drops any half-done 2FA attempt
        val s = UserSession(login)
        currentSession = s
        return AuthResult.Success(s)
    }

    private fun pause() {
        if (delayMs > 0) Thread.sleep(delayMs)
    }

    companion object {
        /** Digits of the e-mail code (e.g. 268419249). */
        const val CODE_LENGTH = 9
        /** Digits of the authenticator-app code. */
        const val TOTP_CODE_LENGTH = 6
        /** The only authenticator code the mock rejects (any other 6 digits pass). */
        const val WRONG_TOTP_CODE = "000000"
        /** Username (and e-mail local part) the mock reports as already taken. */
        const val TAKEN = "taken"
    }
}

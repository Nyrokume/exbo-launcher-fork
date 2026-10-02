package dev.launcherfork.data.mock

import dev.launcherfork.domain.AuthResult
import dev.launcherfork.domain.AuthService
import dev.launcherfork.domain.UserSession

/**
 * Fake auth, no network:
 *  - any non-empty login/password -> success
 *  - password "wrong" -> wrong credentials error
 *  - login "2fa" -> e-mail code screen, any 6-digit code is accepted
 */
class MockAuthService(private val delayMs: Long = 500) : AuthService {
    @Volatile private var pendingLogin: String? = null
    @Volatile override var currentSession: UserSession? = null
        private set
    @Volatile override var savedLogin: String? = "player"
        private set

    override fun login(login: String, password: String, remember: Boolean): AuthResult {
        pause()
        if (password == "wrong") return AuthResult.Failure("auth.error.wrong_credentials")
        if (remember) savedLogin = login
        if (login.equals("2fa", ignoreCase = true)) {
            pendingLogin = login
            return AuthResult.CodeRequired
        }
        return success(login)
    }

    override fun confirmCode(code: String): AuthResult {
        pause()
        val login = pendingLogin ?: return AuthResult.Failure("auth.error.server_error")
        if (code.length != 6 || !code.all(Char::isDigit)) return AuthResult.Failure("auth.error.wrong_code")
        pendingLogin = null
        return success(login)
    }

    override fun logout() {
        currentSession = null
    }

    private fun success(login: String): AuthResult {
        val s = UserSession(login)
        currentSession = s
        return AuthResult.Success(s)
    }

    private fun pause() {
        if (delayMs > 0) Thread.sleep(delayMs)
    }
}

package dev.launcherfork.data.exbo

import dev.launcherfork.domain.AuthError
import dev.launcherfork.domain.AuthResult
import dev.launcherfork.domain.AuthService
import dev.launcherfork.domain.RegisterError
import dev.launcherfork.domain.RegisterResult
import dev.launcherfork.domain.RegistrationRequest
import dev.launcherfork.domain.UserSession
import java.io.IOException

/**
 * Auth against the installed EXBO launcher backend.
 * The password is sent the same way the working client sends it and is not stored here.
 */
class ExboAuthService(private val backend: ExboBackend) : AuthService {
    constructor(api: ExboApi) : this(ExboBackend(api))

    private val api: ExboApi get() = backend.api
    @Volatile private var pendingLogin: String? = null
    @Volatile private var pendingRemember = false

    @Volatile override var savedLogin: String? = api.savedLogin()
        private set

    @Volatile override var currentSession: UserSession? = null
        private set

    override fun login(login: String, password: String, remember: Boolean): AuthResult {
        pendingRemember = remember
        return exchange(login) { api.passwordAuth(login, password) }
    }

    override fun confirmCode(code: String): AuthResult {
        val login = pendingLogin ?: return AuthResult.Failure(AuthError.SERVER_ERROR)
        return exchange(login) { api.mailCodeAuth(login, code) }
    }

    override fun confirmTotp(code: String): AuthResult {
        val login = pendingLogin ?: return AuthResult.Failure(AuthError.SERVER_ERROR)
        return exchange(login) { api.totpAuth(login, code) }
    }

    /**
     * TODO(live register): no registration route is described in this repo.
     * `ExternalLinks.REGISTER` is the public site page, not a launcher API.
     * Do not invent `/auth` or `/register` parameters. The form says the call is unavailable.
     */
    override fun register(request: RegistrationRequest): RegisterResult =
        RegisterResult.Failure(RegisterError.NOT_SUPPORTED)

    /**
     * Same auto-auth the working launcher runs before the password form.
     * On `OK` the session is ready and [ExboServerRepository] can load the server list.
     */
    @Volatile var autoType: String? = null
        private set

    fun tryAuto(): Boolean {
        val seen = backend.generation
        val current = backend.api
        val type = try {
            current.autoAuth()
        } catch (_: IOException) {
            if (backend.generation != seen || backend.api !== current) return false
            autoType = "NO_CONNECTION"
            return false
        }
        if (backend.generation != seen || backend.api !== current) return false
        autoType = type
        if (type != "OK" || current.token == null) return false
        val name = current.accountLogin ?: return false
        currentSession = UserSession(name)
        savedLogin = name
        return true
    }

    /** Drops the session after a realm switch. The login field reads [savedLogin] from the new install. */
    fun onRealmChanged() {
        currentSession = null
        pendingLogin = null
        autoType = null
        savedLogin = api.savedLogin()
    }

    override fun logout() {
        currentSession = null
        pendingLogin = null
        api.clearToken()
    }

    private fun exchange(login: String, call: () -> ExboAuthReply): AuthResult {
        val reply = try {
            call()
        } catch (_: IOException) {
            return AuthResult.Failure(AuthError.NO_CONNECTION)
        }
        return when (val mapped = mapAuthType(reply.type, reply.timeLeft)) {
            MappedAuth.Success -> {
                if (api.token == null) return AuthResult.Failure(AuthError.SERVER_ERROR)
                pendingLogin = null
                val name = reply.trueLogin ?: login
                if (pendingRemember) {
                    api.remember(name, reply.session)
                    savedLogin = name
                }
                val session = UserSession(name)
                currentSession = session
                AuthResult.Success(session)
            }
            MappedAuth.CodeRequired -> {
                pendingLogin = login
                AuthResult.CodeRequired
            }
            MappedAuth.TotpRequired -> {
                pendingLogin = login
                AuthResult.TotpRequired
            }
            is MappedAuth.Failure -> AuthResult.Failure(mapped.error, mapped.waitSeconds)
        }
    }
}

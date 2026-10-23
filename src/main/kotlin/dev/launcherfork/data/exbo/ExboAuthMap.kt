package dev.launcherfork.data.exbo

import dev.launcherfork.domain.AuthError

/** What an `/auth` `type` means for the login form. No I/O and no logging. */
internal sealed interface MappedAuth {
    data object Success : MappedAuth
    data object CodeRequired : MappedAuth
    data object TotpRequired : MappedAuth
    data class Failure(val error: AuthError, val waitSeconds: Int? = null) : MappedAuth
}

internal fun mapAuthType(type: String, timeLeft: Int? = null): MappedAuth = when (type) {
    "OK" -> MappedAuth.Success
    "MAIL_AUTH" -> MappedAuth.CodeRequired
    "GOOGLE_AUTH" -> MappedAuth.TotpRequired
    "WRONG_CREDENTIALS" -> MappedAuth.Failure(AuthError.WRONG_CREDENTIALS)
    "WRONG_CODE" -> MappedAuth.Failure(AuthError.WRONG_CODE)
    "WRONG_HWID" -> MappedAuth.Failure(AuthError.WRONG_HWID)
    "BANNED" -> MappedAuth.Failure(AuthError.BANNED, timeLeft)
    "BANNED_BOOTSTRAP", "FORCE_UPDATE" -> MappedAuth.Failure(AuthError.LAUNCHER_UPDATE)
    "NEW_PLACE" -> MappedAuth.Failure(AuthError.NEW_PLACE)
    "EMAIL_CONFIRMATION" -> MappedAuth.Failure(AuthError.EMAIL_CONFIRMATION)
    "PASSWORD_COMPROMISED" -> MappedAuth.Failure(AuthError.PASSWORD_COMPROMISED)
    else -> MappedAuth.Failure(AuthError.SERVER_ERROR)
}

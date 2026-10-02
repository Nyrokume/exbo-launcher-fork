package dev.launcherfork.ui

import dev.launcherfork.domain.AuthError
import dev.launcherfork.domain.UpdatePhase

/** Domain enums -> i18n keys. The domain never sees message keys. */
object Messages {
    fun key(error: AuthError): String = when (error) {
        AuthError.EMPTY_LOGIN -> "auth.error.empty_login"
        AuthError.EMPTY_PASSWORD -> "auth.error.empty_password"
        AuthError.EMPTY_CODE -> "auth.error.empty_code"
        AuthError.WRONG_CREDENTIALS -> "auth.error.wrong_credentials"
        AuthError.WRONG_CODE -> "auth.error.wrong_code"
        AuthError.NO_CONNECTION -> "auth.error.no_internet"
        AuthError.SERVER_ERROR -> "auth.error.server_error"
    }

    fun key(phase: UpdatePhase): String = when (phase) {
        UpdatePhase.CHECKING -> "auth.checking_for_updates"
        UpdatePhase.DOWNLOADING -> "auth.downloading_update"
        UpdatePhase.APPLYING -> "auth.applying_update"
    }
}

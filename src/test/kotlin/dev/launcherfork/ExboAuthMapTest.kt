package dev.launcherfork

import dev.launcherfork.data.exbo.mapAuthType
import dev.launcherfork.data.exbo.MappedAuth
import dev.launcherfork.domain.AuthError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExboAuthMapTest {
    @Test
    fun mapsTheRemainingAuthTypesOntoTheLoginForm() {
        assertEquals(MappedAuth.Failure(AuthError.BANNED, 282), mapAuthType("BANNED", 282))
        assertEquals(MappedAuth.Failure(AuthError.LAUNCHER_UPDATE), mapAuthType("BANNED_BOOTSTRAP"))
        assertEquals(MappedAuth.Failure(AuthError.LAUNCHER_UPDATE), mapAuthType("FORCE_UPDATE"))
        assertEquals(MappedAuth.Failure(AuthError.NEW_PLACE), mapAuthType("NEW_PLACE"))
        assertEquals(MappedAuth.Failure(AuthError.EMAIL_CONFIRMATION), mapAuthType("EMAIL_CONFIRMATION"))
        assertEquals(MappedAuth.Failure(AuthError.PASSWORD_COMPROMISED), mapAuthType("PASSWORD_COMPROMISED"))
        assertEquals(MappedAuth.Success, mapAuthType("OK"))
        assertEquals(MappedAuth.Failure(AuthError.SERVER_ERROR), mapAuthType("POW_BCRYPT"))
        assertEquals(MappedAuth.Failure(AuthError.SERVER_ERROR), mapAuthType("NEED_SECRET"))
    }
}

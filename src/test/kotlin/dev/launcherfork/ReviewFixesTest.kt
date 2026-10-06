package dev.launcherfork

import dev.launcherfork.data.mock.MockAuthService
import dev.launcherfork.domain.AuthResult
import dev.launcherfork.domain.RegistrationRequest
import dev.launcherfork.i18n.Strings
import dev.launcherfork.ui.components.CodeInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ReviewFixesTest {
    @Test
    fun registrationRequestToStringMasksPassword() {
        val r = RegistrationRequest("a@b.c", "nick", "S3cretPass!", LocalDate.of(2000, 1, 2), true)
        val s = r.toString()
        assertFalse("S3cretPass!" in s, s)
        assertTrue("password=***" in s, s)
        assertTrue("a@b.c" in s && "nick" in s && "2000-01-02" in s && "newsletter=true" in s, s)
    }

    @Test
    fun normalLoginClearsStalePendingFactor() {
        val auth = MockAuthService(0)
        assertEquals(AuthResult.CodeRequired, auth.login("2fa", "pw", false))
        assertTrue(auth.login("player", "pw", false) is AuthResult.Success)
        assertTrue(auth.confirmCode("123456") is AuthResult.Failure)
        assertEquals("player", auth.currentSession?.login)
    }

    @Test
    fun codeCellsHaveLocalizedAccessibleText() {
        val ru = Strings.bundle("ru"); val en = Strings.bundle("en")
        FxTestSupport.onFx {
            val c = CodeInput().apply { cellCount = 6 }
            assertEquals("Digit 1 of 6", c.cells[0].accessibleText)
            c.strings = ru
            assertEquals("Цифра 2 из 6", c.cells[1].accessibleText)
            c.strings = en
            assertEquals("Digit 6 of 6", c.cells[5].accessibleText)
            c.cellCount = 4
            assertEquals("Digit 3 of 4", c.cells[2].accessibleText)
        }
        val base = java.util.ResourceBundle.getBundle(Strings.BUNDLE, java.util.Locale.ROOT,
            java.util.ResourceBundle.Control.getNoFallbackControl(java.util.ResourceBundle.Control.FORMAT_PROPERTIES))
        assertTrue(base.containsKey("fork.code.cell"))
    }
}

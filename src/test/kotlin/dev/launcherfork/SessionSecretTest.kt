package dev.launcherfork

import dev.launcherfork.stalzone.ExboSessions
import dev.launcherfork.stalzone.MemoryRegistry
import dev.launcherfork.stalzone.PlainSessionSecrets
import dev.launcherfork.stalzone.RefusingSessionSecrets
import dev.launcherfork.stalzone.SealedSessionSecrets
import dev.launcherfork.stalzone.SessionFile
import dev.launcherfork.stalzone.SessionSecretException
import dev.launcherfork.stalzone.SessionSecrets
import dev.launcherfork.stalzone.StalzoneDesk
import dev.launcherfork.stalzone.WindowsDpapi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SessionSecretTest {
    private val seal = SealedSessionSecrets(
        seal = { bytes -> bytes.reversedArray() },
        open = { bytes -> bytes.reversedArray() },
    )

    @Test
    fun sealedFileHidesTheTokenAndApplyWritesTheRawSession(@TempDir dir: Path) {
        val file = dir.resolve("sessions.json")
        val registry = MemoryRegistry()
        registry.writeValues(
            ExboSessions.KEY_PATH,
            mapOf("EXBO_LOGIN" to "Nyrokume", "EXBO_SESSION" to "token-a", "EXBO_AUTO_AUTH" to "true"),
        )
        val desk = StalzoneDesk(registry, file, dir.resolve("jvm"), dir.resolve("options.json"), seal)
        desk.captureCurrent("main")
        val stored = Files.readString(file)
        assertFalse(stored.contains("token-a"))
        assertTrue(stored.contains("dpapi1:"))
        assertTrue(stored.contains("Nyrokume"))

        val opened = desk.sessions().single()
        assertEquals("token-a", opened.data.values.first { it.name == "EXBO_SESSION" }.value)
        registry.writeValues(ExboSessions.KEY_PATH, mapOf("EXBO_LOGIN" to "other", "EXBO_SESSION" to "other"))
        desk.apply(opened)
        val live = registry.readValues(ExboSessions.KEY_PATH)
        assertEquals("token-a", live["EXBO_SESSION"])
        assertEquals("Nyrokume", live["EXBO_LOGIN"])
        assertEquals("true", live["EXBO_AUTO_AUTH"])
    }

    @Test
    fun corruptBlobIsDroppedAndNotApplied(@TempDir dir: Path) {
        val file = dir.resolve("sessions.json")
        val session = ExboSessions.capture(
            "main",
            1,
            "t",
            mapOf("EXBO_LOGIN" to "Nyrokume", "EXBO_SESSION" to "token-a"),
        )
        SessionFile.save(file, listOf(session), seal)
        val broken = Files.readString(file).replace(Regex("dpapi1:[A-Za-z0-9+/=]+"), "dpapi1:!!!!")
        Files.writeString(file, broken)
        assertTrue(SessionFile.load(file, seal).isEmpty())
        assertTrue(SessionFile.load(file, PlainSessionSecrets).isEmpty())
        assertTrue(deskSessions(dir, file).isEmpty())
    }

    @Test
    fun legacyPlaintextStillOpensAndTheNextSaveSealsIt(@TempDir dir: Path) {
        val file = dir.resolve("sessions.json")
        val session = ExboSessions.capture(
            "main",
            1,
            "t",
            mapOf("EXBO_LOGIN" to "Nyrokume", "EXBO_SESSION" to "token-a"),
        )
        SessionFile.save(file, listOf(session), PlainSessionSecrets)
        assertTrue(Files.readString(file).contains("token-a"))
        assertEquals("token-a", SessionFile.load(file, seal).single().data.values.first { it.name == "EXBO_SESSION" }.value)
        SessionFile.save(file, SessionFile.load(file, seal), seal)
        assertFalse(Files.readString(file).contains("token-a"))
    }

    @Test
    fun refusingProtectorDoesNotWriteANewToken(@TempDir dir: Path) {
        val file = dir.resolve("sessions.json")
        val session = ExboSessions.capture("main", 1, "t", mapOf("EXBO_SESSION" to "token-a"))
        assertThrows<SessionSecretException> { SessionFile.save(file, listOf(session), RefusingSessionSecrets) }
        assertFalse(Files.exists(file))
        assertEquals("legacy", RefusingSessionSecrets.unprotect("legacy"))
    }

    @Test
    fun dpapiCommandDoesNotCarryTheSecret() {
        val secret = "super-secret-token"
        assertFalse(WindowsDpapi.script.contains(secret))
        assertTrue(WindowsDpapi.command().none { secret in it })
        assertTrue(WindowsDpapi.script.contains("ProtectedData"))
        assertTrue(WindowsDpapi.script.contains("CurrentUser"))
        assertTrue(WindowsDpapi.script.contains("EXBO_DPAPI_OP"))
    }

    @Test
    fun platformWithoutWindowsRefusesANewToken() {
        val os = System.getProperty("os.name").orEmpty()
        val protector = SessionSecrets.platform()
        if (os.contains("windows", ignoreCase = true)) {
            assertTrue(protector is SealedSessionSecrets)
        } else {
            assertThrows<SessionSecretException> { protector.protect("token-a") }
            assertEquals("already-there", protector.unprotect("already-there"))
        }
    }

    private fun deskSessions(dir: Path, file: Path) =
        StalzoneDesk(MemoryRegistry(), file, dir.resolve("jvm"), dir.resolve("options.json"), seal).sessions()
}

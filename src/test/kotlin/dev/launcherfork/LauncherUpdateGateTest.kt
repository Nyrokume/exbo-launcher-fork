package dev.launcherfork

import dev.launcherfork.data.exbo.ExboApi
import dev.launcherfork.data.exbo.ExboAuthService
import dev.launcherfork.data.exbo.LAUNCHER_UPDATE_UNSIGNED
import dev.launcherfork.data.exbo.LauncherUpdateDecision
import dev.launcherfork.data.exbo.LauncherUpdateOffer
import dev.launcherfork.data.exbo.LiveClientSource
import dev.launcherfork.data.exbo.LiveUpdateService
import dev.launcherfork.data.exbo.decideLauncherUpdate
import dev.launcherfork.data.exbo.exboSha256Hex
import dev.launcherfork.data.exbo.stageLauncherUpdate
import dev.launcherfork.domain.Progress
import dev.launcherfork.domain.ProgressListener
import dev.launcherfork.domain.RegisterError
import dev.launcherfork.domain.RegisterResult
import dev.launcherfork.domain.RegistrationRequest
import dev.launcherfork.domain.Server
import dev.launcherfork.domain.UpdatePhase
import dev.launcherfork.stalzone.MemoryRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Live launcher self-update stays closed until a release public key is pinned.
 * A matching digest, a large payload, or an HTTPS URL is not a signature.
 */
class LauncherUpdateGateTest {
    @Test
    fun noOfferFinishesSoLoginCanOpen(@TempDir dir: Path) {
        assertEquals(LauncherUpdateDecision.Current, decideLauncherUpdate(null))
        val listener = RecordingListener()
        LiveUpdateService(idleSource(dir), { error("no network") }, launcherOffer = null)
            .checkLauncherUpdate(listener)
        listener.await()
        assertTrue(listener.finished)
        assertNull(listener.failed)
        assertEquals(listOf("checking"), listener.phases)
    }

    @Test
    fun unsignedOfferIsRejectedAndNeverWritten(@TempDir dir: Path) {
        val payload = ByteArray(200_000) { 7 }
        val digest = exboSha256Hex(payload)
        val matching = offer(payload, sha256 = digest, signature = "deadbeef")
        val dest = dir.resolve("staged").resolve("launcher.bin")

        assertEquals(LauncherUpdateDecision.Rejected(LAUNCHER_UPDATE_UNSIGNED), decideLauncherUpdate(matching))
        assertFalse(stageLauncherUpdate(matching, dest))

        val cases = listOf(
            matching.copy(signature = " "),
            matching.copy(sha256 = " "),
            matching.copy(url = "http://updates.example/launcher.bin"),
            matching.copy(url = "https://user:secret@updates.example/launcher.bin"),
            matching.copy(sha256 = "00".repeat(32)),
        )
        cases.forEach { bad ->
            assertEquals(LauncherUpdateDecision.Rejected(LAUNCHER_UPDATE_UNSIGNED), decideLauncherUpdate(bad))
            assertFalse(stageLauncherUpdate(bad, dest))
        }
        assertFalse(Files.exists(dest))
        assertFalse(Files.exists(dest.parent))

        val listener = RecordingListener()
        LiveUpdateService(idleSource(dir), { error("no network") }, launcherOffer = matching)
            .checkLauncherUpdate(listener)
        listener.await()
        assertFalse(listener.finished)
        assertEquals(LAUNCHER_UPDATE_UNSIGNED, listener.failed)
    }

    @Test
    fun bundlesSayTheUpdateHasNoTrustedSignatureAndRegistrationIsUnavailable() {
        listOf("messages.properties", "messages_ru.properties", "messages_en.properties").forEach { file ->
            val props = Properties()
            javaClass.getResourceAsStream("/dev/launcherfork/ui/i18n/$file")!!
                .reader(Charset.forName("UTF-8"))
                .use(props::load)
            assertFalse(props.getProperty("auth.update.rejected").isNullOrBlank(), file)
            assertFalse(props.getProperty("fork.reg.error.unavailable").isNullOrBlank(), file)
        }
        val english = Properties()
        javaClass.getResourceAsStream("/dev/launcherfork/ui/i18n/messages_en.properties")!!
            .reader(Charset.forName("UTF-8"))
            .use(english::load)
        val rejected = english.getProperty("auth.update.rejected")
        val unavailable = english.getProperty("fork.reg.error.unavailable")
        assertTrue(rejected.contains("signature"), rejected)
        assertFalse(unavailable.contains("internal error", ignoreCase = true), unavailable)
        assertFalse(unavailable.contains("try again", ignoreCase = true), unavailable)
    }

    @Test
    fun liveRegisterReportsThatNoLauncherApiExists(@TempDir dir: Path) {
        val api = ExboApi(root = dir, registry = MemoryRegistry())
        val auth = ExboAuthService(api)
        val result = auth.register(
            RegistrationRequest(
                email = "player@example.com",
                username = "player",
                password = "not-sent",
                birthDate = LocalDate.of(2000, 1, 2),
                newsletter = false,
            ),
        )
        assertEquals(RegisterResult.Failure(RegisterError.NOT_SUPPORTED), result)
        assertNull(api.token)
    }

    private fun offer(bytes: ByteArray, sha256: String, signature: String) = LauncherUpdateOffer(
        url = "https://updates.example/launcher.bin",
        sha256 = sha256,
        signature = signature,
        bytes = bytes,
    )

    private fun idleSource(root: Path) = object : LiveClientSource {
        override fun directory(server: Server): Path? = null
        override fun remoteVersion(id: String): String? = null
        override fun httpSeeds(id: String): List<String> = emptyList()
        override fun exclusions(id: String): List<String> = emptyList()
        override fun catalog(id: String): ByteArray = ByteArray(0)
        override fun canDownload(server: Server): Boolean = false
        override fun runtime(): Path = root.resolve("runtime")
        override fun exboRoot(): Path = root
    }

    private class RecordingListener : ProgressListener {
        val phases = mutableListOf<String>()
        val done = CountDownLatch(1)
        var finished = false
        var failed: String? = null
        override fun onPhase(phase: UpdatePhase) {
            phases += phase.name.lowercase()
        }
        override fun onProgress(progress: Progress) = Unit
        override fun onFinished() {
            finished = true
            done.countDown()
        }
        override fun onFailed(reason: String) {
            failed = reason
            done.countDown()
        }
        fun await() {
            assertTrue(done.await(10, TimeUnit.SECONDS), "launcher update check did not finish")
        }
    }
}

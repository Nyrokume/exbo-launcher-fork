package dev.launcherfork

import dev.launcherfork.data.mock.MockAuthService
import dev.launcherfork.data.mock.MockData
import dev.launcherfork.data.mock.MockUpdateService
import dev.launcherfork.domain.AuthResult
import dev.launcherfork.domain.Progress
import dev.launcherfork.domain.ProgressListener
import dev.launcherfork.ui.components.WindowChrome
import dev.launcherfork.theme.Theme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MockServicesTest {
    @Test
    fun authFlows() {
        val auth = MockAuthService(delayMs = 0)
        assertTrue(auth.login("player", "x", false) is AuthResult.Success)
        assertEquals(AuthResult.Failure("auth.error.wrong_credentials"), auth.login("player", "wrong", false))
        assertEquals(AuthResult.CodeRequired, auth.login("2fa", "x", false))
        assertEquals(AuthResult.Failure("auth.error.wrong_code"), auth.confirmCode("12"))
        assertTrue(auth.confirmCode("123456") is AuthResult.Success)
    }

    @Test
    fun fakeDownloadCompletesAndMarksInstalled() {
        val updates = MockUpdateService(speed = 100.0)
        val server = MockData.servers.first { !it.installed }
        assertFalse(updates.isInstalled(server))
        val done = CountDownLatch(1)
        var last = 0.0
        updates.downloadClient(server, object : ProgressListener {
            override fun onProgress(progress: Progress) { last = progress.fraction }
            override fun onFinished() = done.countDown()
        })
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(1.0, last, 1e-9)
        assertTrue(updates.isInstalled(server))
    }

    @Test
    fun dragPolygonMatchesOriginalHeader() {
        val poly = Theme.mainDragPolygon(886.0)
        assertTrue(WindowChrome.isPointInPolygon(100.0, 10.0, poly))   // left header
        assertFalse(WindowChrome.isPointInPolygon(400.0, 28.0, poly))  // below the notch
        assertFalse(WindowChrome.isPointInPolygon(100.0, 100.0, poly)) // content
    }
}

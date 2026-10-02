package dev.launcherfork

import dev.launcherfork.data.mock.MockAuthService
import dev.launcherfork.data.mock.MockData
import dev.launcherfork.data.mock.MockUpdateService
import dev.launcherfork.domain.AuthError
import dev.launcherfork.domain.AuthResult
import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.GameListener
import dev.launcherfork.data.mock.MockGameLauncher
import dev.launcherfork.domain.UpdatePhase
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
        assertEquals(AuthResult.Failure(AuthError.WRONG_CREDENTIALS), auth.login("player", "wrong", false))
        assertEquals(AuthResult.CodeRequired, auth.login("2fa", "x", false))
        assertEquals(AuthResult.Failure(AuthError.WRONG_CODE), auth.confirmCode("12"))
        assertEquals(AuthResult.Failure(AuthError.WRONG_CODE), auth.confirmCode("1234567"))
        assertTrue(auth.confirmCode("123456") is AuthResult.Success)
    }

    @Test
    fun launcherUpdateReportsPhasesInOrderAndFinishes() {
        val updates = MockUpdateService(speed = 100.0)
        val phases = java.util.Collections.synchronizedList(mutableListOf<UpdatePhase>())
        val done = CountDownLatch(1)
        updates.checkLauncherUpdate(object : ProgressListener {
            override fun onPhase(phase: UpdatePhase) { phases += phase }
            override fun onProgress(progress: Progress) = Unit
            override fun onFinished() = done.countDown()
        })
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(listOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.APPLYING), phases.toList())
    }

    @Test
    fun cancelledDownloadDeliversNoMoreCallbacks() {
        val updates = MockUpdateService(speed = 1.0)
        val server = MockData.servers.first { !it.installed }
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val job: Cancellable = updates.downloadClient(server, object : ProgressListener {
            override fun onProgress(progress: Progress) { calls.incrementAndGet() }
            override fun onFinished() { calls.incrementAndGet() }
        })
        Thread.sleep(120)
        job.cancel()
        Thread.sleep(60) // a tick that was already running may still finish
        val after = calls.get()
        Thread.sleep(300)
        assertEquals(after, calls.get())
        assertTrue(updates.pausedProgress(server) > 0.0)
    }

    @Test
    fun mockGameLauncherStartsAndCanBeKilled() {
        val launcher = MockGameLauncher(speed = 100.0)
        val started = CountDownLatch(1)
        val exited = CountDownLatch(1)
        launcher.launch(MockData.servers.first(), object : GameListener {
            override fun onStarted() = started.countDown()
            override fun onExited() = exited.countDown()
        })
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertTrue(exited.await(5, TimeUnit.SECONDS))

        val killedStarted = CountDownLatch(1)
        launcher.launch(MockData.servers.first(), object : GameListener {
            override fun onStarted() = killedStarted.countDown()
            override fun onExited() = Unit
        }).cancel()
        assertFalse(killedStarted.await(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun mockImagesAreUrisThatResolve() {
        (MockData.servers.flatMap { listOf(it.iconUri, it.backgroundUri) } + MockData.news.map { it.imageUri }).forEach { uri ->
            uri.toURL().openStream().use { assertTrue(it.read() >= 0, "$uri is empty") }
        }
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

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
import dev.launcherfork.ui.components.ResizeEdge
import dev.launcherfork.theme.Theme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MockServicesTest {
    /** The only mock game is installed; downloads run on a not-installed copy of it. */
    private val notInstalled = MockData.servers.single().copy(id = "not-installed", installed = false)

    @Test
    fun authFlows() {
        val auth = MockAuthService(delayMs = 0)
        assertTrue(auth.login("player", "x", false) is AuthResult.Success)
        assertEquals(AuthResult.Failure(AuthError.WRONG_CREDENTIALS), auth.login("player", "wrong", false))
        assertEquals(AuthResult.CodeRequired, auth.login("2fa", "x", false))
        assertEquals(AuthResult.Failure(AuthError.WRONG_CODE), auth.confirmCode("12"))
        assertEquals(AuthResult.Failure(AuthError.WRONG_CODE), auth.confirmCode("1234567"))
        assertEquals(AuthResult.Failure(AuthError.SERVER_ERROR), auth.confirmTotp("123456"))   // not the TOTP step
        assertEquals(AuthResult.Failure(AuthError.WRONG_CODE), auth.confirmCode("123456"))     // 6 digits: too short now
        assertTrue(auth.confirmCode("268419249") is AuthResult.Success)

        assertEquals(AuthResult.TotpRequired, auth.login("totp", "x", false))
        assertEquals(AuthResult.Failure(AuthError.WRONG_CODE), auth.confirmTotp("000000"))     // the mock's wrong code
        assertEquals(AuthResult.Failure(AuthError.WRONG_CODE), auth.confirmTotp("12345"))
        assertEquals(AuthResult.Failure(AuthError.SERVER_ERROR), auth.confirmCode("123456"))   // not the e-mail step
        assertTrue(auth.confirmTotp("123456") is AuthResult.Success)
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
        val server = notInstalled
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
        MockData.servers.flatMap { listOf(it.iconUri, it.backgroundUri) }.forEach { uri ->
            uri.toURL().openStream().use { assertTrue(it.read() >= 0, "$uri is empty") }
        }
    }

    @Test
    fun fakeDownloadCompletesAndMarksInstalled() {
        val updates = MockUpdateService(speed = 100.0)
        val server = notInstalled
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
    fun resizeEdgesAreDetectedOnlyAtTheBorder() {
        val w = 1024.0
        val h = 640.0
        assertEquals(ResizeEdge.NW, ResizeEdge.at(2.0, 2.0, w, h))
        assertEquals(ResizeEdge.SE, ResizeEdge.at(w - 1, h - 1, w, h))
        assertEquals(ResizeEdge.E, ResizeEdge.at(w - 3, 300.0, w, h))
        assertEquals(ResizeEdge.N, ResizeEdge.at(500.0, 1.0, w, h))
        assertNull(ResizeEdge.at(500.0, 300.0, w, h)) // content
        assertNull(ResizeEdge.at(500.0, 14.0, w, h))  // title bar drag area, not an edge
    }

    @Test
    fun resizeZoneLeavesWindowButtonsAlone() {
        val w = Theme.WINDOW_WIDTH
        val h = Theme.WINDOW_HEIGHT
        // close button in the top-right corner (40x27), maximize left of it
        val close = javafx.geometry.BoundingBox(w - 40, 0.0, 40.0, 27.0)
        val maximize = javafx.geometry.BoundingBox(w - 80, 0.0, 40.0, 27.0)
        val buttons = listOf(close, maximize)
        assertEquals(ResizeEdge.NE, ResizeEdge.at(w - 2, 2.0, w, h)) // without exclusions the corner resizes
        assertNull(ResizeEdge.at(w - 2, 2.0, w, h, exclusions = buttons), "corner click must reach the close button")
        assertNull(ResizeEdge.at(w - 60, 1.0, w, h, exclusions = buttons), "top edge over maximize is a button click")
        assertNull(ResizeEdge.at(w - 3, 20.0, w, h, exclusions = buttons), "right edge next to close is a button click")
        // right edge below the buttons and top edge left of them still resize
        assertEquals(ResizeEdge.E, ResizeEdge.at(w - 3, 40.0, w, h, exclusions = buttons))
        assertEquals(ResizeEdge.N, ResizeEdge.at(500.0, 1.0, w, h, exclusions = buttons))
    }

    @Test
    fun resizeKeepsMinimumSizeAndOppositeEdge() {
        val start = javafx.geometry.Rectangle2D(100.0, 100.0, 1024.0, 640.0)
        val grown = ResizeEdge.SE.resize(start, 50.0, 30.0, 900.0, 580.0)
        assertEquals(1074.0, grown.width, 1e-9)
        assertEquals(670.0, grown.height, 1e-9)
        // dragging the left edge far to the right stops at the min width, the right edge stays put
        val shrunk = ResizeEdge.W.resize(start, 500.0, 0.0, 900.0, 580.0)
        assertEquals(900.0, shrunk.width, 1e-9)
        assertEquals(start.maxX, shrunk.maxX, 1e-9)
    }
}

package dev.launcherfork

import dev.launcherfork.data.mock.MockData
import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.ClientOperation
import dev.launcherfork.domain.GameLauncher
import dev.launcherfork.domain.GameListener
import dev.launcherfork.domain.Progress
import dev.launcherfork.domain.ProgressListener
import dev.launcherfork.domain.Server
import dev.launcherfork.domain.UpdateService
import dev.launcherfork.ui.state.ClientState
import dev.launcherfork.ui.state.ClientStateStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** State machine of [ClientStateStore] with hand-driven fakes and a direct (same-thread) UI dispatcher. */
class ClientStateStoreTest {
    private class Job(val listener: ProgressListener) {
        var cancelled = false
    }

    private class FakeUpdates : UpdateService {
        val jobs = mutableListOf<Job>()
        val installed = mutableSetOf<String>()
        var paused = 0.0
        override fun checkLauncherUpdate(listener: ProgressListener) = Cancellable {}
        override fun downloadClient(server: Server, listener: ProgressListener): Cancellable {
            val job = Job(listener)
            jobs += job
            return Cancellable { job.cancelled = true }
        }
        override fun isInstalled(server: Server) = server.id in installed
        override fun pausedProgress(server: Server) = paused
        val operations = mutableListOf<Pair<ClientOperation, Job>>()
        override fun runOperation(server: Server, operation: ClientOperation, listener: ProgressListener): Cancellable {
            val job = Job(listener)
            operations += operation to job
            return Cancellable { job.cancelled = true }
        }
    }

    private class Launch(val listener: GameListener) {
        var cancelled = false
    }

    private class FakeLauncher : GameLauncher {
        val launches = mutableListOf<Launch>()
        override fun launch(server: Server, listener: GameListener): Cancellable {
            val l = Launch(listener)
            launches += l
            return Cancellable { l.cancelled = true }
        }
    }

    private val updates = FakeUpdates()
    private val launcher = FakeLauncher()
    private val store = ClientStateStore(updates, launcher, ui = { it() })
    // the store keeps state per server id: three not-installed copies of the single mock game
    private val server = MockData.servers.single().copy(id = "a", installed = false)
    private val other = server.copy(id = "b")

    private fun state() = store.state(server).get()
    private fun progress(f: Double) = Progress(f, (f * server.clientSizeBytes).toLong(), server.clientSizeBytes, 1000)

    @Test
    fun initialStateFollowsUpdateService() {
        assertEquals(ClientState.NotInstalled, state())
        updates.installed += other.id
        assertEquals(ClientState.Ready, store.state(other).get())
        updates.paused = 0.4
        val third = server.copy(id = "c", clientSizeBytes = 1_000)
        assertEquals(ClientState.Paused(0.4, third.clientSizeBytes), store.state(third).get())
    }

    @Test
    fun downloadProgressThenReady() {
        store.primaryAction(server)
        assertTrue(state() is ClientState.Downloading)
        updates.jobs.single().listener.onProgress(progress(0.5))
        assertEquals(ClientState.Downloading(progress(0.5)), state())
        updates.jobs.single().listener.onFinished()
        assertEquals(ClientState.Ready, state())
    }

    @Test
    fun pauseCancelsJobAndResumeStartsNewOne() {
        store.primaryAction(server)
        updates.paused = 0.3
        store.primaryAction(server) // pause
        assertEquals(ClientState.Paused(0.3, server.clientSizeBytes), state())
        assertTrue(updates.jobs[0].cancelled)

        store.primaryAction(server) // continue
        assertEquals(2, updates.jobs.size)
        assertEquals(0.3, (state() as ClientState.Downloading).progress.fraction, 1e-9)
        updates.jobs[1].listener.onFinished()
        assertEquals(ClientState.Ready, state())
    }

    @Test
    fun lateCallbacksOfAPausedJobAreIgnored() {
        store.startDownload(server)
        val old = updates.jobs[0].listener
        store.pause(server)

        old.onProgress(progress(0.9))
        old.onFinished()
        assertTrue(state() is ClientState.Paused, "late callbacks after pause must not change state")

        store.startDownload(server)
        val fresh = updates.jobs[1].listener
        old.onFinished() // late onFinished of the first job after the restart
        old.onProgress(progress(0.99))
        assertTrue(state() is ClientState.Downloading, "old job must not mark the new download Ready")
        assertFalse(updates.jobs[1].cancelled, "old job must not remove/cancel the new one")

        fresh.onProgress(progress(0.5))
        assertEquals(ClientState.Downloading(progress(0.5)), state())
        fresh.onFinished()
        assertEquals(ClientState.Ready, state())
    }

    @Test
    fun launchStartingRunningExited() {
        updates.installed += server.id
        assertEquals(ClientState.Ready, state())
        store.primaryAction(server)
        assertEquals(ClientState.Starting, state())
        store.primaryAction(server) // ignored while starting
        assertEquals(1, launcher.launches.size)
        launcher.launches[0].listener.onStarted()
        assertEquals(ClientState.Running, state())
        launcher.launches[0].listener.onExited()
        assertEquals(ClientState.Ready, state())
    }

    @Test
    fun killStopsGameAndIgnoresItsLateExit() {
        updates.installed += server.id
        store.primaryAction(server)
        launcher.launches[0].listener.onStarted()
        store.primaryAction(server) // kill
        assertEquals(ClientState.Ready, state())
        assertTrue(launcher.launches[0].cancelled)

        store.primaryAction(server) // launch again
        launcher.launches[0].listener.onStarted() // stale
        launcher.launches[0].listener.onExited() // stale
        assertEquals(ClientState.Starting, state())
        launcher.launches[1].listener.onStarted()
        assertEquals(ClientState.Running, state())
    }

    @Test
    fun anyDownloadingIgnoresGivenServer() {
        store.startDownload(server)
        assertFalse(store.isAnyDownloading(except = server))
        assertTrue(store.isAnyDownloading(except = other))
    }

    @Test
    fun stateSurvivesAcrossObserversAndShutdownPausesDownloads() {
        store.startDownload(server)
        updates.jobs[0].listener.onProgress(progress(0.25))
        // a rebuilt scene asks for the same property again: same object, still downloading
        assertTrue(store.state(server) === store.state(server))
        assertEquals(ClientState.Downloading(progress(0.25)), state())

        store.shutdown()
        assertTrue(updates.jobs[0].cancelled)
        assertEquals(ClientState.Paused(0.25, server.clientSizeBytes), state())
        updates.jobs[0].listener.onFinished() // late
        assertTrue(state() is ClientState.Paused)
    }

    @Test
    fun verifyRunsAsBusyAndEndsReady() {
        updates.installed += server.id
        assertTrue(store.runOperation(server, ClientOperation.VERIFY))
        assertEquals(ClientState.Busy(ClientOperation.VERIFY, 0.0), state())
        updates.operations[0].second.listener.onProgress(progress(0.5))
        assertEquals(ClientState.Busy(ClientOperation.VERIFY, 0.5), state())
        store.primaryAction(server) // the CTA does nothing while busy
        assertEquals(ClientState.Busy(ClientOperation.VERIFY, 0.5), state())
        assertTrue(launcher.launches.isEmpty())
        updates.operations[0].second.listener.onFinished()
        assertEquals(ClientState.Ready, state())
    }

    @Test
    fun deleteEndsNotInstalledReinstallEndsReady() {
        updates.installed += server.id
        assertTrue(store.runOperation(server, ClientOperation.DELETE))
        updates.operations[0].second.listener.onFinished()
        assertEquals(ClientState.NotInstalled, state())
        assertFalse(store.runOperation(server, ClientOperation.REINSTALL), "nothing to reinstall")
        assertFalse(store.runOperation(server, ClientOperation.VERIFY), "nothing to verify")
        assertEquals(1, updates.operations.size)

        store.state(server).set(ClientState.Ready)
        assertTrue(store.runOperation(server, ClientOperation.REINSTALL))
        updates.operations[1].second.listener.onFinished()
        assertEquals(ClientState.Ready, state())
    }

    @Test
    fun verifyOfARunningGameStopsItFirst() {
        updates.installed += server.id
        store.primaryAction(server)
        launcher.launches[0].listener.onStarted()
        assertEquals(ClientState.Running, state())
        assertTrue(store.runOperation(server, ClientOperation.VERIFY))
        assertTrue(launcher.launches[0].cancelled, "the game is stopped before the check")
        launcher.launches[0].listener.onExited() // stale
        assertTrue(state() is ClientState.Busy)
        assertFalse(store.runOperation(server, ClientOperation.DELETE), "no second operation while busy")
    }

    @Test
    fun shutdownCancelsAnOperation() {
        updates.installed += server.id
        store.runOperation(server, ClientOperation.VERIFY)
        store.shutdown()
        assertTrue(updates.operations[0].second.cancelled)
        assertEquals(ClientState.Ready, state())
        updates.operations[0].second.listener.onFinished() // late
        assertEquals(ClientState.Ready, state())
    }
}

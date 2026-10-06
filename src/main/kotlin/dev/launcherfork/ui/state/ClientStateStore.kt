package dev.launcherfork.ui.state

import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.ClientOperation
import dev.launcherfork.domain.GameLauncher
import dev.launcherfork.domain.GameListener
import dev.launcherfork.domain.Progress
import dev.launcherfork.domain.ProgressListener
import dev.launcherfork.domain.Server
import dev.launcherfork.domain.UpdateService
import dev.launcherfork.ui.components.fx
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty

/** UI state of a game client (what the Play button and the server card show). */
sealed interface ClientState {
    data object NotInstalled : ClientState
    data class Downloading(val progress: Progress) : ClientState
    data class Paused(val fraction: Double, val totalBytes: Long) : ClientState
    data object Ready : ClientState
    data object Starting : ClientState
    data object Running : ClientState
    /** Verify / reinstall / delete from the game-actions menu is running; the Play button is disabled meanwhile. */
    data class Busy(val operation: ClientOperation, val fraction: Double) : ClientState
}

/**
 * Per-server state machine for Download / Pause / Continue / Play / Exit, shared by the status bar and the
 * server cards. Session-scoped: owned by AppContext, so it survives scene rebuilds (e.g. language switch).
 * Talks only to the [UpdateService] and [GameLauncher] interfaces.
 *
 * Every operation gets a generation number; callbacks from an older generation (a late onFinished after
 * pause + restart, a game exiting after it was killed) are ignored.
 *
 * @param ui dispatcher to the UI thread (FX in the app, direct call in tests). All state changes happen on it.
 */
class ClientStateStore(
    private val updates: UpdateService,
    private val launcher: GameLauncher,
    private val ui: (() -> Unit) -> Unit = ::fx,
) {
    private val states = HashMap<String, ObjectProperty<ClientState>>()
    private val jobs = HashMap<String, Cancellable>()
    private val generations = HashMap<String, Long>()

    fun state(server: Server): ObjectProperty<ClientState> = states.getOrPut(server.id) {
        val paused = updates.pausedProgress(server)
        SimpleObjectProperty(
            when {
                updates.isInstalled(server) -> ClientState.Ready
                paused > 0 -> ClientState.Paused(paused, server.clientSizeBytes)
                else -> ClientState.NotInstalled
            },
        )
    }

    fun isAnyDownloading(except: Server): Boolean =
        states.any { (id, p) -> id != except.id && p.get() is ClientState.Downloading }

    /** The big button: Download -> Pause -> Continue -> Play -> Exit. */
    fun primaryAction(server: Server) {
        when (state(server).get()) {
            ClientState.NotInstalled, is ClientState.Paused -> startDownload(server)
            is ClientState.Downloading -> pause(server)
            ClientState.Ready -> launch(server)
            ClientState.Running -> kill(server)
            ClientState.Starting, is ClientState.Busy -> Unit
        }
    }

    /**
     * Game-actions menu: verify / reinstall / delete. Allowed only where [GameActions] enables it (the UI asks for
     * confirmation first); a running game is stopped before a verification. Returns false if not started.
     */
    fun runOperation(server: Server, operation: ClientOperation): Boolean {
        val prop = state(server)
        val action = when (operation) {
            ClientOperation.VERIFY -> GameAction.VERIFY
            ClientOperation.REINSTALL -> GameAction.REINSTALL
            ClientOperation.DELETE -> GameAction.DELETE
        }
        if (!GameActions.isEnabled(action, prop.get())) return false
        val gen = nextGeneration(server) // also stops a running game / a paused download's leftovers
        prop.set(ClientState.Busy(operation, 0.0))
        jobs[server.id] = updates.runOperation(server, operation, object : ProgressListener {
            override fun onProgress(progress: Progress) = ui {
                if (isCurrent(server, gen)) prop.set(ClientState.Busy(operation, progress.fraction))
            }

            override fun onFinished() = ui {
                if (!isCurrent(server, gen)) return@ui
                jobs.remove(server.id)
                prop.set(if (operation == ClientOperation.DELETE) ClientState.NotInstalled else ClientState.Ready)
            }
        })
        return true
    }

    fun startDownload(server: Server) {
        val prop = state(server)
        val gen = nextGeneration(server)
        prop.set(ClientState.Downloading(Progress(updates.pausedProgress(server), 0, server.clientSizeBytes, 0)))
        jobs[server.id] = updates.downloadClient(server, object : ProgressListener {
            override fun onProgress(progress: Progress) = ui {
                if (isCurrent(server, gen)) prop.set(ClientState.Downloading(progress))
            }

            override fun onFinished() = ui {
                if (!isCurrent(server, gen)) return@ui
                jobs.remove(server.id)
                prop.set(ClientState.Ready)
            }
        })
    }

    fun pause(server: Server) {
        nextGeneration(server)
        state(server).set(ClientState.Paused(updates.pausedProgress(server), server.clientSizeBytes))
    }

    /** Starting -> Running (when the launcher reports the game is up) -> Ready (when it exits). */
    fun launch(server: Server) {
        val prop = state(server)
        val gen = nextGeneration(server)
        prop.set(ClientState.Starting)
        jobs[server.id] = launcher.launch(server, object : GameListener {
            override fun onStarted() = ui { if (isCurrent(server, gen)) prop.set(ClientState.Running) }

            override fun onExited() = ui {
                if (!isCurrent(server, gen)) return@ui
                jobs.remove(server.id)
                prop.set(ClientState.Ready)
            }
        })
    }

    fun kill(server: Server) {
        nextGeneration(server)
        state(server).set(ClientState.Ready)
    }

    /** Stops everything (logout / app exit). Downloads stay resumable as Paused. */
    fun shutdown() {
        states.keys.toList().forEach { id ->
            generations[id] = (generations[id] ?: 0) + 1
            jobs.remove(id)?.cancel()
        }
        states.forEach { (_, p) ->
            when (val s = p.get()) {
                is ClientState.Downloading -> p.set(ClientState.Paused(s.progress.fraction, s.progress.totalBytes))
                ClientState.Starting, ClientState.Running -> p.set(ClientState.Ready)
                // TODO(real update service): an interrupted verify / reinstall / delete is shown as Ready here (the mock
                //  has nothing half-done); the real service must report the client's actual state (Agent.Master, PR #8)
                is ClientState.Busy -> p.set(ClientState.Ready)
                else -> Unit
            }
        }
    }

    /** Invalidates callbacks of the previous operation for [server] and cancels it. */
    private fun nextGeneration(server: Server): Long {
        jobs.remove(server.id)?.cancel()
        val gen = (generations[server.id] ?: 0) + 1
        generations[server.id] = gen
        return gen
    }

    private fun isCurrent(server: Server, gen: Long) = generations[server.id] == gen
}

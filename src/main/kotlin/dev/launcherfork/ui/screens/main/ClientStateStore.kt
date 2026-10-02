package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.Progress
import dev.launcherfork.domain.ProgressListener
import dev.launcherfork.domain.Server
import dev.launcherfork.domain.UpdateService
import dev.launcherfork.ui.components.fx
import javafx.animation.PauseTransition
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.util.Duration

/** UI state of a game client (what the Play button and the server card show). */
sealed interface ClientState {
    data object NotInstalled : ClientState
    data class Downloading(val progress: Progress) : ClientState
    data class Paused(val fraction: Double, val totalBytes: Long) : ClientState
    data object Ready : ClientState
    data object Starting : ClientState
    data object Running : ClientState
}

/**
 * Per-server state machine for the fake Play/Download flow, shared by the status bar and the server cards.
 * Only talks to the [UpdateService] interface. Nothing is downloaded or launched.
 */
class ClientStateStore(private val updates: UpdateService) {
    private val states = HashMap<String, ObjectProperty<ClientState>>()
    private val jobs = HashMap<String, Cancellable>()

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
            ClientState.Ready -> fakeLaunch(server)
            ClientState.Running -> state(server).set(ClientState.Ready)
            ClientState.Starting -> Unit
        }
    }

    fun startDownload(server: Server) {
        val prop = state(server)
        jobs.remove(server.id)?.cancel()
        prop.set(ClientState.Downloading(Progress(updates.pausedProgress(server), 0, server.clientSizeBytes, 0)))
        jobs[server.id] = updates.downloadClient(server, object : ProgressListener {
            override fun onProgress(progress: Progress) = fx {
                if (prop.get() is ClientState.Downloading) prop.set(ClientState.Downloading(progress))
            }

            override fun onFinished() = fx {
                jobs.remove(server.id)
                prop.set(ClientState.Ready)
            }
        })
    }

    fun pause(server: Server) {
        jobs.remove(server.id)?.cancel()
        state(server).set(ClientState.Paused(updates.pausedProgress(server), server.clientSizeBytes))
    }

    /** Pretend to start the game: Starting -> Running for a few seconds -> Ready. */
    private fun fakeLaunch(server: Server) {
        val prop = state(server)
        prop.set(ClientState.Starting)
        PauseTransition(Duration.seconds(1.2)).apply {
            setOnFinished {
                prop.set(ClientState.Running)
                PauseTransition(Duration.seconds(5.0)).apply {
                    setOnFinished { if (prop.get() == ClientState.Running) prop.set(ClientState.Ready) }
                }.play()
            }
        }.play()
    }

    fun shutdown() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}

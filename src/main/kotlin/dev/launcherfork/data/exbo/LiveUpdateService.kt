package dev.launcherfork.data.exbo

import dev.launcherfork.data.mock.MockUpdateService
import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.ClientOperation
import dev.launcherfork.domain.Progress
import dev.launcherfork.domain.ProgressListener
import dev.launcherfork.domain.Server
import dev.launcherfork.domain.UpdatePhase
import dev.launcherfork.domain.ExboPaths
import dev.launcherfork.domain.UpdateService
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.isDirectory

/**
 * Launcher self-update check finishes at once. A catalog client with a version and web seeds is fetched with
 * the same torrent swarm the working launcher uses, into the directory [ExboClients] already maps (created
 * when it is missing). Reinstall stays disabled: this does not delete a client directory.
 */
class LiveUpdateService(
    private val api: ExboApi,
    private val servers: ExboServerRepository,
    speed: Double = 1.0,
) : UpdateService {
    private val idle = MockUpdateService(speed)
    private val pausedAt = ConcurrentHashMap<String, Double>()

    override fun checkLauncherUpdate(listener: ProgressListener): Cancellable {
        Thread({ listener.onFinished() }, "exbo-launcher-update").apply { isDaemon = true }.start()
        return Cancellable {}
    }

    override fun isInstalled(server: Server): Boolean = idle.isInstalled(server)

    override fun pausedProgress(server: Server): Double = pausedAt[server.id] ?: idle.pausedProgress(server)

    override fun runOperation(server: Server, operation: ClientOperation, listener: ProgressListener): Cancellable =
        idle.runOperation(server, operation, listener)

    override fun downloadClient(server: Server, listener: ProgressListener): Cancellable {
        val cancelled = AtomicBoolean(false)
        val worker = Thread({
            try {
                listener.onPhase(UpdatePhase.CHECKING)
                if (!servers.canDownload(server)) throw java.io.IOException("no catalog")
                val dir = servers.clientDirectory(server) ?: throw java.io.IOException("no client")
                if (!dir.isDirectory()) Files.createDirectories(dir)
                syncClientTorrent(
                    catalog = api.torrentData(server.id),
                    seeds = servers.httpSeeds(server.id),
                    exclusions = servers.exclusions(server.id),
                    clientDir = dir,
                    launcherJar = ExboPaths.roamingRoot().resolve("launcher").resolve("launcher.jar"),
                    cancelled = cancelled::get,
                ) { done, total, rate, checking ->
                    listener.onPhase(if (checking) UpdatePhase.CHECKING else UpdatePhase.DOWNLOADING)
                    val fraction = if (total <= 0) 0.0 else done.toDouble() / total
                    pausedAt[server.id] = fraction
                    listener.onProgress(Progress(fraction, done, total, rate))
                }
                if (cancelled.get()) return@Thread
                val version = servers.remoteVersion(server.id)
                if (!version.isNullOrBlank()) {
                    writeInstalledHash(server.id, version, ExboClients.runtimeDir())
                }
                pausedAt.remove(server.id)
                listener.onPhase(UpdatePhase.APPLYING)
                listener.onFinished()
            } catch (_: ExboSyncStopped) {
                // pause or shutdown cancelled the job; the store already moved on
            } catch (e: Throwable) {
                if (!cancelled.get()) {
                    System.err.println("[launcher-fork] client_update=${e.javaClass.simpleName}")
                    listener.onFailed(e.javaClass.simpleName)
                }
            }
        }, "exbo-client-update")
        worker.isDaemon = true
        worker.start()
        return Cancellable {
            cancelled.set(true)
            worker.interrupt()
        }
    }
}

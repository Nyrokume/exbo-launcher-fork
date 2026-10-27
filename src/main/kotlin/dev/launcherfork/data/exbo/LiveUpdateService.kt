package dev.launcherfork.data.exbo

import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.ClientOperation
import dev.launcherfork.domain.ExboPaths
import dev.launcherfork.domain.Progress
import dev.launcherfork.domain.ProgressListener
import dev.launcherfork.domain.Server
import dev.launcherfork.domain.UpdatePhase
import dev.launcherfork.domain.UpdateService
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.isDirectory

/** Catalog, client directory, and EXBO root the live maintenance operations read. */
internal interface LiveClientSource {
    fun directory(server: Server): Path?
    fun remoteVersion(id: String): String?
    fun httpSeeds(id: String): List<String>
    fun exclusions(id: String): List<String>
    fun catalog(id: String): ByteArray
    fun canDownload(server: Server): Boolean
    fun runtime(): Path
    fun exboRoot(): Path
}

internal class ExboLiveClientSource(
    private val api: ExboApi,
    private val servers: ExboServerRepository,
    private val runtime: Path = ExboClients.runtimeDir(),
    private val exboRoot: Path = ExboPaths.roamingRoot(),
) : LiveClientSource {
    override fun directory(server: Server): Path? = servers.clientDirectory(server)
    override fun remoteVersion(id: String): String? = servers.remoteVersion(id)
    override fun httpSeeds(id: String): List<String> = servers.httpSeeds(id)
    override fun exclusions(id: String): List<String> = servers.exclusions(id)
    override fun catalog(id: String): ByteArray = api.torrentData(id)
    override fun canDownload(server: Server): Boolean = servers.canDownload(server)
    override fun runtime(): Path = runtime
    override fun exboRoot(): Path = exboRoot
}

/**
 * Installed EXBO client maintenance. [isInstalled] reads the client directory and the catalog hash,
 * not [Server.installed]. Verify repairs files through [syncClientFiles]. Reinstall and delete remove
 * only that client directory. The main window asks before either one starts; an unsafe path is refused
 * here before anything is deleted. Launcher self-update installs nothing: a missing offer finishes
 * the check, and any offer is rejected until a release public key exists. Web seeds that reach this
 * service are the HTTPS list from the server catalog; [exboOpenSeed] refuses cleartext again.
 *
 * The speed argument matches the mock constructor so the app can pass one value through. Live work is not timed.
 */
class LiveUpdateService internal constructor(
    private val source: LiveClientSource,
    private val openSeed: (String) -> InputStream,
    /** Null in production: no signed launcher build is offered, so nothing is installed. */
    private val launcherOffer: LauncherUpdateOffer? = null,
    private val fetcher: ((Server, AtomicBoolean, ProgressListener) -> Unit)? = null,
) : UpdateService {
    @Suppress("UNUSED_PARAMETER")
    constructor(api: ExboApi, servers: ExboServerRepository, speed: Double = 1.0) : this(
        ExboLiveClientSource(api, servers),
        ::exboOpenSeed,
        null,
    )

    private val pausedAt = ConcurrentHashMap<String, Double>()

    /**
     * Checks [decideLauncherUpdate] and installs nothing.
     * With no pinned release key every offer is rejected and no file is written.
     * No offer is configured, so the check finishes and the login window can open.
     */
    override fun checkLauncherUpdate(listener: ProgressListener): Cancellable {
        val worker = Thread({
            listener.onPhase(UpdatePhase.CHECKING)
            when (val decision = decideLauncherUpdate(launcherOffer)) {
                LauncherUpdateDecision.Current -> listener.onFinished()
                is LauncherUpdateDecision.Rejected -> listener.onFailed(decision.code)
            }
        }, "exbo-launcher-update")
        worker.isDaemon = true
        worker.start()
        return Cancellable { worker.interrupt() }
    }

    override fun isInstalled(server: Server): Boolean {
        val dir = source.directory(server) ?: return false
        return diskInstall(dir, source.remoteVersion(server.id), readInstalledHash(server.id, source.runtime())).present
    }

    override fun pausedProgress(server: Server): Double = pausedAt[server.id] ?: 0.0

    override fun downloadClient(server: Server, listener: ProgressListener): Cancellable =
        start("client_update", listener) { cancelled -> fetchClient(server, cancelled, listener) }

    override fun runOperation(server: Server, operation: ClientOperation, listener: ProgressListener): Cancellable =
        start("client_${operation.name.lowercase()}", listener) { cancelled ->
            when (operation) {
                ClientOperation.VERIFY -> verify(server, cancelled, listener)
                ClientOperation.REINSTALL -> reinstall(server, cancelled, listener)
                ClientOperation.DELETE -> remove(server, cancelled, listener)
            }
        }

    private fun verify(server: Server, cancelled: AtomicBoolean, listener: ProgressListener) {
        val dir = source.directory(server) ?: throw java.io.IOException("no client")
        verifyClientFiles(
            directory = dir,
            catalog = source.catalog(server.id),
            seeds = source.httpSeeds(server.id),
            exclusions = source.exclusions(server.id),
            open = openSeed,
            cancelled = cancelled::get,
            onPhase = listener::onPhase,
            onProgress = listener::onProgress,
        )
        if (cancelled.get()) throw ExboSyncStopped()
        val version = source.remoteVersion(server.id)
        if (!version.isNullOrBlank()) writeInstalledHash(server.id, version, source.runtime())
        listener.onPhase(UpdatePhase.APPLYING)
        listener.onFinished()
    }

    private fun remove(server: Server, cancelled: AtomicBoolean, listener: ProgressListener) {
        val dir = source.directory(server) ?: throw java.io.IOException("no client")
        deleteClientDirectory(dir, source.exboRoot(), cancelled::get) { fraction ->
            listener.onProgress(Progress(fraction, 0, 1, 0))
        }
        if (cancelled.get()) throw ExboSyncStopped()
        pausedAt.remove(server.id)
        listener.onFinished()
    }

    private fun reinstall(server: Server, cancelled: AtomicBoolean, listener: ProgressListener) {
        val dir = source.directory(server) ?: throw java.io.IOException("no client")
        if (!source.canDownload(server)) throw java.io.IOException("no catalog")
        listener.onPhase(UpdatePhase.CHECKING)
        deleteClientDirectory(dir, source.exboRoot(), cancelled::get) { fraction ->
            listener.onProgress(Progress(fraction, 0, 1, 0))
        }
        if (cancelled.get()) throw ExboSyncStopped()
        pausedAt.remove(server.id)
        val fetch = fetcher
        if (fetch != null) fetch(server, cancelled, listener) else fetchClient(server, cancelled, listener)
    }

    private fun fetchClient(server: Server, cancelled: AtomicBoolean, listener: ProgressListener) {
        listener.onPhase(UpdatePhase.CHECKING)
        if (!source.canDownload(server)) throw java.io.IOException("no catalog")
        val dir = source.directory(server) ?: throw java.io.IOException("no client")
        if (!dir.isDirectory()) Files.createDirectories(dir)
        syncClientTorrent(
            catalog = source.catalog(server.id),
            seeds = source.httpSeeds(server.id),
            exclusions = source.exclusions(server.id),
            clientDir = dir,
            launcherJar = source.exboRoot().resolve("launcher").resolve("launcher.jar"),
            cancelled = cancelled::get,
        ) { done, total, rate, checking ->
            listener.onPhase(if (checking) UpdatePhase.CHECKING else UpdatePhase.DOWNLOADING)
            val fraction = if (total <= 0) 0.0 else done.toDouble() / total
            pausedAt[server.id] = fraction
            listener.onProgress(Progress(fraction, done, total, rate))
        }
        if (cancelled.get()) throw ExboSyncStopped()
        val version = source.remoteVersion(server.id)
        if (!version.isNullOrBlank()) writeInstalledHash(server.id, version, source.runtime())
        pausedAt.remove(server.id)
        listener.onPhase(UpdatePhase.APPLYING)
        listener.onFinished()
    }

    private fun start(name: String, listener: ProgressListener, block: (AtomicBoolean) -> Unit): Cancellable {
        val cancelled = AtomicBoolean(false)
        val worker = Thread({
            try {
                block(cancelled)
            } catch (_: ExboSyncStopped) {
                // pause or shutdown cancelled the job; the store already moved on
            } catch (e: Throwable) {
                if (!cancelled.get()) {
                    System.err.println("[launcher-fork] $name=${e.javaClass.simpleName}")
                    listener.onFailed(clientOperationFailure(e))
                }
            }
        }, "exbo-$name")
        worker.isDaemon = true
        worker.start()
        return Cancellable {
            cancelled.set(true)
            worker.interrupt()
        }
    }
}

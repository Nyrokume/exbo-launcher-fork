package dev.launcherfork.data.exbo

import dev.launcherfork.domain.Server
import dev.launcherfork.domain.ServerRepository
import dev.launcherfork.theme.Theme
import javafx.application.Platform
import java.io.IOException
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Server cards from the working launcher's `/listServers`, using the token from [ExboAuthService]. */
class ExboServerRepository(
    api: ExboApi,
    private val locateOverride: ((String) -> Path)? = null,
) : ServerRepository {
    private var api: ExboApi = api

    /** Follow a realm switch. The previous catalog is dropped. */
    internal fun use(next: ExboApi) {
        if (next === api) return
        api.onTokenCleared = null
        api = next
        api.onTokenCleared = { drop() }
        drop()
    }

    private fun locate(id: String): Path {
        locateOverride?.let { return it(id) }
        val root = api.dataRoot
        return ExboClients.directory(id, ExboClients.clientsFile(root), ExboClients.runtimeDir(root))
    }
    private val commands = ConcurrentHashMap<String, String>()
    private val versions = ConcurrentHashMap<String, String>()
    private val seeds = ConcurrentHashMap<String, List<String>>()
    private val exclusions = ConcurrentHashMap<String, List<String>>()

    @Volatile private var cached: List<Server> = emptyList()
    @Volatile private var cachedSource: String? = null
    private val refetchStarted = AtomicBoolean(false)

    init {
        api.onTokenCleared = { drop() }
    }

    override fun servers(): List<Server> {
        if (api.token == null) {
            drop()
            return emptyList()
        }
        val body = api.serverListJson
        if (body == null) {
            refetchOnce()
            return emptyList()
        }
        if (cachedSource != body) apply(body)
        return cached
    }

    fun runCommand(id: String): String? = commands[id]?.takeIf { it.isNotBlank() }

    fun remoteVersion(id: String): String? = versions[id]?.takeIf { it.isNotBlank() }

    fun httpSeeds(id: String): List<String> = httpsWebSeeds(seeds[id].orEmpty())

    fun exclusions(id: String): List<String> = exclusions[id].orEmpty()

    override fun canDownload(server: Server): Boolean =
        catalogClientDownloadable(versions[server.id], seeds[server.id].orEmpty())

    override fun needsClientUpdate(server: Server): Boolean {
        val state = diskInstall(
            locate(server.id),
            versions[server.id],
            readInstalledHash(server.id, ExboClients.runtimeDir(api.dataRoot)),
        )
        return state.present && !state.current
    }

    override fun clientDirectory(server: Server): Path? = locate(server.id)

    override fun close() = Unit

    private fun apply(body: String) {
        val catalog = parseServerCatalog(body, api.dataRoot)
        commands.clear()
        commands.putAll(catalog.runCommands)
        versions.clear()
        versions.putAll(catalog.versions)
        seeds.clear()
        seeds.putAll(catalog.httpSeeds)
        exclusions.clear()
        exclusions.putAll(catalog.exclusions)
        cached = catalog.servers.map { server ->
            val dir = locate(server.id)
            val installed = clientFilesPresent(dir)
            server.copy(iconUri = sidebarIcon(server), installed = installed)
        }
        cachedSource = body
    }

    private fun drop() {
        cached = emptyList()
        cachedSource = null
        commands.clear()
        versions.clear()
        seeds.clear()
        exclusions.clear()
        refetchStarted.set(false)
    }

    /** One refetch, and never on the FX thread. Without a token [ExboApi.refreshServers] returns immediately. */
    private fun refetchOnce() {
        if (!refetchStarted.compareAndSet(false, true)) return
        val task = Runnable {
            try {
                api.refreshServers()
            } catch (_: IOException) {
                // the cards stay empty until the next successful auth
            }
        }
        if (onFxThread()) {
            Thread(task, "exbo-server-list").apply { isDaemon = true }.start()
        } else {
            task.run()
        }
    }

    private fun onFxThread(): Boolean = try {
        Platform.isFxApplicationThread()
    } catch (_: IllegalStateException) {
        false
    }
}

/**
 * Each catalog client keeps the icon `/listServers` sent (STALZONE's radiation mark, the OTS atom, …)
 * when that icon is HTTPS. Cleartext art is not loaded. The stalker helmet is the main `stalcraft`
 * row when the response has no HTTPS icon, and any other row whose only icon was `http://`.
 */
internal fun sidebarIcon(server: Server): URI {
    if (exboHttpsUrl(server.iconUri.toString()) != null) return server.iconUri
    val scheme = server.iconUri.scheme?.lowercase()
    if (server.id == MAIN_CLIENT_ID || scheme == "http" || scheme == "https") return Theme.gameIconUri()
    return server.iconUri
}

private const val MAIN_CLIENT_ID = "stalcraft"

/**
 * Official `fetchCompoundState`: an installed client needs an update when `runtime/<id>.hash`
 * is missing or differs from the catalog version. `<client>/version` is a file inside the game
 * and is not this check. A client that is not on disk is not an update.
 */
/** A catalog client can be fetched when the server list gave it a version and at least one HTTPS web seed. */
internal fun catalogClientDownloadable(version: String?, seeds: List<String>): Boolean =
    !version.isNullOrBlank() && httpsWebSeeds(seeds).isNotEmpty()

internal fun installedClientNeedsUpdate(remoteVersion: String?, installedHash: String?, clientPresent: Boolean): Boolean {
    val remote = remoteVersion?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    if (!clientPresent) return false
    val local = installedHash?.trim()?.takeIf { it.isNotEmpty() } ?: return true
    return local != remote
}

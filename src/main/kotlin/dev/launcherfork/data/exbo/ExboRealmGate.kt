package dev.launcherfork.data.exbo

import dev.launcherfork.data.local.SettingsFile
import dev.launcherfork.domain.ExboRealm
import java.nio.file.Path

/** The live EXBO client the auth, catalog, and torrent decode are talking to. */
class ExboBackend(initial: ExboApi) {
    @Volatile var api: ExboApi = initial
        private set

    @Volatile var generation: Int = 0
        private set

    fun replace(next: ExboApi) {
        api.clearToken()
        api = next
        generation++
    }
}

/**
 * Switches the running launcher between the RU install and the EU (GLOBAL) install.
 * A realm that is not installed is refused, so the current session stays where it was.
 * The choice is written to the fork's settings file, not into either official tree.
 */
class ExboRealmGate(
    private val backend: ExboBackend,
    private val auth: ExboAuthService,
    private val servers: ExboServerRepository,
    private val settingsFile: Path,
    private val present: (ExboRealm) -> Boolean = { ExboApi(it).installed() },
    private val open: (ExboRealm) -> ExboApi = { ExboApi(it) },
) {
    val current: ExboRealm get() = backend.api.realm

    fun installed(realm: ExboRealm): Boolean = present(realm)

    /** False when [realm] has no launcher install. The previous realm stays current. */
    fun select(realm: ExboRealm): Boolean {
        if (!present(realm)) return false
        if (realm == current) return true
        val next = open(realm)
        backend.replace(next)
        servers.use(next)
        auth.onRealmChanged()
        SettingsFile.write(settingsFile, ExboRealm.SETTINGS_KEY, realm.wireName)
        return true
    }
}

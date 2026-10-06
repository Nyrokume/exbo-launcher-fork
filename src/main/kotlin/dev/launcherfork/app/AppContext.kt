package dev.launcherfork.app

import dev.launcherfork.data.mock.InMemorySettingsStore
import dev.launcherfork.data.mock.MockAuthService
import dev.launcherfork.data.mock.MockGameLauncher
import dev.launcherfork.data.mock.MockServerRepository
import dev.launcherfork.data.mock.MockSystemInfo
import dev.launcherfork.data.mock.MockUpdateService
import dev.launcherfork.domain.FolderOpener
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.LinkOpener
import dev.launcherfork.domain.Services
import dev.launcherfork.ui.state.ClientStateStore

/**
 * The ONE place where implementations are chosen (manual DI) and where session-scoped state lives.
 * To plug a real backend later, build [Services] with other implementations here; the UI only sees interfaces.
 */
class AppContext(val services: Services, val devMode: Boolean = false) {
    /** Download/launch state of every client. Lives as long as the app, not as long as a scene. */
    val clientStates: ClientStateStore by lazy { ClientStateStore(services.updates, services.launcher) }

    /** Stops running fake downloads/launches (logout, app exit) and the mock online-count drift. */
    fun shutdown() {
        clientStates.shutdown()
        services.servers.close()
    }

    companion object {
        fun mock(
            language: String = "ru",
            speed: Double = 1.0,
            authDelayMs: Long = 500,
            devMode: Boolean = false,
            /** LauncherApp passes the real browser opener (HostServices); tests pass a recording fake. */
            links: LinkOpener = LinkOpener { url -> System.err.println("[launcher-fork] open link (no browser here): $url") },
            /** LauncherApp passes the real file-manager opener (HostServices); tests pass a recording fake. */
            folders: FolderOpener = FolderOpener { dir -> System.err.println("[launcher-fork] open folder (no file manager here): $dir") },
            /** > 0: the mock online count drifts every [onlineTickMs] ms (live app); 0 = fixed 1284 (tests, tour). */
            onlineTickMs: Long = 0,
        ): AppContext = AppContext(
            Services(
                auth = MockAuthService(authDelayMs),
                servers = MockServerRepository(tickMs = onlineTickMs),
                updates = MockUpdateService(speed),
                launcher = MockGameLauncher(),
                settings = InMemorySettingsStore(LauncherSettings(language = language)),
                system = MockSystemInfo(),
                links = links,
                folders = folders,
            ),
            devMode,
        )
    }
}

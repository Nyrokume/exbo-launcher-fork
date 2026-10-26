package dev.launcherfork.app

import dev.launcherfork.data.exbo.ExboApi
import dev.launcherfork.data.exbo.ExboAuthService
import dev.launcherfork.data.exbo.ExboClients
import dev.launcherfork.data.exbo.ExboServerRepository
import dev.launcherfork.data.exbo.ExboSettingsStore
import dev.launcherfork.data.exbo.HostSystemInfo
import dev.launcherfork.data.exbo.InstalledGameLauncher
import dev.launcherfork.data.exbo.LiveUpdateService
import dev.launcherfork.domain.ExboPaths
import dev.launcherfork.domain.ExitMode
import dev.launcherfork.domain.RamOptions
import dev.launcherfork.stalzone.MemoryRegistry
import dev.launcherfork.stalzone.StalzoneDesk
import dev.launcherfork.stalzone.StalzonePaths
import dev.launcherfork.stalzone.WindowsRegistry
import dev.launcherfork.data.mock.InMemorySettingsStore
import dev.launcherfork.data.mock.MockAuthService
import dev.launcherfork.data.mock.MockGameLauncher
import dev.launcherfork.data.mock.MockSecondFactor
import dev.launcherfork.data.mock.MockServerRepository
import dev.launcherfork.data.mock.MockSystemInfo
import dev.launcherfork.data.mock.MockUpdateService
import dev.launcherfork.domain.FolderOpener
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.LinkOpener
import dev.launcherfork.domain.Services
import dev.launcherfork.ui.state.ClientStateStore
import javafx.application.Platform
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.random.Random

/**
 * The ONE place where implementations are chosen (manual DI) and where session-scoped state lives.
 * [mock] is the showcase and the tests. [live] is the installed EXBO auth and server list. The UI only sees interfaces.
 */
class AppContext(
    val services: Services,
    val devMode: Boolean = false,
    /** JVM / accounts / sensitivity. Mock and tests get an in-memory registry and a temp directory. */
    val desk: StalzoneDesk = memoryDesk(),
    /** Installed EXBO auth, client paths and settings. Showcase and tests stay on [mock]. */
    val liveBackend: Boolean = false,
) {
    /** Download/launch state of every client. Lives as long as the app, not as long as a scene. */
    val clientStates: ClientStateStore by lazy {
        ClientStateStore(
            services.updates,
            services.launcher,
            startWhenDownloaded = { liveBackend && services.settings.load().startOnDownloaded },
            exitMode = { if (liveBackend) services.settings.load().exitMode else ExitMode.NEVER },
            exitApp = { Platform.exit() },
        )
    }

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
            /** Second factor after a password login; LauncherApp passes -Dlauncher.mock2fa (default random). */
            mock2fa: MockSecondFactor = MockSecondFactor.NONE,
            random: Random = Random.Default,
        ): AppContext = AppContext(
            Services(
                auth = MockAuthService(authDelayMs, mock2fa, random),
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

        /**
         * Installed EXBO auth, server list, game process, and client download.
         * A catalog client is fetched into the directory the official launcher already maps. Reinstall stays disabled.
         */
        fun live(
            language: String = "ru",
            speed: Double = 1.0,
            devMode: Boolean = false,
            links: LinkOpener = LinkOpener { url -> System.err.println("[launcher-fork] open link (no browser here): $url") },
            folders: FolderOpener = FolderOpener { dir -> System.err.println("[launcher-fork] open folder (no file manager here): $dir") },
            api: ExboApi = ExboApi(),
            settingsFile: Path = Path.of(System.getProperty("user.home"), ".launcher-fork", "settings.properties"),
        ): AppContext {
            val desk = StalzoneDesk(
                WindowsRegistry(),
                StalzonePaths.sessionsFile(),
                StalzonePaths.jvmConfigsDir(),
                StalzonePaths.optionsFile(),
            )
            val auth = ExboAuthService(api)
            val system = HostSystemInfo()
            val settings = ExboSettingsStore(
                initial = LauncherSettings(language = language),
                desk = desk,
                file = settingsFile,
                exboJson = ExboPaths.roamingRoot().resolve("runtime").resolve("settings.json"),
                totalRamMb = system.totalRamMb(),
            )
            val servers = ExboServerRepository(api)
            return AppContext(
                Services(
                    auth = auth,
                    servers = servers,
                    updates = LiveUpdateService(api, servers, speed),
                    launcher = InstalledGameLauncher(
                        login = { auth.currentSession?.login },
                        token = { api.token },
                        ramMb = {
                            val loaded = settings.load()
                            val gb = RamOptions.selectedGb(loaded, system.totalRamMb())
                            if (loaded.ramMb != null) runCatching { desk.syncHeap(gb) }
                            gb * RamOptions.MB_PER_GB
                        },
                        joinGame = { name -> api.joinGame(name) },
                        runCommand = { id -> servers.runCommand(id) },
                        clientDir = { id -> ExboClients.directory(id).takeIf { path -> path.isDirectory() } },
                    ),
                    settings = settings,
                    system = system,
                    links = links,
                    folders = folders,
                ),
                devMode,
                desk,
                liveBackend = true,
            )
        }

        /** Shared desk for a UI context that was not given [AppContext.desk]. Never HKCU. */
        private val sharedMemoryDesk by lazy { memoryDesk() }

        fun fallbackDesk(): StalzoneDesk = sharedMemoryDesk

        /** In-memory registry and a private temp directory. Never HKCU and never the real EXBO files. */
        fun memoryDesk(): StalzoneDesk {
            val dir = Files.createTempDirectory("launcher-fork-desk")
            return StalzoneDesk(
                MemoryRegistry(),
                dir.resolve("sessions.json"),
                dir.resolve("jvm"),
                dir.resolve("options.json"),
            )
        }
    }
}

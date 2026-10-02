package dev.launcherfork.app

import dev.launcherfork.data.mock.InMemorySettingsStore
import dev.launcherfork.data.mock.MockAuthService
import dev.launcherfork.data.mock.MockGameLauncher
import dev.launcherfork.data.mock.MockNewsRepository
import dev.launcherfork.data.mock.MockServerRepository
import dev.launcherfork.data.mock.MockUpdateService
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.Services
import dev.launcherfork.ui.state.ClientStateStore

/**
 * The ONE place where implementations are chosen (manual DI) and where session-scoped state lives.
 * To plug a real backend later, build [Services] with other implementations here; the UI only sees interfaces.
 */
class AppContext(val services: Services, val devMode: Boolean = false) {
    /** Download/launch state of every client. Lives as long as the app, not as long as a scene. */
    val clientStates: ClientStateStore by lazy { ClientStateStore(services.updates, services.launcher) }

    /** Stops running fake downloads/launches (logout, app exit). */
    fun shutdown() = clientStates.shutdown()

    companion object {
        fun mock(
            language: String = "ru",
            speed: Double = 1.0,
            authDelayMs: Long = 500,
            devMode: Boolean = false,
        ): AppContext = AppContext(
            Services(
                auth = MockAuthService(authDelayMs),
                servers = MockServerRepository(),
                news = MockNewsRepository(),
                updates = MockUpdateService(speed),
                launcher = MockGameLauncher(),
                settings = InMemorySettingsStore(LauncherSettings(language = language)),
            ),
            devMode,
        )
    }
}

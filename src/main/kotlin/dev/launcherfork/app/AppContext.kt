package dev.launcherfork.app

import dev.launcherfork.data.mock.InMemorySettingsStore
import dev.launcherfork.data.mock.MockAuthService
import dev.launcherfork.data.mock.MockNewsRepository
import dev.launcherfork.data.mock.MockServerRepository
import dev.launcherfork.data.mock.MockUpdateService
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.Services

/**
 * The ONE place where implementations are chosen (manual DI).
 * To plug a real backend later, build [Services] with other implementations here; the UI only sees interfaces.
 */
class AppContext(val services: Services) {
    companion object {
        fun mock(language: String = "ru", speed: Double = 1.0, authDelayMs: Long = 500): AppContext = AppContext(
            Services(
                auth = MockAuthService(authDelayMs),
                servers = MockServerRepository(),
                news = MockNewsRepository(),
                updates = MockUpdateService(speed),
                settings = InMemorySettingsStore(LauncherSettings(language = language)),
            ),
        )
    }
}

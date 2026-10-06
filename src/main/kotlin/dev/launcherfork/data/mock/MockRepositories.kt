package dev.launcherfork.data.mock

import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.Server
import dev.launcherfork.domain.ServerRepository
import dev.launcherfork.domain.SettingsStore

class MockServerRepository(private val data: List<Server> = MockData.servers) : ServerRepository {
    override fun servers(): List<Server> = data
}

/** Settings live only in memory for the session (nothing is written to disk / registry). */
class InMemorySettingsStore(initial: LauncherSettings = LauncherSettings()) : SettingsStore {
    @Volatile private var current = initial
    override fun load(): LauncherSettings = current
    override fun save(settings: LauncherSettings) {
        current = settings
    }
}

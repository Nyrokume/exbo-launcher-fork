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

/** Fixed amount of RAM (16 GB by default), so settings and screenshots look the same on every machine. */
class MockSystemInfo(private val totalMb: Long = 16L * 1024) : dev.launcherfork.domain.SystemInfo {
    override fun totalRamMb(): Long = totalMb
}

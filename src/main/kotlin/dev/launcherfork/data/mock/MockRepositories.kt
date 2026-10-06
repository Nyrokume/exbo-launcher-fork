package dev.launcherfork.data.mock

import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.Server
import dev.launcherfork.domain.ServerRepository
import dev.launcherfork.domain.SettingsStore
import kotlin.random.Random

/**
 * Games from [MockData]. The online count starts at [Server.onlineCount] (STALZONE: 1284) and can be changed with
 * [setOnline]; watchers get every change. With [tickMs] > 0 it also drifts by a few players every [tickMs] ms on the
 * mock timer thread (live preview); 0 keeps it fixed (tests, screenshot tour).
 */
class MockServerRepository(
    private val data: List<Server> = MockData.servers,
    private val tickMs: Long = 0,
    private val random: Random = Random.Default,
) : ServerRepository {
    private val lock = Any()
    private val counts = HashMap<String, Int?>()
    private val watchers = HashMap<String, MutableList<(Int?) -> Unit>>()
    private var ticker: Cancellable? = null

    override fun servers(): List<Server> = data

    /** Current mock count of [serverId]; null = unknown / offline. */
    fun online(serverId: String): Int? = synchronized(lock) { current(serverId, null) }

    /** Sets the mock count (null or negative = offline) and notifies the watchers on the calling thread. */
    fun setOnline(serverId: String, count: Int?) {
        val value = count?.takeIf { it >= 0 }
        val targets = synchronized(lock) {
            counts[serverId] = value
            watchers[serverId].orEmpty().toList()
        }
        targets.forEach { it(value) }
    }

    override fun watchOnline(server: Server, listener: (Int?) -> Unit): Cancellable {
        val now = synchronized(lock) {
            watchers.getOrPut(server.id) { mutableListOf() } += listener
            if (tickMs > 0 && ticker == null) ticker = MockScheduler.repeatUntilDone(tickMs, tickMs) { tick(); false }
            current(server.id, server)
        }
        listener(now)
        return Cancellable { synchronized(lock) { watchers[server.id]?.remove(listener) } }
    }

    /** Stops the drift timer (app exit). */
    fun shutdown() = synchronized(lock) {
        ticker?.cancel()
        ticker = null
    }

    private fun current(id: String, server: Server?): Int? =
        if (id in counts) counts[id] else (server ?: data.firstOrNull { it.id == id })?.onlineCount

    /** A few players join or leave the watched online servers. */
    private fun tick() {
        val ids = synchronized(lock) { watchers.filterValues { it.isNotEmpty() }.keys.toList() }
        ids.forEach { id ->
            val n = online(id) ?: return@forEach
            setOnline(id, maxOf(0, n + random.nextInt(-6, 7)))
        }
    }
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

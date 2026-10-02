package dev.launcherfork.data.mock

import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.Progress
import dev.launcherfork.domain.ProgressListener
import dev.launcherfork.domain.Server
import dev.launcherfork.domain.UpdateService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Pure timer-driven fake progress. No files, no torrents, no hashing.
 * [speed] > 1 makes everything faster (used by the screenshot tour / tests).
 */
class MockUpdateService(private val speed: Double = 1.0) : UpdateService {
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "mock-updates").apply { isDaemon = true }
    }
    private val installed = ConcurrentHashMap<String, Boolean>()
    private val partial = ConcurrentHashMap<String, Double>()

    override fun isInstalled(server: Server): Boolean = installed[server.id] ?: server.installed

    override fun checkLauncherUpdate(listener: ProgressListener): Cancellable {
        val total = 4_700_000L
        val tickMs = 50L
        val steps = (2_000 / speed / tickMs).toInt().coerceAtLeast(1)
        var tick = -(800 / speed / tickMs).toInt() // "checking" phase
        listener.onStatus("auth.checking_for_updates")
        var future: ScheduledFuture<*>? = null
        future = scheduler.scheduleAtFixedRate({
            tick++
            when {
                tick < 0 -> Unit
                tick == 0 -> listener.onStatus("auth.downloading_update")
                tick <= steps -> {
                    val f = tick.toDouble() / steps
                    listener.onProgress(Progress(f, (total * f).toLong(), total, 2_300_000))
                }
                tick == steps + 1 -> listener.onStatus("auth.applying_update")
                tick > steps + (600 / speed / tickMs) -> {
                    future?.cancel(false)
                    listener.onFinished()
                }
            }
        }, 0, tickMs, TimeUnit.MILLISECONDS)
        return Cancellable { future?.cancel(false) }
    }

    override fun downloadClient(server: Server, listener: ProgressListener): Cancellable {
        val total = server.clientSizeBytes
        val tickMs = 50L
        val durationMs = 6_000 / speed
        val delta = tickMs / durationMs
        var f = partial[server.id] ?: 0.0
        listener.onStatus("main.status.updating")
        var future: ScheduledFuture<*>? = null
        future = scheduler.scheduleAtFixedRate({
            f = (f + delta).coerceAtMost(1.0)
            partial[server.id] = f
            val jitter = 0.85 + 0.3 * Math.random()
            listener.onProgress(Progress(f, (total * f).toLong(), total, (total / (durationMs / 1000) * jitter).toLong()))
            if (f >= 1.0) {
                future?.cancel(false)
                installed[server.id] = true
                partial.remove(server.id)
                listener.onFinished()
            }
        }, tickMs, tickMs, TimeUnit.MILLISECONDS)
        return Cancellable { future?.cancel(false) }
    }

    override fun pausedProgress(server: Server): Double = partial[server.id] ?: 0.0
}

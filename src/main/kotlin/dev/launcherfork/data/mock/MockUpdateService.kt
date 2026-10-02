package dev.launcherfork.data.mock

import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.Progress
import dev.launcherfork.domain.ProgressListener
import dev.launcherfork.domain.Server
import dev.launcherfork.domain.UpdatePhase
import dev.launcherfork.domain.UpdateService
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure timer-driven fake progress. No files, no torrents, no hashing.
 * [speed] > 1 makes everything faster (used by the screenshot tour / tests).
 */
class MockUpdateService(private val speed: Double = 1.0) : UpdateService {
    private val installed = ConcurrentHashMap<String, Boolean>()
    private val partial = ConcurrentHashMap<String, Double>()

    override fun isInstalled(server: Server): Boolean = installed[server.id] ?: server.installed

    override fun pausedProgress(server: Server): Double = partial[server.id] ?: 0.0

    override fun checkLauncherUpdate(listener: ProgressListener): Cancellable {
        val total = 4_700_000L
        val checkingTicks = ticks(800)
        val downloadTicks = ticks(2_000)
        val applyingTicks = ticks(600)
        var tick = 0
        listener.onPhase(UpdatePhase.CHECKING)
        return MockScheduler.repeatUntilDone(TICK_MS, TICK_MS) {
            tick++
            val t = tick - checkingTicks
            when {
                t < 0 -> false
                t == 0 -> { listener.onPhase(UpdatePhase.DOWNLOADING); false }
                t <= downloadTicks -> {
                    val f = t.toDouble() / downloadTicks
                    listener.onProgress(Progress(f, (total * f).toLong(), total, 2_300_000))
                    false
                }
                t == downloadTicks + 1 -> { listener.onPhase(UpdatePhase.APPLYING); false }
                t > downloadTicks + applyingTicks -> { listener.onFinished(); true }
                else -> false
            }
        }
    }

    override fun downloadClient(server: Server, listener: ProgressListener): Cancellable {
        val total = server.clientSizeBytes
        val durationMs = 6_000 / speed
        val delta = TICK_MS / durationMs
        var f = partial[server.id] ?: 0.0
        listener.onPhase(UpdatePhase.DOWNLOADING)
        return MockScheduler.repeatUntilDone(TICK_MS, TICK_MS) {
            f = (f + delta).coerceAtMost(1.0)
            partial[server.id] = f
            val jitter = 0.85 + 0.3 * Math.random()
            listener.onProgress(Progress(f, (total * f).toLong(), total, (total / (durationMs / 1000) * jitter).toLong()))
            if (f >= 1.0) {
                installed[server.id] = true
                partial.remove(server.id)
                listener.onFinished()
                true
            } else {
                false
            }
        }
    }

    private fun ticks(ms: Int): Int = (ms / speed / TICK_MS).toInt().coerceAtLeast(1)

    private companion object {
        const val TICK_MS = 50L
    }
}

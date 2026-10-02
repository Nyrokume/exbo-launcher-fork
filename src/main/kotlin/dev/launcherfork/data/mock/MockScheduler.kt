package dev.launcherfork.data.mock

import dev.launcherfork.domain.Cancellable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Shared daemon timer thread for the fake services. */
internal object MockScheduler {
    val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "mock-services").apply { isDaemon = true }
    }

    /**
     * Runs [tick] every [periodMs] until it returns true or the returned handle is cancelled.
     * Safe even if the first tick runs before scheduleAtFixedRate() returns: completion is tracked by a flag,
     * and the future is cancelled as soon as it is known.
     */
    fun repeatUntilDone(initialDelayMs: Long, periodMs: Long, tick: () -> Boolean): Cancellable {
        val done = AtomicBoolean(false)
        val future = AtomicReference<ScheduledFuture<*>?>()
        val f = executor.scheduleAtFixedRate({
            if (done.get()) return@scheduleAtFixedRate
            if (tick()) {
                done.set(true)
                future.get()?.cancel(false)
            }
        }, initialDelayMs, periodMs, TimeUnit.MILLISECONDS)
        future.set(f)
        if (done.get()) f.cancel(false) // finished before we got the handle
        return Cancellable {
            done.set(true)
            f.cancel(false)
        }
    }

    /** One-shot delayed action; cancelling prevents it from running. */
    fun after(delayMs: Long, action: () -> Unit): Cancellable {
        val done = AtomicBoolean(false)
        val f = executor.schedule({ if (done.compareAndSet(false, true)) action() }, delayMs, TimeUnit.MILLISECONDS)
        return Cancellable {
            done.set(true)
            f.cancel(false)
        }
    }
}

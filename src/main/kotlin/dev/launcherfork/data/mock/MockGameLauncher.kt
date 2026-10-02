package dev.launcherfork.data.mock

import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.GameLauncher
import dev.launcherfork.domain.GameListener
import dev.launcherfork.domain.Server
import java.util.concurrent.atomic.AtomicReference

/** Pretends to start the game: "started" after ~1.2 s, "exited" ~5 s later. No processes are created. */
class MockGameLauncher(private val speed: Double = 1.0) : GameLauncher {
    override fun launch(server: Server, listener: GameListener): Cancellable {
        val exit = AtomicReference<Cancellable?>()
        val start = MockScheduler.after((1_200 / speed).toLong()) {
            listener.onStarted()
            exit.set(MockScheduler.after((5_000 / speed).toLong()) { listener.onExited() })
        }
        return Cancellable {
            start.cancel()
            exit.get()?.cancel()
        }
    }
}

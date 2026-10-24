package dev.launcherfork.data.exbo

import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.GameLauncher
import dev.launcherfork.domain.GameListener
import dev.launcherfork.domain.Server
import dev.launcherfork.stalzone.StalzonePaths
import dev.launcherfork.stalzone.TunnelOverrides
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.isDirectory

/**
 * Starts the installed client with the official `runCommand`: `GET /joinGame` supplies `<sess>`,
 * then [bakeRunCommand] fills the placeholders. The command line and the join response are not logged.
 * The waiter follows the process tree, because `ExensLauncher.exe` exits while the game is still running.
 */
class InstalledGameLauncher(
    private val login: () -> String?,
    private val token: () -> String?,
    private val ramMb: () -> Int,
    private val joinGame: (String) -> String?,
    private val runCommand: (String) -> String?,
    private val clientDir: (String) -> Path?,
) : GameLauncher {
    override fun launch(server: Server, listener: GameListener): Cancellable {
        val name = login()?.takeIf { it.isNotBlank() }
        val sessionToken = token()?.takeIf { it.isNotBlank() }
        val command = runCommand(server.id)?.takeIf { it.isNotBlank() }
        val root = clientDir(server.id)?.takeIf { it.isDirectory() }
        if (name == null || sessionToken == null || command == null || root == null) {
            listener.onExited()
            return Cancellable {}
        }
        val process = AtomicReference<Process?>()
        val seen = LinkedHashSet<Long>()
        val waiter = Thread {
            try {
                val session = joinGame(name)?.takeIf { it.isNotBlank() }
                if (session != null) {
                    val baked = TunnelOverrides.commandWithOverrides(
                        bakeRunCommand(parseRunArgs(command), root, name, session, ramMb()),
                        StalzonePaths.tunnelOverridesFile(),
                    )
                    val proc = ProcessBuilder(baked)
                        .directory(root.toFile())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start()
                    process.set(proc)
                    listener.onStarted()
                    waitForTree(proc, seen)
                }
            } catch (_: Exception) {
                // the button returns to Play; the command line is not logged
            } finally {
                listener.onExited()
            }
        }
        waiter.isDaemon = true
        waiter.name = "exbo-game"
        waiter.start()
        return Cancellable {
            destroyTree(process.get(), seen)
            waiter.interrupt()
        }
    }

    private fun waitForTree(process: Process, seen: MutableSet<Long>) {
        synchronized(seen) { seen += process.toHandle().pid() }
        while (!Thread.currentThread().isInterrupted) {
            val alive = ArrayList<ProcessHandle>()
            val snapshot = synchronized(seen) { seen.toList() }
            for (pid in snapshot) {
                val handle = ProcessHandle.of(pid).orElse(null) ?: continue
                if (handle.isAlive) alive += handle
            }
            if (alive.isEmpty()) return
            synchronized(seen) {
                for (handle in alive) {
                    handle.children().forEach { seen += it.pid() }
                    handle.descendants().forEach { seen += it.pid() }
                }
            }
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    /** Descendants recorded while the starter was alive, including ones Windows reparented after it exited. */
    private fun destroyTree(process: Process?, seen: MutableSet<Long>) {
        val ids = LinkedHashSet<Long>()
        process?.toHandle()?.let { root ->
            ids += root.pid()
            root.descendants().forEach { ids += it.pid() }
        }
        synchronized(seen) { ids += seen }
        val handles = ids.mapNotNull { pid -> ProcessHandle.of(pid).orElse(null) }
        for (handle in handles.asReversed()) runCatching { handle.destroy() }
        for (handle in handles) if (handle.isAlive) runCatching { handle.destroyForcibly() }
    }
}

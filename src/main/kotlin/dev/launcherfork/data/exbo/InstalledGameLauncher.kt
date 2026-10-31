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
 * `ExensLauncher.exe` exits while the game is still running, and Image File Execution Options starts the
 * JVM outside that process tree. The waiter keeps every live executable under the client `java/bin`.
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
            System.err.println(
                "[launcher-fork] launch_skip login=${name != null} token=${sessionToken != null} " +
                    "command=${command != null} client=${root != null}",
            )
            listener.onExited()
            return Cancellable {}
        }
        val process = AtomicReference<Process?>()
        val seen = LinkedHashSet<Long>()
        val waiter = Thread {
            try {
                val session = joinGame(name)?.takeIf { it.isNotBlank() }
                if (session == null) {
                    System.err.println("[launcher-fork] launch_skip session=false")
                } else {
                    val baked = TunnelOverrides.commandWithOverrides(
                        bakeRunCommand(parseRunArgs(command), root, name, session, ramMb()),
                        StalzonePaths.tunnelOverridesFile(),
                    )
                    val proc = ProcessBuilder(baked)
                        .directory(root.toFile())
                        .redirectInput(ProcessBuilder.Redirect.INHERIT)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start()
                    process.set(proc)
                    listener.onStarted()
                    waitForTree(proc, seen, root)
                }
            } catch (e: Exception) {
                // the button returns to Play; the command line is not logged
                System.err.println("[launcher-fork] launch_failed=${e.javaClass.simpleName}")
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

    private fun waitForTree(process: Process, seen: MutableSet<Long>, root: Path) {
        val bin = root.resolve(clientArchFolder()).resolve("java").resolve("bin")
        synchronized(seen) { seen += process.toHandle().pid() }
        var emptySince = 0L
        while (!Thread.currentThread().isInterrupted) {
            adoptClientProcesses(bin, seen)
            val alive = ArrayList<ProcessHandle>()
            val snapshot = synchronized(seen) { seen.toList() }
            for (pid in snapshot) {
                val handle = ProcessHandle.of(pid).orElse(null) ?: continue
                if (handle.isAlive) alive += handle
            }
            if (alive.isEmpty()) {
                if (emptySince == 0L) emptySince = System.nanoTime()
                if (System.nanoTime() - emptySince >= EMPTY_GRACE_NS) return
            } else {
                emptySince = 0L
                synchronized(seen) {
                    for (handle in alive) {
                        handle.children().forEach { seen += it.pid() }
                        handle.descendants().forEach { seen += it.pid() }
                    }
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

    /** JVM the IFEO wrapper spawns is not a child of `ExensLauncher.exe`, but its exe stays in this folder. */
    private fun adoptClientProcesses(bin: Path, seen: MutableSet<Long>) {
        val found = ArrayList<Long>()
        ProcessHandle.allProcesses().forEach { handle ->
            val command = handle.info().command().orElse(null)
            if (handle.isAlive && isClientRuntimeProcess(command, bin)) found += handle.pid()
        }
        if (found.isNotEmpty()) synchronized(seen) { seen += found }
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

    private companion object {
        /** How long an empty tree is tolerated so the wrapper can publish the real JVM. */
        const val EMPTY_GRACE_NS = 3_000_000_000L
    }
}

internal fun clientArchFolder(): String =
    if (System.getProperty("os.arch").contains("64")) "win64" else "win32"

/** True when [command] is an executable inside the client JRE `bin` (Exens, stalzone, java). */
internal fun isClientRuntimeProcess(command: String?, bin: Path): Boolean {
    if (command.isNullOrBlank()) return false
    val exe = command.trim().trim('"').replace('/', '\\').lowercase()
    val root = normalizedClientBin(bin)
    return exe == root || exe.startsWith("$root\\")
}

/**
 * A Windows drive path is already absolute. [Path.toAbsolutePath] on Linux would prefix the
 * working directory, so `C:\Games\...\bin\java.exe` would never match that `bin`.
 */
private fun normalizedClientBin(bin: Path): String {
    val raw = bin.toString()
    val windowsDrive = raw.length >= 2 && raw[0].isLetter() && raw[1] == ':'
    val path = if (bin.isAbsolute || windowsDrive) bin else bin.toAbsolutePath()
    return path.toString().trimEnd('\\').replace('/', '\\').lowercase()
}

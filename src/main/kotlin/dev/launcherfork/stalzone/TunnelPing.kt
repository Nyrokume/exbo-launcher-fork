package dev.launcherfork.stalzone

import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class PingTarget(val id: String, val host: String, val port: Int)

data class PingResult(val id: String, val ms: Int?)

/** Why a TCP connect did not finish. Used only to classify a ping; it does not open a socket. */
internal enum class ConnectFailure { REFUSED, RESET, TIMEOUT, UNREACHABLE, OTHER }

/**
 * TCP connect latency, the same measurement as `ping.rs` (`TcpStream::connect_timeout` to a literal IPv4).
 *
 * A millisecond value is a completed round-trip: the handshake finished, or the host answered with
 * connection refused / reset. `ping.rs` counts those two explicitly ("host is reachable"). Timeout,
 * unreachable, and every other failure are a miss (`null`), shown as «нет». A refused connect is not
 * success merely because an exception was thrown.
 * Invalid addresses fail as `null` and never throw out of the batch.
 */
object TunnelPing {
    const val CONCURRENCY = 10

    fun tcpPingMs(host: String, port: Int, timeoutMs: Long): Int? {
        if (!ServerBlock.isValidIpv4(host) || port !in 1..65535) return null
        val timeout = timeoutMs.coerceIn(100, 10_000).toInt()
        val start = System.nanoTime()
        return try {
            Socket().use { socket -> socket.connect(InetSocketAddress(host, port), timeout) }
            classifyPing(null, elapsedMs(start))
        } catch (error: Exception) {
            classifyPing(error, elapsedMs(start))
        }
    }

    /** [error] null means the handshake completed. No socket is opened here. */
    internal fun classifyPing(error: Throwable?, elapsedMs: Int): Int? {
        val ms = elapsedMs.coerceAtLeast(0)
        if (error == null) return ms
        return when (connectFailure(error)) {
            ConnectFailure.REFUSED, ConnectFailure.RESET -> ms
            ConnectFailure.TIMEOUT, ConnectFailure.UNREACHABLE, ConnectFailure.OTHER -> null
        }
    }

    internal fun connectFailure(error: Throwable): ConnectFailure {
        var current: Throwable? = error
        var refused = false
        var reset = false
        while (current != null) {
            if (current is SocketTimeoutException) return ConnectFailure.TIMEOUT
            if (current is NoRouteToHostException || current is PortUnreachableException) return ConnectFailure.UNREACHABLE
            val message = current.message.orEmpty()
            if (message.contains("timed out", ignoreCase = true) || message.contains("timeout", ignoreCase = true)) {
                return ConnectFailure.TIMEOUT
            }
            if (message.contains("unreachable", ignoreCase = true) || message.contains("no route", ignoreCase = true)) {
                return ConnectFailure.UNREACHABLE
            }
            if (message.contains("refused", ignoreCase = true)) refused = true
            if (message.contains("reset", ignoreCase = true)) reset = true
            current = current.cause
        }
        return when {
            refused -> ConnectFailure.REFUSED
            reset -> ConnectFailure.RESET
            else -> ConnectFailure.OTHER
        }
    }

    /**
     * Pings [targets] in chunks of [ServerBlock.PING_CHUNK_SIZE], [CONCURRENCY] connects at a time.
     * [onChunk] runs on this thread after each chunk (`done` is how many targets have a result).
     */
    fun ping(
        targets: List<PingTarget>,
        timeoutMs: Long = ServerBlock.PING_TIMEOUT_MS,
        onChunk: (results: List<PingResult>, done: Int, total: Int) -> Unit,
    ) {
        if (targets.isEmpty()) return
        var done = 0
        for (chunk in targets.chunked(ServerBlock.PING_CHUNK_SIZE)) {
            val results = pingChunk(chunk, timeoutMs)
            done += chunk.size
            onChunk(results, done, targets.size)
        }
    }

    internal fun pingChunk(targets: List<PingTarget>, timeoutMs: Long): List<PingResult> {
        if (targets.isEmpty()) return emptyList()
        val pool = Executors.newFixedThreadPool(minOf(CONCURRENCY, targets.size)) { runnable ->
            Thread(runnable, "tunnel-ping").apply { isDaemon = true }
        }
        return try {
            val futures = targets.map { target ->
                pool.submit<PingResult> { PingResult(target.id, tcpPingMs(target.host, target.port, timeoutMs)) }
            }
            futures.map { it.get() }
        } finally {
            pool.shutdown()
            pool.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    private fun elapsedMs(start: Long): Int =
        ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(0)
}

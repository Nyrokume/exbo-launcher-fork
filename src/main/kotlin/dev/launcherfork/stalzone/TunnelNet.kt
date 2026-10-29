package dev.launcherfork.stalzone

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Catalog fetch and the UDP probe from `internal/tunnel`.
 * [TunnelOverrides.commandWithOverrides] does not call either of these.
 */
object TunnelCatalogs {
    const val LOGIN = "EXBO-Community"
    const val MAX_BYTES = 1 shl 20
    const val MAX_POOLS = 100
    const val MAX_ENDPOINTS = 1000
    const val MAX_WEIGHT = 1000.0
    private val SOURCE_TIMEOUT: Duration = Duration.ofSeconds(3)
    private val FETCH_BUDGET: Duration = Duration.ofSeconds(10)

    val fetchAttempts = AtomicInteger()

    fun sources(region: TunnelRegion): List<String> = when (region) {
        TunnelRegion.EU -> listOf(
            "https://backend-eu.stalzone.com/address_list",
            "https://backend-eu.stalzone.net/address_list",
        )
        TunnelRegion.NA -> listOf(
            "https://backend-na.stalzone.com/address_list",
            "https://backend-na.stalzone.net/address_list",
        )
        TunnelRegion.SEA -> listOf(
            "https://backend-sea.stalzone.com/address_list",
            "https://backend-sea.stalzone.net/address_list",
        )
        TunnelRegion.NEA -> listOf(
            "https://backend-nea.stalzone.com/address_list",
            "https://backend-nea.stalzone.net/address_list",
        )
        TunnelRegion.RU -> listOf(
            "https://backend.stalzone.net/address_list",
            "https://backend.stalzone.com/address_list",
            "https://backend.stalcraftx.ru/address_list",
        )
    }

    fun parse(text: String): RoxyCatalog {
        val root = MiniJson.parse(text) as? Map<*, *> ?: error("catalog")
        val mode = root["mode"] as? String ?: ""
        if (mode != "roxy") error("mode")
        val weight = when (val raw = root["clientToTunnelRttWeight"]) {
            null -> 0.0
            is Double -> raw
            is Long -> raw.toDouble()
            is Int -> raw.toDouble()
            else -> error("weight")
        }
        if (!weight.isFinite() || weight < 0.0 || weight > MAX_WEIGHT) error("weight")
        val rawPools = root["pools"] as? List<*> ?: error("pools")
        if (rawPools.isEmpty() || rawPools.size > MAX_POOLS) error("pools")
        val seenPools = HashSet<String>()
        val pools = ArrayList<TunnelPool>()
        var total = 0
        for (item in rawPools) {
            val pool = item as? Map<*, *> ?: error("pool")
            val name = pool["name"] as? String ?: error("pool")
            if (!TunnelAddresses.validLabel(name)) error("pool")
            if (!seenPools.add(name)) error("pool")
            val seenAddresses = HashSet<String>()
            val tunnels = ArrayList<TunnelEndpoint>()
            for (row in (pool["tunnels"] as? List<*>).orEmpty()) {
                val tunnel = row as? Map<*, *> ?: error("endpoint")
                val tunnelName = tunnel["name"] as? String ?: error("endpoint")
                val address = tunnel["address"]?.toString()?.trim().orEmpty()
                if (!TunnelAddresses.validLabel(tunnelName)) error("endpoint")
                if (TunnelAddresses.parse(address) == null) error("endpoint")
                if (!seenAddresses.add(address)) continue
                tunnels += TunnelEndpoint(tunnelName, address)
                total++
                if (total > MAX_ENDPOINTS) error("endpoints")
            }
            if (tunnels.isNotEmpty()) pools += TunnelPool(name, tunnels)
        }
        if (pools.isEmpty()) error("endpoints")
        return RoxyCatalog(mode, pools, weight)
    }

    /**
     * Classpath snapshot used by the mock launcher and tests. Live [fetch] is unchanged and still
     * talks only to the existing region hosts. A missing file or an empty region is null.
     */
    fun bundled(region: TunnelRegion): RoxyCatalog? = try {
        when (region) {
            TunnelRegion.RU -> parse(resource("servers-ru.json"))
            TunnelRegion.NEA -> parse(NEA_FIXTURE)
            else -> parse(regional(resource("servers.json"), region))
        }
    } catch (_: Exception) {
        null
    }

    private fun resource(name: String): String =
        TunnelCatalogs::class.java.getResourceAsStream("/dev/launcherfork/stalzone/$name")
            ?.readBytes()?.toString(Charsets.UTF_8)
            ?: error(name)

    /** Keeps pools whose `region` field matches. The extra field is dropped before [parse]. */
    private fun regional(text: String, region: TunnelRegion): String {
        val root = MiniJson.parse(text) as Map<*, *>
        val pools = (root["pools"] as List<*>).filter { (it as Map<*, *>)["region"] == region.name }
        return MiniJson.write(
            linkedMapOf(
                "mode" to "roxy",
                "clientToTunnelRttWeight" to (root["clientToTunnelRttWeight"] ?: 0),
                "pools" to pools,
            ),
        )
    }

    fun fetch(region: TunnelRegion): RoxyCatalog {
        fetchAttempts.incrementAndGet()
        val client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(SOURCE_TIMEOUT)
            .build()
        val deadline = System.nanoTime() + FETCH_BUDGET.toNanos()
        var last = "no tunnel catalog sources"
        for (source in sources(region)) {
            if (System.nanoTime() > deadline) break
            try {
                return fetchSource(client, source)
            } catch (error: Exception) {
                last = error.message ?: last
            }
        }
        error(last)
    }

    private fun fetchSource(client: HttpClient, source: String): RoxyCatalog {
        val endpoint = URI(source)
        if (!endpoint.scheme.equals("https", ignoreCase = true) || endpoint.host.isNullOrBlank()) error("source")
        val login = URLEncoder.encode(LOGIN, Charsets.UTF_8)
        val request = HttpRequest.newBuilder(URI("$source?login=$login"))
            .timeout(SOURCE_TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            if (response.statusCode() != 200) error("${endpoint.host} ${response.statusCode()}")
            val data = body.readNBytes(MAX_BYTES + 1)
            if (data.size > MAX_BYTES) error("${endpoint.host} size")
            return parse(data.toString(Charsets.UTF_8))
        }
    }

    /** Same shape as the other bundled regions. Not a live host. */
    private const val NEA_FIXTURE = """
        {"mode":"roxy","clientToTunnelRttWeight":1,"pools":[
          {"name":"NEA1","tunnels":[
            {"name":"NEA1-1","address":"203.0.113.10:29450"},
            {"name":"NEA1-2","address":"203.0.113.11:29450"}
          ]}
        ]}
    """
}

object TunnelProbe {
    const val REQUEST_SIZE = 16
    const val RESPONSE_SIZE = 21
    private const val MAX_DATAGRAM = 64 * 1024

    val attempts = AtomicInteger()

    fun token(): ByteArray {
        val bytes = ByteArray(REQUEST_SIZE)
        SecureRandom().nextBytes(bytes)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        return bytes
    }

    /** Null when the datagram is not our 21-byte reply. Negative server time is a failed probe. */
    fun readReply(token: ByteArray, packet: ByteArray): Pair<Long, Boolean>? {
        if (packet.size != RESPONSE_SIZE) return null
        if (!packet.copyOfRange(0, REQUEST_SIZE).contentEquals(token)) return null
        val server = ByteBuffer.wrap(packet, 16, 4).order(ByteOrder.BIG_ENDIAN).int
        if (server < 0) error("server rtt")
        return server.toLong() to (packet[20] != 0.toByte())
    }

    fun measure(endpoint: TunnelEndpoint, timeout: Duration = Duration.ofMillis(TunnelOverrides.ROUND_TIMEOUT_MS)): TunnelProbeOutcome {
        attempts.incrementAndGet()
        val target = TunnelAddresses.probeAddress(endpoint.address) ?: return TunnelProbeOutcome.NoReply
        val (host, port) = TunnelAddresses.parse(target) ?: return TunnelProbeOutcome.NoReply
        val token = token()
        val started = System.nanoTime()
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeout.toMillis().toInt().coerceAtLeast(1)
                val address = InetAddress.getByName(host)
                socket.send(DatagramPacket(token, token.size, address, port))
                val buffer = ByteArray(MAX_DATAGRAM)
                val deadline = started + timeout.toNanos()
                while (System.nanoTime() < deadline) {
                    val left = deadline - System.nanoTime()
                    socket.soTimeout = (left / 1_000_000L).toInt().coerceAtLeast(1)
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val data = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                    val reply = try {
                        readReply(token, data)
                    } catch (_: Exception) {
                        return TunnelProbeOutcome.NoReply
                    } ?: continue
                    val client = Duration.ofNanos(System.nanoTime() - started)
                    return TunnelProbeOutcome.Reply(client, Duration.ofMillis(reply.first), reply.second)
                }
                TunnelProbeOutcome.NoReply
            }
        } catch (_: Exception) {
            TunnelProbeOutcome.NoReply
        }
    }
}

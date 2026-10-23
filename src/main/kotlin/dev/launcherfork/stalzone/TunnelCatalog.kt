package dev.launcherfork.stalzone

/**
 * Tunnel catalog grouped the way the original wrapper groups it: region, then pool.
 * Addresses live in the same JSON the wrapper ships (`servers.json` for EU/NA/SEA,
 * `servers-ru.json` for RU). `MSK2X` is dropped from the roxy file, matching the wrapper merge.
 */
object TunnelCatalog {
    data class Tunnel(val name: String, val address: String, val host: String, val port: Int)
    data class Pool(val name: String, val tunnels: List<Tunnel>)
    data class Region(val id: String, val pools: List<Pool>)

    data class Server(
        val id: String,
        val pool: String,
        val name: String,
        val address: String,
        val host: String,
        val port: Int,
        val region: String,
    )

    private val regionOrder = listOf("RU", "EU", "NA", "SEA")
    private const val RU = "/dev/launcherfork/stalzone/servers-ru.json"
    private const val ROXY = "/dev/launcherfork/stalzone/servers.json"

    fun regions(): List<Region> {
        val ru = readPools(RU, forceRegion = "RU", dropMsk2x = false)
        val rest = readPools(ROXY, forceRegion = null, dropMsk2x = true)
        val byRegion = linkedMapOf<String, MutableList<Pool>>()
        rest.forEach { pool -> byRegion.getOrPut(pool.region) { mutableListOf() }.add(pool.pool) }
        val known = regionOrder.mapNotNull { id ->
            val pools = if (id == "RU") ru.map { it.pool } else byRegion.remove(id)
            pools?.takeIf { it.isNotEmpty() }?.let { Region(id, it) }
        }
        val extra = byRegion.map { (id, pools) -> Region(id, pools) }
        return known + extra
    }

    fun servers(): List<Server> = regions().flatMap { region ->
        region.pools.flatMap { pool ->
            pool.tunnels.map { tunnel ->
                Server(
                    id = "${pool.name}::${tunnel.name}",
                    pool = pool.name,
                    name = tunnel.name,
                    address = tunnel.address,
                    host = tunnel.host,
                    port = tunnel.port,
                    region = region.id,
                )
            }
        }
    }

    fun names(): List<String> = regions().flatMap { region -> region.pools.flatMap { it.tunnels.map { tunnel -> tunnel.name } } }

    fun regionOf(tunnel: String): String? =
        regions().firstOrNull { region -> region.pools.any { pool -> pool.tunnels.any { it.name == tunnel } } }?.id

    private data class Stamped(val region: String, val pool: Pool)

    private fun readPools(resource: String, forceRegion: String?, dropMsk2x: Boolean): List<Stamped> {
        val text = TunnelCatalog::class.java.getResourceAsStream(resource)?.readBytes()?.toString(Charsets.UTF_8)
            ?: return emptyList()
        val root = MiniJson.parse(text) as? Map<*, *> ?: return emptyList()
        val pools = root["pools"] as? List<*> ?: return emptyList()
        return pools.mapNotNull { item ->
            val pool = item as? Map<*, *> ?: return@mapNotNull null
            val name = pool["name"] as? String ?: return@mapNotNull null
            if (dropMsk2x && name == "MSK2X") return@mapNotNull null
            val region = forceRegion ?: pool["region"] as? String ?: return@mapNotNull null
            val tunnels = (pool["tunnels"] as? List<*>).orEmpty().mapNotNull { row ->
                val tunnel = row as? Map<*, *> ?: return@mapNotNull null
                val tunnelName = tunnel["name"] as? String ?: return@mapNotNull null
                val address = tunnel["address"]?.toString()?.trim().orEmpty()
                if (address.isEmpty()) return@mapNotNull null
                val (host, port) = splitAddress(address)
                Tunnel(tunnelName, address, host, port)
            }
            if (tunnels.isEmpty()) return@mapNotNull null
            Stamped(region, Pool(name, tunnels))
        }
    }

    /** `host:port`. A missing or zero port becomes 29450, the same fallback the wrapper uses. */
    internal fun splitAddress(address: String): Pair<String, Int> {
        val idx = address.lastIndexOf(':')
        if (idx <= 0) return address to DEFAULT_PORT
        val host = address.substring(0, idx).trim()
        val port = address.substring(idx + 1).trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: DEFAULT_PORT
        return host to port
    }

    private const val DEFAULT_PORT = 29450
}

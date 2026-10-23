package dev.launcherfork.stalzone

import dev.launcherfork.domain.ServerBlockState

/**
 * Pure selection rules from the wrapper's `server-blocker-logic.js`.
 * Mode is always a blocklist: `blocked` holds the server ids to firewall, and an empty list blocks nothing
 * (the game picks). Auto-best keeps the [AUTO_BEST_TOP_N] lowest acceptable pings among per-pool winners.
 */
object ServerBlock {
    const val PING_ACCEPTABLE_MAX_MS = 200
    const val PING_VERY_BAD_MS = 300
    const val AUTO_BEST_TOP_N = 3
    const val PING_CHUNK_SIZE = 16
    const val PING_TIMEOUT_MS = 1200L
    const val MODE = "blocklist"

    fun isAcceptablePing(ms: Int?): Boolean = ms != null && ms >= 0 && ms <= PING_ACCEPTABLE_MAX_MS

    fun isVeryBadPing(ms: Int?): Boolean = ms != null && ms > PING_VERY_BAD_MS

    /** A measured ping that is null (timeout) or slower than [PING_ACCEPTABLE_MAX_MS]. */
    fun isUnacceptablePing(ms: Int?): Boolean = ms == null || !isAcceptablePing(ms)

    fun pickBestServerId(pings: Map<String, Int?>, serverIds: List<String>): String? {
        var bestId: String? = null
        var bestMs = Int.MAX_VALUE
        for (id in serverIds) {
            if (id !in pings) continue
            val ms = pings[id]
            if (!isAcceptablePing(ms)) continue
            if (ms!! < bestMs) {
                bestMs = ms
                bestId = id
            }
        }
        return bestId
    }

    fun pickBestPerPool(pings: Map<String, Int?>, servers: List<TunnelCatalog.Server>): Map<String, String> {
        val byPool = linkedMapOf<String, MutableList<String>>()
        for (server in servers) byPool.getOrPut(server.pool) { mutableListOf() }.add(server.id)
        val best = linkedMapOf<String, String>()
        for ((pool, ids) in byPool) {
            val winner = pickBestServerId(pings, ids) ?: continue
            best[pool] = winner
        }
        return best
    }

    fun pickTopNByPing(pings: Map<String, Int?>, serverIds: List<String>, n: Int = AUTO_BEST_TOP_N): List<String> =
        serverIds
            .filter { id -> id in pings && isAcceptablePing(pings[id]) }
            .sortedBy { id -> pings[id] }
            .take(n)

    /**
     * Per region: best server in each pool, then the [n] lowest of those, then block everyone else in scope.
     * Preferred pools are only the winners that made the top-N cut.
     */
    fun pickBestPerRegionTopN(
        pings: Map<String, Int?>,
        servers: List<TunnelCatalog.Server>,
        n: Int = AUTO_BEST_TOP_N,
    ): Selection {
        val allowed = mutableListOf<String>()
        val preferred = linkedMapOf<String, String>()
        for (regionServers in groupByRegion(servers).values) {
            val bestByPool = pickBestPerPool(pings, regionServers)
            val top = pickTopNByPing(pings, bestByPool.values.toList(), n)
            allowed += top
            for ((pool, id) in bestByPool) {
                if (id in top) preferred[pool] = id
            }
        }
        return Selection(allowed, preferred)
    }

    /** Blocklist: every server in [servers] whose id is not in [allowedIds]. */
    fun blockedExcept(servers: List<TunnelCatalog.Server>, allowedIds: Collection<String>): List<String> {
        val allowed = allowedIds.toSet()
        return servers.filter { it.id !in allowed }.map { it.id }
    }

    /** Drop ids that belong to [scopeIds], then add [nextInScope]. Other regions stay. */
    fun mergeBlockedForScope(prevBlocked: List<String>, scopeIds: Collection<String>, nextInScope: Collection<String>): List<String> {
        val scope = scopeIds.toSet()
        val kept = prevBlocked.filter { it !in scope }
        return (kept + nextInScope).distinct()
    }

    /** Replace preferred pools that appear in [scope]; keep every other pool. */
    fun mergePreferredForScope(
        prev: Map<String, String>,
        scope: List<TunnelCatalog.Server>,
        next: Map<String, String>,
    ): Map<String, String> {
        val scopePools = scope.map { it.pool }.toSet()
        val out = linkedMapOf<String, String>()
        for ((pool, id) in prev) if (pool !in scopePools) out[pool] = id
        for ((pool, id) in next) if (pool in scopePools) out[pool] = id
        return out
    }

    /**
     * Measured bad or missing pings join the blocklist and lose their "best" mark.
     * Servers that have not been pinged are left alone.
     */
    fun mergeAutoBlockUnacceptable(
        servers: List<TunnelCatalog.Server>,
        pings: Map<String, Int?>,
        blocked: List<String>,
        preferred: Map<String, String>,
    ): Pair<List<String>, Map<String, String>> {
        val set = blocked.toMutableSet()
        val nextPreferred = preferred.toMutableMap()
        for (server in servers) {
            if (server.id !in pings) continue
            if (!isUnacceptablePing(pings[server.id])) continue
            set += server.id
            if (nextPreferred[server.pool] == server.id) nextPreferred.remove(server.pool)
        }
        return set.toList() to nextPreferred
    }

    fun prune(servers: List<TunnelCatalog.Server>, state: ServerBlockState): ServerBlockState {
        val ids = servers.map { it.id }.toSet()
        val pools = servers.map { it.pool }.toSet()
        return state.copy(
            blocked = state.blocked.filter { it in ids },
            pings = state.pings.filterKeys { it in ids },
            preferredByPool = state.preferredByPool.filter { (pool, id) -> pool in pools && id in ids },
        )
    }

    /** IPv4 hosts of the blocked servers, first-seen order, invalid hosts dropped. */
    fun resolveBlockedHosts(servers: List<TunnelCatalog.Server>, blockedIds: Collection<String>): List<String> {
        val selected = blockedIds.toSet()
        return uniqueValidIpv4(servers.filter { it.id in selected }.map { it.host })
    }

    fun hasResolvableBlocks(servers: List<TunnelCatalog.Server>, blockedIds: Collection<String>): Boolean =
        resolveBlockedHosts(servers, blockedIds).isNotEmpty()

    fun hostSetsEqual(a: Collection<String>, b: Collection<String>): Boolean {
        val left = a.filter { it.isNotBlank() }.distinct().sorted()
        val right = b.filter { it.isNotBlank() }.distinct().sorted()
        return left == right
    }

    fun poolsWithValidPing(pings: Map<String, Int?>, servers: List<TunnelCatalog.Server>): Int {
        val pools = servers.map { it.pool }.distinct()
        return pools.count { pool ->
            servers.any { it.pool == pool && it.id in pings && isAcceptablePing(pings[it.id]) }
        }
    }

    fun groupByRegion(servers: List<TunnelCatalog.Server>): Map<String, List<TunnelCatalog.Server>> {
        val map = linkedMapOf<String, MutableList<TunnelCatalog.Server>>()
        for (server in servers) map.getOrPut(server.region) { mutableListOf() }.add(server)
        return map
    }

    /** Unpinged rows stay. A measured timeout or a ping over [PING_ACCEPTABLE_MAX_MS] would be hidden in the wrapper. */
    fun shouldShowServer(pings: Map<String, Int?>, serverId: String, pinging: Boolean = false): Boolean {
        if (pinging) return true
        if (serverId !in pings) return true
        return !isUnacceptablePing(pings[serverId])
    }

    fun isValidIpv4(raw: String): Boolean {
        val ip = raw.trim()
        val parts = ip.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            if (part.isEmpty() || part.length > 3) return@all false
            if (part.length > 1 && part[0] == '0') return@all false
            val n = part.toIntOrNull() ?: return@all false
            n in 0..255
        }
    }

    /** Deduplicated valid IPv4 addresses, first-seen order. */
    fun uniqueValidIpv4(ips: List<String>): List<String> {
        val unique = mutableListOf<String>()
        for (ip in ips) {
            val trimmed = ip.trim()
            if (trimmed.isEmpty() || !isValidIpv4(trimmed)) continue
            if (trimmed !in unique) unique += trimmed
        }
        return unique
    }

    data class Selection(val allowed: List<String>, val preferredByPool: Map<String, String>)
}

/** `settings.properties` shape for [ServerBlockState]. Mode is stored and always read back as blocklist. */
object ServerBlockCodec {
    fun writeBlocked(ids: List<String>): String = ids.joinToString(",")

    fun readBlocked(raw: String?): List<String> =
        raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    fun writePings(pings: Map<String, Int?>): String =
        pings.entries.joinToString(";") { (id, ms) -> "$id=${ms ?: -1}" }

    fun readPings(raw: String?): Map<String, Int?> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = linkedMapOf<String, Int?>()
        for (part in raw.split(';')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val id = part.substring(0, eq).trim()
            val ms = part.substring(eq + 1).trim().toIntOrNull()
            if (id.isEmpty() || ms == null) continue
            out[id] = if (ms < 0) null else ms
        }
        return out
    }

    fun writePreferred(preferred: Map<String, String>): String =
        preferred.entries.joinToString(";") { (pool, id) -> "$pool=$id" }

    fun readPreferred(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = linkedMapOf<String, String>()
        for (part in raw.split(';')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val pool = part.substring(0, eq).trim()
            val id = part.substring(eq + 1).trim()
            if (pool.isEmpty() || id.isEmpty()) continue
            out[pool] = id
        }
        return out
    }

    fun readRegion(raw: String?): String = raw?.takeIf { it in listOf("RU", "EU", "NA", "SEA") } ?: "RU"

    /** The wrapper forces blocklist on load, whatever was stored. */
    fun readMode(raw: String?): String = if (raw == ServerBlock.MODE) ServerBlock.MODE else ServerBlock.MODE
}

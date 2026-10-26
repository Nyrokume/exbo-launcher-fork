package dev.launcherfork.stalzone

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Regional Roxy override from EXBO-Community/stalcraft-jvm-optimization (`internal/tunnel`).
 *
 * The game reads one flag per region, `-Droxy_address_override.<region>=host:port`.
 * Choices and the search mode live in `jvm_wrapper/overrides.json`. The last 20 samples per node
 * live in `jvm_wrapper/cache/tunnel_stats.json` and are dropped after 24 hours.
 * Launch only checks the saved address. It does not fetch a catalog and does not probe.
 */
enum class TunnelRegion(val id: String) {
    RU("ru"),
    EU("eu"),
    NA("na"),
    SEA("sea"),
    NEA("nea"),
    ;

    val label: String get() = id.uppercase(Locale.US)

    companion object {
        fun fromId(raw: String?): TunnelRegion? = entries.firstOrNull { it.id == raw?.lowercase(Locale.US) }
    }
}

enum class TunnelSearchMode(val id: String) {
    PING("ping"),
    CLIENT_RTT("client_rtt"),
    SERVER_RTT("server_rtt"),
    STABILITY("stability"),
    ;

    companion object {
        fun fromId(raw: String?): TunnelSearchMode? = entries.firstOrNull { it.id == raw }
    }
}

data class TunnelEndpoint(val name: String, val address: String)

data class TunnelPool(val name: String, val tunnels: List<TunnelEndpoint>)

data class RoxyCatalog(
    val mode: String,
    val pools: List<TunnelPool>,
    val clientToTunnelRttWeight: Double,
) {
    /** Nodes in pool order. Excluded pools are skipped. The first copy of an address wins. */
    fun targets(region: TunnelRegion, excluded: (String) -> Boolean = { false }): List<TunnelTarget> {
        val seen = HashSet<String>()
        val out = ArrayList<TunnelTarget>()
        for (pool in pools) {
            if (excluded(pool.name)) continue
            for (endpoint in pool.tunnels) {
                if (!seen.add(endpoint.address)) continue
                out += TunnelTarget(region, pool.name, endpoint)
            }
        }
        return out
    }
}

data class TunnelTarget(val region: TunnelRegion, val pool: String, val endpoint: TunnelEndpoint) {
    fun key(): String = TunnelOverrides.cacheKey(region, endpoint.address)
}

data class TunnelSelection(
    val region: TunnelRegion,
    val pool: String,
    val name: String,
    val address: String,
) {
    fun flag(): String? {
        if (!valid()) return null
        return "-Droxy_address_override.${region.id}=$address"
    }

    fun valid(): Boolean {
        if (TunnelAddresses.parse(address) == null) return false
        if (pool.isNotEmpty() && !TunnelAddresses.validLabel(pool)) return false
        if (name.isNotEmpty() && !TunnelAddresses.validLabel(name)) return false
        return true
    }
}

data class TunnelSettings(
    val version: Int = TunnelOverrides.VERSION,
    val overrides: Map<TunnelRegion, TunnelSelection> = emptyMap(),
    val mode: TunnelSearchMode = TunnelSearchMode.PING,
    val excludedPools: Map<TunnelRegion, List<String>> = emptyMap(),
) {
    fun deepCopy(): TunnelSettings = copy(
        overrides = overrides.mapValues { it.value.copy() },
        excludedPools = excludedPools.mapValues { it.value.toList() },
    )

    fun override(region: TunnelRegion): TunnelSelection? = overrides[region]

    fun isExcluded(region: TunnelRegion, pool: String): Boolean = pool in excludedPools[region].orEmpty()

    fun withOverride(selection: TunnelSelection): TunnelSettings =
        copy(overrides = overrides + (selection.region to selection))

    fun withoutOverride(region: TunnelRegion): TunnelSettings =
        copy(overrides = overrides - region)

    fun withMode(mode: TunnelSearchMode): TunnelSettings = copy(mode = mode)

    fun withExclusion(region: TunnelRegion, pool: String, excluded: Boolean): TunnelSettings {
        val current = excludedPools[region].orEmpty().filter { it != pool }
        val next = if (excluded) current + pool else current
        val map = excludedPools.toMutableMap()
        if (next.isEmpty()) map.remove(region) else map[region] = next
        return copy(excludedPools = map)
    }

    /** One flag per configured region, in region order. An invalid address drops the whole set. */
    fun jvmFlags(): List<String> {
        val flags = ArrayList<String>()
        for (region in TunnelRegion.entries) {
            val selection = overrides[region] ?: continue
            if (selection.region != region) return emptyList()
            flags += selection.flag() ?: return emptyList()
        }
        return flags
    }
}

/** What the page shows for one node after the latest probe in this session. */
data class NodeProbe(
    val received: Boolean = false,
    val freshSuccess: Boolean = false,
    val failed: Boolean = false,
    val limitReached: Boolean = false,
    val clientMs: Double = 0.0,
    val serverMs: Double = 0.0,
) {
    /** A node at its connection limit stays on screen and cannot be chosen until a later success. */
    val selectable: Boolean get() = received && freshSuccess && !failed && !limitReached

    val totalMs: Double get() = clientMs + serverMs
}

sealed interface TunnelProbeOutcome {
    data class Reply(val client: Duration, val server: Duration, val limitReached: Boolean) : TunnelProbeOutcome
    data object NoReply : TunnelProbeOutcome
}

fun TunnelProbeOutcome.toView(): NodeProbe = when (this) {
    is TunnelProbeOutcome.Reply -> NodeProbe(
        received = true,
        freshSuccess = true,
        failed = false,
        limitReached = limitReached,
        clientMs = client.toNanos() / 1_000_000.0,
        serverMs = server.toNanos() / 1_000_000.0,
    )
    TunnelProbeOutcome.NoReply -> NodeProbe(received = true, freshSuccess = false, failed = true)
}

data class TunnelSample(
    val measuredAt: Instant,
    val clientRttMicros: Long,
    val serverRttMillis: Long,
    val timedOut: Boolean,
) {
    val valid: Boolean get() = clientRttMicros >= 0 && serverRttMillis >= 0
}

data class EndpointHistory(
    val region: TunnelRegion,
    val pool: String,
    val name: String,
    val address: String,
    val samples: List<TunnelSample>,
    val limitReached: Boolean,
    val limitCheckedAt: Instant?,
)

data class TunnelMetrics(
    val samples: Int,
    val successes: Int,
    val lossRate: Double,
    val clientRtt: Double,
    val serverRtt: Double,
    val ping: Double,
    val p90: Double,
    val jitter: Double,
) {
    fun score(mode: TunnelSearchMode): Double? {
        if (successes == 0) return null
        val values = listOf(lossRate, clientRtt, serverRtt, ping, p90, jitter)
        if (values.any { it < 0.0 || it.isNaN() || it.isInfinite() }) return null
        val score = when (mode) {
            TunnelSearchMode.CLIENT_RTT -> clientRtt
            TunnelSearchMode.SERVER_RTT -> serverRtt
            TunnelSearchMode.STABILITY -> p90 + 2.0 * jitter + 500.0 * lossRate
            TunnelSearchMode.PING -> ping
        }
        if (score < 0.0 || score.isNaN() || score.isInfinite()) return null
        return score
    }
}

data class RankedTarget(val target: TunnelTarget, val metrics: TunnelMetrics, val score: Double)

class TunnelCache {
    var version: Int = TunnelOverrides.VERSION
    val endpoints: MutableMap<String, EndpointHistory> = linkedMapOf()

    fun record(target: TunnelTarget, outcome: TunnelProbeOutcome, at: Instant) {
        val key = target.key()
        val previous = endpoints[key]
        val sample = when (outcome) {
            is TunnelProbeOutcome.Reply -> TunnelSample(
                measuredAt = at,
                clientRttMicros = outcome.client.toNanos() / 1_000,
                serverRttMillis = outcome.server.toMillis(),
                timedOut = false,
            )
            TunnelProbeOutcome.NoReply -> TunnelSample(at, 0, 0, timedOut = true)
        }
        val samples = (previous?.samples.orEmpty() + sample).takeLast(TunnelOverrides.MAX_HISTORY)
        val limitReached = if (outcome is TunnelProbeOutcome.Reply) outcome.limitReached else previous?.limitReached ?: false
        val checked = if (outcome is TunnelProbeOutcome.Reply) at else previous?.limitCheckedAt
        endpoints[key] = EndpointHistory(
            region = target.region,
            pool = target.pool,
            name = target.endpoint.name,
            address = target.endpoint.address,
            samples = samples,
            limitReached = limitReached,
            limitCheckedAt = checked,
        )
    }

    fun wasRecentlyLimited(region: TunnelRegion, address: String, now: Instant): Boolean {
        val history = endpoints[TunnelOverrides.cacheKey(region, address)] ?: return false
        val checked = history.limitCheckedAt ?: return false
        if (!history.limitReached) return false
        val age = Duration.between(checked, now)
        return !age.isNegative && age <= TunnelOverrides.CACHE_TTL
    }

    /** Nodes that reported a connection limit inside the cache window go first. Order inside each group stays. */
    fun prioritize(targets: List<TunnelTarget>, now: Instant): List<TunnelTarget> {
        val first = ArrayList<TunnelTarget>()
        val rest = ArrayList<TunnelTarget>()
        for (target in targets) {
            if (wasRecentlyLimited(target.region, target.endpoint.address, now)) first += target else rest += target
        }
        return first + rest
    }

    fun metrics(region: TunnelRegion, address: String, now: Instant): TunnelMetrics? {
        val history = endpoints[TunnelOverrides.cacheKey(region, address)] ?: return null
        return calculateMetrics(history.samples, now, TunnelOverrides.RECENT_TTL)
    }

    /**
     * Lowest score wins. A tie prefers the steadier recent hour, then the shorter hop to the
     * tunnel, then the name. The score is the mode: ping is the sum, client and server are one hop,
     * stability is the worst recent ping plus jitter and loss.
     */
    fun rank(targets: List<TunnelTarget>, mode: TunnelSearchMode, now: Instant): List<RankedTarget> {
        val ranked = ArrayList<RankedTarget>()
        for (target in targets) {
            val metrics = metrics(target.region, target.endpoint.address, now) ?: continue
            val score = metrics.score(mode) ?: continue
            ranked += RankedTarget(target, metrics, score)
        }
        ranked.sortWith(
            compareBy<RankedTarget> { it.score }
                .thenBy { it.metrics.jitter }
                .thenBy { it.metrics.lossRate }
                .thenBy { it.metrics.clientRtt }
                .thenBy { it.target.endpoint.name },
        )
        return ranked
    }

    /**
     * The five best nodes that answered this round without hitting the connection limit.
     * Excluded pools never enter [targets]. A limited node is not in this list.
     */
    fun best(
        targets: List<TunnelTarget>,
        probes: Map<String, NodeProbe>,
        mode: TunnelSearchMode,
        now: Instant,
        limit: Int = TunnelOverrides.TOP_N,
    ): List<RankedTarget> {
        val available = targets.filter { probes[it.key()]?.selectable == true }
        return rank(available, mode, now).take(limit)
    }

    /** Limited nodes stay visible in the result list and are not selectable. */
    fun limitedTargets(targets: List<TunnelTarget>, probes: Map<String, NodeProbe>, now: Instant): List<TunnelTarget> {
        val out = ArrayList<TunnelTarget>()
        for (target in targets) {
            val view = probes[target.key()]
            val limited = wasRecentlyLimited(target.region, target.endpoint.address, now)
            val currentLimit = view != null && view.received && !view.failed && view.limitReached
            if (view != null && view.received && !view.failed && !view.limitReached && !limited) continue
            if (limited || currentLimit) out += target
        }
        return out
    }

    fun prune(now: Instant) {
        val keys = endpoints.keys.toList()
        for (key in keys) {
            val history = endpoints[key] ?: continue
            val kept = history.samples.mapNotNull { sample ->
                if (!sample.valid) return@mapNotNull null
                val age = Duration.between(sample.measuredAt, now)
                if (age.isNegative || age > TunnelOverrides.CACHE_TTL) return@mapNotNull null
                sample
            }.sortedBy { it.measuredAt }.takeLast(TunnelOverrides.MAX_HISTORY)
            var limitReached = history.limitReached
            var checked = history.limitCheckedAt
            if (checked != null) {
                val age = Duration.between(checked, now)
                if (age.isNegative || age > TunnelOverrides.CACHE_TTL) {
                    limitReached = false
                    checked = null
                }
            }
            if (kept.isEmpty() && checked == null) {
                endpoints.remove(key)
            } else {
                endpoints[key] = history.copy(samples = kept, limitReached = limitReached, limitCheckedAt = checked)
            }
        }
    }
}

object TunnelAddresses {
    fun parse(address: String): Pair<String, Int>? {
        if (address != address.trim()) return null
        val (host, rawPort) = splitHostPort(address) ?: return null
        if (!validHost(host)) return null
        val port = rawPort.toIntOrNull() ?: return null
        if (port < 1 || port >= 65535) return null
        return host to port
    }

    /** The game port plus one. The catalog address itself is what the JVM flag carries. */
    fun probeAddress(address: String): String? {
        val (host, port) = parse(address) ?: return null
        val rendered = if (':' in host) "[$host]" else host
        return "$rendered:${port + 1}"
    }

    fun validLabel(label: String): Boolean {
        if (label.isEmpty() || label.length > 128 || label != label.trim()) return false
        return label.none { it.isISOControl() }
    }

    private fun splitHostPort(address: String): Pair<String, String>? {
        if (address.startsWith("[")) {
            val end = address.indexOf(']')
            if (end <= 1 || end + 1 >= address.length || address[end + 1] != ':') return null
            return address.substring(1, end) to address.substring(end + 2)
        }
        val idx = address.lastIndexOf(':')
        if (idx <= 0 || idx == address.lastIndex) return null
        return address.substring(0, idx) to address.substring(idx + 1)
    }

    private fun validHost(host: String): Boolean {
        if (host.isEmpty() || host.length > 253) return false
        if (isIpv4(host) || isIpv6(host)) return true
        val labels = host.split('.')
        for (label in labels) {
            if (label.isEmpty() || label.length > 63 || label.first() == '-' || label.last() == '-') return false
            if (label.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' && it != '-' }) return false
        }
        return true
    }

    private fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            if (part.isEmpty() || (part.length > 1 && part.startsWith('0'))) return@all false
            val value = part.toIntOrNull() ?: return@all false
            value in 0..255
        }
    }

    private fun isIpv6(host: String): Boolean {
        if (':' !in host || host.length > 45) return false
        if (host.any { it !in "0123456789abcdefABCDEF:" }) return false
        val halves = host.split("::", limit = 3)
        if (halves.size > 2) return false
        fun groups(text: String): Int {
            if (text.isEmpty()) return 0
            val items = text.split(':')
            if (items.any { it.isEmpty() || it.length > 4 }) return -1
            return items.size
        }
        val left = groups(halves[0])
        if (left < 0) return false
        if (halves.size == 1) return left == 8
        val right = groups(halves[1])
        if (right < 0) return false
        return left + right < 8
    }
}

object TunnelOverrides {
    const val VERSION = 1
    const val TOP_N = 5
    const val MAX_HISTORY = 20
    val RECENT_TTL: Duration = Duration.ofHours(1)
    val CACHE_TTL: Duration = Duration.ofHours(24)
    const val MAX_SETTINGS_BYTES = 1 shl 20
    const val MAX_CACHE_BYTES = 8 shl 20
    const val PRIORITY_HEAD_START_MS = 35L
    const val ROUND_TIMEOUT_MS = 1_000L

    fun cacheKey(region: TunnelRegion, address: String): String = "${region.id}|$address"

    fun formatMs(value: Double): String = when {
        value < 100.0 -> String.format(Locale.US, "%.1f", value)
        else -> String.format(Locale.US, "%.0f", value)
    }

    /** Green under 50 ms, amber under 100, red under 150, worse after that. */
    fun latencyBand(ms: Double): String = when {
        ms < 50.0 -> "good"
        ms < 100.0 -> "mid"
        ms < 150.0 -> "bad"
        else -> "very-bad"
    }

    fun loadSettings(path: Path): Pair<TunnelSettings, String?> = load(path, overridesOnly = false)

    fun loadForLaunch(path: Path): TunnelSettings = load(path, overridesOnly = true).first

    /**
     * Saved flags for a process this launcher starts itself.
     * Reads [file], checks each address, and returns. No catalog request and no probe.
     */
    fun commandWithOverrides(args: List<String>, file: Path): List<String> {
        val flags = loadForLaunch(file).jvmFlags()
        if (flags.isEmpty()) return args
        val identities = flags.map { flagIdentity(it) }.toSet()
        return args.filter { flagIdentity(it) !in identities } + flags
    }

    fun saveSettings(path: Path, settings: TunnelSettings) {
        val normalized = normalize(settings)
        write(path, settingsJson(normalized), MAX_SETTINGS_BYTES)
    }

    fun loadCache(path: Path): Pair<TunnelCache, String?> {
        if (!Files.exists(path)) return TunnelCache() to null
        return try {
            val text = readLimited(path, MAX_CACHE_BYTES)
            val cache = parseCache(text)
            if (cache.version != VERSION) TunnelCache() to "cache"
            else {
                cache.prune(Instant.now())
                cache to null
            }
        } catch (_: Exception) {
            TunnelCache() to "cache"
        }
    }

    fun saveCache(path: Path, cache: TunnelCache) {
        cache.version = VERSION
        cache.prune(Instant.now())
        write(path, cacheJson(cache), MAX_CACHE_BYTES)
    }

    private fun load(path: Path, overridesOnly: Boolean): Pair<TunnelSettings, String?> {
        if (!Files.exists(path)) return TunnelSettings() to null
        return try {
            val parsed = TunnelJson.parseSettings(readLimited(path, MAX_SETTINGS_BYTES), lenientSearch = overridesOnly)
            val settings = normalize(parsed)
            val overridesOk = settings.version == VERSION && settings.overrides.all { (region, selection) ->
                selection.region == region && selection.valid()
            }
            if (!overridesOk) return TunnelSettings() to "overrides"
            if (!overridesOnly) {
                val searchOk = settings.excludedPools.all { (region, pools) ->
                    pools.all { TunnelAddresses.validLabel(it) }
                }
                if (!searchOk) return TunnelSettings() to "overrides"
            }
            settings to null
        } catch (_: Exception) {
            TunnelSettings() to "overrides"
        }
    }

    private fun normalize(settings: TunnelSettings): TunnelSettings {
        val excluded = linkedMapOf<TunnelRegion, List<String>>()
        for ((region, pools) in settings.excludedPools) {
            val seen = LinkedHashSet<String>()
            for (pool in pools) {
                val name = pool.trim()
                if (name.isNotEmpty()) seen += name
            }
            if (seen.isNotEmpty()) excluded[region] = seen.toList()
        }
        return settings.copy(version = VERSION, excludedPools = excluded)
    }

    private fun flagIdentity(flag: String): String {
        val trimmed = flag.trim()
        if (trimmed.startsWith("-D")) {
            val eq = trimmed.indexOf('=')
            if (eq >= 0) return trimmed.substring(0, eq)
        }
        return trimmed
    }
}

internal object TunnelJson {
    fun parseSettings(text: String, lenientSearch: Boolean): TunnelSettings {
        val root = MiniJson.parse(text) as? Map<*, *> ?: error("overrides")
        val version = number(root["version"])?.toInt() ?: 0
        val overrides = linkedMapOf<TunnelRegion, TunnelSelection>()
        val rawOverrides = root["tunnel_overrides"] as? Map<*, *>
        rawOverrides?.forEach { (key, value) ->
            val region = TunnelRegion.fromId(key?.toString()) ?: error("region")
            val selection = selection(value) ?: error("selection")
            overrides[region] = selection
        }
        val legacy = selection(root["tunnel_override"])
        if (legacy != null && legacy.region !in overrides) overrides[legacy.region] = legacy
        val search = root["tunnel_search"] as? Map<*, *>
        val rawMode = search?.get("mode") as? String ?: ""
        val retiredGameWeight = rawMode == "game_weighted"
        val mode = TunnelSearchMode.fromId(rawMode) ?: TunnelSearchMode.PING
        if (rawMode.isNotEmpty() && TunnelSearchMode.fromId(rawMode) == null && !retiredGameWeight && !lenientSearch) error("mode")
        val excluded = linkedMapOf<TunnelRegion, List<String>>()
        val rawExcluded = search?.get("excluded_pools") as? Map<*, *>
        rawExcluded?.forEach { (key, value) ->
            val region = TunnelRegion.fromId(key?.toString()) ?: if (lenientSearch) return@forEach else error("region")
            val pools = (value as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            if (pools.isNotEmpty()) excluded[region] = pools
        }
        return TunnelSettings(version = if (version == 0) TunnelOverrides.VERSION else version, overrides = overrides, mode = mode, excludedPools = excluded)
    }

    fun parseCache(text: String): TunnelCache {
        val root = MiniJson.parse(text) as? Map<*, *> ?: error("cache")
        val cache = TunnelCache()
        cache.version = number(root["version"])?.toInt() ?: 0
        val endpoints = root["endpoints"] as? Map<*, *> ?: return cache
        endpoints.forEach { (_, value) ->
            val row = value as? Map<*, *> ?: return@forEach
            val region = TunnelRegion.fromId(row["region"] as? String) ?: return@forEach
            val address = row["address"] as? String ?: return@forEach
            val samples = (row["samples"] as? List<*>).orEmpty().mapNotNull { sample(it) }
            val checked = (row["last_limit_checked_at"] as? String)?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val history = EndpointHistory(
                region = region,
                pool = row["pool"] as? String ?: "",
                name = row["name"] as? String ?: "",
                address = address,
                samples = samples,
                limitReached = row["is_last_time_limit_reached"] == true,
                limitCheckedAt = checked,
            )
            cache.endpoints[TunnelOverrides.cacheKey(region, address)] = history
        }
        return cache
    }

    fun settingsJson(settings: TunnelSettings): String {
        val overrides = settings.overrides.entries.sortedBy { it.key.id }.joinToString(",\n") { (region, selection) ->
            val fields = ArrayList<String>()
            fields += """"region": ${quote(region.id)}"""
            if (selection.pool.isNotEmpty()) fields += """"pool": ${quote(selection.pool)}"""
            if (selection.name.isNotEmpty()) fields += """"name": ${quote(selection.name)}"""
            fields += """"address": ${quote(selection.address)}"""
            "    ${quote(region.id)}: {\n${fields.joinToString(",\n") { "      $it" }}\n    }"
        }
        val excluded = settings.excludedPools.entries.sortedBy { it.key.id }.joinToString(",\n") { (region, pools) ->
            "      ${quote(region.id)}: [${pools.joinToString(", ") { quote(it) }}]"
        }
        val searchFields = ArrayList<String>()
        searchFields += """    "mode": ${quote(settings.mode.id)}"""
        if (excluded.isNotEmpty()) searchFields += """    "excluded_pools": {
$excluded
    }"""
        val body = ArrayList<String>()
        body += """  "version": ${settings.version}"""
        if (overrides.isNotEmpty()) body += """  "tunnel_overrides": {
$overrides
  }"""
        body += """  "tunnel_search": {
${searchFields.joinToString(",\n")}
  }"""
        return "{\n${body.joinToString(",\n")}\n}\n"
    }

    fun cacheJson(cache: TunnelCache): String {
        val rows = cache.endpoints.entries.sortedBy { it.key }.joinToString(",\n") { (_, history) ->
            val samples = history.samples.joinToString(",\n") { sample ->
                val fields = ArrayList<String>()
                fields += """"measured_at": ${quote(sample.measuredAt.toString())}"""
                if (sample.clientRttMicros != 0L) fields += """"client_rtt_micros": ${sample.clientRttMicros}"""
                if (sample.serverRttMillis != 0L) fields += """"server_rtt_millis": ${sample.serverRttMillis}"""
                if (sample.timedOut) fields += """"timed_out": true"""
                "      { ${fields.joinToString(", ")} }"
            }
            val fields = ArrayList<String>()
            fields += """"region": ${quote(history.region.id)}"""
            fields += """"pool": ${quote(history.pool)}"""
            fields += """"name": ${quote(history.name)}"""
            fields += """"address": ${quote(history.address)}"""
            if (samples.isNotEmpty()) fields += """"samples": [
$samples
      ]"""
            if (history.limitReached) fields += """"is_last_time_limit_reached": true"""
            if (history.limitCheckedAt != null) fields += """"last_limit_checked_at": ${quote(history.limitCheckedAt.toString())}"""
            "    ${quote(TunnelOverrides.cacheKey(history.region, history.address))}: {\n${fields.joinToString(",\n") { "      $it" }}\n    }"
        }
        val endpoints = if (rows.isEmpty()) "{}" else "{\n$rows\n  }"
        return "{\n  \"version\": ${cache.version},\n  \"endpoints\": $endpoints\n}\n"
    }

    private fun selection(value: Any?): TunnelSelection? {
        val row = value as? Map<*, *> ?: return null
        val region = TunnelRegion.fromId(row["region"] as? String) ?: return null
        val address = row["address"] as? String ?: return null
        return TunnelSelection(
            region = region,
            pool = row["pool"] as? String ?: "",
            name = row["name"] as? String ?: "",
            address = address,
        )
    }

    private fun sample(value: Any?): TunnelSample? {
        val row = value as? Map<*, *> ?: return null
        val at = (row["measured_at"] as? String)?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        return TunnelSample(
            measuredAt = at,
            clientRttMicros = number(row["client_rtt_micros"])?.toLong() ?: 0L,
            serverRttMillis = number(row["server_rtt_millis"])?.toLong() ?: 0L,
            timedOut = row["timed_out"] == true,
        )
    }

    private fun number(value: Any?): Double? = when (value) {
        is Double -> value
        is Long -> value.toDouble()
        is Int -> value.toDouble()
        else -> null
    }

    private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

private fun parseCache(text: String) = TunnelJson.parseCache(text)

private fun settingsJson(settings: TunnelSettings) = TunnelJson.settingsJson(settings)

private fun cacheJson(cache: TunnelCache) = TunnelJson.cacheJson(cache)

private fun calculateMetrics(samples: List<TunnelSample>, now: Instant, maxAge: Duration): TunnelMetrics? {
    val recent = samples.filter { sample ->
        if (!sample.valid) return@filter false
        val age = Duration.between(sample.measuredAt, now)
        !age.isNegative && age <= maxAge
    }.sortedBy { it.measuredAt }
    if (recent.isEmpty()) return null
    var successes = 0
    var clientSum = 0.0
    var serverSum = 0.0
    val totals = ArrayList<Double>()
    var previous = 0.0
    var jitterTotal = 0.0
    var jitterSamples = 0
    for (sample in recent) {
        if (sample.timedOut) continue
        val client = sample.clientRttMicros / 1000.0
        val server = sample.serverRttMillis.toDouble()
        val total = client + server
        successes++
        clientSum += client
        serverSum += server
        totals += total
        if (successes > 1) {
            jitterTotal += abs(total - previous)
            jitterSamples++
        }
        previous = total
    }
    val loss = (recent.size - successes).toDouble() / recent.size.toDouble()
    if (successes == 0) {
        return TunnelMetrics(recent.size, 0, loss, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
    val client = clientSum / successes
    val server = serverSum / successes
    val sorted = totals.sorted()
    val index = (ceil(sorted.size * 0.9).toInt() - 1).coerceIn(0, sorted.lastIndex)
    val jitter = if (jitterSamples > 0) jitterTotal / jitterSamples else 0.0
    return TunnelMetrics(recent.size, successes, loss, client, server, client + server, sorted[index], jitter)
}

private fun readLimited(path: Path, maxBytes: Int): String {
    val size = Files.size(path)
    if (size > maxBytes) error("too large")
    return Files.readString(path)
}

private fun write(path: Path, text: String, maxBytes: Int) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    if (bytes.size > maxBytes) error("too large")
    Files.createDirectories(path.parent)
    val temp = Files.createTempFile(path.parent, ".${path.fileName}.", ".tmp")
    try {
        Files.write(temp, bytes)
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temp)
    }
}

package dev.launcherfork

import dev.launcherfork.data.exbo.ExboSettingsStore
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.ServerBlockState
import dev.launcherfork.stalzone.FirewallNotElevated
import dev.launcherfork.stalzone.MemoryRegistry
import dev.launcherfork.stalzone.ServerBlock
import dev.launcherfork.stalzone.ServerBlockCodec
import dev.launcherfork.stalzone.StalzoneDesk
import dev.launcherfork.stalzone.TunnelCatalog
import dev.launcherfork.stalzone.TunnelFirewall
import dev.launcherfork.stalzone.TunnelPing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import dev.launcherfork.stalzone.NodeProbe
import dev.launcherfork.stalzone.RoxyCatalog
import dev.launcherfork.stalzone.TunnelAddresses
import dev.launcherfork.stalzone.TunnelCache
import dev.launcherfork.stalzone.TunnelCatalogs
import dev.launcherfork.stalzone.TunnelEndpoint
import dev.launcherfork.stalzone.TunnelOverrides
import dev.launcherfork.stalzone.TunnelPool
import dev.launcherfork.stalzone.TunnelProbe
import dev.launcherfork.stalzone.TunnelProbeOutcome
import dev.launcherfork.stalzone.TunnelRegion
import dev.launcherfork.stalzone.TunnelSearchMode
import dev.launcherfork.stalzone.TunnelSelection
import dev.launcherfork.stalzone.TunnelSettings
import dev.launcherfork.stalzone.TunnelTarget
import dev.launcherfork.stalzone.toView
import java.net.ConnectException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.file.Path

class ServerBlockTest {
    @Test
    fun catalogKeepsRegionThenPoolAndCarriesAddresses() {
        assertEquals(listOf("RU", "EU", "NA", "SEA"), TunnelCatalog.regions().map { it.id })
        val msk1 = TunnelCatalog.regions().first { it.id == "RU" }.pools.first { it.name == "MSK1" }
        assertEquals("85.119.149.7", msk1.tunnels.first { it.name == "MSK1-1" }.host)
        val eu = TunnelCatalog.servers().first { it.name == "GAME-EU-2" }
        assertEquals("EU", eu.region)
        assertEquals("79.127.241.67", eu.host)
        assertEquals(29450, eu.port)
        assertEquals("GAME-EU::GAME-EU-2", eu.id)
        val msk2x = TunnelCatalog.regions().first { it.id == "RU" }.pools.first { it.name == "MSK2X" }
        assertEquals("111.88.96.18", msk2x.tunnels.first { it.name == "MSK2-ROXY1" }.host)
        assertEquals("EU", TunnelCatalog.regionOf("GAME-EU-2"))
    }

    @Test
    fun autoBestKeepsTopThreePoolWinnersAndBlocksTheRestOfTheRegion() {
        val ru = listOf(
            server("RU", "A", "a1", "1.1.1.1"),
            server("RU", "A", "a2", "1.1.1.2"),
            server("RU", "B", "b1", "1.1.1.3"),
            server("RU", "C", "c1", "1.1.1.4"),
            server("RU", "D", "d1", "1.1.1.5"),
            server("RU", "E", "e1", "1.1.1.6"),
        )
        val eu = server("EU", "G", "g1", "2.2.2.2")
        val pings = mapOf(
            "A::a1" to 50,
            "A::a2" to 80,
            "B::b1" to 40,
            "C::c1" to 100,
            "D::d1" to 10,
            "E::e1" to 250,
            "G::g1" to 15,
        )
        assertEquals(3, ServerBlock.AUTO_BEST_TOP_N)
        assertEquals(200, ServerBlock.PING_ACCEPTABLE_MAX_MS)
        val picked = ServerBlock.pickBestPerRegionTopN(pings, ru)
        assertEquals(listOf("D::d1", "B::b1", "A::a1"), picked.allowed)
        assertEquals(mapOf("D" to "D::d1", "B" to "B::b1", "A" to "A::a1"), picked.preferredByPool)
        val blockedInRu = ServerBlock.blockedExcept(ru, picked.allowed)
        assertEquals(listOf("A::a2", "C::c1", "E::e1"), blockedInRu)
        val merged = ServerBlock.mergeBlockedForScope(
            prevBlocked = listOf("G::g1", "A::a1"),
            scopeIds = ru.map { it.id },
            nextInScope = blockedInRu,
        )
        assertEquals(listOf("G::g1", "A::a2", "C::c1", "E::e1"), merged)
        assertEquals(listOf("1.1.1.2", "1.1.1.4", "1.1.1.6", "2.2.2.2"), ServerBlock.resolveBlockedHosts(ru + eu, merged))
        assertEquals(4, ServerBlock.poolsWithValidPing(pings, ru))
    }

    @Test
    fun badPingJoinsTheBlockListAndAFailedPingStaysDistinguishable() {
        val servers = listOf(server("RU", "A", "a1", "1.1.1.1"), server("RU", "A", "a2", "1.1.1.2"))
        val pings = mapOf("A::a1" to null, "A::a2" to 40)
        val (blocked, preferred) = ServerBlock.mergeAutoBlockUnacceptable(
            servers, pings, blocked = listOf("A::a2"), preferred = mapOf("A" to "A::a1"),
        )
        assertEquals(listOf("A::a2", "A::a1"), blocked)
        assertTrue(preferred.isEmpty())
        assertFalse(ServerBlock.shouldShowServer(pings, "A::a1"))
        assertTrue(ServerBlock.shouldShowServer(pings, "A::a1", pinging = true))
        assertTrue(ServerBlock.shouldShowServer(pings, "A::a2"))
        assertTrue(ServerBlock.shouldShowServer(emptyMap(), "A::a1"))
        val hosts = ServerBlock.resolveBlockedHosts(servers, blocked)
        assertEquals(listOf("1.1.1.1", "1.1.1.2"), hosts)
        assertTrue(ServerBlock.hostSetsEqual(hosts, listOf("1.1.1.1", "1.1.1.2")))
    }

    @Test
    fun pruneDropsIdsTheCatalogNoLongerHas() {
        val servers = listOf(server("RU", "A", "a1", "1.1.1.1"))
        val pruned = ServerBlock.prune(
            servers,
            ServerBlockState(
                blocked = listOf("A::a1", "gone"),
                pings = mapOf("A::a1" to 12, "gone" to 9),
                preferredByPool = mapOf("A" to "A::a1", "Z" to "Z::z"),
            ),
        )
        assertEquals(listOf("A::a1"), pruned.blocked)
        assertEquals(mapOf("A::a1" to 12), pruned.pings)
        assertEquals(mapOf("A" to "A::a1"), pruned.preferredByPool)
    }

    @Test
    fun pingCountsAFinishedRoundTripAndMissesTimeoutOrUnreachable() {
        assertEquals(28, TunnelPing.classifyPing(null, 28))
        assertEquals(12, TunnelPing.classifyPing(ConnectException("Connection refused: connect"), 12))
        assertEquals(15, TunnelPing.classifyPing(SocketException("Connection reset"), 15))
        assertNull(TunnelPing.classifyPing(SocketTimeoutException("Connect timed out"), 1_200))
        assertNull(TunnelPing.classifyPing(ConnectException("Connection timed out: connect"), 1_200))
        assertNull(TunnelPing.classifyPing(NoRouteToHostException("No route to host: connect"), 0))
        assertNull(TunnelPing.classifyPing(PortUnreachableException("ICMP Port Unreachable"), 4))
        assertNull(TunnelPing.classifyPing(SocketException("Network is unreachable: connect"), 0))
        assertNull(TunnelPing.classifyPing(SocketException("Permission denied: connect"), 0))
        assertNull(TunnelPing.classifyPing(IllegalStateException("boom"), 5))
        assertNull(TunnelPing.tcpPingMs("example.com", 29450, 200))
        assertNull(TunnelPing.tcpPingMs("1.2.3.4", 0, 200))
        assertNull(TunnelPing.tcpPingMs("1.2.3.4", 70_000, 200))
    }

    @Test
    fun firewallNamesMatchTheWrapperAndRefuseToRunWithoutAdmin() {
        assertEquals("STALZONE-SB-TCP-1-2-3-4", TunnelFirewall.ruleName("TCP", "1.2.3.4"))
        assertEquals("STALZONE-SB-UDP-1-2-3-4", TunnelFirewall.ruleName("UDP", "1.2.3.4"))
        assertEquals(listOf("1.1.1.1", "2.2.2.2"), ServerBlock.uniqueValidIpv4(listOf("1.1.1.1", "bad", " 2.2.2.2 ", "1.1.1.1", "::1")))
        val script = TunnelFirewall.applyScript(listOf("1.2.3.4"))
        assertTrue(script.contains("New-NetFirewallRule"))
        assertTrue(script.contains("-Direction Outbound"))
        assertTrue(script.contains("-Action Block"))
        assertTrue(script.contains("-RemotePort 29450-29460"))
        assertTrue(script.contains("STALZONE-SB-TCP-1-2-3-4"))
        assertTrue(script.contains("STALZONE-SB-UDP-1-2-3-4"))
        assertTrue(TunnelFirewall.CLEAR_SCRIPT.contains("Remove-NetFirewallRule"))
        assertTrue(TunnelFirewall.CLEAR_SCRIPT.contains("STALZONE-SB-"))
        var calls = 0
        val denied = TunnelFirewall(elevated = { false }, shell = { calls++; Result.success("0") })
        assertTrue(denied.apply(listOf("1.2.3.4")).exceptionOrNull() is FirewallNotElevated)
        assertTrue(denied.clear().exceptionOrNull() is FirewallNotElevated)
        assertEquals(0, calls, "a missing admin token must not spawn a firewall process")
        val scripts = mutableListOf<String>()
        val allowed = TunnelFirewall(elevated = { true }, shell = { body ->
            scripts += body
            if (body.contains("New-NetFirewallRule")) Result.success("ok") else Result.success("2")
        })
        assertTrue(allowed.apply(listOf("1.2.3.4", "not-an-ip", "1.2.3.4")).getOrThrow().contains("1"))
        assertTrue(scripts.first().contains("Remove-NetFirewallRule"))
        assertTrue(scripts.any { it.contains("RemoteAddress '1.2.3.4'") && !it.contains("not-an-ip") })
        var steps = 0
        val failing = TunnelFirewall(elevated = { true }, shell = { body ->
            steps++
            if (body.contains("New-NetFirewallRule")) Result.failure(IllegalStateException("access denied"))
            else Result.success("0")
        })
        assertTrue(failing.apply(listOf("9.9.9.9")).exceptionOrNull()!!.message!!.contains("access denied"))
        assertTrue(steps >= 3, "a failed apply rolls the rules back")
    }

    @Test
    fun selectionSurvivesARestartAndModeStaysBlocklist(@TempDir dir: Path) {
        val desk = StalzoneDesk(MemoryRegistry(), dir.resolve("sessions.json"), dir.resolve("configs"), dir.resolve("options.json"))
        val file = dir.resolve("settings.properties")
        val missing = dir.resolve("missing.json")
        val store = ExboSettingsStore(LauncherSettings(), desk, file, missing, 16_000)
        val state = ServerBlockState(
            blocked = listOf("MSK1::MSK1-1"),
            region = "EU",
            pings = mapOf("MSK1::MSK1-1" to 42, "MSK1::MSK1-2" to null),
            preferredByPool = mapOf("MSK1" to "MSK1::MSK1-1"),
            mode = "allowlist",
        )
        store.save(store.load().copy(serverBlock = state))
        val loaded = ExboSettingsStore(LauncherSettings(), desk, file, missing, 16_000).load().serverBlock
        assertEquals(listOf("MSK1::MSK1-1"), loaded.blocked)
        assertEquals("EU", loaded.region)
        assertEquals(42, loaded.pings["MSK1::MSK1-1"])
        assertTrue("MSK1::MSK1-2" in loaded.pings && loaded.pings["MSK1::MSK1-2"] == null)
        assertEquals("MSK1::MSK1-1", loaded.preferredByPool["MSK1"])
        assertEquals(ServerBlock.MODE, loaded.mode)
        assertEquals(ServerBlock.MODE, ServerBlockCodec.readMode("allowlist"))
        assertEquals("RU", ServerBlockCodec.readRegion("ALL"))
    }

    @Test
    fun addressValidationRejectsPortsAndKeepsDnsLookingHosts() {
        assertEquals("85.119.149.7" to 29450, TunnelAddresses.parse("85.119.149.7:29450"))
        assertEquals("::1" to 1, TunnelAddresses.parse("[::1]:1"))
        assertEquals("edge.example" to 29450, TunnelAddresses.parse("edge.example:29450"))
        assertEquals("01.2.3.4" to 29450, TunnelAddresses.parse("01.2.3.4:29450"))
        assertNull(TunnelAddresses.parse(" 85.119.149.7:29450"))
        assertNull(TunnelAddresses.parse("85.119.149.7:65535"))
        assertNull(TunnelAddresses.parse("85.119.149.7:0"))
        assertNull(TunnelAddresses.parse("not a host:1"))
        assertNull(TunnelAddresses.parse("-bad.example:29450"))
    }

    @Test
    fun flagsFollowRegionOrderAndABadAddressDropsEveryRegion() {
        val eu = TunnelSelection(TunnelRegion.EU, "GAME-EU", "GAME-EU-2", "79.127.241.67:29450")
        val ru = TunnelSelection(TunnelRegion.RU, "MSK1", "MSK1-1", "85.119.149.7:29450")
        val settings = TunnelSettings().withOverride(eu).withOverride(ru)
        assertEquals(
            listOf(
                "-Droxy_address_override.ru=85.119.149.7:29450",
                "-Droxy_address_override.eu=79.127.241.67:29450",
            ),
            settings.jvmFlags(),
        )
        assertTrue(settings.withOverride(TunnelSelection(TunnelRegion.RU, "MSK1", "MSK1-1", "85.119.149.7:65535")).jvmFlags().isEmpty())
    }

    @Test
    fun clearingRuKeepsEu() {
        val settings = TunnelSettings(
            overrides = mapOf(
                TunnelRegion.RU to TunnelSelection(TunnelRegion.RU, "MSK1", "MSK1-1", "85.119.149.7:29450"),
                TunnelRegion.EU to TunnelSelection(TunnelRegion.EU, "GAME-EU", "GAME-EU-2", "79.127.241.67:29450"),
            ),
        )
        val cleared = settings.withoutOverride(TunnelRegion.RU)
        assertNull(cleared.override(TunnelRegion.RU))
        assertEquals("79.127.241.67:29450", cleared.override(TunnelRegion.EU)!!.address)
    }

    @Test
    fun topFiveSkipsExcludedPoolsAndConnectionLimits() {
        val now = Instant.parse("2026-10-24T00:00:00Z")
        val cache = TunnelCache()
        val probes = linkedMapOf<String, NodeProbe>()
        fun node(pool: String, name: String, host: String, client: Long, server: Long, limited: Boolean): TunnelTarget {
            val target = TunnelTarget(TunnelRegion.RU, pool, TunnelEndpoint(name, "$host:29450"))
            val outcome = TunnelProbeOutcome.Reply(Duration.ofMillis(client), Duration.ofMillis(server), limited)
            cache.record(target, outcome, now)
            probes[target.key()] = outcome.toView()
            return target
        }
        node("FAST", "n1", "203.0.113.1", 1, 1, false)
        val limited = node("REST", "n2", "203.0.113.2", 1, 1, true)
        val rest = (3..7).map { n -> node("REST", "n$n", "203.0.113.$n", n.toLong(), 1, false) }
        val catalog = RoxyCatalog(
            "roxy",
            listOf(
                TunnelPool("FAST", listOf(TunnelEndpoint("n1", "203.0.113.1:29450"))),
                TunnelPool("REST", listOf(TunnelEndpoint("n2", "203.0.113.2:29450")) + rest.map { it.endpoint }),
            ),
            1.0,
        )
        val targets = catalog.targets(TunnelRegion.RU) { it == "FAST" }
        assertTrue(targets.none { it.pool == "FAST" })
        assertFalse(probes.getValue(limited.key()).selectable)
        val ranked = cache.best(targets, probes, TunnelSearchMode.PING, 1.0, now)
        assertEquals(TunnelOverrides.TOP_N, ranked.size)
        assertEquals(listOf("n3", "n4", "n5", "n6", "n7"), ranked.map { it.target.endpoint.name })
        assertFalse(ranked.any { it.target.endpoint.name == "n2" })
        val cachedOnly = TunnelTarget(TunnelRegion.RU, "REST", TunnelEndpoint("cached", "203.0.113.20:29450"))
        cache.record(cachedOnly, TunnelProbeOutcome.Reply(Duration.ofMillis(1), Duration.ofMillis(1), false), now)
        probes[cachedOnly.key()] = NodeProbe()
        assertFalse(probes.getValue(cachedOnly.key()).selectable)
        assertTrue(cache.best(targets + cachedOnly, probes, TunnelSearchMode.PING, 1.0, now).none { it.target.endpoint.name == "cached" })
    }

    @Test
    fun historyKeepsTwentySamplesForADayAndRanksTheLastHour() {
        val now = Instant.parse("2026-10-24T12:00:00Z")
        val cache = TunnelCache()
        val target = TunnelTarget(TunnelRegion.RU, "MSK1", TunnelEndpoint("h", "203.0.113.50:29450"))
        repeat(25) {
            cache.record(target, TunnelProbeOutcome.Reply(Duration.ofMillis(10), Duration.ofMillis(1), false), now.minusSeconds(it.toLong()))
        }
        assertEquals(20, cache.endpoints.getValue(target.key()).samples.size)
        cache.record(target, TunnelProbeOutcome.Reply(Duration.ofMillis(10), Duration.ofMillis(1), false), now.minus(Duration.ofHours(25)))
        cache.prune(now)
        assertTrue(cache.endpoints.getValue(target.key()).samples.none { Duration.between(it.measuredAt, now) > TunnelOverrides.CACHE_TTL })
        cache.record(target, TunnelProbeOutcome.Reply(Duration.ofMillis(10), Duration.ofMillis(1), false), now.minus(Duration.ofHours(2)))
        val kept = cache.endpoints.getValue(target.key()).samples
        val recent = cache.metrics(TunnelRegion.RU, target.endpoint.address, now)!!
        assertTrue(recent.samples < kept.size)
    }

    @Test
    fun gameWeightAndPingUseTheMeasuredRtts() {
        val now = Instant.parse("2026-10-24T00:00:00Z")
        val cache = TunnelCache()
        val target = TunnelTarget(TunnelRegion.EU, "GAME-EU", TunnelEndpoint("g", "79.127.241.67:29450"))
        cache.record(target, TunnelProbeOutcome.Reply(Duration.ofMillis(20), Duration.ofMillis(10), false), now)
        val metrics = cache.metrics(TunnelRegion.EU, target.endpoint.address, now)!!
        assertEquals(40.0, metrics.score(TunnelSearchMode.GAME, 1.5)!!, 0.001)
        assertEquals(30.0, metrics.score(TunnelSearchMode.PING, 1.5)!!, 0.001)
    }

    @Test
    fun overridesRoundTripAndLegacyNeaMigrates(@TempDir dir: Path) {
        val file = dir.resolve("overrides.json")
        val original = TunnelSettings(
            overrides = mapOf(
                TunnelRegion.RU to TunnelSelection(TunnelRegion.RU, "MSK1", "MSK1-1", "85.119.149.7:29450"),
                TunnelRegion.EU to TunnelSelection(TunnelRegion.EU, "GAME-EU", "GAME-EU-2", "79.127.241.67:29450"),
            ),
            mode = TunnelSearchMode.STABILITY,
            excludedPools = mapOf(TunnelRegion.RU to listOf("MSK1")),
        )
        TunnelOverrides.saveSettings(file, original)
        val loaded = TunnelOverrides.loadSettings(file).first
        assertEquals(TunnelSearchMode.STABILITY, loaded.mode)
        assertEquals(listOf("MSK1"), loaded.excludedPools[TunnelRegion.RU])
        assertEquals("85.119.149.7:29450", loaded.override(TunnelRegion.RU)!!.address)
        assertEquals("79.127.241.67:29450", loaded.override(TunnelRegion.EU)!!.address)

        Files.writeString(
            file,
            """{"version":1,"tunnel_override":{"region":"nea","pool":"NEA1","name":"NEA1-1","address":"203.0.113.10:29450"},"tunnel_search":{"mode":"ping"}}""",
        )
        assertEquals(
            listOf("-Droxy_address_override.nea=203.0.113.10:29450"),
            TunnelOverrides.loadForLaunch(file).jvmFlags(),
        )
    }

    @Test
    fun launchReadsSavedFlagsAndDoesNotMeasure(@TempDir dir: Path) {
        val file = dir.resolve("overrides.json")
        TunnelOverrides.saveSettings(
            file,
            TunnelSettings(overrides = mapOf(TunnelRegion.RU to TunnelSelection(TunnelRegion.RU, "MSK1", "MSK1-1", "85.119.149.7:29450"))),
        )
        val fetches = TunnelCatalogs.fetchAttempts.get()
        val probes = TunnelProbe.attempts.get()
        val args = TunnelOverrides.commandWithOverrides(
            listOf("java", "-Droxy_address_override.ru=203.0.113.9:1"),
            file,
        )
        assertEquals(fetches, TunnelCatalogs.fetchAttempts.get())
        assertEquals(probes, TunnelProbe.attempts.get())
        assertTrue(args.contains("-Droxy_address_override.ru=85.119.149.7:29450"))
        assertFalse(args.any { it.contains("203.0.113.9") })

        Files.writeString(
            file,
            """{"version":1,"tunnel_overrides":{"ru":{"region":"ru","address":"1.2.3.4:65535"},"eu":{"region":"eu","address":"79.127.241.67:29450"}},"tunnel_search":{"mode":"ping"}}""",
        )
        assertEquals(listOf("java"), TunnelOverrides.commandWithOverrides(listOf("java"), file))

        Files.writeString(
            file,
            """{"version":1,"tunnel_overrides":{"ru":{"region":"ru","address":"85.119.149.7:29450"}},"tunnel_search":{"mode":"not-a-mode"}}""",
        )
        assertEquals(
            listOf("-Droxy_address_override.ru=85.119.149.7:29450"),
            TunnelOverrides.loadForLaunch(file).jvmFlags(),
        )
        assertTrue(TunnelOverrides.loadSettings(file).first.overrides.isEmpty())
    }

    @Test
    fun regeneratingAJvmConfigLeavesOverrideFiles(@TempDir dir: Path) {
        val wrapper = dir.resolve("jvm_wrapper")
        val overrides = wrapper.resolve("overrides.json")
        val stats = wrapper.resolve("cache").resolve("tunnel_stats.json")
        Files.createDirectories(stats.parent)
        Files.writeString(overrides, "keep-overrides")
        Files.writeString(stats, "keep-stats")
        val desk = StalzoneDesk(MemoryRegistry(), dir.resolve("sessions.json"), wrapper.resolve("configs"), dir.resolve("options.json"))
        desk.activatePreset("v1.1.2/default", """{"heap_size_gb":6}""")
        assertEquals("keep-overrides", Files.readString(overrides))
        assertEquals("keep-stats", Files.readString(stats))
    }

    @Test
    fun probeCodecRejectsAShortOrNegativeReplyWithoutASocket() {
        val token = ByteArray(TunnelProbe.REQUEST_SIZE) { it.toByte() }
        val packet = ByteArray(TunnelProbe.RESPONSE_SIZE)
        token.copyInto(packet)
        ByteBuffer.wrap(packet, 16, 4).putInt(8)
        packet[20] = 1
        assertEquals(8L to true, TunnelProbe.readReply(token, packet))
        assertNull(TunnelProbe.readReply(token, ByteArray(20)))
        val wrong = packet.copyOf()
        wrong[0] = (wrong[0].toInt() + 1).toByte()
        assertNull(TunnelProbe.readReply(token, wrong))
        val negative = packet.copyOf()
        ByteBuffer.wrap(negative, 16, 4).putInt(-1)
        assertThrows(IllegalStateException::class.java) { TunnelProbe.readReply(token, negative) }
        assertFalse(TunnelProbeOutcome.Reply(Duration.ofMillis(5), Duration.ofMillis(4), true).toView().selectable)
        assertTrue(TunnelProbeOutcome.Reply(Duration.ofMillis(12), Duration.ofMillis(8), false).toView().selectable)
    }

    private fun server(region: String, pool: String, name: String, host: String) = TunnelCatalog.Server(
        id = "$pool::$name",
        pool = pool,
        name = name,
        address = "$host:29450",
        host = host,
        port = 29450,
        region = region,
    )
}

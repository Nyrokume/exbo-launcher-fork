package dev.launcherfork

import dev.launcherfork.stalzone.ExboKey
import dev.launcherfork.stalzone.ExboSession
import dev.launcherfork.stalzone.ExboSessions
import dev.launcherfork.stalzone.ExboValue
import dev.launcherfork.stalzone.JvmPresets
import dev.launcherfork.stalzone.JvmProfile
import dev.launcherfork.stalzone.MemoryRegistry
import dev.launcherfork.stalzone.TunnelCatalog
import dev.launcherfork.stalzone.OptionsSens
import dev.launcherfork.stalzone.ScopeSens
import dev.launcherfork.stalzone.SessionFile
import dev.launcherfork.stalzone.StalzoneDesk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class StalzoneCoreTest {
    @Test
    fun wikiTableMatchesTheRegeditFormula() {
        val values = ScopeSens.snapshot(5.5)
        assertEquals(5.5, values[1.0])
        assertTrue(values[8.0]!! > values[1.0]!!)
        assertEquals(ScopeSens.MAGNIFICATIONS.toSet(), values.keys)
        assertEquals("1.0", ScopeSens.magKey(1.0))
        assertEquals("1.25", ScopeSens.magKey(1.25))
    }

    @Test
    fun applyForcesAutoAuthAndKeepsLogin() {
        val snap = ExboKey(
            name = "EXBO",
            path = ExboSessions.KEY_PATH,
            values = listOf(
                ExboValue("EXBO_LOGIN", "DementiyRezak"),
                ExboValue("EXBO_SESSION", "token"),
                ExboValue("EXBO_AUTO_AUTH", "false"),
                ExboValue("OTHER", "ignored"),
            ),
        )
        assertEquals(
            mapOf("EXBO_AUTO_AUTH" to "true", "EXBO_LOGIN" to "DementiyRezak", "EXBO_SESSION" to "token"),
            ExboSessions.applyPlan(snap),
        )
    }

    @Test
    fun sessionFileRoundTrip(@TempDir dir: Path) {
        val file = dir.resolve("sessions.json")
        val session = ExboSessions.capture("main", 1, "2026-10-22T00:00:00Z", mapOf("EXBO_LOGIN" to "Nyrokume"))
        SessionFile.save(file, listOf(session))
        val loaded = SessionFile.load(file)
        assertEquals("Nyrokume", loaded.single().data.values.single { it.name == "EXBO_LOGIN" }.value)
        assertEquals("main", loaded.single().name)
    }

    @Test
    fun tunedProfileIsTheJdk9Preset() {
        val json = JvmProfile.tunedJson()
        assertTrue(json.contains("\"heap_size_gb\": 6"))
        assertTrue(json.contains("\"max_gc_pause_millis\": 20"))
        assertTrue(json.contains("\"g1_heap_region_size_mb\": 16"))
        assertTrue(json.contains("\"max_tenuring_threshold\": 4"))
        assertTrue(json.contains("\"use_string_deduplication\": false"))
        assertTrue(json.contains("\"thread_priority_policy\": 0"))
    }

    @Test
    fun deskActivatesProfileAndSession(@TempDir dir: Path) {
        val registry = MemoryRegistry()
        registry.writeValues(ExboSessions.KEY_PATH, mapOf("EXBO_LOGIN" to "Nyrokume", "EXBO_SESSION" to "abc"))
        val desk = StalzoneDesk(
            registry,
            dir.resolve("sessions.json"),
            dir.resolve("configs"),
            dir.resolve("options.json"),
        )
        desk.activateTunedProfile()
        assertEquals("8khz", desk.activeProfile())
        desk.useTunedProfile(false)
        assertEquals(null, desk.activeProfile())
        desk.useTunedProfile(true)
        assertEquals("8khz", desk.activeProfile())
        assertTrue(Files.readString(dir.resolve("configs").resolve("8khz.json")).contains("\"heap_size_gb\": 6"))

        val current = JvmPresets.build(JvmPresets.CURRENT, 16, 16, 8, 8)!!
        assertEquals(6, current.heapGb)
        assertTrue(current.json.contains("\"max_gc_pause_millis\": 100"))
        desk.activatePreset(JvmPresets.CURRENT, current.json)
        assertEquals(JvmPresets.CURRENT, desk.activeProfile())
        assertTrue(Files.readString(dir.resolve("configs").resolve("v1.1.2").resolve("default.json")).contains("\"heap_size_gb\": 6"))
        val aggressive = JvmPresets.build("v1.1.0/default", 24, 24, 8, 8)!!
        assertEquals(8, aggressive.heapGb)
        assertTrue(aggressive.json.contains("\"use_string_deduplication\": true"))
        val aikar = JvmPresets.build("aikar", 8, 8, 4, 4)!!
        assertEquals(6, aikar.heapGb)
        assertTrue(aikar.json.contains("\"max_gc_pause_millis\": 150"))
        assertEquals(null, JvmPresets.build("v1.0.4/default", 8, 8, 4, 4))

        val chosen = JvmPresets.build(JvmPresets.CURRENT, 32, 32, 16, 8, heapGb = 4)!!
        assertEquals(4, chosen.heapGb)
        assertTrue(chosen.json.contains("\"heap_size_gb\": 4"))
        assertTrue(chosen.json.contains("\"g1_heap_region_size_mb\": 8"))
        assertTrue(chosen.json.contains("\"max_gc_pause_millis\": 100"))
        val small = JvmPresets.build("v1.1.0/default", 24, 24, 8, 8, heapGb = 3)!!
        assertEquals(3, small.heapGb)
        assertTrue(small.json.contains("\"g1_heap_region_size_mb\": 4"))
        assertTrue(small.json.contains("\"use_string_deduplication\": true"))
        val legacy = JvmPresets.build("v1.0.6/default", 16, 16, 8, 4, heapGb = 4)!!
        assertEquals(4, legacy.heapGb)
        assertTrue(legacy.json.contains("\"metaspace_mb\": 128"))
        assertTrue(legacy.json.contains("\"g1_heap_region_size_mb\": 4"))
        assertTrue(legacy.json.contains("\"reserved_code_cache_size_mb\": 256"))
        assertTrue(legacy.json.contains("\"soft_ref_lru_policy_ms_per_mb\": 10"))
        val aikarHeap = JvmPresets.build("aikar", 32, 32, 8, 8, heapGb = 4)!!
        assertEquals(4, aikarHeap.heapGb)
        assertTrue(aikarHeap.json.contains("\"max_gc_pause_millis\": 150"))
        assertTrue(aikarHeap.json.contains("\"g1_heap_region_size_mb\": 8"))

        val ru = TunnelCatalog.regions().first { it.id == "RU" }
        val msk1 = ru.pools.first { it.name == "MSK1" }
        assertEquals(listOf("MSK1-1", "MSK1-2", "MSK1-3", "MSK1-4", "MSK1-5"), msk1.tunnels.map { it.name })
        assertEquals("85.119.149.7", msk1.tunnels.first().host)
        assertEquals(29450, msk1.tunnels.first().port)
        assertTrue(ru.pools.none { it.name == "GAME-EU" })
        assertEquals("EU", TunnelCatalog.regionOf("GAME-EU-2"))
        assertEquals(listOf("RU", "EU", "NA", "SEA"), TunnelCatalog.regions().map { it.id })

        assertEquals("Nyrokume", desk.liveAccount()["EXBO_LOGIN"])
        val saved = desk.captureCurrent("alt")
        assertTrue(desk.rename(saved.id, "  Новая  "))
        val renamed = desk.sessions().single { it.id == saved.id }
        assertEquals("Новая", renamed.name)
        assertEquals(saved.createdAt, renamed.createdAt)
        assertEquals(saved.data, renamed.data)
        assertFalse(desk.rename(saved.id, "   "))
        assertEquals("Новая", desk.sessions().single { it.id == saved.id }.name)
        assertFalse(desk.rename(99, "другая"))
        desk.apply(renamed)
        assertEquals("true", registry.readValues(ExboSessions.KEY_PATH)["EXBO_AUTO_AUTH"])
        assertTrue(ExboSessions.isCurrent(saved, desk.liveAccount()))

        Files.writeString(dir.resolve("options.json"), """{ "mouse_sensitivity": 1.0, "fps_limit": 165 }""")
        desk.writeSensitivity(5.5)
        val options = Files.readString(dir.resolve("options.json"))
        assertTrue(options.contains("\"mouse_sensitivity\": 5.5"))
        assertTrue(options.contains("\"sensitivity_zoom_1.0\": 5.5"))
        assertTrue(options.contains("\"fps_limit\": 165"))
        assertEquals(5.5, ScopeSens.snapshot(5.5)[1.0])
        assertTrue(OptionsSens.patch("{ }", 2.0, ScopeSens.snapshot(2.0)).contains("sensitivity_zoom_8.0"))
    }

    @Test
    fun ramBarRewritesTheProfileTheWrapperInjects(@TempDir dir: Path) {
        val registry = MemoryRegistry()
        val configs = dir.resolve("configs")
        val desk = StalzoneDesk(registry, dir.resolve("sessions.json"), configs, dir.resolve("options.json"))
        val preset = JvmPresets.build(JvmPresets.CURRENT, 32, 32, 8, 8)!!
        desk.activatePreset(JvmPresets.CURRENT, preset.json)
        Files.writeString(configs.resolve("default.json"), """{ "heap_size_gb": 6, "g1_heap_region_size_mb": 8 }""")
        desk.syncHeap(4)
        val active = Files.readString(configs.resolve("v1.1.2").resolve("default.json"))
        assertTrue(active.contains("\"heap_size_gb\": 4"))
        assertTrue(active.contains("\"max_gc_pause_millis\": 100"))
        assertTrue(active.contains("\"g1_heap_region_size_mb\": 8"))
        assertTrue(Files.readString(configs.resolve("default.json")).contains("\"heap_size_gb\": 6"), "another profile stays")

        desk.syncHeap(3)
        val low = Files.readString(configs.resolve("v1.1.2").resolve("default.json"))
        assertTrue(low.contains("\"heap_size_gb\": 3"))
        assertTrue(low.contains("\"g1_heap_region_size_mb\": 4"))
        assertTrue(low.contains("\"parallel_gc_threads\":"))

        desk.activateTunedProfile()
        desk.syncHeap(4)
        val tuned = Files.readString(configs.resolve("8khz.json"))
        assertTrue(tuned.contains("\"heap_size_gb\": 4"))
        assertTrue(tuned.contains("\"g1_heap_region_size_mb\": 16"))
        assertTrue(tuned.contains("\"max_gc_pause_millis\": 20"))

        registry.deleteValue(JvmProfile.ACTIVE_KEY, JvmProfile.ACTIVE_VALUE)
        assertEquals(null, desk.activeProfile())
        desk.syncHeap(2)
        assertTrue(Files.readString(configs.resolve("default.json")).contains("\"heap_size_gb\": 2"))
        assertTrue(Files.readString(configs.resolve("default.json")).contains("\"g1_heap_region_size_mb\": 4"))
        assertEquals(null, desk.activeProfile())
        assertTrue(Files.readString(configs.resolve("8khz.json")).contains("\"heap_size_gb\": 4"), "the 8 kHz file is not the fallback")

        val empty = StalzoneDesk(MemoryRegistry(), dir.resolve("none.json"), dir.resolve("missing"), dir.resolve("options.json"))
        empty.syncHeap(8)
        assertFalse(Files.exists(dir.resolve("missing")))
    }

    @Test
    fun currentSessionMatchesLiveLoginAndToken() {
        val live = mapOf("EXBO_LOGIN" to "Nyrokume", "EXBO_SESSION" to "abc")
        val same = ExboSessions.capture("main", 1, "t", live)
        val otherToken = ExboSessions.capture("alt", 2, "t", mapOf("EXBO_LOGIN" to "Nyrokume", "EXBO_SESSION" to "other"))
        val loginOnly = ExboSessions.capture("x", 3, "t", mapOf("EXBO_LOGIN" to "Nyrokume"))
        assertTrue(ExboSessions.isCurrent(same, live))
        assertFalse(ExboSessions.isCurrent(otherToken, live))
        assertFalse(ExboSessions.isCurrent(loginOnly, live))
        assertTrue(ExboSessions.isCurrent(loginOnly, mapOf("EXBO_LOGIN" to "Nyrokume")))
        assertFalse(ExboSessions.isCurrent(same, emptyMap()))
        val nested = ExboSession(
            id = 4,
            name = "nested",
            createdAt = "t",
            data = ExboKey(
                name = "EXBO",
                path = ExboSessions.KEY_PATH,
                subkeys = listOf(
                    ExboKey(
                        name = "child",
                        path = "SOFTWARE\\EXBO\\child",
                        values = listOf(ExboValue("EXBO_LOGIN", "deep")),
                    ),
                ),
            ),
        )
        assertEquals(listOf("EXBO_LOGIN"), ExboSessions.storedValues(nested.data).map { it.name })
        assertEquals("deep", ExboSessions.storedValues(nested.data).single().value)
        assertTrue(ExboSessions.isCurrent(nested, mapOf("EXBO_LOGIN" to "deep")))
    }
}

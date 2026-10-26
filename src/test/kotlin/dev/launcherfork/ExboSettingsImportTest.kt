package dev.launcherfork

import dev.launcherfork.data.exbo.ExboSettingsStore
import dev.launcherfork.data.exbo.exboHeapMb
import dev.launcherfork.data.exbo.importExboSettings
import dev.launcherfork.domain.ExitMode
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.P2pMode
import dev.launcherfork.stalzone.MemoryRegistry
import dev.launcherfork.stalzone.StalzoneDesk
import dev.launcherfork.stalzone.JvmPresets
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ExboSettingsImportTest {
    @Test
    fun fixtureJsonIsByteIdenticalAfterLoad(@TempDir dir: Path) {
        val json = dir.resolve("settings.json")
        val text = """
            {"tray":true,"exit_new":0,"download":15,"start_on_downloaded":false,"upload":4,"run_on_start":true,"ram":1.0,"p2p":2,"defender":false}
        """.trim()
        Files.writeString(json, text)
        val before = Files.readAllBytes(json)
        val props = dir.resolve("settings.properties")
        val desk = StalzoneDesk(MemoryRegistry(), dir.resolve("sessions.json"), dir.resolve("jvm"), dir.resolve("options.json"))
        val store = ExboSettingsStore(
            initial = LauncherSettings(language = "en"),
            desk = desk,
            file = props,
            exboJson = json,
            totalRamMb = 16L * 1024,
        )
        val loaded = store.load()
        assertArrayEquals(before, Files.readAllBytes(json))
        assertEquals(exboHeapMb(1.0, 16L * 1024), loaded.ramMb)
        assertEquals(4096, loaded.ramMb)
        assertEquals(ExitMode.ON_GAME_START, loaded.exitMode)
        assertEquals(P2pMode.NEVER, loaded.p2pMode)
        assertEquals(15, loaded.downloadLimitKb)
        assertEquals(4, loaded.uploadLimitKb)
        assertFalse(loaded.startOnDownloaded)
        assertEquals(true, loaded.runOnStartUp)
        assertEquals(false, loaded.defenderHint)
        assertEquals(true, loaded.hideToTray)
        assertEquals("en", loaded.language)
        assertFalse(Files.readString(json).contains("ramMb"))
    }

    @Test
    fun storedRamRewritesTheActiveJvmProfile(@TempDir dir: Path) {
        val configs = dir.resolve("jvm")
        val desk = StalzoneDesk(MemoryRegistry(), dir.resolve("sessions.json"), configs, dir.resolve("options.json"))
        desk.activatePreset(JvmPresets.CURRENT, JvmPresets.build(JvmPresets.CURRENT, 32, 32, 8, 8)!!.json)
        val props = dir.resolve("settings.properties")
        Files.writeString(props, "ramMb=4096\n")
        val store = ExboSettingsStore(
            desk = desk,
            file = props,
            exboJson = dir.resolve("missing.json"),
            totalRamMb = 32L * 1024,
        )
        assertEquals(4096, store.load().ramMb)
        val text = Files.readString(configs.resolve("v1.1.2").resolve("default.json"))
        assertTrue(text.contains("\"heap_size_gb\": 4"), text)
        assertTrue(text.contains("\"max_gc_pause_millis\": 100"))
        store.save(store.load().copy(ramMb = 3 * 1024))
        val next = Files.readString(configs.resolve("v1.1.2").resolve("default.json"))
        assertTrue(next.contains("\"heap_size_gb\": 3"), next)
        assertTrue(next.contains("\"g1_heap_region_size_mb\": 4"))
    }

    @Test
    fun importDoesNotRequireAFile() {
        val settings = importExboSettings("""{"ram":0.0,"exit_new":1,"p2p":0}""", 8192, "ru")
        assertEquals(512, settings.ramMb)
        assertEquals(ExitMode.ON_GAME_EXIT, settings.exitMode)
        assertEquals(P2pMode.ALWAYS, settings.p2pMode)
    }
}

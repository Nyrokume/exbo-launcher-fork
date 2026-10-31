package dev.launcherfork

import dev.launcherfork.UiTestKit.applyTheme
import dev.launcherfork.app.AppContext
import dev.launcherfork.data.exbo.HostSystemInfo
import dev.launcherfork.data.exbo.MachineTables
import dev.launcherfork.domain.MachineProfile
import dev.launcherfork.domain.SystemInfo
import dev.launcherfork.stalzone.JvmPresets
import dev.launcherfork.stalzone.MemoryRegistry
import dev.launcherfork.stalzone.StalzoneDesk
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.screens.settings.SettingsController
import javafx.scene.Scene
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MachineProfileTest {
    @Test
    fun memorySpeedAndCacheFollowTheGeneratorRules() {
        val slow = ByteArray(0x22)
        slow[0x0C] = 8
        slow[0x20] = 0x40
        slow[0x21] = 0x06
        assertEquals(1600, MachineTables.memDeviceSpeedMts(slow))

        val extended = ByteArray(0x5C)
        extended[0x0C] = 8
        extended[0x20] = 0xFF.toByte()
        extended[0x21] = 0xFF.toByte()
        extended[0x58] = 0x20
        extended[0x59] = 0x1C
        assertEquals(7200, MachineTables.memDeviceSpeedMts(extended))

        val empty = ByteArray(0x22)
        assertEquals(null, MachineTables.memDeviceSpeedMts(empty))

        val table = ByteArray(8 + 0x22 + 2)
        table[4] = (0x22 + 2).toByte()
        table[8 + 0] = 17
        table[8 + 1] = 0x22
        table[8 + 0x0C] = 8
        table[8 + 0x20] = 0x55
        table[8 + 0x21] = 0x0B
        assertEquals(2901, MachineTables.smbiosBestMemSpeed(table))
        assertEquals(0, MachineTables.smbiosBestMemSpeed(ByteArray(4)))

        assertEquals(2, MachineTables.countProcessorCores(record(16) + record(24)))
        val l3 = cacheRecord(level = 3, bytes = 96L * 1024 * 1024) + cacheRecord(level = 3, bytes = 32L * 1024 * 1024) +
            cacheRecord(level = 2, bytes = 1024L * 1024)
        assertEquals(96, MachineTables.maxUnifiedL3Mb(l3))

        val mid = MachineProfile(16L * 1024, 16L * 1024, 16, 8, l3Mb = 32, memSpeedMts = 0)
        val ddr = mid.copy(memSpeedMts = 2933, l3Mb = 96)
        assertFalse(mid.slowRam)
        assertFalse(mid.bigCache)
        assertTrue(ddr.slowRam)
        assertTrue(ddr.bigCache)
        assertFalse(ddr.copy(memSpeedMts = 2934).slowRam)
        assertEquals(16, mid.totalGb)
        assertEquals(7, mid.copy(freeMb = 7L * 1024 + 900).freeGb)
    }

    @Test
    fun presetsFollowTheMachineTheyAreGiven() {
        val slow = JvmPresets.build(JvmPresets.CURRENT, 16, 16, 8, 8, slowRam = true, largePages = true)!!
        val mid = JvmPresets.build(JvmPresets.CURRENT, 16, 16, 8, 8)!!
        assertTrue(slow.json.contains("\"max_gc_pause_millis\": 150"))
        assertTrue(slow.json.contains("\"g1_new_size_percent\": 30"))
        assertTrue(slow.json.contains("\"use_large_pages\": true"))
        assertTrue(mid.json.contains("\"max_gc_pause_millis\": 100"))
        assertTrue(mid.json.contains("\"use_large_pages\": false"))

        val cache = JvmPresets.build("v1.1.0/default", 16, 16, 8, 8, bigCache = true)!!
        val plain = JvmPresets.build("v1.1.0/default", 16, 16, 8, 8)!!
        assertTrue(cache.json.contains("\"max_gc_pause_millis\": 25"))
        assertTrue(plain.json.contains("\"max_gc_pause_millis\": 50"))

        val tuned = JvmPresets.build("8khz", 16, 16, 8, 8, slowRam = true, bigCache = true, largePages = true)!!
        assertTrue(tuned.json.contains("\"max_gc_pause_millis\": 20"))
        assertTrue(tuned.json.contains("\"use_large_pages\": false"))

        assertEquals(null, JvmPresets.build("v1.0.6/default", 16, 5, 8, 8))
        val weak = JvmPresets.build("v1.0.8/default", 16, 12, 12, 6)!!
        val strong = JvmPresets.build("v1.0.8/default", 16, 12, 16, 8)!!
        assertTrue(weak.json.contains("\"dont_compile_huge_methods\": true"))
        assertTrue(strong.json.contains("\"dont_compile_huge_methods\": false"))

        val machine = MachineProfile(32L * 1024, 10L * 1024, 12, 6, memSpeedMts = 2133, largePages = true)
        val built = JvmPresets.build(JvmPresets.CURRENT, machine)!!
        assertEquals(6, built.heapGb)
        assertTrue(built.json.contains("\"max_gc_pause_millis\": 150"))
        assertTrue(built.json.contains("\"parallel_gc_threads\": 10"))
    }

    @Test
    fun thisPcStaysInsideSaneBounds() {
        val machine = HostSystemInfo().machine()
        assertNotNull(machine)
        assertTrue(machine.totalMb >= 1024, machine.toString())
        assertTrue(machine.freeMb in 0..machine.totalMb, machine.toString())
        assertTrue(machine.threads >= 1, machine.toString())
        assertTrue(machine.cores in 1..machine.threads, machine.toString())
        assertTrue(machine.memSpeedMts == 0 || machine.memSpeedMts in 200..20000, machine.toString())
        assertTrue(machine.l3Mb == 0 || machine.l3Mb in 1..512, machine.toString())
        if (System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)) {
            assertTrue(machine.cpuName.isNotBlank(), machine.toString())
        }
    }

    @Test
    fun jvmPageNamesThePcAndWritesItsFlags(@TempDir dir: Path) {
        val machine = MachineProfile(
            totalMb = 16L * 1024,
            freeMb = 8L * 1024,
            threads = 16,
            cores = 8,
            l3Mb = 96,
            memSpeedMts = 2133,
            largePages = true,
            cpuName = "AMD Ryzen 7 5800X3D",
            gpuName = "NVIDIA GeForce RTX 4070",
        )
        val desk = StalzoneDesk(
            MemoryRegistry(),
            dir.resolve("sessions.json"),
            dir.resolve("jvm"),
            dir.resolve("options.json"),
        )
        val mock = AppContext.mock(language = "ru", authDelayMs = 0)
        val app = AppContext(
            mock.services.copy(system = object : SystemInfo {
                override fun totalRamMb(): Long = machine.totalMb
                override fun machine(): MachineProfile = machine
            }),
            desk = desk,
        )
        FxTestSupport.onFx {
            val plain = Fxml.load<SettingsController>(FxTestSupport.context("ru", mock), "settings.fxml").controller
            assertFalse(plain.jvmMachine.isVisible)
            assertFalse(plain.jvmMachine.isManaged)

            val c = Fxml.load<SettingsController>(FxTestSupport.context("ru", app), "settings.fxml").controller
            val scene = Scene(c.settingsRoot, Theme.WINDOW_WIDTH, Theme.WINDOW_HEIGHT)
            applyTheme(scene, "zone", "ru")
            c.showClientSection("jvm")
            c.settingsRoot.applyCss()
            c.settingsRoot.layout()
            assertTrue(c.jvmMachine.isVisible)
            assertTrue(c.jvmMachine.text.contains("5800X3D"))
            assertTrue(c.jvmMachine.text.contains("RTX 4070"))
            assertTrue(c.jvmMachine.text.contains("8 ядер"))
            assertTrue(c.jvmMachine.text.contains("16 потоков"))
            val one = Fxml.load<SettingsController>(
                FxTestSupport.context(
                    "ru",
                    AppContext(
                        mock.services.copy(system = object : SystemInfo {
                            override fun totalRamMb(): Long = machine.totalMb
                            override fun machine(): MachineProfile = machine.copy(cores = 1, threads = 22)
                        }),
                        desk = desk,
                    ),
                ),
                "settings.fxml",
            ).controller
            assertTrue(one.jvmMachine.text.contains("1 ядро"), one.jvmMachine.text)
            assertTrue(one.jvmMachine.text.contains("22 потока"), one.jvmMachine.text)
            assertTrue(c.jvmMachine.text.contains("медленная память"))
            assertTrue(c.jvmMachine.text.contains("большой кэш"))
            assertTrue(c.jvmMachine.text.contains("большие страницы"))
            val card = c.jvmGrid.children.filterIsInstance<javafx.scene.control.Button>()
                .first { it.userData == JvmPresets.CURRENT }
            card.fire()
        }
        val written = Files.readString(dir.resolve("jvm").resolve("v1.1.2").resolve("default.json"))
        assertTrue(written.contains("\"max_gc_pause_millis\": 150"))
        assertTrue(written.contains("\"use_large_pages\": true"))
        assertTrue(written.contains("\"parallel_gc_threads\": 10"))
    }

    private fun record(size: Int): ByteArray {
        val buf = ByteArray(size)
        buf[4] = size.toByte()
        return buf
    }

    private fun cacheRecord(level: Int, bytes: Long): ByteArray {
        val buf = ByteArray(48)
        buf[4] = 48
        buf[8] = level.toByte()
        putU32(buf, 12, bytes)
        return buf
    }

    private fun putU32(buf: ByteArray, at: Int, value: Long) {
        buf[at] = value.toByte()
        buf[at + 1] = (value shr 8).toByte()
        buf[at + 2] = (value shr 16).toByte()
        buf[at + 3] = (value shr 24).toByte()
    }
}

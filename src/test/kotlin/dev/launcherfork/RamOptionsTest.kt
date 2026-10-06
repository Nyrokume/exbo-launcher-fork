package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.data.mock.InMemorySettingsStore
import dev.launcherfork.data.mock.MockSystemInfo
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.RamOptions
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.screens.settings.SettingsController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** RAM choices of the settings page: option ladder, capping by the computer's memory, recommendation, storage. */
class RamOptionsTest {
    private val gb = 1024L

    @Test
    fun optionsAreCappedByTotalMemoryMinusReserve() {
        assertEquals(listOf(2, 3, 4, 6, 8, 12), RamOptions.options(16 * gb))
        assertEquals(listOf(2, 3, 4, 6), RamOptions.options(8 * gb))
        assertEquals(listOf(2, 3, 4, 6, 8, 12, 16), RamOptions.options(32 * gb))
        assertEquals(listOf(2), RamOptions.options(4 * gb))
        assertEquals(listOf(2), RamOptions.options(2 * gb), "the smallest option is always offered")
        // a "16 GB" machine reports a bit less than 16384 MB
        assertEquals(16, RamOptions.totalGb(16_250))
        assertEquals(listOf(2, 3, 4, 6, 8, 12), RamOptions.options(16_250))
    }

    @Test
    fun recommendedIsHalfOfMemoryAtMostSixGb() {
        assertEquals(6, RamOptions.recommended(16 * gb))
        assertEquals(6, RamOptions.recommended(64 * gb))
        assertEquals(4, RamOptions.recommended(8 * gb))
        assertEquals(3, RamOptions.recommended(6 * gb))
        assertEquals(2, RamOptions.recommended(4 * gb))
        assertEquals(2, RamOptions.recommended(2 * gb))
    }

    @Test
    fun storedValuesSnapToOptions() {
        val total = 16 * gb
        assertEquals(6, RamOptions.selectedGb(LauncherSettings(), total), "nothing stored -> recommended")
        assertEquals(8, RamOptions.selectedGb(LauncherSettings(ramMb = 8192), total))
        assertEquals(12, RamOptions.selectedGb(LauncherSettings(ramMb = 16384), total), "above the cap -> largest option")
        assertEquals(4, RamOptions.selectedGb(LauncherSettings(ramMb = 4500), total))
        assertEquals(2, RamOptions.nearest(2560, total), "tie between 2 and 3 GB goes to the smaller")
        // legacy segmented bar: 512 + 3584 * f MB
        assertEquals(2304, RamOptions.legacyFractionToMb(0.5))
        assertEquals(4096, RamOptions.legacyFractionToMb(1.0))
        assertEquals(2, RamOptions.selectedGb(LauncherSettings(ramFraction = 0.5), total))
        assertEquals(4, RamOptions.selectedGb(LauncherSettings(ramFraction = 1.0), total))
        assertEquals(8, RamOptions.selectedGb(LauncherSettings(ramMb = 8192, ramFraction = 0.0), total), "MB wins over legacy")
    }

    @Test
    fun formatsWithLocalizedUnit() {
        assertEquals("4 ГБ", RamOptions.format(4, "ГБ"))
        assertEquals("12 GB", RamOptions.format(12, "GB"))
    }

    private fun settingsWith(settings: LauncherSettings, totalMb: Long = 16 * gb, lang: String = "ru"): Pair<SettingsController, InMemorySettingsStore> {
        val store = InMemorySettingsStore(settings)
        val base = AppContext.mock(speed = 50.0, authDelayMs = 0)
        val app = AppContext(base.services.copy(settings = store, system = MockSystemInfo(totalMb)))
        val c = FxTestSupport.onFx { Fxml.load<SettingsController>(FxTestSupport.context(lang, app), "settings.fxml").controller }
        return c to store
    }

    @Test
    fun settingsPageShowsGbChipsAndSavesMb() {
        val (c, store) = settingsWith(LauncherSettings(ramFraction = 0.5))
        FxTestSupport.onFx {
            val sel = c.ramSelector
            assertEquals(listOf("2 ГБ", "3 ГБ", "4 ГБ", "6 ГБ", "8 ГБ", "12 ГБ"), sel.buttons.map { it.text })
            assertEquals(2, sel.selected, "legacy 0.5 = 2304 MB -> 2 GB")
            assertEquals("Выделено: 2 ГБ из 16 ГБ", c.ramSummary.text)
            assertEquals(6, sel.recommended)
            sel.select(4)
            assertEquals("Выделено: 4 ГБ из 16 ГБ", c.ramSummary.text)
            assertEquals(4096, store.load().ramMb)
            assertNull(store.load().ramFraction, "the legacy value is dropped after a change")
        }
    }

    @Test
    fun smallMachineGetsFewerChipsInEnglish() {
        val (c, _) = settingsWith(LauncherSettings(), totalMb = 8 * gb, lang = "en")
        FxTestSupport.onFx {
            assertEquals(listOf("2 GB", "3 GB", "4 GB", "6 GB"), c.ramSelector.buttons.map { it.text })
            assertEquals(4, c.ramSelector.selected, "nothing stored -> recommended")
            assertEquals("Allocated: 4 GB of 8 GB", c.ramSummary.text)
        }
    }
}

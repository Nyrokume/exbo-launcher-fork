package dev.launcherfork

import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.RamOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** RAM of the settings bar: 1 GB steps, bounds (minimum .. total minus reserve, never above the total), recommendation. */
class RamOptionsTest {
    private val gb = 1024L

    @Test
    fun boundsAreMinimumAndTotalMinusReserve() {
        assertEquals(2, RamOptions.MIN_GB)
        assertEquals(1, RamOptions.STEP_GB)
        assertEquals(14, RamOptions.maxGb(16 * gb))
        assertEquals(6, RamOptions.maxGb(8 * gb))
        assertEquals(30, RamOptions.maxGb(32 * gb))
        assertEquals(2, RamOptions.maxGb(4 * gb))
        assertEquals(2, RamOptions.maxGb(2 * gb), "the minimum is always allowed")
        // a "16 GB" machine reports a bit less than 16384 MB
        assertEquals(16, RamOptions.totalGb(16_250))
        assertEquals(14, RamOptions.maxGb(16_250))
        for (total in 2..128) assertTrue(RamOptions.maxGb(total * gb) <= total, "max above the total for $total GB")
    }

    @Test
    fun valuesAreClampedToBothBounds() {
        val total = 16 * gb
        assertEquals(2, RamOptions.clamp(0, total))
        assertEquals(2, RamOptions.clamp(-5, total))
        assertEquals(2, RamOptions.clamp(1, total))
        assertEquals(7, RamOptions.clamp(7, total))
        assertEquals(14, RamOptions.clamp(15, total))
        assertEquals(14, RamOptions.clamp(16, total))
        assertEquals(14, RamOptions.clamp(999, total))
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
    fun storedValuesRoundToWholeGbAndClamp() {
        val total = 16 * gb
        assertEquals(6, RamOptions.selectedGb(LauncherSettings(), total), "nothing stored -> recommended")
        assertEquals(8, RamOptions.selectedGb(LauncherSettings(ramMb = 8192), total))
        assertEquals(7, RamOptions.selectedGb(LauncherSettings(ramMb = 7 * 1024), total), "any whole GB, not a ladder")
        assertEquals(14, RamOptions.selectedGb(LauncherSettings(ramMb = 16384), total), "above the max -> max")
        assertEquals(2, RamOptions.selectedGb(LauncherSettings(ramMb = 512), total), "below the min -> min")
        assertEquals(4, RamOptions.selectedGb(LauncherSettings(ramMb = 4500), total))
        // legacy segmented bar: 512 + 3584 * f MB
        assertEquals(2304, RamOptions.legacyFractionToMb(0.5))
        assertEquals(4096, RamOptions.legacyFractionToMb(1.0))
        assertEquals(2, RamOptions.selectedGb(LauncherSettings(ramFraction = 0.5), total))
        assertEquals(4, RamOptions.selectedGb(LauncherSettings(ramFraction = 1.0), total))
        assertEquals(8, RamOptions.selectedGb(LauncherSettings(ramMb = 8192, ramFraction = 0.0), total), "MB wins over legacy")
    }

    @Test
    fun fillFractionIsAllocationOverTotal() {
        assertEquals(6.0 / 16, RamOptions.fraction(6, 16 * gb), 1e-9)
        assertEquals(1.0, RamOptions.fraction(40, 16 * gb), 1e-9)
        assertEquals(0.25, RamOptions.fraction(2, 8 * gb), 1e-9)
    }

    @Test
    fun tickLabelsEndWithTheTotal() {
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), RamOptions.tickLabels(16 * gb))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), RamOptions.tickLabels(8 * gb))
        assertEquals(listOf(4, 8, 12, 16, 20, 24, 28, 32), RamOptions.tickLabels(32 * gb))
        assertEquals(listOf(2, 4, 6, 8, 10, 12), RamOptions.tickLabels(12 * gb))
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16, 18), RamOptions.tickLabels(18 * gb))
        assertTrue(RamOptions.tickLabels(24 * gb).last() == 24)
    }

    @Test
    fun formatsWithLocalizedUnit() {
        assertEquals("4 ГБ", RamOptions.format(4, "ГБ"))
        assertEquals("12 GB", RamOptions.format(12, "GB"))
    }
}

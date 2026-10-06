package dev.launcherfork

import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.RamOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** RAM of the settings bar: 1 GB steps, bounds (minimum .. the whole memory, «от 2 до 16 ГБ»), recommendation. */
class RamOptionsTest {
    private val gb = 1024L

    @Test
    fun boundsAreMinimumAndTheWholeMemory() {
        assertEquals(2, RamOptions.MIN_GB)
        assertEquals(1, RamOptions.STEP_GB)
        assertEquals(16, RamOptions.maxGb(16 * gb))
        assertEquals(8, RamOptions.maxGb(8 * gb))
        assertEquals(32, RamOptions.maxGb(32 * gb))
        assertEquals(4, RamOptions.maxGb(4 * gb))
        assertEquals(2, RamOptions.maxGb(2 * gb))
        assertEquals(2, RamOptions.maxGb(1 * gb), "the minimum is always allowed")
        // a "16 GB" machine reports a bit less than 16384 MB
        assertEquals(16, RamOptions.totalGb(16_250), "the summary names the rounded size")
        assertEquals(15, RamOptions.maxGb(16_250), "never above the physical memory (floor)")
        // 15.6 GB: «из 16 ГБ», but at most 15 GB can be chosen
        val gb156 = (15.6 * gb).toLong()
        assertEquals(16, RamOptions.totalGb(gb156)); assertEquals(15, RamOptions.usableGb(gb156)); assertEquals(15, RamOptions.maxGb(gb156))
        assertEquals(15, RamOptions.clamp(16, gb156))
        for (mb in 2048L..131_072L step 97) assertTrue(RamOptions.maxGb(mb) * gb <= mb, "max above the memory for $mb MB")
        for (total in 2..128) assertEquals(total, RamOptions.maxGb(total * gb), "max is the whole memory for $total GB")
    }

    @Test
    fun lastTwoGbLeaveLittleForTheSystem() {
        assertEquals(2, RamOptions.LOW_SYSTEM_MEMORY_GB)
        val t16 = 16 * gb
        assertFalse(RamOptions.leavesLittleForSystem(6, t16))
        assertFalse(RamOptions.leavesLittleForSystem(14, t16), "14 of 16 GB leaves 2 GB: no note")
        assertTrue(RamOptions.leavesLittleForSystem(15, t16), "15 of 16 GB: note")
        assertTrue(RamOptions.leavesLittleForSystem(16, t16), "16 of 16 GB: note")
        // last 2 GB of the floor max: 15.6 GB -> max 15 -> note at 14 and 15, not at 13
        val gb156 = (15.6 * gb).toLong()
        assertFalse(RamOptions.leavesLittleForSystem(13, gb156)); assertTrue(RamOptions.leavesLittleForSystem(14, gb156))
        assertTrue(RamOptions.leavesLittleForSystem(15, gb156))
        assertTrue(RamOptions.leavesLittleForSystem(14, 16_250), "16 250 MB: max 15, so 14 is in the last 2 GB")
        assertFalse(RamOptions.leavesLittleForSystem(6, 8 * gb)); assertTrue(RamOptions.leavesLittleForSystem(7, 8 * gb))
        assertTrue(RamOptions.leavesLittleForSystem(2, 2 * gb), "2 of 2 GB leaves nothing")
        for (total in 2..64) assertEquals((RamOptions.MIN_GB..total).count { RamOptions.leavesLittleForSystem(it, total * gb) },
            minOf(2, total - 1), "$total GB: only the last two values carry the note")
    }

    @Test
    fun valuesAreClampedToBothBounds() {
        val total = 16 * gb
        assertEquals(2, RamOptions.clamp(0, total))
        assertEquals(2, RamOptions.clamp(-5, total))
        assertEquals(2, RamOptions.clamp(1, total))
        assertEquals(7, RamOptions.clamp(7, total))
        assertEquals(15, RamOptions.clamp(15, total))
        assertEquals(16, RamOptions.clamp(16, total))
        assertEquals(16, RamOptions.clamp(999, total))
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
        assertEquals(16, RamOptions.selectedGb(LauncherSettings(ramMb = 16384), total), "the whole memory is allowed")
        assertEquals(16, RamOptions.selectedGb(LauncherSettings(ramMb = 64 * 1024), total), "above the max -> max")
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
    fun formatsWithLocalizedUnit() {
        assertEquals("4 ГБ", RamOptions.format(4, "ГБ"))
        assertEquals("12 GB", RamOptions.format(12, "GB"))
    }
}

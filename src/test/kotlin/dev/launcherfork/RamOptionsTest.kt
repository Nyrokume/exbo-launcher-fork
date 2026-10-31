package dev.launcherfork

import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.RamOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** RAM of the settings bar: 1 GB steps, bounds (minimum .. the machine, never above 8 GB), recommendation. */
class RamOptionsTest {
    private val gb = 1024L

    @Test
    fun boundsAreTheMachineAndNeverAHugeHeap() {
        assertEquals(2, RamOptions.MIN_GB)
        assertEquals(1, RamOptions.STEP_GB)
        assertEquals(8, RamOptions.ALLOCATION_CAP_GB)
        assertEquals(8, RamOptions.maxGb(16 * gb))
        assertEquals(8, RamOptions.maxGb(8 * gb))
        assertEquals(8, RamOptions.maxGb(32 * gb), "32 GB installed: the bar stops at 8")
        assertEquals(8, RamOptions.maxGb(64 * gb))
        assertEquals(4, RamOptions.maxGb(4 * gb), "a 4 GB PC cannot offer more than it has")
        assertEquals(2, RamOptions.maxGb(2 * gb))
        assertEquals(2, RamOptions.maxGb(1 * gb), "the minimum is always allowed")
        // hardware-reserved memory: within 0.3 GB (307 MB) below N GB counts as N GB, otherwise rounded down
        assertEquals(307, RamOptions.HARDWARE_RESERVE_TOLERANCE_MB)
        assertEquals(16, RamOptions.usableGb(16_250), "a 16 GB PC reporting a bit less still counts as 16")
        assertEquals(8, RamOptions.maxGb(16_250), "that PC still cannot offer more than 8")
        assertEquals(16, RamOptions.usableGb(16_384))
        assertEquals(16, RamOptions.usableGb(16_384 - 307), "exactly 0.3 GB below")
        assertEquals(15, RamOptions.usableGb(16_384 - 308), "just more than 0.3 GB below")
        assertEquals(15, RamOptions.usableGb(15_974), "15.6 GB")
        assertEquals(8, RamOptions.usableGb(8_100), "8 192 - 8 100 = 92 MB")
        assertEquals(8, RamOptions.maxGb(8_100))
        assertEquals(16, RamOptions.usableGb(16_500), "above 16 GB but far from 17: rounded down")
        val gb156 = (15.6 * gb).toLong()
        assertEquals(15, RamOptions.usableGb(gb156))
        assertEquals(8, RamOptions.totalGb(gb156)); assertEquals(8, RamOptions.maxGb(gb156))
        assertEquals(8, RamOptions.clamp(16, gb156))
        for (mb in 2048L..131_072L step 97) {
            val physical = RamOptions.usableGb(mb)
            val max = RamOptions.maxGb(mb)
            assertTrue(physical * gb <= mb + RamOptions.HARDWARE_RESERVE_TOLERANCE_MB, "physical $physical GB more than 0.3 GB above $mb MB")
            assertTrue((physical + 1) * gb > mb + RamOptions.HARDWARE_RESERVE_TOLERANCE_MB, "physical $physical GB too low for $mb MB")
            assertEquals(minOf(RamOptions.ALLOCATION_CAP_GB, maxOf(RamOptions.MIN_GB, physical)), max)
        }
        for (total in 2..128) {
            assertEquals(minOf(total, RamOptions.ALLOCATION_CAP_GB), RamOptions.maxGb(total * gb), "cap for $total GB")
        }
    }

    /** «из N ГБ», the max and the bar's scale are the ceiling, which is the machine until 8 GB. */
    @Test
    fun displayedTotalEqualsTheMax() {
        mapOf(15_974L to 8, 16_250L to 8, 16_384L to 8, 32_768L to 8, 8_100L to 8, 4_096L to 4, 2_048L to 2).forEach { (mb, n) ->
            assertEquals(n, RamOptions.totalGb(mb), "$mb MB: displayed total")
            assertEquals(n, RamOptions.maxGb(mb), "$mb MB: max")
        }
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
        assertFalse(RamOptions.leavesLittleForSystem(14, 16_250), "16 250 MB: max 16, 14 leaves room")
        assertTrue(RamOptions.leavesLittleForSystem(16, 16_250), "16 of a 16 250 MB PC (above the reported memory): note")
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
        assertEquals(8, RamOptions.clamp(15, total))
        assertEquals(8, RamOptions.clamp(8, total))
        assertEquals(8, RamOptions.clamp(16, total))
        assertEquals(8, RamOptions.clamp(999, total))
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
        assertEquals(8, RamOptions.selectedGb(LauncherSettings(ramMb = 8 * 1024), total), "the ceiling is allowed")
        assertEquals(8, RamOptions.selectedGb(LauncherSettings(ramMb = 16384), total), "above the cap -> cap")
        assertEquals(8, RamOptions.selectedGb(LauncherSettings(ramMb = 64 * 1024), total), "above the max -> max")
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
        assertEquals(6.0 / 8, RamOptions.fraction(6, 16 * gb), 1e-9)
        assertEquals(6.0 / 8, RamOptions.fraction(6, 32 * gb), 1e-9)
        assertEquals(1.0, RamOptions.fraction(40, 16 * gb), 1e-9)
        assertEquals(0.25, RamOptions.fraction(2, 8 * gb), 1e-9)
    }

    @Test
    fun formatsWithLocalizedUnit() {
        assertEquals("4 ГБ", RamOptions.format(4, "ГБ"))
        assertEquals("12 GB", RamOptions.format(12, "GB"))
    }
}

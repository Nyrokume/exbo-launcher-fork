package dev.launcherfork.domain

import kotlin.math.roundToInt

/**
 * RAM of the game (-Xmx) for the settings RAM bar, in whole gigabytes:
 *  - a value moves in steps of exactly [STEP_GB] (1 GB) and is clamped to [MIN_GB]..[maxGb];
 *  - [maxGb] is the machine's whole GB ([usableGb]: rounded down, but a size up to 0.3 GB below N GB counts as N),
 *    and never above [ALLOCATION_CAP_GB]. A 32 GB PC does not get a 32 GB heap: past 8 GB the game does not run
 *    better and GC pauses get longer. A smaller PC still tops out at that PC (8 GB → 2..8, 4 GB → 2..4);
 *  - the line above the bar and the scale use that same ceiling ("4 ГБ из 8 ГБ" on a 32 GB PC);
 *  - a value that leaves the machine with less than [LOW_SYSTEM_MEMORY_GB] GB gets a soft note
 *    «Мало памяти останется системе» ([leavesLittleForSystem]). On a 32 GB PC, 8 GB does not;
 *  - the recommended value is half of the machine, at most [RECOMMENDED_MAX_GB], clamped to the same range.
 */
object RamOptions {
    const val MIN_GB = 2
    const val STEP_GB = 1
    const val RECOMMENDED_MAX_GB = 6
    /** Largest heap the bar offers. JVM presets stop at this size; a larger one does not help the game. */
    const val ALLOCATION_CAP_GB = 8
    const val MB_PER_GB = 1024
    /** Selections that leave the machine with less than 2 GB show the «little memory left» note. */
    const val LOW_SYSTEM_MEMORY_GB = 2

    /** «из N ГБ» and the bar scale: the selectable ceiling, not the raw installed size. */
    fun totalGb(totalMb: Long): Int = maxGb(totalMb)

    /** How far below a whole N GB the reported memory may be and still count as N GB ([usableGb]): 0.3 GB. */
    const val HARDWARE_RESERVE_TOLERANCE_MB = 307

    /**
     * Whole GB of memory the game may get. A PC with N GB installed reports a little less than N * 1024 MB (memory
     * reserved by the hardware / firmware, integrated graphics), e.g. 16 250 MB on a 16 GB PC, and the user asked for
     * 2..16 GB there. So a size within [HARDWARE_RESERVE_TOLERANCE_MB] (0.3 GB) below N GB counts as N GB, anything
     * else is rounded down: 16 384 / 16 250 MB -> 16, 8 100 MB -> 8, 15.6 GB (15 974 MB) -> 15. The max can therefore
     * exceed the reported memory by at most 0.3 GB, and such a value is always in the last 2 GB, where the «Мало памяти
     * останется системе» note ([leavesLittleForSystem]) warns about it.
     */
    fun usableGb(totalMb: Long): Int {
        if (totalMb <= 0) return 0
        val up = (totalMb + MB_PER_GB - 1) / MB_PER_GB
        return (if (up * MB_PER_GB - totalMb <= HARDWARE_RESERVE_TOLERANCE_MB) up else totalMb / MB_PER_GB).toInt()
    }

    /**
     * Largest heap the bar offers: the machine's [usableGb], never below [MIN_GB] and never above [ALLOCATION_CAP_GB].
     */
    fun maxGb(totalMb: Long): Int = minOf(ALLOCATION_CAP_GB, maxOf(MIN_GB, usableGb(totalMb)))

    /** [gb] limited to [MIN_GB]..[maxGb]. */
    fun clamp(gb: Int, totalMb: Long): Int = gb.coerceIn(MIN_GB, maxGb(totalMb))

    /**
     * True when [gb] leaves the machine with less than [LOW_SYSTEM_MEMORY_GB] GB.
     * Compared with [usableGb], not the cap: 8 GB of a 32 GB PC leaves plenty.
     */
    fun leavesLittleForSystem(gb: Int, totalMb: Long): Boolean = gb > usableGb(totalMb) - LOW_SYSTEM_MEMORY_GB

    fun recommended(totalMb: Long): Int = clamp(minOf(usableGb(totalMb) / 2, RECOMMENDED_MAX_GB), totalMb)

    /** Nearest whole GB of [mb] (ties go up, like Math.round), clamped. */
    fun fromMb(mb: Int, totalMb: Long): Int = clamp((mb / MB_PER_GB.toDouble()).roundToInt(), totalMb)

    /** Old segmented-bar value (0..1) -> MB, same mapping as before the redesign. */
    fun legacyFractionToMb(fraction: Double): Int = (3584 * fraction.coerceIn(0.0, 1.0)).roundToInt() + 512

    /**
     * Allocation (GB) for stored [settings]: the saved MB value rounded to whole GB and clamped, else the legacy
     * fraction converted the same way, else the recommended value.
     */
    fun selectedGb(settings: LauncherSettings, totalMb: Long): Int = when {
        settings.ramMb != null -> fromMb(settings.ramMb, totalMb)
        settings.ramFraction != null -> fromMb(legacyFractionToMb(settings.ramFraction), totalMb)
        else -> recommended(totalMb)
    }

    /** Filled part of the bar for [gb]: allocation / total memory (0..1). */
    fun fraction(gb: Int, totalMb: Long): Double {
        val total = totalGb(totalMb)
        return if (total <= 0) 0.0 else (gb.toDouble() / total).coerceIn(0.0, 1.0)
    }

    /** "4 ГБ" / "4 GB": [unit] is the localized unit. */
    fun format(gb: Int, unit: String): String = "$gb $unit"
}

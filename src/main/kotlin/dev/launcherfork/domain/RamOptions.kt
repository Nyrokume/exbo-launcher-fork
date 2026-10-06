package dev.launcherfork.domain

import kotlin.math.roundToInt

/**
 * RAM of the game (-Xmx) for the settings RAM bar, in whole gigabytes:
 *  - a value moves in steps of exactly [STEP_GB] (1 GB) and is clamped to [MIN_GB]..[maxGb] = [usableGb], the whole
 *    GB of memory (rounded down, but a size up to 0.3 GB below N GB counts as N: hardware-reserved memory), so a
 *    16 GB PC (16 384 or 16 250 MB) allows 2..16 GB (user request: «от 2 до 16 ГБ»; no system reserve is held back)
 *    and 15.6 GB allows 2..15 GB;
 *  - the line above the bar names the same size ([totalGb] = [usableGb]: "6 ГБ из 16 ГБ", 15.6 GB: "из 15 ГБ") and
 *    the bar has exactly that many segments (0..[maxGb]), all reachable: one function for the text, the scale, the max;
 *  - a value within the last [LOW_SYSTEM_MEMORY_GB] GB of [maxGb] (15, 16 of 16; 14, 15 of 15) is allowed but gets a
 *    soft note «Мало памяти останется системе» ([leavesLittleForSystem]);
 *  - the recommended value is half of the memory, at most [RECOMMENDED_MAX_GB] (more heap does not make the game
 *    faster, it makes GC pauses longer), clamped to the same range.
 */
object RamOptions {
    const val MIN_GB = 2
    const val STEP_GB = 1
    const val RECOMMENDED_MAX_GB = 6
    const val MB_PER_GB = 1024
    /** Selections in the last 2 GB of [maxGb] (gb > maxGb - 2) show the «little memory left» note. */
    const val LOW_SYSTEM_MEMORY_GB = 2

    /** Memory size in GB for the summary line («из N ГБ»): the same [usableGb] as the bar's scale and the max. */
    fun totalGb(totalMb: Long): Int = usableGb(totalMb)

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

    /** Largest allowed allocation: [usableGb] (at most 0.3 GB above the reported memory), never below [MIN_GB]. */
    fun maxGb(totalMb: Long): Int = maxOf(MIN_GB, usableGb(totalMb))

    /** [gb] limited to [MIN_GB]..[maxGb]. */
    fun clamp(gb: Int, totalMb: Long): Int = gb.coerceIn(MIN_GB, maxGb(totalMb))

    /** True when [gb] is within the last [LOW_SYSTEM_MEMORY_GB] GB of [maxGb]: 15 and 16 of 16 GB, not 14. */
    fun leavesLittleForSystem(gb: Int, totalMb: Long): Boolean = gb > maxGb(totalMb) - LOW_SYSTEM_MEMORY_GB

    fun recommended(totalMb: Long): Int = clamp(minOf(totalGb(totalMb) / 2, RECOMMENDED_MAX_GB), totalMb)

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

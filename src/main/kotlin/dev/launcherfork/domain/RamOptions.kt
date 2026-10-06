package dev.launcherfork.domain

import kotlin.math.roundToInt

/**
 * RAM of the game (-Xmx) for the settings RAM bar, in whole gigabytes:
 *  - a value moves in steps of exactly [STEP_GB] (1 GB) and is clamped to [MIN_GB]..[maxGb] = the whole GB that fit
 *    in the physical memory (floor, never above it): 16384 MB allows 2..16 GB (user request: «от 2 до 16 ГБ»; no
 *    system reserve is held back), 15.6 GB allows 2..15 GB;
 *  - the bar's scale is that selectable range, 0..[maxGb] (no unreachable segments); the line above it names the
 *    rounded memory size ([totalGb]: "6 ГБ из 16 ГБ");
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

    /** Memory size in GB for the summary line, rounded (16 GB machines report a bit less than 16384 MB). */
    fun totalGb(totalMb: Long): Int = (totalMb / MB_PER_GB.toDouble()).roundToInt()

    /** Whole GB that fit in the physical memory (floor): 15.6 GB -> 15, 16384 MB -> 16. */
    fun usableGb(totalMb: Long): Int = (totalMb / MB_PER_GB).toInt()

    /** Largest allowed allocation: [usableGb] (never above the physical memory), but never below [MIN_GB]. */
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

package dev.launcherfork.domain

import kotlin.math.roundToInt

/**
 * RAM of the game (-Xmx) for the settings RAM bar, in whole gigabytes:
 *  - the bar's scale is the computer's memory, 0..[totalGb]; the filled part is the allocation ("6 ГБ из 16 ГБ");
 *  - a value moves in steps of exactly [STEP_GB] (1 GB) and is clamped to [MIN_GB]..[maxGb] = the whole memory, so a
 *    16 GB computer allows 2..16 GB (user request: «от 2 до 16 ГБ»; no system reserve is held back);
 *  - a value within the last [LOW_SYSTEM_MEMORY_GB] GB of the memory (15, 16 on a 16 GB computer) is allowed but
 *    gets a soft note «Мало памяти останется системе» ([leavesLittleForSystem]);
 *  - the recommended value is half of the memory, at most [RECOMMENDED_MAX_GB] (more heap does not make the game
 *    faster, it makes GC pauses longer), clamped to the same range.
 */
object RamOptions {
    const val MIN_GB = 2
    const val STEP_GB = 1
    const val RECOMMENDED_MAX_GB = 6
    const val MB_PER_GB = 1024
    /** Selections that leave less than this to the OS (gb > total - 2) show the «little memory left» note. */
    const val LOW_SYSTEM_MEMORY_GB = 2

    /** Whole GB of a memory size in MB (16 GB machines report a bit less than 16384 MB, so this rounds). */
    fun totalGb(totalMb: Long): Int = (totalMb / MB_PER_GB.toDouble()).roundToInt()

    /** Largest allowed allocation: the computer's whole memory, never below [MIN_GB]. */
    fun maxGb(totalMb: Long): Int = maxOf(MIN_GB, totalGb(totalMb))

    /** [gb] limited to [MIN_GB]..[maxGb]. */
    fun clamp(gb: Int, totalMb: Long): Int = gb.coerceIn(MIN_GB, maxGb(totalMb))

    /** True when [gb] is within the last [LOW_SYSTEM_MEMORY_GB] GB of the memory: 15 and 16 of 16 GB, not 14. */
    fun leavesLittleForSystem(gb: Int, totalMb: Long): Boolean = gb > totalGb(totalMb) - LOW_SYSTEM_MEMORY_GB

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

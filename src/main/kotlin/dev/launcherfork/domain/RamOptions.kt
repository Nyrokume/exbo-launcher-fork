package dev.launcherfork.domain

import kotlin.math.roundToInt

/**
 * RAM of the game (-Xmx) for the settings RAM bar, in whole gigabytes:
 *  - the bar's scale is the computer's memory, 0..[totalGb]; the filled part is the allocation ("6 ГБ из 16 ГБ");
 *  - a value moves in steps of exactly [STEP_GB] (1 GB) and is clamped to [MIN_GB]..[maxGb]: at least
 *    [SYSTEM_RESERVE_GB] stay for the OS and the launcher, so a 16 GB computer allows 2..14 GB (never above the total);
 *  - the recommended value is half of the memory, at most [RECOMMENDED_MAX_GB] (more heap does not make the game
 *    faster, it makes GC pauses longer), clamped to the same range.
 */
object RamOptions {
    const val MIN_GB = 2
    const val STEP_GB = 1
    const val SYSTEM_RESERVE_GB = 2
    const val RECOMMENDED_MAX_GB = 6
    const val MB_PER_GB = 1024

    /** Whole GB of a memory size in MB (16 GB machines report a bit less than 16384 MB, so this rounds). */
    fun totalGb(totalMb: Long): Int = (totalMb / MB_PER_GB.toDouble()).roundToInt()

    /** Largest allowed allocation: total minus the system reserve, never below [MIN_GB]. */
    fun maxGb(totalMb: Long): Int = maxOf(MIN_GB, totalGb(totalMb) - SYSTEM_RESERVE_GB)

    /** [gb] limited to [MIN_GB]..[maxGb]. */
    fun clamp(gb: Int, totalMb: Long): Int = gb.coerceIn(MIN_GB, maxGb(totalMb))

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

    /**
     * GB values that get a tick label under the bar: every 1, 2, 4 or 8 GB (the smallest step giving at most
     * [maxLabels] labels), always ending with the total.
     */
    fun tickLabels(totalMb: Long, maxLabels: Int = 9): List<Int> {
        val total = totalGb(totalMb)
        if (total <= 0) return emptyList()
        val step = listOf(1, 2, 4, 8, 16, 32).first { total / it <= maxLabels }
        val ticks = (step..total step step).toMutableList()
        if (ticks.lastOrNull() != total) {
            if (ticks.isNotEmpty() && total - ticks.last() < step / 2.0 + 0.01) ticks.removeAt(ticks.lastIndex)
            ticks += total
        }
        return ticks
    }

    /** "4 ГБ" / "4 GB": [unit] is the localized unit. */
    fun format(gb: Int, unit: String): String = "$gb $unit"
}

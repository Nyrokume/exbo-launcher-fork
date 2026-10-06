package dev.launcherfork.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * RAM choices of the settings page, in whole gigabytes (what the game's -Xmx gets):
 *  - the ladder is [LADDER_GB] (2, 3, 4, 6, 8, 12, 16 GB);
 *  - options are capped by the computer's memory: at least [SYSTEM_RESERVE_GB] stay for the OS and the launcher,
 *    so a 16 GB computer offers 2..12 GB and an 8 GB computer 2..6 GB; [LADDER_GB].first() is always offered;
 *  - the recommended value is the largest option not above half of the memory and not above [RECOMMENDED_MAX_GB]
 *    (more heap does not make the game faster, it makes GC pauses longer).
 */
object RamOptions {
    val LADDER_GB = listOf(2, 3, 4, 6, 8, 12, 16)
    const val SYSTEM_RESERVE_GB = 2
    const val RECOMMENDED_MAX_GB = 6
    const val MB_PER_GB = 1024

    /** Whole GB of a memory size in MB (16 GB machines report a bit less than 16384 MB, so this rounds). */
    fun totalGb(totalMb: Long): Int = (totalMb / MB_PER_GB.toDouble()).roundToInt()

    fun options(totalMb: Long): List<Int> {
        val cap = totalGb(totalMb) - SYSTEM_RESERVE_GB
        return LADDER_GB.filter { it <= cap }.ifEmpty { listOf(LADDER_GB.first()) }
    }

    fun recommended(totalMb: Long): Int {
        val opts = options(totalMb)
        val limit = minOf(totalGb(totalMb) / 2, RECOMMENDED_MAX_GB)
        return opts.lastOrNull { it <= limit } ?: opts.first()
    }

    /** The option nearest to [mb] (ties go to the smaller one). */
    fun nearest(mb: Int, totalMb: Long): Int =
        options(totalMb).minWith(compareBy<Int> { abs(it * MB_PER_GB - mb) }.thenBy { it })

    /** Old segmented-bar value (0..1) -> MB, same mapping as before the redesign. */
    fun legacyFractionToMb(fraction: Double): Int = (3584 * fraction.coerceIn(0.0, 1.0)).roundToInt() + 512

    /**
     * Selected option (GB) for stored [settings]: the saved MB value snapped to an option, else the legacy fraction
     * converted and snapped, else the recommended value.
     */
    fun selectedGb(settings: LauncherSettings, totalMb: Long): Int = when {
        settings.ramMb != null -> nearest(settings.ramMb, totalMb)
        settings.ramFraction != null -> nearest(legacyFractionToMb(settings.ramFraction), totalMb)
        else -> recommended(totalMb)
    }

    /** "4 ГБ" / "4 GB": [unit] is the localized unit. */
    fun format(gb: Int, unit: String): String = "$gb $unit"
}

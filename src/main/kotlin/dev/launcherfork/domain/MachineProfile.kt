package dev.launcherfork.domain

/**
 * This computer, as far as the JVM generators need it.
 * [totalGb] follows [RamOptions.usableGb], so a 16 GB PC that reports a little less still counts as 16.
 * [freeGb] is whole free gigabytes, floored. A missing free reading is passed in as [totalMb].
 * Speed 0 is not slow. An L3 of [BIG_CACHE_L3_MB] or more is the big-cache class.
 */
data class MachineProfile(
    val totalMb: Long,
    val freeMb: Long,
    val threads: Int,
    val cores: Int,
    val l3Mb: Int = 0,
    val memSpeedMts: Int = 0,
    val largePages: Boolean = false,
    val cpuName: String = "",
    val gpuName: String = "",
) {
    val totalGb: Int get() = RamOptions.usableGb(totalMb).coerceAtLeast(1)
    val freeGb: Int get() = (freeMb.coerceAtLeast(0) / RamOptions.MB_PER_GB).toInt()
    val slowRam: Boolean get() = memSpeedMts in 1..SLOW_RAM_MAX_MTS
    val bigCache: Boolean get() = l3Mb >= BIG_CACHE_L3_MB

    companion object {
        /** DDR at or under this speed uses the slow-RAM G1 branch. Unknown speed does not. */
        const val SLOW_RAM_MAX_MTS = 2933

        /** Per-CCD L3 that the v1.1.0 generator treats as a big cache (X3D class). */
        const val BIG_CACHE_L3_MB = 64
    }
}

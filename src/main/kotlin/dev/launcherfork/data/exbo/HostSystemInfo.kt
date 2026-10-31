package dev.launcherfork.data.exbo

import dev.launcherfork.domain.MachineProfile
import dev.launcherfork.domain.SystemInfo
import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean

/**
 * This computer: installed and free RAM from the JVM, plus cores, cache, memory speed and
 * large pages from Windows. A failed probe keeps the old numbers (logical processors as cores,
 * free memory equal to installed, no slow-RAM or big-cache branch).
 */
class HostSystemInfo : SystemInfo {
    private val hardware = WindowsHardware.probe()

    override fun totalRamMb(): Long = installedMb()

    override fun machine(): MachineProfile {
        val total = installedMb()
        val free = freeMb(total)
        val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cores = hardware.cores.takeIf { it in 1..threads } ?: threads
        return MachineProfile(
            totalMb = total,
            freeMb = free,
            threads = threads,
            cores = cores,
            l3Mb = hardware.l3Mb.coerceAtLeast(0),
            memSpeedMts = hardware.memSpeedMts.coerceAtLeast(0),
            largePages = hardware.largePages,
            cpuName = hardware.cpuName.takeUnless { it.isBlank() || MachineTables.isGenericCpu(it) }.orEmpty(),
            gpuName = hardware.gpuName.takeIf { MachineTables.gpuUsable(it) }.orEmpty(),
        )
    }

    private fun installedMb(): Long {
        val bean = ManagementFactory.getOperatingSystemMXBean()
        val bytes = (bean as? OperatingSystemMXBean)?.totalMemorySize ?: return 16L * 1024
        return (bytes / (1024 * 1024)).coerceAtLeast(1024)
    }

    /** Whole free MB. A missing reading is the installed size, which is what the generators used before. */
    private fun freeMb(totalMb: Long): Long {
        val bean = ManagementFactory.getOperatingSystemMXBean()
        val bytes = (bean as? OperatingSystemMXBean)?.freeMemorySize ?: return totalMb
        if (bytes <= 0L) return totalMb
        return (bytes / (1024 * 1024)).coerceIn(0, totalMb)
    }
}

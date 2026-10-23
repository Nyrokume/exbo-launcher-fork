package dev.launcherfork.data.exbo

import dev.launcherfork.domain.SystemInfo
import java.lang.management.ManagementFactory
import com.sun.management.OperatingSystemMXBean

/** Physical memory of this computer, for the settings RAM bar. */
class HostSystemInfo : SystemInfo {
    override fun totalRamMb(): Long {
        val bean = ManagementFactory.getOperatingSystemMXBean()
        val bytes = (bean as? OperatingSystemMXBean)?.totalMemorySize ?: return 16L * 1024
        return (bytes / (1024 * 1024)).coerceAtLeast(1024)
    }
}

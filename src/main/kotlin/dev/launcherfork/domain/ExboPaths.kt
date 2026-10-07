package dev.launcherfork.domain

import java.nio.file.Path

/**
 * Well-known EXBO client paths outside the game install root (Roaming).
 * «Открыть папку логов» uses [runtimeLogs] → `%APPDATA%/EXBO/runtime/logs`.
 */
object ExboPaths {
    /** `%APPDATA%/EXBO` (or `~/AppData/Roaming/EXBO` when APPDATA is unset). */
    fun roamingRoot(): Path {
        val roaming = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
            ?: Path.of(System.getProperty("user.home"), "AppData", "Roaming").toString()
        return Path.of(roaming, "EXBO")
    }

    /** Client log directory: `<roamingRoot>/runtime/logs`. */
    fun runtimeLogs(): Path = roamingRoot().resolve("runtime").resolve("logs")
}

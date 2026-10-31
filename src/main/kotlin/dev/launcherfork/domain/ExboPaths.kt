package dev.launcherfork.domain

import java.nio.file.Path

/**
 * Well-known EXBO client paths outside the game install root (Roaming).
 * «Открыть папку логов» uses [runtimeLogs] → `%APPDATA%/EXBO/runtime/logs` for the RU install.
 * The EU install keeps the same layout under `%APPDATA%/Exbo_East`.
 */
object ExboPaths {
    /** `%APPDATA%/EXBO` (or `~/AppData/Roaming/EXBO` when APPDATA is unset). */
    fun roamingRoot(): Path = roamingRoot(ExboRealm.RU)

    /** Roaming folder of one official launcher instance. */
    fun roamingRoot(realm: ExboRealm): Path {
        val roaming = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
            ?: Path.of(System.getProperty("user.home"), "AppData", "Roaming").toString()
        return Path.of(roaming, realm.dirName)
    }

    /** Client log directory: `<roamingRoot>/runtime/logs`. */
    fun runtimeLogs(): Path = runtimeLogs(ExboRealm.RU)

    fun runtimeLogs(realm: ExboRealm): Path = roamingRoot(realm).resolve("runtime").resolve("logs")
}

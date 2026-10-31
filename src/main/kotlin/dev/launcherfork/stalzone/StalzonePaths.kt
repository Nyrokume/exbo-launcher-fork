package dev.launcherfork.stalzone

import dev.launcherfork.domain.ExboPaths
import java.nio.file.Path

object StalzonePaths {
    fun sessionsFile(): Path = local("stalcraft-nyrokume").resolve("sessions.json")

    fun jvmWrapperDir(): Path = ExboPaths.roamingRoot().resolve("jvm_wrapper")

    fun jvmConfigsDir(): Path = jvmWrapperDir().resolve("configs")

    fun tunnelOverridesFile(): Path = jvmWrapperDir().resolve("overrides.json")

    fun tunnelStatsFile(): Path = jvmWrapperDir().resolve("cache").resolve("tunnel_stats.json")

    fun optionsFile(): Path =
        ExboPaths.roamingRoot().resolve("runtime").resolve("stalcraft").resolve("config").resolve("options.json")

    /** `%LOCALAPPDATA%`, or `~/AppData/Local` when the variable is missing. */
    private fun local(dir: String): Path {
        val root = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
            ?: Path.of(System.getProperty("user.home"), "AppData", "Local").toString()
        return Path.of(root, dir)
    }
}

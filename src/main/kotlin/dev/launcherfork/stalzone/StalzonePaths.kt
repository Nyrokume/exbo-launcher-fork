package dev.launcherfork.stalzone

import java.nio.file.Path

object StalzonePaths {
    fun sessionsFile(): Path = local("stalcraft-nyrokume").resolve("sessions.json")

    fun jvmWrapperDir(): Path = roaming("EXBO").resolve("jvm_wrapper")

    fun jvmConfigsDir(): Path = jvmWrapperDir().resolve("configs")

    fun tunnelOverridesFile(): Path = jvmWrapperDir().resolve("overrides.json")

    fun tunnelStatsFile(): Path = jvmWrapperDir().resolve("cache").resolve("tunnel_stats.json")

    fun optionsFile(): Path = roaming("EXBO").resolve("runtime").resolve("stalcraft").resolve("config").resolve("options.json")

    private fun local(dir: String): Path {
        val root = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        return Path.of(root, dir)
    }

    private fun roaming(dir: String): Path {
        val root = System.getenv("APPDATA") ?: System.getProperty("user.home")
        return Path.of(root, dir)
    }
}

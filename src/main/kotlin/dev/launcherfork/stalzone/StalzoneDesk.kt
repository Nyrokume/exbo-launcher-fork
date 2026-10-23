package dev.launcherfork.stalzone

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** File and registry actions shared by the desk and the tests. */
class StalzoneDesk(
    private val registry: RegistryStore,
    private val sessionsFile: Path,
    private val configsDir: Path,
    private val optionsFile: Path,
) {
    fun activeProfile(): String? = registry.readValues(JvmProfile.ACTIVE_KEY)[JvmProfile.ACTIVE_VALUE]

    fun activateTunedProfile() = useTunedProfile(true)

    /** On writes the 8 kHz file and makes it active. Off clears the active profile. */
    fun useTunedProfile(on: Boolean) {
        if (!on) {
            registry.deleteValue(JvmProfile.ACTIVE_KEY, JvmProfile.ACTIVE_VALUE)
            return
        }
        Files.createDirectories(configsDir)
        Files.writeString(configsDir.resolve("${JvmProfile.RECOMMENDED}.json"), JvmProfile.tunedJson())
        registry.writeValues(JvmProfile.ACTIVE_KEY, mapOf(JvmProfile.ACTIVE_VALUE to JvmProfile.RECOMMENDED))
    }

    /**
     * Writes `configs/<id>.json` (an id may contain slashes, `v1.1.2/default`) and makes it the active profile.
     * `service.exe` reads that value on the next game start.
     */
    fun activatePreset(id: String, json: String) {
        require(id.isNotBlank() && !id.contains("..")) { "id" }
        val file = configsDir.resolve("$id.json")
        Files.createDirectories(file.parent)
        Files.writeString(file, json)
        registry.writeValues(JvmProfile.ACTIVE_KEY, mapOf(JvmProfile.ACTIVE_VALUE to id))
    }

    /** Keeps the active 8 kHz file's heap in step with the RAM chosen in settings. No-op otherwise. */
    fun syncHeap(gb: Int) {
        if (gb <= 0 || activeProfile() != JvmProfile.RECOMMENDED) return
        val file = configsDir.resolve("${JvmProfile.RECOMMENDED}.json")
        if (!Files.isRegularFile(file)) return
        val text = Files.readString(file)
        val next = text.replace(Regex(""""heap_size_gb"\s*:\s*\d+"""), """"heap_size_gb": $gb""")
        if (next != text) Files.writeString(file, next)
    }

    fun sessions(): List<ExboSession> = SessionFile.load(sessionsFile)

    /** Live `SOFTWARE\EXBO` values, so the accounts page can mark the session that is already applied. */
    fun liveAccount(): Map<String, String> = registry.readValues(ExboSessions.KEY_PATH)

    fun captureCurrent(name: String): ExboSession {
        val live = registry.readValues(ExboSessions.KEY_PATH)
        val existing = sessions()
        val nextId = (existing.maxOfOrNull { it.id } ?: 0L) + 1
        val session = ExboSessions.capture(name.ifBlank { "session $nextId" }, nextId, Instant.now().toString(), live)
        SessionFile.save(sessionsFile, existing + session)
        return session
    }

    fun apply(session: ExboSession) {
        registry.writeValues(ExboSessions.KEY_PATH, ExboSessions.applyPlan(session.data))
    }

    fun delete(id: Long) {
        SessionFile.save(sessionsFile, sessions().filter { it.id != id })
    }

    fun writeSensitivity(base: Double): Map<Double, Double> {
        require(base > 0) { "base" }
        val values = ScopeSens.snapshot(base)
        require(Files.exists(optionsFile)) { "options.json is missing" }
        val patched = OptionsSens.patch(Files.readString(optionsFile), base, values)
        val tmp = optionsFile.resolveSibling("options.json.tmp")
        Files.writeString(tmp, patched)
        try {
            Files.move(
                tmp, optionsFile,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(tmp, optionsFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.io.IOException) {
            Files.move(tmp, optionsFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        return values
    }
}

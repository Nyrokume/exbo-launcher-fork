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
    /** Seals `EXBO_SESSION` in [sessionsFile]. The registry write stays the raw session. */
    private val secrets: SessionSecretProtector = PlainSessionSecrets,
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

    /**
     * The RAM bar, in whole GB, written into the profile `service.exe` injects on the next start.
     * The wrapper removes the client's `-Xmx` and uses `heap_size_gb`, so a value that lives only in
     * the launch command never reaches the game while a profile is active.
     * An active file is updated in place. With no active file, the wrapper's `default.json` is the fallback.
     * Nothing is created: a machine without the wrapper is left alone.
     */
    fun syncHeap(gb: Int) {
        if (gb <= 0) return
        val active = activeProfile()
        if (active != null) {
            val file = configsDir.resolve("$active.json")
            if (Files.isRegularFile(file)) {
                writeHeap(active, file, gb)
                return
            }
        }
        val fallback = configsDir.resolve("default.json")
        if (Files.isRegularFile(fallback)) writeHeap("default", fallback, gb)
    }

    private fun writeHeap(id: String, file: Path, gb: Int) {
        val text = Files.readString(file)
        val next = JvmPresets.applyHeapToJson(id, text, gb)
        if (next != text) Files.writeString(file, next)
    }

    fun sessions(): List<ExboSession> = SessionFile.load(sessionsFile, secrets)

    /** Live `SOFTWARE\EXBO` values, so the accounts page can mark the session that is already applied. */
    fun liveAccount(): Map<String, String> = registry.readValues(ExboSessions.KEY_PATH)

    fun captureCurrent(name: String): ExboSession {
        val live = registry.readValues(ExboSessions.KEY_PATH)
        val existing = sessions()
        val nextId = (existing.maxOfOrNull { it.id } ?: 0L) + 1
        val session = ExboSessions.capture(name.ifBlank { "session $nextId" }, nextId, Instant.now().toString(), live)
        SessionFile.save(sessionsFile, existing + session, secrets)
        return session
    }

    fun apply(session: ExboSession) {
        registry.writeValues(ExboSessions.KEY_PATH, ExboSessions.applyPlan(session.data))
    }

    fun delete(id: Long) {
        SessionFile.save(sessionsFile, sessions().filter { it.id != id }, secrets)
    }

    /**
     * Changes the saved label of one session. The captured registry snapshot stays as it was.
     * A blank name is refused. An unknown id is refused.
     */
    fun rename(id: Long, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val existing = sessions()
        val index = existing.indexOfFirst { it.id == id }
        if (index < 0) return false
        if (existing[index].name == trimmed) return true
        val next = existing.toMutableList()
        next[index] = existing[index].copy(name = trimmed)
        SessionFile.save(sessionsFile, next, secrets)
        return true
    }

    /** [values] defaults to the wiki table for [base]. A shown table is passed through as the player left it. */
    fun writeSensitivity(base: Double, values: Map<Double, Double> = ScopeSens.snapshot(base)): Map<Double, Double> {
        require(base > 0) { "base" }
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

package dev.launcherfork.stalzone

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Client graphics the launcher can persist. FOV is 70..110 — the client rejects anything higher.
 * Shadows stay off in `options.json` on launch, while the client runs, and after it exits.
 */
object GameOptions {
    const val DEFAULT_FOV = 110.0
    const val MIN_FOV = 70.0
    const val MAX_FOV = 110.0

    data class Unlock(val fov: Double = DEFAULT_FOV, val shadowsOff: Boolean = true)

    fun clampFov(value: Double): Double = value.coerceIn(MIN_FOV, MAX_FOV)

    fun patch(text: String, fov: Double = DEFAULT_FOV, shadowsOff: Boolean = true): String {
        var out = upsertNumber(text, "fov", clampFov(fov))
        if (shadowsOff) {
            out = upsertNumber(out, "shadows_quality", 0)
            out = upsertNumber(out, "shadows_map_size", 0)
            out = upsertNumber(out, "soft_lighting_mode", 0)
            out = upsertBool(out, "shadows_dynamic", false)
            out = upsertBool(out, "shadows_pcf", false)
        }
        return out
    }

    fun apply(clientDir: Path, fov: Double = DEFAULT_FOV, shadowsOff: Boolean = true) {
        applyFile(clientDir.resolve("config").resolve("options.json"), fov, shadowsOff)
    }

    fun applyFile(file: Path, fov: Double = DEFAULT_FOV, shadowsOff: Boolean = true) {
        if (!Files.isRegularFile(file)) return
        val original = Files.readString(file)
        val next = patch(original, fov, shadowsOff)
        if (next == original) return
        val tmp = file.resolveSibling("options.json.tmp")
        Files.writeString(tmp, next)
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.io.IOException) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun upsertNumber(text: String, key: String, value: Double): String {
        val rendered = if (value % 1.0 == 0.0) "${value.toInt()}.0" else value.toString()
        return upsert(text, key, rendered)
    }

    private fun upsertNumber(text: String, key: String, value: Int): String = upsert(text, key, value.toString())

    private fun upsertBool(text: String, key: String, value: Boolean): String = upsert(text, key, value.toString())

    private fun upsert(text: String, key: String, rendered: String): String {
        val pattern = Regex("(\"${Regex.escape(key)}\"\\s*:\\s*)(?:-?[0-9]+(?:\\.[0-9]+)?|true|false)")
        if (pattern.containsMatchIn(text)) return pattern.replace(text, "$1$rendered")
        val entry = "\"$key\": $rendered"
        val close = text.lastIndexOf('}')
        if (close < 0) return "{$entry\n}\n"
        val body = text.substring(0, close).trimEnd()
        val sep = if (body.endsWith("{") || body.endsWith(",")) "\n  " else ",\n  "
        return body + sep + entry + "\n" + text.substring(close)
    }
}

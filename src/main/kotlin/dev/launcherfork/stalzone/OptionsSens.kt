package dev.launcherfork.stalzone

/**
 * Patches the STALZONE `options.json` the same way Regedit's `save_config` does:
 * `mouse_sensitivity` plus `sensitivity_zoom_<mag>` for each scope.
 * Other keys in the file stay as they are.
 */
object OptionsSens {
    fun patch(text: String, base: Double, values: Map<Double, Double>): String {
        var out = upsertNumber(text, "mouse_sensitivity", ScopeSens.round2(base))
        for (mag in ScopeSens.MAGNIFICATIONS) {
            val value = values[mag] ?: continue
            out = upsertNumber(out, "sensitivity_zoom_${ScopeSens.magKey(mag)}", value)
        }
        return out
    }

    private fun upsertNumber(text: String, key: String, value: Double): String {
        val rendered = format(value)
        val pattern = Regex("(\"${Regex.escape(key)}\"\\s*:\\s*)-?[0-9]+(?:\\.[0-9]+)?")
        if (pattern.containsMatchIn(text)) {
            return pattern.replace(text, "$1$rendered")
        }
        val entry = "\"$key\": $rendered"
        val close = text.lastIndexOf('}')
        if (close < 0) return "{$entry\n}\n"
        val body = text.substring(0, close).trimEnd()
        val sep = if (body.endsWith("{") || body.endsWith(",")) "\n  " else ",\n  "
        return body + sep + entry + "\n" + text.substring(close)
    }

    private fun format(value: Double): String {
        val rounded = ScopeSens.round2(value)
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}

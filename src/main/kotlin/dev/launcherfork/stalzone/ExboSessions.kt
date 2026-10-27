package dev.launcherfork.stalzone

/**
 * Account sessions in the same `sessions.json` as Regedit Nyrokume Exbo
 * (`%LOCALAPPDATA%\stalcraft-nyrokume\sessions.json`).
 * Applying a session writes only `EXBO_AUTO_AUTH`, `EXBO_LOGIN` and `EXBO_SESSION`
 * under `HKCU\SOFTWARE\EXBO`, and forces auto-auth to `true`.
 * Those registry strings stay raw: the installed launcher and `/auth?session=` read them.
 * The copy in `sessions.json` is sealed separately.
 */
data class ExboValue(val name: String, val value: String, val valueType: String = "REG_SZ")

data class ExboKey(
    val name: String,
    val path: String,
    val values: List<ExboValue> = emptyList(),
    val subkeys: List<ExboKey> = emptyList(),
)

data class ExboSession(
    val id: Long,
    val name: String,
    val createdAt: String,
    val data: ExboKey,
)

object ExboSessions {
    val ALLOWED = listOf("EXBO_AUTO_AUTH", "EXBO_LOGIN", "EXBO_SESSION")
    const val KEY_PATH = "SOFTWARE\\EXBO"

    /** Values that `apply_exbo_registry_snapshot` would write. Auto-auth is always true. */
    fun applyPlan(snapshot: ExboKey): Map<String, String> {
        val found = linkedMapOf<String, String>()
        fun walk(key: ExboKey) {
            for (v in key.values) {
                if (v.name in ALLOWED && v.name !in found) found[v.name] = v.value
            }
            key.subkeys.forEach(::walk)
        }
        walk(snapshot)
        found["EXBO_AUTO_AUTH"] = "true"
        return ALLOWED.mapNotNull { name -> found[name]?.let { name to it } }.toMap()
    }

    /** Every value stored in the snapshot, including nested subkeys, in walk order. */
    fun storedValues(snapshot: ExboKey): List<ExboValue> {
        val found = mutableListOf<ExboValue>()
        fun walk(key: ExboKey) {
            found += key.values
            key.subkeys.forEach(::walk)
        }
        walk(snapshot)
        return found
    }

    /**
     * The snapshot is the one written in the registry when each live login and session token
     * that is present matches the stored value. A registry with neither is not a current session.
     */
    fun isCurrent(session: ExboSession, live: Map<String, String>): Boolean {
        val stored = storedValues(session.data).associate { it.name to it.value }
        val login = live["EXBO_LOGIN"].orEmpty()
        val token = live["EXBO_SESSION"].orEmpty()
        if (login.isEmpty() && token.isEmpty()) return false
        if (login.isNotEmpty() && stored["EXBO_LOGIN"] != login) return false
        if (token.isNotEmpty() && stored["EXBO_SESSION"] != token) return false
        return true
    }

    fun capture(name: String, id: Long, createdAt: String, live: Map<String, String>): ExboSession {
        val values = ALLOWED.mapNotNull { key ->
            live[key]?.let { ExboValue(key, it) }
        }
        return ExboSession(
            id = id,
            name = name,
            createdAt = createdAt,
            data = ExboKey(name = "EXBO", path = KEY_PATH, values = values),
        )
    }
}

package dev.launcherfork.domain

/**
 * Official launcher instance. RU and EU are separate installs: each has its own
 * roaming folder, registry key, account session, and torrent catalog.
 * The folder names come from the installer's `LauncherRealm` (`EXBO`, `Exbo_East`).
 */
enum class ExboRealm(val dirName: String) {
    RU("EXBO"),
    GLOBAL("Exbo_East"),
    ;

    /** HKCU path the installed client uses for `EXBO_LOGIN` / `EXBO_SESSION`. */
    val registryKey: String get() = "SOFTWARE\\$dirName"

    /** Value stored in `settings.properties` and accepted by [fromName]. */
    val wireName: String
        get() = when (this) {
            RU -> "ru"
            GLOBAL -> "global"
        }

    companion object {
        const val PROPERTY = "launcher.realm"
        const val SETTINGS_KEY = "exbo.realm"

        fun fromName(raw: String?): ExboRealm? = when (raw?.trim()?.lowercase()) {
            "ru", "exbo" -> RU
            "global", "eu", "east", "exbo_east" -> GLOBAL
            else -> null
        }
    }
}

/**
 * Realm to open. [pinRequested] is `-Dlauncher.realm=…`: that install wins when it is present.
 * Otherwise the install whose official client hash is newest wins, so a leftover
 * `exbo.realm=global` does not hide a current RU client. No hashes: the request if
 * that install exists, then RU, then whichever install is left.
 */
fun chooseExboRealm(requested: ExboRealm?, installed: (ExboRealm) -> Boolean): ExboRealm =
    chooseExboRealm(requested, installed, { null }, pinRequested = false)

fun chooseExboRealm(
    requested: ExboRealm?,
    installed: (ExboRealm) -> Boolean,
    touchedAt: (ExboRealm) -> Long?,
    pinRequested: Boolean = false,
): ExboRealm {
    val present = ExboRealm.entries.filter(installed)
    if (present.isEmpty()) return requested ?: ExboRealm.RU
    if (pinRequested && requested != null && requested in present) return requested
    val ranked = present.mapNotNull { realm -> touchedAt(realm)?.let { realm to it } }
    if (ranked.isNotEmpty()) {
        val newest = ranked.maxOf { it.second }
        val ties = ranked.filter { it.second == newest }.map { it.first }
        if (requested != null && requested in ties) return requested
        return ranked.first { it.second == newest }.first
    }
    if (requested != null && requested in present) return requested
    return present.first()
}

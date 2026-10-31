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
 * Realm to open. A request is used only when that install is present.
 * Otherwise the RU install wins, then whichever install exists.
 * Neither install still returns the request, or RU, so the caller can stay on the usual root.
 */
fun chooseExboRealm(requested: ExboRealm?, installed: (ExboRealm) -> Boolean): ExboRealm = when {
    requested != null && installed(requested) -> requested
    installed(ExboRealm.RU) -> ExboRealm.RU
    installed(ExboRealm.GLOBAL) -> ExboRealm.GLOBAL
    else -> requested ?: ExboRealm.RU
}

package dev.launcherfork.data.mock

/**
 * Which second factor the mock asks for after a successful password login (`-Dlauncher.mock2fa=random|email|totp|none`):
 * [RANDOM] picks the e-mail code or the authenticator app per login attempt (the app's default), [EMAIL] / [TOTP]
 * always ask for that one, [NONE] signs in straight away (the screenshot tour, tests).
 */
enum class MockSecondFactor {
    RANDOM, EMAIL, TOTP, NONE;

    companion object {
        const val PROPERTY = "launcher.mock2fa"

        /** null / blank / unknown -> [RANDOM] (unknown values are reported on stderr). */
        fun parse(value: String?): MockSecondFactor {
            val v = value?.trim()?.lowercase().orEmpty()
            if (v.isEmpty()) return RANDOM
            return entries.firstOrNull { it.name.lowercase() == v }
                ?: RANDOM.also { System.err.println("[launcher-fork] WARNING: unknown -D$PROPERTY=$value, using random") }
        }

        fun fromSystemProperties(get: (String) -> String? = System::getProperty): MockSecondFactor = parse(get(PROPERTY))
    }
}

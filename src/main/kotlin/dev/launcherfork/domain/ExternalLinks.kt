package dev.launcherfork.domain

/**
 * Canonical https URLs the launcher opens in the system browser (profile menu, license modal;
 * communities stay in [CommunityLinks]). Every constant must pass [LinkPolicy.isAllowed].
 */
object ExternalLinks {
    const val LICENSE = "https://exbo.games/legal/gl_licence.pdf"
    const val PRIVACY = "https://exbo.games/legal/gl_privacy.pdf"
    const val ACCOUNT = "https://stalzone.com/user"
    const val REFERRAL = "https://stalzone.com/referral"
    /** Donate / top-up (stalzone.com account menu `.donate` → `/donate`). */
    const val TOP_UP = "https://stalzone.com/donate"
}

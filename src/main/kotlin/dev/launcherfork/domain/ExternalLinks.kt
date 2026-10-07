package dev.launcherfork.domain

/**
 * Canonical https URLs the launcher opens in the system browser (profile menu; communities stay in
 * [CommunityLinks]). Every constant must pass [LinkPolicy.isAllowed].
 */
object ExternalLinks {
    const val ACCOUNT = "https://stalzone.com/user"
    const val REFERRAL = "https://stalzone.com/referral"
    /** Donate / top-up (stalzone.com account menu `.donate` → `/donate`). */
    const val TOP_UP = "https://stalzone.com/donate"
}

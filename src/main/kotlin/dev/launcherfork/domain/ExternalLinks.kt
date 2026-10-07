package dev.launcherfork.domain

/**
 * Canonical https URLs the launcher opens in the system browser (profile menu, license modal, communities stay in
 * [CommunityLinks]). Every constant must pass [LinkPolicy.isAllowed].
 *
 * Note for Front.end.master: [dev.launcherfork.ui.screens.auth.RegisterController.onDocLink] still shows
 * `fork.not_available`. Wire [LICENSE] / [PRIVACY] there the same way [LicenseModalController] does (LinkOpener).
 */
object ExternalLinks {
    const val LICENSE = "https://exbo.games/legal/gl_licence.pdf"
    const val PRIVACY = "https://exbo.games/legal/gl_privacy.pdf"
    const val ACCOUNT = "https://stalzone.com/user"
    const val REFERRAL = "https://stalzone.com/referral"
}

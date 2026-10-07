package dev.launcherfork.domain

import java.net.URI

/**
 * Which external URLs the launcher may hand to the OS (HostServices.showDocument -> ShellExecute / xdg-open).
 * Pure and side-effect free: only `https` URLs whose host is (a subdomain of) [allowedHosts]; no userinfo.
 */
object LinkPolicy {
    /** The ONE allowlist of hosts (exact match or any subdomain). */
    val allowedHosts: Set<String> = setOf(
        "discord.gg", "discord.com", "youtube.com", "reddit.com",
        "t.me", "telegram.org", "stalzone.com", "exbo.net", "exbo.games",
    )

    fun isAllowed(url: String): Boolean {
        val uri = try { URI(url) } catch (_: Exception) { return false }
        if (uri.scheme != "https" || uri.isOpaque) return false
        if (uri.rawUserInfo != null) return false
        val authority = uri.rawAuthority ?: return false
        if ('@' in authority) return false
        val host = uri.host?.lowercase() ?: return false
        return allowedHosts.any { host == it || host.endsWith(".$it") }
    }
}

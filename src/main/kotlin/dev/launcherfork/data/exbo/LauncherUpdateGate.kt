package dev.launcherfork.data.exbo

import java.net.URI
import java.nio.file.Path

internal const val LAUNCHER_UPDATE_UNSIGNED = "unsigned"

/** One offered launcher build. [signature] is a detached signature over [sha256], not the file size. */
internal data class LauncherUpdateOffer(
    val url: String,
    val sha256: String,
    val signature: String,
    val bytes: ByteArray,
)

internal sealed interface LauncherUpdateDecision {
    /** Nothing to apply. The login window may open. */
    data object Current : LauncherUpdateDecision

    /** The offer must not be written. [code] is safe to show; it is not a path or a URL. */
    data class Rejected(val code: String) : LauncherUpdateDecision
}

/**
 * Live self-update.
 * A null offer means this build has nothing to apply.
 * Any offer is rejected until a release public key is pinned in this repo.
 * A matching SHA-256, or a file that is merely large enough, is not a signature.
 *
 * TODO(signed self-update): pin the release public key and accept only a detached signature
 * over the SHA-256. Do not write the launcher before that check can return [LauncherUpdateDecision.Current]
 * for a real offer. No such key or manifest is in this repo.
 */
internal fun decideLauncherUpdate(offer: LauncherUpdateOffer?): LauncherUpdateDecision {
    if (offer == null) return LauncherUpdateDecision.Current
    if (offer.signature.isBlank() || offer.sha256.isBlank() || !httpsUpdateUrl(offer.url)) {
        return LauncherUpdateDecision.Rejected(LAUNCHER_UPDATE_UNSIGNED)
    }
    if (!exboSha256Hex(offer.bytes).equals(offer.sha256, ignoreCase = true)) {
        return LauncherUpdateDecision.Rejected(LAUNCHER_UPDATE_UNSIGNED)
    }
    // The digest matches and the URL is HTTPS. That is still not a signature.
    return LauncherUpdateDecision.Rejected(LAUNCHER_UPDATE_UNSIGNED)
}

/**
 * Returns false and does not create [destination].
 * Every current offer is rejected, including one whose size and digest look right.
 */
internal fun stageLauncherUpdate(offer: LauncherUpdateOffer, destination: Path): Boolean {
    if (decideLauncherUpdate(offer) != LauncherUpdateDecision.Current) return false
    // A trusted offer is not writable until the pinned key exists. Do not create the file.
    if (destination.fileName == null) return false
    return false
}

private fun httpsUpdateUrl(raw: String): Boolean = try {
    val uri = URI(raw.trim())
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
} catch (_: Exception) {
    false
}

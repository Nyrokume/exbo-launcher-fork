package dev.launcherfork.domain

import java.net.URI
import java.nio.file.Path

/**
 * A game server card shown in the sidebar.
 * Images are plain URIs (http(s), file, or classpath-derived jar:/file: URIs for mock data), so the domain
 * does not know about resource folders.
 */
data class Server(
    val id: String,
    val caption: String,
    val online: Boolean,
    val players: Int,
    val description: List<String>,
    val iconUri: URI,
    val backgroundUri: URI,
    /** Fake "client is installed" flag; decides Download vs Play button. */
    val installed: Boolean,
    /** Fake client size for the progress text, bytes. */
    val clientSizeBytes: Long,
)

data class UserSession(val login: String)

/** Why authentication failed. The UI maps these to localized strings. */
enum class AuthError {
    EMPTY_LOGIN,
    EMPTY_PASSWORD,
    EMPTY_CODE,
    WRONG_CREDENTIALS,
    WRONG_CODE,
    NO_CONNECTION,
    SERVER_ERROR,
}

sealed interface AuthResult {
    data class Success(val session: UserSession) : AuthResult
    /** Second factor (e-mail code) is required -> mail_auth screen. */
    data object CodeRequired : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}

/** Phase of a long-running update/download operation. The UI maps these to localized strings. */
enum class UpdatePhase {
    CHECKING,
    DOWNLOADING,
    APPLYING,
}

/** Progress snapshot emitted by [UpdateService]. */
data class Progress(
    val fraction: Double,
    val doneBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
)

/** Peer-to-peer download policy (settings). */
enum class P2pMode { ALWAYS, EXCEPT_MOBILE, NEVER }

/** When the launcher closes itself (settings). */
enum class ExitMode { ON_GAME_START, ON_GAME_EXIT, NEVER }

data class LauncherSettings(
    val runOnStartUp: Boolean = false,
    val startOnDownloaded: Boolean = true,
    val hideToTray: Boolean = true,
    val defenderHint: Boolean = true,
    val p2pMode: P2pMode = P2pMode.EXCEPT_MOBILE,
    val exitMode: ExitMode = ExitMode.NEVER,
    /** Game heap in MB chosen in settings (one of the RAM options); null = not chosen yet -> recommended value. */
    val ramMb: Int? = null,
    /**
     * Legacy value of the old segmented bar: 0..1, mapped to `512 + 3584 * f` MB. Only read when [ramMb] is null
     * (settings saved before the RAM selector) and converted to the nearest RAM option; cleared on the next change.
     */
    val ramFraction: Double? = null,
    val downloadLimitKb: Int = 0,
    val uploadLimitKb: Int = 0,
    val gameRoot: Path = Path.of(System.getProperty("user.home"), "Games", "EXBO"),
    val language: String = "ru",
)

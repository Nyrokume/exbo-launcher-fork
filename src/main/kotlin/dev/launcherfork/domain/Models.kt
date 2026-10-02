package dev.launcherfork.domain

/** A game server card shown in the sidebar. Images are classpath resources (mock data only). */
data class Server(
    val id: String,
    val caption: String,
    val online: Boolean,
    val players: Int,
    val description: List<String>,
    val iconResource: String,
    val backgroundResource: String,
    /** Fake "client is installed" flag; decides Download vs Play button. */
    val installed: Boolean,
    /** Fake client size for the progress text, bytes. */
    val clientSizeBytes: Long,
)

data class NewsItem(
    val title: String,
    val summary: String,
    val imageResource: String,
)

data class UserSession(val login: String)

sealed interface AuthResult {
    data class Success(val session: UserSession) : AuthResult
    /** Second factor (e-mail code) is required -> mail_auth screen. */
    data object CodeRequired : AuthResult
    /** [messageKey] is a key from the i18n bundle. */
    data class Failure(val messageKey: String) : AuthResult
}

/** Progress snapshot emitted by [UpdateService]. */
data class Progress(
    val fraction: Double,
    val doneBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
)

data class LauncherSettings(
    val runOnStartUp: Boolean = false,
    val startOnDownloaded: Boolean = true,
    val hideToTray: Boolean = true,
    val defenderHint: Boolean = true,
    /** 0 always, 1 except mobile, 2 never */
    val p2pMode: Int = 1,
    /** 0 on game start, 1 on game exit, 2 never */
    val exitMode: Int = 2,
    /** 0..1, mapped to MB by the UI */
    val ramFraction: Double = 0.5,
    val downloadLimitKb: Int = 0,
    val uploadLimitKb: Int = 0,
    val gameRoot: String = "C:\\Games\\EXBO",
    val language: String = "ru",
)

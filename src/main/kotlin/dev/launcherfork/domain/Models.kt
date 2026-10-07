package dev.launcherfork.domain

import java.net.URI
import java.nio.file.Path
import java.time.LocalDate

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
    /** Banner text on the main screen (title, lead, …); null = the game has no description, the banner stays empty. */
    val description: GameDescription?,
    val iconUri: URI,
    val backgroundUri: URI,
    /** Fake "client is installed" flag; decides Download vs Play button. */
    val installed: Boolean,
    /** Fake client size for the progress text, bytes. */
    val clientSizeBytes: Long,
) {
    /** Players online for the games list; null = unknown or offline ([online] is false or [players] is negative). */
    val onlineCount: Int? get() = players.takeIf { online && it >= 0 }
}

/**
 * Text of a game's banner on the main screen: title, lead, subheading, body and a closing line, plus the live online
 * line built by the UI. It comes with the game from ServerRepository, the UI never hard-codes it:
 *  - [Keys]: i18n keys `<prefix>.title|lead|subheading|body|closing` in the messages bundles (mock data, ru + en);
 *  - [Text]: ready text in the user's language, for a real server that sends its own description later.
 */
sealed interface GameDescription {
    data class Keys(val prefix: String) : GameDescription
    data class Text(val title: String, val lead: String, val subheading: String, val body: String, val closing: String) : GameDescription
}

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
    /** Second factor (authenticator app / TOTP, 6 digits) is required -> totp_auth screen. */
    data object TotpRequired : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}

/** What the registration form sends (after the age gate). */
data class RegistrationRequest(
    val email: String,
    val username: String,
    val password: String,
    val birthDate: LocalDate,
    /** "Я даю согласие на получение информационной рассылки". */
    val newsletter: Boolean,
) {
    /** Never print the password (logs, crash reports): the generated toString would. */
    override fun toString(): String =
        "RegistrationRequest(email=$email, username=$username, password=***, birthDate=$birthDate, newsletter=$newsletter)"
}

/** Why registration failed on the server side. The UI maps these to localized strings. */
enum class RegisterError {
    USERNAME_TAKEN,
    EMAIL_TAKEN,
    UNDER_AGE,
    SERVER_ERROR,
}

sealed interface RegisterResult {
    /** Account created; [login] is what the login form gets pre-filled with. */
    data class Success(val login: String) : RegisterResult
    data class Failure(val error: RegisterError) : RegisterResult
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

/** Long-running maintenance of an installed client, started from the game-actions menu next to ИГРАТЬ. */
enum class ClientOperation {
    /** «Проверить файлы»: re-check the installed files (client stays installed). */
    VERIFY,
    /** «Переустановить»: wipe and download the client again (installed at the end). */
    REINSTALL,
    /** «Удалить игру»: remove the client files (not installed at the end). */
    DELETE,
}

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

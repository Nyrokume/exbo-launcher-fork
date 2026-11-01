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
    /** Mock install flag. The live service ignores it and reads the client directory. */
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
    WRONG_HWID,
    NO_CONNECTION,
    SERVER_ERROR,
    /** Short cooldown. [AuthResult.Failure.waitSeconds] is the remaining time. */
    BANNED,
    /** `BANNED_BOOTSTRAP` / `FORCE_UPDATE`: the installed EXBO launcher has to be updated. */
    LAUNCHER_UPDATE,
    /** Password login answered `NEW_PLACE`. The form stays up. */
    NEW_PLACE,
    /** The account e-mail is not confirmed. */
    EMAIL_CONFIRMATION,
    /** The password was marked compromised. */
    PASSWORD_COMPROMISED,
}

sealed interface AuthResult {
    data class Success(val session: UserSession) : AuthResult
    /** Second factor (e-mail code) is required -> mail_auth screen. */
    data object CodeRequired : AuthResult
    /** Second factor (authenticator app / TOTP, 6 digits) is required -> totp_auth screen. */
    data object TotpRequired : AuthResult
    data class Failure(val error: AuthError, val waitSeconds: Int? = null) : AuthResult
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
    /**
     * This launcher has no registration call. The form must not pretend the server failed.
     * `ExternalLinks.REGISTER` is the public site page, not a launcher API.
     */
    NOT_SUPPORTED,
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

/**
 * Server blocker selection. [blocked] is the server ids (`pool::name`) the firewall should drop.
 * Empty [blocked] means nothing is blocked and the game picks (Авто). [mode] is always `blocklist`.
 */
data class ServerBlockState(
    val blocked: List<String> = emptyList(),
    val region: String = "RU",
    val pings: Map<String, Int?> = emptyMap(),
    val preferredByPool: Map<String, String> = emptyMap(),
    val mode: String = "blocklist",
)

data class LauncherSettings(
    val runOnStartUp: Boolean = false,
    val startOnDownloaded: Boolean = true,
    val hideToTray: Boolean = true,
    val defenderHint: Boolean = true,
    /** «Анимации» (Поведение): interface motion on / off (ui/motion/Motion); persists like wallpaper.last. */
    val animations: Boolean = true,
    val p2pMode: P2pMode = P2pMode.EXCEPT_MOBILE,
    val exitMode: ExitMode = ExitMode.NEVER,
    /** Game heap in MB chosen in settings (one of the RAM options); null = not chosen yet -> recommended value. */
    val ramMb: Int? = null,
    /** In-game FOV. The client clamps to 70..110; the launcher does the same. */
    val gameFov: Double = 110.0,
    /** When true, shadows and soft lighting stay off in `options.json`. */
    val gameShadowsOff: Boolean = true,
    /**
     * Legacy value of the old segmented bar: 0..1, mapped to `512 + 3584 * f` MB. Only read when [ramMb] is null
     * (settings saved before the RAM selector) and converted to the nearest RAM option; cleared on the next change.
     */
    val ramFraction: Double? = null,
    val downloadLimitKb: Int = 0,
    val uploadLimitKb: Int = 0,
    /**
     * Legacy single tunnel name (empty = Авто). The server blocker does not use it: an empty [serverBlock]
     * block list is what leaves every tunnel open.
     */
    val tunnel: String = "",
    val serverBlock: ServerBlockState = ServerBlockState(),
    val gameRoot: Path = Path.of(System.getProperty("user.home"), "Games", "EXBO"),
    val language: String = "ru",
)

package dev.launcherfork.domain

/** Cancels a running operation. After cancel() no further callbacks are delivered. */
fun interface Cancellable {
    fun cancel()
}

/**
 * Callbacks are invoked on a background thread; UI code must hop to the FX thread itself.
 */
interface ProgressListener {
    fun onPhase(phase: UpdatePhase) {}
    fun onProgress(progress: Progress)
    fun onFinished()
}

/** Callbacks of [GameLauncher]; invoked on a background thread. */
interface GameListener {
    /** The game process is up. */
    fun onStarted()
    /** The game process ended by itself. Not called after the handle was cancelled. */
    fun onExited()
}

interface AuthService {
    /** Login remembered by "auto auth" (null if none). */
    val savedLogin: String?
    val currentSession: UserSession?

    /** Blocking call; run it off the FX thread. May throw on transport errors. */
    fun login(login: String, password: String, remember: Boolean): AuthResult
    /** E-mail code after [AuthResult.CodeRequired]. Blocking. */
    fun confirmCode(code: String): AuthResult
    /** Authenticator-app code (6 digits) after [AuthResult.TotpRequired]. Blocking. */
    fun confirmTotp(code: String): AuthResult
    /** Creates an account (age gate + registration form). Blocking; may throw on transport errors. */
    fun register(request: RegistrationRequest): RegisterResult
    fun logout()
}

/** Facts about the user's computer the UI needs (RAM options in settings). */
fun interface SystemInfo {
    /** Physical memory of the computer, MB. */
    fun totalRamMb(): Long
}

interface ServerRepository {
    fun servers(): List<Server>

    /**
     * Live player count of [server] (games list, under the name). [listener] gets the current value at once, on the
     * calling thread, and then every change, possibly from a background thread; null = unknown / offline.
     * Cancel the handle when the card goes away. The default has no live source: just [Server.onlineCount].
     */
    fun watchOnline(server: Server, listener: (Int?) -> Unit): Cancellable {
        listener(server.onlineCount)
        return Cancellable {}
    }
}

interface UpdateService {
    /** Launcher self-update check shown on update.fxml. */
    fun checkLauncherUpdate(listener: ProgressListener): Cancellable

    /** Client download for [server]. */
    fun downloadClient(server: Server, listener: ProgressListener): Cancellable

    fun isInstalled(server: Server): Boolean

    /** Progress kept after a pause, so "Continue" resumes from it (0 if none). */
    fun pausedProgress(server: Server): Double
}

/** Starts the game client. Cancelling the returned handle stops ("kills") the game. */
interface GameLauncher {
    fun launch(server: Server, listener: GameListener): Cancellable
}

interface SettingsStore {
    fun load(): LauncherSettings
    fun save(settings: LauncherSettings)
}

/** Opens an external web page (community links) in the user's default browser. Returns at once. */
fun interface LinkOpener {
    fun open(url: String)
}

/** Everything the UI is allowed to depend on. Built once in app/AppContext. */
data class Services(
    val auth: AuthService,
    val servers: ServerRepository,
    val updates: UpdateService,
    val launcher: GameLauncher,
    val settings: SettingsStore,
    val system: SystemInfo,
    val links: LinkOpener,
)

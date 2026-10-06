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
    fun confirmCode(code: String): AuthResult
    fun logout()
}

/** Facts about the user's computer the UI needs (RAM options in settings). */
fun interface SystemInfo {
    /** Physical memory of the computer, MB. */
    fun totalRamMb(): Long
}

interface ServerRepository {
    fun servers(): List<Server>
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

/** Everything the UI is allowed to depend on. Built once in app/AppContext. */
data class Services(
    val auth: AuthService,
    val servers: ServerRepository,
    val updates: UpdateService,
    val launcher: GameLauncher,
    val settings: SettingsStore,
    val system: SystemInfo,
)

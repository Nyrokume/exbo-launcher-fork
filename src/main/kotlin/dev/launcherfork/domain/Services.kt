package dev.launcherfork.domain

/** Cancels a running fake operation. */
fun interface Cancellable {
    fun cancel()
}

/**
 * Callbacks are invoked on a background thread; UI code must hop to the FX thread itself.
 */
interface ProgressListener {
    fun onStatus(statusKey: String) {}
    fun onProgress(progress: Progress)
    fun onFinished()
}

interface AuthService {
    /** Login remembered by "auto auth" (null if none). */
    val savedLogin: String?
    val currentSession: UserSession?

    /** Blocking call; run it off the FX thread. */
    fun login(login: String, password: String, remember: Boolean): AuthResult
    fun confirmCode(code: String): AuthResult
    fun logout()
}

interface ServerRepository {
    fun servers(): List<Server>
}

interface NewsRepository {
    fun latest(): List<NewsItem>
}

interface UpdateService {
    /** Fake launcher self-update check shown on update.fxml. */
    fun checkLauncherUpdate(listener: ProgressListener): Cancellable

    /** Fake client download for [server]. */
    fun downloadClient(server: Server, listener: ProgressListener): Cancellable

    fun isInstalled(server: Server): Boolean

    /** Progress kept after a pause, so "Continue" resumes from it (0 if none). */
    fun pausedProgress(server: Server): Double
}

interface SettingsStore {
    fun load(): LauncherSettings
    fun save(settings: LauncherSettings)
}

/** Everything the UI is allowed to depend on. Built once in app/AppContext. */
data class Services(
    val auth: AuthService,
    val servers: ServerRepository,
    val news: NewsRepository,
    val updates: UpdateService,
    val settings: SettingsStore,
)

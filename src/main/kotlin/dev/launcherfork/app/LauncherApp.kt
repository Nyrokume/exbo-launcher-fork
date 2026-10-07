package dev.launcherfork.app

import dev.launcherfork.data.local.WallpaperFileMemory
import dev.launcherfork.data.mock.MockSecondFactor
import dev.launcherfork.domain.ExboPaths
import dev.launcherfork.domain.FolderOpener
import dev.launcherfork.domain.FolderTarget
import dev.launcherfork.domain.GameFolders
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.LinkOpener
import dev.launcherfork.domain.LinkPolicy
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.components.loadImage
import dev.launcherfork.wallpaper.Wallpapers
import javafx.application.Application
import javafx.stage.Stage
import javafx.stage.StageStyle
import java.io.File
import java.nio.file.Path
import kotlin.random.Random

/**
 * Entry point of the visual fork. Two windows (docs/design/WINDOWS.md): the pre-main window (primary stage:
 * update, then auth / mail code) and, after login, the main window.
 * System properties: launcher.lang=ru|en, launcher.mockSpeed=<factor>, launcher.screenshots=<dir> (auto tour),
 * launcher.dev=true (F1-F7 modal shortcuts), launcher.theme=zone|exbo|shadcn (zone by default),
 * launcher.settingsFile=<path> (where the last wallpaper number is kept),
 * launcher.onlineTickMs=<ms> (mock online-count drift; 10 s by default, 0 = fixed, always 0 during the tour),
 * launcher.wallpaper=1..6 (always this main-window wallpaper; the tour uses Wallpapers.TOUR_DEFAULT without it).
 */
class LauncherApp : Application() {
    private var app: AppContext? = null

    override fun start(stage: Stage) {
        val fonts = Theme.loadFonts()
        val lang = System.getProperty("launcher.lang", "ru")
        val speed = System.getProperty("launcher.mockSpeed")?.toDoubleOrNull() ?: 1.0
        val devMode = System.getProperty("launcher.dev").toBoolean()
        // community links (header «Сообщества») open in the system browser through JavaFX HostServices
        val browser = hostServices
        val links = LinkOpener { url ->
            if (LinkPolicy.isAllowed(url)) {
                System.err.println("[launcher-fork] open link: $url")
                browser.showDocument(url)
            } else {
                System.err.println("[launcher-fork] WARNING: refused to open non-allowlisted link: $url")
            }
        }
        // game-actions: «Открыть папку игры» (under gameRoot) or «Открыть папку логов» (under %APPDATA%/EXBO).
        // Not through LinkOpener. Missing dirs: nearest existing parent, never above the allowlisted root.
        val folders = FolderOpener { dir ->
            val abs = dir.toAbsolutePath().normalize()
            val gameRoot = this.app?.services?.settings?.load()?.gameRoot ?: LauncherSettings().gameRoot
            val exboRoot = ExboPaths.roamingRoot().toAbsolutePath().normalize()
            val allowRoot = if (abs.startsWith(exboRoot)) exboRoot else gameRoot
            when (val target = GameFolders.folderToOpen(dir, allowRoot)) {
                is FolderTarget.Open -> {
                    System.err.println("[launcher-fork] open folder: ${target.dir}")
                    browser.showDocument(target.dir.toUri().toString())
                }
                is FolderTarget.Missing ->
                    System.err.println("[launcher-fork] WARNING: root ${target.root} does not exist, nothing to open for $dir")
                is FolderTarget.OutsideRoot ->
                    System.err.println("[launcher-fork] WARNING: refused to open ${target.dir}: outside ${target.root}")
            }
        }
        val shotsDir = System.getProperty("launcher.screenshots")
        // the games list shows the online count: the mock drifts a little in normal runs, the tour keeps it fixed
        val onlineTickMs = if (shotsDir != null) 0L else System.getProperty("launcher.onlineTickMs")?.toLongOrNull() ?: 10_000L
        // second factor after the password: random e-mail code / authenticator app per login unless -Dlauncher.mock2fa
        // says otherwise; the tour signs in without one (it shows the panels through the "2fa" / "totp" logins)
        val mock2fa = if (shotsDir != null && System.getProperty(MockSecondFactor.PROPERTY) == null) MockSecondFactor.NONE
            else MockSecondFactor.fromSystemProperties()
        System.err.println("[launcher-fork] mock 2FA: ${mock2fa.name.lowercase()}")
        val settingsFile = settingsFile(shotsDir)
        val app = AppContext.mock(
            language = lang, speed = speed, devMode = devMode, links = links, folders = folders, onlineTickMs = onlineTickMs,
            mock2fa = mock2fa,
        )
        this.app = app
        val zoneFonts = if (Theme.themeName == "zone") Theme.zoneFonts else emptyList()
        System.err.println("[launcher-fork] fonts: ${fonts + zoneFonts}, theme: ${Theme.themeName}")

        // pre-main window: no OS frame, transparent outside its rounded corners, fixed size
        stage.initStyle(StageStyle.TRANSPARENT)
        stage.isResizable = false
        // every size (the main window mirrors this list, StageNavigator binds mainStage.icons to it)
        stage.icons += Theme.appIconResources().map { loadImage(it) }

        // this launch's wallpaper, chosen ONCE before any window exists (both windows share Wallpapers.current):
        // -Dlauncher.wallpaper=N, the tour's fixed one, else random but never the previous launch's (wallpaper.last)
        Wallpapers.choose(
            memory = settingsFile?.let(::WallpaperFileMemory),
            random = Random.Default,
            forced = Wallpapers.forcedNumber() ?: if (shotsDir != null) Wallpapers.TOUR_DEFAULT else null,
        )

        val navigator = StageNavigator(stage, app, lang)
        if (shotsDir != null) ScreenshotTour(navigator, File(shotsDir)).install()

        navigator.showLauncherUpdate(afterUpdate = navigator::showAuth)
        stage.show()
        stage.centerOnScreen()
    }

    /**
     * Where the last wallpaper number is kept: -Dlauncher.settingsFile, else
     * ~/.launcher-fork/settings.properties. The screenshot tour keeps nothing (same pictures on every machine).
     */
    private fun settingsFile(shotsDir: String?): Path? = System.getProperty("launcher.settingsFile")?.let { Path.of(it) }
        ?: if (shotsDir != null) null else Path.of(System.getProperty("user.home"), ".launcher-fork", "settings.properties")

    override fun stop() {
        app?.shutdown()
    }
}

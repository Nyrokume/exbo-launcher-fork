package dev.launcherfork.app

import dev.launcherfork.data.local.WallpaperFileMemory
import dev.launcherfork.data.mock.MockSecondFactor
import dev.launcherfork.domain.Appearance
import dev.launcherfork.domain.FolderOpener
import dev.launcherfork.domain.FolderTarget
import dev.launcherfork.domain.GameFolders
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.LinkOpener
import dev.launcherfork.domain.LinkPolicy
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.components.loadImage
import dev.launcherfork.ui.motion.Motion
import dev.launcherfork.wallpaper.Wallpapers
import javafx.application.Application
import javafx.stage.Stage
import javafx.stage.StageStyle
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

/**
 * Entry point of the visual fork. Two windows (docs/design/WINDOWS.md): the pre-main window (primary stage:
 * update, then auth / mail code) and, after login, the main window.
 * System properties: launcher.lang=ru|en, launcher.mockSpeed=<factor>, launcher.screenshots=<dir> (auto tour),
 * launcher.dev=true (F1-F7 modal shortcuts), launcher.theme=zone|zone-light|exbo|shadcn (zone by default; zone-light: start in the white
 * scheme), launcher.settingsFile=<path> (where the chosen theme and the last wallpaper are kept),
 * launcher.onlineTickMs=<ms> (mock online-count drift; 10 s by default, 0 = fixed, always 0 during the tour),
 * launcher.wallpaper=1..6 (always this main-window wallpaper; the tour uses Wallpapers.TOUR_DEFAULT without it),
 * launcher.animations=on|off (interface motion, wins over the saved «Анимации»; ui/motion/Motion),
 * launcher.wallpaper.drift=on (very slow wallpaper zoom, off by default),
 * launcher.showcase=true (looping presentation with mock data that keeps nothing; launcher.showcase.size=WxH,
 * launcher.showcase.captions=true|false, launcher.showcase.stills=<dir>, launcher.showcase.loops=N; app/Showcase).
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
        // game-actions menu «Открыть папку игры»: the game folder (GameFolders) in the OS file manager, NOT through
        // LinkOpener (that one only takes allowlisted https URLs). The folder may not exist in the fork: open the
        // nearest existing parent, but never above settings.gameRoot; without the root, open nothing.
        val folders = FolderOpener { dir ->
            val gameRoot = this.app?.services?.settings?.load()?.gameRoot ?: LauncherSettings().gameRoot
            when (val target = GameFolders.folderToOpen(dir, gameRoot)) {
                is FolderTarget.Open -> {
                    System.err.println("[launcher-fork] open folder: ${target.dir}")
                    browser.showDocument(target.dir.toUri().toString())
                }
                is FolderTarget.Missing ->
                    System.err.println("[launcher-fork] WARNING: game root ${target.root} does not exist, nothing to open for $dir")
                is FolderTarget.OutsideRoot ->
                    System.err.println("[launcher-fork] WARNING: refused to open ${target.dir}: outside the game root ${target.root}")
            }
        }
        // showcase mode (app/Showcase): mock data only, animations on, keeps nothing; wins over the screenshot tour
        val showcase = Showcase.fromProperties()
        val shotsDir = if (showcase != null) null else System.getProperty("launcher.screenshots")
        if (showcase != null) {
            Motion.override = Motion.parse(System.getProperty(Motion.PROPERTY)) ?: true
            if (System.getProperty(Motion.DRIFT_PROPERTY) == null) System.setProperty(Motion.DRIFT_PROPERTY, "on")
            System.err.println("[launcher-fork] showcase: ${showcase.width.toInt()}x${showcase.height.toInt()}, " +
                "captions ${if (showcase.captions) "on" else "off"}, nothing is saved (in-memory settings)")
        }
        val tour = shotsDir != null || showcase != null
        // the games list shows the online count: the mock drifts a little in normal runs, the tour keeps it fixed
        val onlineTickMs = if (tour) 0L else System.getProperty("launcher.onlineTickMs")?.toLongOrNull() ?: 10_000L
        // second factor after the password: random e-mail code / authenticator app per login unless -Dlauncher.mock2fa
        // says otherwise; the tour signs in without one (it shows the panels through the "2fa" / "totp" logins)
        val mock2fa = if (showcase != null) MockSecondFactor.NONE
            else if (shotsDir != null && System.getProperty(MockSecondFactor.PROPERTY) == null) MockSecondFactor.NONE
            else MockSecondFactor.fromSystemProperties()
        System.err.println("[launcher-fork] mock 2FA: ${mock2fa.name.lowercase()}")
        val settingsFile = settingsFile(
            showcase = showcase != null, screenshots = shotsDir != null,
            property = System.getProperty("launcher.settingsFile"), home = System.getProperty("user.home"),
        )
        val app = AppContext.mock(
            language = lang, speed = speed, devMode = devMode, links = links, folders = folders, onlineTickMs = onlineTickMs,
            mock2fa = mock2fa, settingsFile = settingsFile,
        )
        this.app = app
        // colour scheme (zone only): -Dlauncher.theme=zone-light wins, else the saved «Тема» (dark by default)
        Theme.appearance = if (!Theme.supportsLight) Appearance.DARK
            else Theme.forcedAppearance ?: app.services.settings.load().appearance
        // interface motion: -Dlauncher.animations=on|off wins over the saved «Анимации» (on by default); the screenshot
        // tour switches it off itself (ScreenshotTour.install), so its pictures never catch a half-finished fade
        Motion.setting = app.services.settings.load().animations
        System.err.println("[launcher-fork] animations: ${Motion.settingValue(Motion.enabled)}" +
            (Motion.override?.let { " (-D${Motion.PROPERTY})" } ?: ""))
        val zoneFonts = if (Theme.themeName == "zone") Theme.zoneFonts else emptyList()
        System.err.println("[launcher-fork] fonts: ${fonts + zoneFonts}, theme: ${Theme.themeName}, appearance: ${Theme.appearance.name.lowercase()}")

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
            forced = Wallpapers.forcedNumber() ?: if (tour) Wallpapers.TOUR_DEFAULT else null,
        )

        val navigator = StageNavigator(stage, app, lang)
        if (shotsDir != null) ScreenshotTour(navigator, File(shotsDir)).install()
        if (showcase != null) {
            navigator.mainWindowSize = showcase.width to showcase.height
            ShowcaseTour(navigator, showcase, lang).install()
        }

        navigator.showLauncherUpdate(afterUpdate = navigator::showAuth)
        stage.show()
        stage.centerOnScreen()
    }

    override fun stop() {
        app?.shutdown()
    }

    companion object {
        /**
         * Where the chosen theme and the last wallpaper are kept: -Dlauncher.settingsFile ([property]), else
         * ~/.launcher-fork/settings.properties. The screenshot tour keeps nothing unless told where (same pictures on
         * every machine); the showcase NEVER keeps anything, not even with -Dlauncher.settingsFile (null = in memory).
         */
        fun settingsFile(showcase: Boolean, screenshots: Boolean, property: String?, home: String): Path? = when {
            showcase -> null
            property != null -> Path.of(property)
            screenshots -> null
            else -> Path.of(home, ".launcher-fork", "settings.properties")
        }
    }
}

package dev.launcherfork.app

import dev.launcherfork.data.exbo.ExboApi
import dev.launcherfork.data.exbo.ExboAuthService
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
import dev.launcherfork.ui.motion.Motion
import dev.launcherfork.wallpaper.Wallpapers
import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import javafx.stage.StageStyle
import java.io.File
import java.nio.file.Path
import kotlin.random.Random

/**
 * Entry point of the visual fork. Two windows (docs/design/WINDOWS.md): the pre-main window (primary stage:
 * update, then auth / mail code) and, after login, the main window.
 * System properties: launcher.lang=ru|en, launcher.mockSpeed=<factor>, launcher.screenshots=<dir> (auto tour),
 * launcher.dev=true (F1-F7 modal shortcuts), launcher.theme=zone|exbo|shadcn|frost (zone by default),
 * launcher.settingsFile=<path> (where the last wallpaper number is kept),
 * launcher.onlineTickMs=<ms> (mock online-count drift; 10 s by default, 0 = fixed, always 0 during the tour),
 * launcher.wallpaper=1..6 (always this main-window wallpaper; the tour uses Wallpapers.TOUR_DEFAULT without it),
 * launcher.animations=on|off (interface motion; ui/motion/Motion),
 * launcher.wallpaper.drift=on (very slow wallpaper zoom, off by default),
 * launcher.showcase=true (looping interactive presentation; launcher.showcase.size=WxH,
 * launcher.showcase.captions=true|false, launcher.showcase.stills=<dir>, launcher.showcase.loops=N),
 * launcher.demo=mail2fa (e-mail code: wrong → success → main),
 * launcher.demo=2fa (layout: mail idle/error, then TOTP idle/error and stay).
 */
class LauncherApp : Application() {
    private var app: AppContext? = null

    override fun start(stage: Stage) {
        val fonts = Theme.loadFonts()
        val lang = System.getProperty("launcher.lang", "ru")
        val speed = System.getProperty("launcher.mockSpeed")?.toDoubleOrNull() ?: 1.0
        val devMode = System.getProperty("launcher.dev").toBoolean()
        val browser = hostServices
        val links = LinkOpener { url ->
            if (LinkPolicy.isAllowed(url)) {
                System.err.println("[launcher-fork] open link: $url")
                browser.showDocument(url)
            } else {
                System.err.println("[launcher-fork] WARNING: refused to open non-allowlisted link: $url")
            }
        }
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
        // showcase: mock-only looping presentation; wins over the screenshot tour
        val showcase = Showcase.fromProperties()
        val shotsDir = if (showcase != null) null else System.getProperty("launcher.screenshots")
        if (showcase != null) {
            Motion.override = Motion.parse(System.getProperty(Motion.PROPERTY)) ?: true
            if (System.getProperty(Motion.DRIFT_PROPERTY) == null) System.setProperty(Motion.DRIFT_PROPERTY, "on")
            System.err.println(
                "[launcher-fork] showcase: ${showcase.width.toInt()}x${showcase.height.toInt()}, " +
                    "captions ${if (showcase.captions) "on" else "off"}, nothing is saved"
            )
        } else if (shotsDir != null) {
            // stills must never catch a half-faded menu/modal; -Dlauncher.animations=on can re-enable for debugging
            Motion.override = Motion.parse(System.getProperty(Motion.PROPERTY)) ?: false
        }
        val tour = shotsDir != null || showcase != null
        val onlineTickMs = if (tour) 0L else System.getProperty("launcher.onlineTickMs")?.toLongOrNull() ?: 10_000L
        val mock2fa = when {
            showcase != null -> MockSecondFactor.NONE
            shotsDir != null && System.getProperty(MockSecondFactor.PROPERTY) == null -> MockSecondFactor.NONE
            else -> MockSecondFactor.fromSystemProperties()
        }
        val demo = System.getProperty("launcher.demo")
        val settingsFile = settingsFile(showcase = showcase != null, screenshots = shotsDir != null)
        val useExbo = !tour && demo == null && ExboApi().installed()
        val app = if (useExbo) {
            System.err.println("[launcher-fork] backend: exbo server list")
            AppContext.live(
                language = lang,
                speed = speed,
                devMode = devMode,
                links = links,
                folders = folders,
                settingsFile = settingsFile ?: Path.of(System.getProperty("user.home"), ".launcher-fork", "settings.properties"),
            )
        } else {
            System.err.println("[launcher-fork] mock 2FA: ${mock2fa.name.lowercase()}")
            AppContext.mock(
                language = lang, speed = speed, devMode = devMode, links = links, folders = folders,
                onlineTickMs = onlineTickMs, mock2fa = mock2fa,
            )
        }
        this.app = app
        Motion.setting = app.services.settings.load().animations
        System.err.println(
            "[launcher-fork] animations: ${Motion.settingValue(Motion.enabled)}" +
                (Motion.override?.let { " (-D${Motion.PROPERTY})" } ?: "")
        )
        val zoneFonts = if (Theme.themeName == "zone" || Theme.themeName == "frost") Theme.zoneFonts else emptyList()
        System.err.println("[launcher-fork] fonts: ${fonts + zoneFonts}, theme: ${Theme.themeName}")

        stage.initStyle(StageStyle.TRANSPARENT)
        stage.isResizable = false
        stage.icons += Theme.appIconResources().map { loadImage(it) }

        val pack = Wallpapers.packForTheme(Theme.themeName)
        Wallpapers.choose(
            memory = settingsFile?.let(::WallpaperFileMemory),
            random = Random.Default,
            forced = Wallpapers.forcedNumber(count = pack.size) ?: if (tour) Wallpapers.TOUR_DEFAULT else null,
            list = pack,
        )

        val navigator = StageNavigator(stage, app, lang)
        if (shotsDir != null) ScreenshotTour(navigator, File(shotsDir)).install()
        if (showcase != null) {
            navigator.mainWindowSize = showcase.width to showcase.height
            ShowcaseTour(navigator, showcase, lang).install()
        }

        val authShown = java.util.concurrent.atomic.AtomicBoolean(false)
        val exboAuth = app.services.auth as? ExboAuthService
        fun applyAuto() {
            if (!authShown.get()) return
            if (app.services.auth.currentSession != null) navigator.showMain()
            else if (exboAuth?.autoType == "NEW_PLACE") {
                (navigator.panelController as? dev.launcherfork.ui.screens.auth.InitialAuthController)?.explainNewPlace()
            }
        }
        if (exboAuth != null) {
            Thread {
                exboAuth.tryAuto()
                Platform.runLater { applyAuto() }
            }.apply { isDaemon = true; name = "exbo-auto-auth" }.start()
        }
        // Showcase / 2FA demos start on auth; skip the update splash for a clean narrative.
        when {
            showcase != null || demo != null -> navigator.showAuth()
            else -> navigator.showLauncherUpdate(afterUpdate = {
                navigator.showAuth()
                authShown.set(true)
                applyAuto()
            })
        }
        when (demo) {
            "mail2fa" -> Mail2faDemo(navigator).install()
            "2fa" -> SecondFactorLayoutDemo(navigator).install()
        }
        stage.show()
        stage.centerOnScreen()
    }

    /**
     * Wallpaper persistence path. Showcase never writes anything (null = in-memory). Screenshot tour also keeps
     * nothing unless -Dlauncher.settingsFile is set.
     */
    private fun settingsFile(showcase: Boolean, screenshots: Boolean): Path? = when {
        showcase -> null
        System.getProperty("launcher.settingsFile") != null -> Path.of(System.getProperty("launcher.settingsFile"))
        screenshots -> null
        else -> Path.of(System.getProperty("user.home"), ".launcher-fork", "settings.properties")
    }

    override fun stop() {
        app?.shutdown()
    }
}

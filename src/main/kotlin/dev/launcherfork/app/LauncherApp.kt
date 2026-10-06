package dev.launcherfork.app

import dev.launcherfork.domain.FolderOpener
import dev.launcherfork.domain.LinkOpener
import dev.launcherfork.domain.LinkPolicy
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.components.loadImage
import javafx.application.Application
import javafx.stage.Stage
import javafx.stage.StageStyle
import java.io.File
import java.nio.file.Files

/**
 * Entry point of the visual fork. Two windows (docs/design/WINDOWS.md): the pre-main window (primary stage:
 * update, then auth / mail code) and, after login, the main window.
 * System properties: launcher.lang=ru|en, launcher.mockSpeed=<factor>, launcher.screenshots=<dir> (auto tour),
 * launcher.dev=true (F1-F7 modal shortcuts), launcher.theme=exbo|shadcn|zone,
 * launcher.onlineTickMs=<ms> (mock online-count drift; 10 s by default, 0 = fixed, always 0 during the tour).
 */
class LauncherApp : Application() {
    private var app: AppContext? = null

    override fun start(stage: Stage) {
        val fonts = Theme.loadFonts() + (if (Theme.themeName == "zone") Theme.zoneFonts else emptyList())
        System.err.println("[launcher-fork] fonts: $fonts")
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
        // nearest existing parent instead.
        val folders = FolderOpener { dir ->
            val target = generateSequence(dir.toAbsolutePath().normalize()) { it.parent }.firstOrNull { Files.isDirectory(it) }
            if (target == null) {
                System.err.println("[launcher-fork] WARNING: no existing folder for $dir")
            } else {
                System.err.println("[launcher-fork] open folder: $target")
                browser.showDocument(target.toUri().toString())
            }
        }
        val shotsDir = System.getProperty("launcher.screenshots")
        // the games list shows the online count: the mock drifts a little in normal runs, the tour keeps it fixed
        val onlineTickMs = if (shotsDir != null) 0L else System.getProperty("launcher.onlineTickMs")?.toLongOrNull() ?: 10_000L
        val app = AppContext.mock(language = lang, speed = speed, devMode = devMode, links = links, folders = folders, onlineTickMs = onlineTickMs)
        this.app = app

        // pre-main window: no OS frame, transparent outside its rounded corners, fixed size
        stage.initStyle(StageStyle.TRANSPARENT)
        stage.isResizable = false
        // every size (the main window mirrors this list, StageNavigator binds mainStage.icons to it)
        stage.icons += Theme.appIconResources().map { loadImage(it) }

        val navigator = StageNavigator(stage, app, lang)
        if (shotsDir != null) ScreenshotTour(navigator, File(shotsDir)).install()

        navigator.showLauncherUpdate(afterUpdate = navigator::showAuth)
        stage.show()
        stage.centerOnScreen()
    }

    override fun stop() {
        app?.shutdown()
    }
}

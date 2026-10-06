package dev.launcherfork.app

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.components.loadImage
import javafx.application.Application
import javafx.stage.Stage
import javafx.stage.StageStyle
import java.io.File

/**
 * Entry point of the visual fork. Two windows (docs/design/WINDOWS.md): the pre-main window (primary stage:
 * update, then auth / mail code) and, after login, the main window.
 * System properties: launcher.lang=ru|en, launcher.mockSpeed=<factor>, launcher.screenshots=<dir> (auto tour),
 * launcher.dev=true (F1-F7 modal shortcuts), launcher.theme=exbo|shadcn|zone.
 */
class LauncherApp : Application() {
    private var app: AppContext? = null

    override fun start(stage: Stage) {
        val fonts = Theme.loadFonts() + (if (Theme.themeName == "zone") Theme.zoneFonts else emptyList())
        System.err.println("[launcher-fork] fonts: $fonts")
        val lang = System.getProperty("launcher.lang", "ru")
        val speed = System.getProperty("launcher.mockSpeed")?.toDoubleOrNull() ?: 1.0
        val devMode = System.getProperty("launcher.dev").toBoolean()
        val app = AppContext.mock(language = lang, speed = speed, devMode = devMode)
        this.app = app

        // pre-main window: no OS frame, transparent outside its rounded corners, fixed size
        stage.initStyle(StageStyle.TRANSPARENT)
        stage.isResizable = false
        stage.icons += loadImage(Theme.IMAGE_ROOT + "icon.png")

        val navigator = StageNavigator(stage, app, lang)
        val shotsDir = System.getProperty("launcher.screenshots")
        if (shotsDir != null) ScreenshotTour(navigator, File(shotsDir)).install()

        navigator.showLauncherUpdate(afterUpdate = navigator::showAuth)
        stage.show()
        stage.centerOnScreen()
    }

    override fun stop() {
        app?.shutdown()
    }
}

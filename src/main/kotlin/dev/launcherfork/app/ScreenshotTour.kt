package dev.launcherfork.app

import dev.launcherfork.ui.screens.auth.InitialAuthController
import dev.launcherfork.theme.Theme
import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.scene.Scene
import javafx.stage.Stage
import javafx.util.Duration
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Dev tool: walks through the screens automatically and saves Scene snapshots as PNG
 * (`./gradlew run -Dlauncher.theme=zone -Dlauncher.screenshots=docs/screenshots/zone`). Not used in normal runs.
 */
class ScreenshotTour(private val stage: Stage, private val nav: StageNavigator, private val dir: File) {
    private val steps = ArrayDeque<Pair<Double, () -> Unit>>()

    fun install() {
        dir.mkdirs()
        var authDone = false
        var mainDone = false
        nav.onScreenChanged = { name ->
            when {
                name == "update" -> later(0.5) { shot("01_update_overlay") }
                name == "auth" && !authDone -> { authDone = true; authFlow() }
                name == "main" && !mainDone -> { mainDone = true; mainFlow() }
            }
        }
    }

    private fun authFlow() {
        val auth = { nav.overlayController as InitialAuthController }
        step(0.6) { shot("02_auth_overlay") }
        step(0.1) {
            auth().loginField.text = ""
            auth().passwordField.text = ""
            auth().onEnter() // shows the "empty login" error
        }
        step(0.3) { shot("03_auth_error") }
        step(0.1) {
            auth().loginField.text = "player"
            auth().passwordField.text = "secret"
            auth().onEnter()
        }
        run()
    }

    private fun mainFlow() {
        val main = { nav.mainController!! }
        step(1.0) { shot("04_main") }
        step(0.1) { main().advancedNewsPaneController.newsScroll.vvalue = 1.0 }
        step(0.4) { shot("05_main_news_scrolled") }
        step(0.1) { main().advancedNewsPaneController.cards.first().onMouseClicked.handle(null) }
        step(0.4) { shot("06_news_article") }
        step(0.1) { main().articleBgController.hide(); main().setSettingsVisible(true) }
        step(0.6) { shot("07_settings_game") }
        step(0.1) { main().settingsController.scroll.vvalue = 0.55 }
        step(0.4) { shot("08_settings_updates") }
        step(0.1) { main().settingsController.scroll.vvalue = 1.0 }
        step(0.4) { shot("09_settings_others") }
        step(0.1) {
            val m = main()
            m.settingsController.scroll.vvalue = 0.0
            m.setSettingsVisible(false)
            val bravo = m.servers.first { it.id == "bravo" }
            m.serversListController.select(bravo)
            m.defaultStatusPaneController.downloadButton.fire() // Download -> install dialog
        }
        step(0.6) { shot("10_install_dialog") }
        step(0.1) { main().installPaneController.installYes.fire() } // start fake download
        step(2.6) { shot("11_download_progress") }
        step(0.1) {
            val m = main()
            m.serversListController.select(m.servers.first())
            m.defaultStatusPaneController.downloadButton.fire() // Play -> Starting -> Running
        }
        step(1.8) { shot("12_running") }
        step(0.1) { main().clientSettingsPaneController.toggle() }
        step(0.3) { shot("13_tools_menu") }
        step(0.1) {
            main().clientSettingsPaneController.hide()
            stage.width = 1366.0
            stage.height = 800.0
        }
        step(0.8) { shot("14_resized_1366x800") }
        step(0.1) {
            stage.width = Theme.WINDOW_WIDTH
            stage.height = Theme.WINDOW_HEIGHT
            nav.switchLanguage("en")
        }
        step(1.0) { shot("15_settings_en") }
        step(0.3) { Platform.exit() }
        run()
    }

    private fun step(delaySec: Double, action: () -> Unit) = steps.addLast(delaySec to action)

    private fun run() {
        val (delay, action) = steps.removeFirstOrNull() ?: return
        later(delay) {
            try { action() } catch (e: Exception) { e.printStackTrace() }
            run()
        }
    }

    private fun later(sec: Double, action: () -> Unit) =
        PauseTransition(Duration.seconds(sec)).apply { setOnFinished { action() } }.play()

    private fun shot(name: String) = save(stage.scene, File(dir, "$name.png"))

    companion object {
        fun save(scene: Scene, file: File) {
            val img = scene.snapshot(null)
            val w = img.width.toInt()
            val h = img.height.toInt()
            val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            val reader = img.pixelReader
            for (y in 0 until h) for (x in 0 until w) out.setRGB(x, y, reader.getArgb(x, y))
            ImageIO.write(out, "png", file)
            System.err.println("[launcher-fork] screenshot ${file.path}")
        }
    }
}

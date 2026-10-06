package dev.launcherfork.app

import dev.launcherfork.ui.screens.auth.InitialAuthController
import dev.launcherfork.theme.Theme
import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.scene.Scene
import javafx.util.Duration
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Dev tool: walks through the screens automatically and saves Scene snapshots as PNG
 * (`./gradlew run -Dlauncher.theme=zone -Dlauncher.screenshots=docs/screenshots/zone`). Not used in normal runs.
 */
class ScreenshotTour(private val nav: StageNavigator, private val dir: File) {
    private val steps = ArrayDeque<Pair<Double, () -> Unit>>()

    fun install() {
        dir.mkdirs()
        var authDone = false
        var mainDone = false
        nav.onScreenChanged = { name ->
            when {
                name == "update" -> later(0.5) { shot("01_update_window") }
                name == "auth" && !authDone -> { authDone = true; authFlow() }
                name == "main" && !mainDone -> { mainDone = true; mainFlow() }
            }
        }
    }

    private fun authFlow() {
        val auth = { nav.panelController as InitialAuthController }
        step(0.6) { shot("02_auth_window") }
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
        val stage = nav.mainStage
        step(1.0) { shot("04_main") }
        step(0.1) { main().profileBarController.showMenu() }
        step(0.3) { shot("05_profile_menu") }
        step(0.1) { main().profileBarController.hideMenu(); main().setSettingsVisible(true) }
        step(0.6) { shot("06_settings_game") }
        step(0.1) { main().settingsController.scroll.vvalue = 0.55 }
        step(0.4) { shot("07_settings_updates") }
        step(0.1) { main().settingsController.scroll.vvalue = 1.0 }
        step(0.4) { shot("08_settings_others") }
        step(0.1) {
            val m = main()
            m.settingsController.scroll.vvalue = 0.0
            m.setSettingsVisible(false)
            m.defaultStatusPaneController.downloadButton.fire() // Play -> Starting -> Running
        }
        step(1.8) { shot("09_running") }
        step(0.1) { main().clientSettingsPaneController.toggle() }
        step(0.3) { shot("10_tools_menu") }
        step(0.1) {
            main().clientSettingsPaneController.hide()
            stage.width = 1366.0
            stage.height = 800.0
        }
        step(0.8) { shot("11_resized_1366x800") }
        step(0.1) {
            stage.width = Theme.WINDOW_WIDTH
            stage.height = Theme.WINDOW_HEIGHT
            nav.switchLanguage("en")
        }
        step(1.0) { shot("12_settings_en") }
        step(0.1) { main().profileBarController.switchAccount.fire() } // back to the separate auth window
        step(0.6) { shot("13_auth_after_switch_account") }
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

    /** Snapshot of the window the user currently sees (pre-main or main). */
    private fun shot(name: String) = save(nav.window.scene, File(dir, "$name.png"))

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

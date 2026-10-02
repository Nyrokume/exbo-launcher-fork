package dev.launcherfork.app

import dev.launcherfork.ui.screens.auth.InitialAuthController
import dev.launcherfork.ui.screens.main.MainController
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
 * (`./gradlew run -Dlauncher.screenshots=docs/screenshots`). Not used in normal runs.
 */
class ScreenshotTour(private val stage: Stage, private val nav: StageNavigator, private val dir: File) {
    private val steps = ArrayDeque<Pair<Double, () -> Unit>>()

    fun install() {
        dir.mkdirs()
        var authDone = false
        var mainDone = false
        nav.onScreenChanged = { name ->
            when {
                name == "update" -> later(0.9) { shot("01_update_progress") }
                name == "auth" && !authDone -> { authDone = true; authFlow() }
                name == "main" && !mainDone -> { mainDone = true; mainFlow() }
            }
        }
    }

    private fun authFlow() {
        step(0.6) { shot("02_auth") }
        step(0.1) {
            val auth = nav.currentController as InitialAuthController
            auth.loginField.text = ""
            auth.passwordField.text = ""
            auth.onEnter() // shows the "empty login" error
        }
        step(0.3) { shot("03_auth_error") }
        step(0.1) {
            val auth = nav.currentController as InitialAuthController
            auth.loginField.text = "player"
            auth.passwordField.text = "secret"
            auth.onEnter()
        }
        run()
    }

    private fun mainFlow() {
        val main = { nav.currentController as MainController }
        step(1.0) { shot("04_main_news") }
        step(0.1) { main().serversListController.select(main().selected!!) }
        step(0.6) { shot("05_main_server") }
        step(0.1) { main().setSettingsVisible(true) }
        step(0.6) { shot("06_settings") }
        step(0.1) { main().setSettingsVisible(false) }
        step(0.1) {
            val m = main()
            val bravo = m.servers.first { it.id == "bravo" }
            m.serversListController.select(bravo)
            m.defaultStatusPaneController.downloadButton.fire() // Download -> install dialog
        }
        step(0.6) { shot("07_install_dialog") }
        step(0.1) { main().installPaneController.installYes.fire() } // start fake download
        step(2.6) { shot("08_fake_progress") }
        step(0.1) {
            val m = main()
            m.serversListController.select(m.servers.first())
            m.defaultStatusPaneController.downloadButton.fire() // Play -> Starting -> Running
        }
        step(1.8) { shot("09_fake_running") }
        step(0.1) { nav.switchLanguage("en") }
        step(1.0) { shot("10_settings_en") }
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

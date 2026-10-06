package dev.launcherfork.app

import dev.launcherfork.ui.screens.auth.AgeGateController
import dev.launcherfork.ui.screens.auth.InitialAuthController
import dev.launcherfork.ui.screens.auth.RegisterController
import dev.launcherfork.ui.screens.auth.TotpAuthController
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.state.GameAction
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
        step(0.1) { main().profileBarController.logout.fire() } // "Выйти из аккаунта" -> the logout confirmation
        step(0.4) { shot("05b_logout_confirm") }
        step(0.1) { main().logoutPaneController.logoutButtonLeft.fire() } // cancel
        step(0.1) { main().headerController.showCommunityMenu() } // «СООБЩЕСТВА ▾»: social links dropdown
        step(0.3) { shot("05c_community_menu") }
        step(0.1) { main().hideMenus(); main().setSettingsVisible(true) }
        step(0.6) { shot("06_settings") } // one screen, no scrolling
        step(0.1) { stage.width = Theme.WINDOW_MIN_WIDTH; stage.height = Theme.WINDOW_MIN_HEIGHT }
        step(0.6) { shot("07_settings_min_size") }
        step(0.1) {
            stage.width = Theme.WINDOW_WIDTH
            stage.height = Theme.WINDOW_HEIGHT
            main().setSettingsVisible(false)
        }
        step(0.4) { main().defaultStatusPaneController.gameActionsController.show() } // «⋯» next to ИГРАТЬ
        step(0.3) { shot("08_game_actions") }
        step(0.1) { main().defaultStatusPaneController.gameActionsController.items.getValue(GameAction.DELETE).fire() }
        step(0.4) { shot("08b_game_actions_delete_confirm") }
        step(0.1) {
            main().deletionPaneController.deletionNo.fire() // cancel: nothing happens
            main().defaultStatusPaneController.downloadButton.fire() // Play -> Starting -> Running
        }
        step(1.8) { shot("09_running") }
        step(0.1) { main().onGameAction(GameAction.VERIFY) } // the game runs: confirmation first
        step(0.3) { main().deletionPaneController.deletionYes.fire() } // close the game and verify
        step(0.5) { shot("10_verifying") }
        step(0.1) {
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
        step(0.1) { nav.switchLanguage("ru"); nav.showAgeGate() }
        registrationAndSecondFactorFlow()
        step(0.3) { Platform.exit() }
        run()
    }

    /** Pre-main window again (ru): age gate, registration form, back to login, authenticator-code panel with its toast. */
    private fun registrationAndSecondFactorFlow() {
        val gate = { nav.panelController as AgeGateController }
        val form = { nav.panelController as RegisterController }
        val auth = { nav.panelController as InitialAuthController }
        val totp = { nav.panelController as TotpAuthController }
        step(0.6) { shot("14_age_gate") }
        step(0.1) {
            gate().dayBox.value = 12; gate().monthBox.value = 3; gate().yearBox.value = gate().today.year - 12
            gate().onContinue() // too young
        }
        step(0.3) { shot("15_age_gate_too_young") }
        step(0.1) {
            gate().dayBox.value = 26; gate().monthBox.value = 7; gate().yearBox.value = 2000
            gate().onContinue()
        }
        step(0.6) { shot("16_register") }
        step(0.1) {
            form().emailField.text = "stalker@mail"
            form().usernameField.text = "stalker_1"
            form().passwordField.text = "zone2026!"
            form().newsletterCheckBox.isSelected = true
            form().licenseCheckBox.isSelected = true
            form().onCreate() // e-mail format error
        }
        step(0.3) { shot("17_register_error") }
        step(0.1) { form().emailField.text = "stalker@mail.ru"; form().onCreate() }
        step(1.3) { shot("18_registered_login") }
        step(0.1) { auth().loginField.text = "totp"; auth().passwordField.text = "secret"; auth().onEnter() }
        step(1.3) { totp().codeInput.setCode("000000"); totp().enter() } // the mock's wrong code
        step(1.3) { shot("19_totp_error") }
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

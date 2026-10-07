package dev.launcherfork.app

import dev.launcherfork.i18n.Strings
import dev.launcherfork.i18n.text
import dev.launcherfork.ui.components.ShowcaseCaption
import dev.launcherfork.wallpaper.Wallpapers
import javafx.animation.PauseTransition
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import java.io.File

/**
 * Showcase mode ([Showcase], docs/SHOWCASE.md): hands-off animated presentation.
 *
 * Narrative order (one pass):
 *  1. Registration — age gate → register form (filled)
 *  2. Authorization — login form → sign-in into main
 *  3. Launcher — wallpaper, looks, banner, settings, profile / logout confirm, communities,
 *     game «⋯» menu, download / pause / ready
 *
 * Escape exits. Optional caption chips. Mock data only; nothing is saved.
 */
class ShowcaseTour(
    private val nav: StageNavigator,
    private val config: Showcase = Showcase(),
    lang: String = "ru",
) {
    /** One step: caption key `showcase.step.<id>`, [seconds] on screen, [action] at its start, [ticks] at offsets. */
    class Step(
        val id: String,
        val seconds: Double,
        val ticks: List<Pair<Double, () -> Unit>> = emptyList(),
        /** The step ends when the main window shows up (sign-in), not after [seconds]. */
        val awaitsMain: Boolean = false,
        val action: () -> Unit,
    )

    private val act = TourActions(nav)
    private val strings = Strings.bundle(lang)
    private val captions = mutableListOf<ShowcaseCaption>()
    private val timers = mutableListOf<PauseTransition>()
    private var waitingForMain: (() -> Unit)? = null
    private var stopped = false

    /** Index of the step on screen (tests). */
    var current = -1
        private set
    /** Finished full narrative passes (last step → wrap). */
    var loopsDone = 0
        private set
    /** Stills written so far (first loop, -Dlauncher.showcase.stills). */
    val stills = mutableListOf<File>()

    /**
     * Registration → authorization → launcher click-through.
     * Loop limit ([Showcase.loops]) counts full passes ending after [paused_ready].
     */
    val steps: List<Step> = listOf(
        // —— registration ——
        Step(
            "age_gate",
            3.5,
            ticks = listOf(
                0.8 to { act.fillAgeGate() },
                2.2 to { act.continueAgeGate() },
            ),
        ) { act.showAgeGate() },
        Step(
            "register",
            4.0,
            ticks = listOf(
                0.6 to { act.fillRegister() },
                2.8 to { act.showAuth() },
            ),
        ) { act.showRegister() },
        // —— authorization ——
        Step(
            "auth",
            3.5,
            ticks = listOf(
                0.8 to {
                    val auth = nav.panelController as? dev.launcherfork.ui.screens.auth.InitialAuthController ?: return@to
                    auth.loginField.text = TourActions.MOCK_LOGIN
                },
                1.6 to {
                    val auth = nav.panelController as? dev.launcherfork.ui.screens.auth.InitialAuthController ?: return@to
                    auth.passwordField.text = TourActions.MOCK_PASSWORD
                },
            ),
        ) { act.showAuth() },
        Step(SIGN_IN, 1.6, ticks = listOf(0.4 to { act.signIn() }), awaitsMain = true) { act.showAuth() },
        // —— launcher ——
        Step("main", 3.5) {
            act.hideMenus(); act.showSettings(false)
            nav.applyLook("zone")
            Wallpapers.current?.let { nav.wallpaperLayer?.show(it) }
            act.ready()
        },
        Step("wallpapers", 4.0, ticks = listOf(2.0 to { showWallpaper(2) })) { showWallpaper(5) },
        Step("theme_exbo", 3.5) { nav.applyLook("exbo") },
        Step("theme_shadcn", 3.5) { nav.applyLook("shadcn") },
        Step("theme_zone", 3.0) { nav.applyLook("zone") },
        Step("banner", 3.5) { act.reselectGame() },
        Step("settings", 4.0, ticks = listOf(1.6 to { toggleTray() }, 2.6 to { toggleTray() })) { act.showSettings(true) },
        Step(
            "profile_menu",
            3.0,
            ticks = listOf(
                0.5 to { act.showProfileMenu() },
                1.6 to { act.showLogoutConfirm() },
                2.5 to { act.cancelLogout() },
            ),
        ) { act.showSettings(false) },
        Step("community_menu", 3.0) { act.showCommunityMenu() },
        Step(
            "game_actions",
            3.5,
            ticks = listOf(2.6 to { act.hideGameActions() }),
        ) {
            act.hideMenus()
            act.ready()
            act.showGameActions()
        },
        Step("download", 4.0, ticks = listOf(
            1.0 to { act.downloading(0.42) },
            2.0 to { act.downloading(0.67) },
            3.0 to { act.downloading(0.86) },
        )) {
            act.hideMenus(); act.hideGameActions(); act.downloading(0.18)
        },
        Step("paused_ready", 3.5, ticks = listOf(1.6 to { act.ready() })) { act.paused(0.86) },
    )

    fun install() {
        config.stillsDir?.mkdirs()
        if (config.captions) {
            captions += ShowcaseCaption().placeIn(nav.mainWindowRoot, Pos.TOP_LEFT, Insets(6.0, 0.0, 0.0, 12.0))
            captions += ShowcaseCaption().placeIn(nav.authWindowRoot, Pos.TOP_CENTER, Insets(6.0, 0.0, 0.0, 0.0))
        }
        listOfNotNull(nav.mainStage.scene, nav.authStage.scene).forEach(::exitOnEscape)
        var started = false
        nav.onScreenChanged = { name ->
            when {
                // App opens on auth → begin the narrative at registration (age gate).
                name == "auth" && !started -> {
                    started = true
                    later(0.9) { run(0) }
                }
                name == "main" -> waitingForMain?.let { waitingForMain = null; later(0.4) { it() } }
            }
        }
    }

    /** Caption text of [step] (Russian by default). */
    fun caption(step: Step): String = strings.text("showcase.step.${step.id}")

    /** Runs step [index] now and schedules the next one (the app); tests call [runAction] instead. */
    fun run(index: Int) {
        if (stopped) return
        val step = steps[index]
        current = index
        captions.forEach { it.text = caption(step) }
        System.err.println("[launcher-fork] showcase step ${index + 1}/${steps.size}: ${step.id}")
        runAction(step)
        val proceed = {
            val next = advance(index)
            if (next == null) finish() else run(next)
        }
        if (step.awaitsMain) waitingForMain = proceed
        else later(step.seconds) { saveStill(index, step); proceed() }
    }

    /** The step's action and its ticks (scheduled), errors logged. */
    fun runAction(step: Step) {
        try { step.action() } catch (e: Exception) { e.printStackTrace() }
        step.ticks.forEach { (t, tick) -> later(t) { try { tick() } catch (e: Exception) { e.printStackTrace() } } }
    }

    /**
     * After step [index]: next index, or null when [Showcase.loops] full passes are done.
     * A pass closes when leaving the last step (wrap to 0).
     */
    fun advance(index: Int): Int? {
        val next = (index + 1) % steps.size
        if (next == 0) {
            loopsDone++
            if (config.loops in 1..loopsDone) return null
        }
        return next
    }

    /**
     * The loop limit is reached: the last launcher frame stays on screen for [FINISH_HOLD_SEC], then
     * "showcase finished" goes to stderr and the app exits (recorders stop on that line).
     */
    private fun finish() {
        captions.forEach { it.text = caption(steps.last()) }
        later(FINISH_HOLD_SEC) {
            System.err.println("[launcher-fork] showcase finished after $loopsDone loop(s)")
            stop()
        }
    }

    /** Ends the showcase: timers stop, the app exits through the navigator. */
    fun stop() {
        stopped = true
        timers.forEach { it.stop() }
        timers.clear()
        nav.close()
    }

    private fun later(sec: Double, action: () -> Unit) {
        timers.removeAll { it.status == javafx.animation.Animation.Status.STOPPED }
        timers += StepQueue.later(sec, action)
    }

    private fun showWallpaper(number: Int) {
        val wp = Wallpapers.all.getOrNull(number - 1) ?: return
        nav.wallpaperLayer?.show(wp)
    }

    private fun toggleTray() {
        val s = act.main.settingsController
        s.trayToggle.isSelected = !s.trayToggle.isSelected
    }

    private fun exitOnEscape(scene: Scene) = scene.addEventFilter(KeyEvent.KEY_PRESSED) { e ->
        if (e.code == KeyCode.ESCAPE) { e.consume(); stop() }
    }

    /** First loop only: the settled frame of the step without the caption (clean still for publication). */
    private fun saveStill(index: Int, step: Step) {
        val dir = config.stillsDir ?: return
        if (loopsDone > 0) return
        val shown = captions.map { it.isVisible }
        captions.forEach { it.isVisible = false }
        try {
            val file = File(dir, "%02d_%s.png".format(index + 1, step.id))
            ScreenshotTour.save(nav.window.scene, file)
            stills += file
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            captions.forEachIndexed { i, c -> c.isVisible = shown[i] }
        }
    }

    companion object {
        const val SIGN_IN = "sign_in"
        /** Seconds the final frame stays on screen before the app exits (`launcher.showcase.loops`). */
        const val FINISH_HOLD_SEC = 2.5
    }
}

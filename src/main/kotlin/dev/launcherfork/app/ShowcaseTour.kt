package dev.launcherfork.app

import dev.launcherfork.domain.Appearance
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
 * Showcase mode ([Showcase], docs/SHOWCASE.md): a looping, hands-off presentation of the redesign with the animations
 * on. Main window with its wallpaper -> other wallpapers -> the four looks (zone-light, exbo, shadcn, zone) -> game
 * banner -> settings (RAM, «Поведение») -> profile menu -> «Сообщества» -> download / pause / ready -> auth window (showAuth) ->
 * registration steps -> sign-in -> again. Each step 3–4 s; navigation through [TourActions] (shared with the screenshot
 * tour). Mock data only (login «player»), nothing is saved: theme switches go through [StageNavigator.applyLook]
 * (session only), wallpapers through the layer (never `wallpaper.last`), and the app runs on an in-memory settings
 * store (LauncherApp). Escape exits. Optional caption chip in the title strip (never in the nav row).
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
    /** The loop proper has begun (the very first sign-in, right after start, does not close a loop). */
    private var looping = false

    /** Index of the step on screen (tests). */
    var current = -1
        private set
    /** Finished loops (the sign-in step closes a loop). */
    var loopsDone = 0
        private set
    /** Stills written so far (first loop, -Dlauncher.showcase.stills). */
    val stills = mutableListOf<File>()

    /** The loop, in order; the first loop starts at [SIGN_IN] (the app opens on the auth window). */
    val steps: List<Step> = listOf(
        Step("main", 3.5) {
            act.hideMenus(); act.showSettings(false)
            nav.applyLook("zone", Appearance.DARK)
            Wallpapers.current?.let { nav.wallpaperLayer?.show(it) }
            act.ready()
        },
        Step("wallpapers", 4.0, ticks = listOf(2.0 to { showWallpaper(2) })) { showWallpaper(5) },
        Step("theme_light", 3.5) { Wallpapers.current?.let { nav.wallpaperLayer?.show(it) }; nav.applyLook("zone", Appearance.LIGHT) },
        Step("theme_exbo", 3.5) { nav.applyLook("exbo", Appearance.DARK) },
        Step("theme_shadcn", 3.5) { nav.applyLook("shadcn", Appearance.DARK) },
        Step("theme_zone", 3.0) { nav.applyLook("zone", Appearance.DARK) },
        Step("banner", 3.5) { act.reselectGame() },
        Step("settings", 4.0, ticks = listOf(1.6 to { toggleTray() }, 2.6 to { toggleTray() })) { act.showSettings(true) },
        Step("profile_menu", 3.0, ticks = listOf(0.5 to { act.showProfileMenu() })) { act.showSettings(false) },
        Step("community_menu", 3.0) { act.showCommunityMenu() },
        Step("download", 4.0, ticks = listOf(1.0 to { act.downloading(0.42) }, 2.0 to { act.downloading(0.67) }, 3.0 to { act.downloading(0.86) })) {
            act.hideMenus(); act.downloading(0.18)
        },
        Step("paused_ready", 3.0, ticks = listOf(1.5 to { act.ready() })) { act.paused(0.86) },
        Step("auth", 3.5) { act.showAuth() },  // leave to auth window (not «Сменить аккаунт»: that item is gone in #19)
        Step("age_gate", 3.0) { act.showAgeGate() },
        Step("register", 3.5) { act.showRegister() },
        Step(SIGN_IN, 1.6, ticks = listOf(1.5 to { act.signIn() }), awaitsMain = true) { act.showAuth() },
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
                name == "auth" && !started -> { started = true; later(0.8) { run(steps.indexOfFirst { it.id == SIGN_IN }) } }
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
     * Bookkeeping after step [index]: the index of the next step, or null when the `launcher.showcase.loops` limit is
     * reached. A loop closes with the sign-in that follows a full pass; the very first sign-in after start does not
     * count (the app opens on the auth window, then the loop proper starts at step 1).
     */
    fun advance(index: Int): Int? {
        if (steps[index].id == SIGN_IN && looping) loopsDone++
        val next = (index + 1) % steps.size
        if (next == 0) looping = true
        return if (config.loops in 1..loopsDone) null else next
    }

    /**
     * The loop limit is reached: the main window (just signed in again) stays on screen for [FINISH_HOLD_SEC], then a
     * "showcase finished" line goes to stderr and the app exits. Recorders stop on that line / on the exit instead of
     * a fixed duration (otherwise the tail of the video is the empty screen after the windows closed).
     */
    private fun finish() {
        captions.forEach { it.text = caption(steps.first()) }
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
        /** Seconds the final main window stays on screen before the app exits (`launcher.showcase.loops`). */
        const val FINISH_HOLD_SEC = 2.5
    }
}

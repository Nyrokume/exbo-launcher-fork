package dev.launcherfork.ui.motion

import javafx.animation.Animation
import javafx.animation.Interpolator
import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.Timeline
import javafx.beans.property.DoubleProperty
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyBooleanWrapper
import javafx.beans.value.ObservableBooleanValue
import javafx.geometry.Rectangle2D
import javafx.scene.Node
import javafx.scene.SnapshotParameters
import javafx.scene.control.CheckBox
import javafx.scene.control.ProgressBar
import javafx.scene.effect.ColorAdjust
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.transform.Translate
import javafx.util.Duration
import kotlin.math.abs

/**
 * Motion of the launcher (docs/design/STYLE-ZONE.md, «Анимации»): ONE place for durations, the easing and the few
 * transitions the UI uses, so every screen moves the same way and the login panels can adopt it later.
 *
 * Rules (the minimalist "QL" look): fades, small slides (at most [MAX_SLIDE] px), a short scale on press; durations
 * [FAST] / [NORMAL] / [SLOW] (120 / 200 / 280 ms) with the ease-out [EASE]; nothing bounces. The only loops are the
 * optional very slow wallpaper drift ([drift], off unless asked for) and the in-flight refresh spin ([spin], on only
 * while a reload is running). Motion never decides state: callers keep their logical state (shown / hidden / busy)
 * themselves, the helpers only animate towards it.
 *
 * Global switch [enabled]: `-Dlauncher.animations=on|off` ([override]) wins over the settings key `animations=on|off`
 * ([setting], «Настройки → Поведение → Анимации», on by default). Disabled, EVERY helper applies its end state at once
 * (tests run with `-Dlauncher.animations=off`, the screenshot tour forces it off), so state and pictures stay
 * deterministic. A helper started on a node stops the node's previous animation of the same kind and continues from
 * the current values, so quick toggles never jump or leave a half-faded node.
 */
object Motion {
    /** System property: on / off (also true / false, yes / no, 1 / 0); wins over the setting. */
    const val PROPERTY = "launcher.animations"
    /** System property: on = the very slow wallpaper drift (off by default; the showcase turns it on). */
    const val DRIFT_PROPERTY = "launcher.wallpaper.drift"
    /** Key in settings.properties (data/local/AppearanceFileStore). */
    const val SETTINGS_KEY = "animations"

    const val FAST_MS = 120.0
    const val NORMAL_MS = 200.0
    const val SLOW_MS = 280.0
    /** Hover / press feedback, closing dropdowns and modals. */
    val FAST: Duration = Duration.millis(FAST_MS)
    /** Appearing dropdowns, modals, banners, screen switches. */
    val NORMAL: Duration = Duration.millis(NORMAL_MS)
    /** Wallpaper fade-in, progress interpolation. */
    val SLOW: Duration = Duration.millis(SLOW_MS)
    /** Half a period of the optional wallpaper drift (1.00 -> [DRIFT_SCALE] and back, alternating). */
    val DRIFT_PERIOD: Duration = Duration.seconds(40.0)
    /** One turn of an in-flight refresh icon. Linear, so a loop has no visible seam. */
    val SPIN_TURN: Duration = Duration.millis(720.0)
    const val DRIFT_SCALE = 1.04

    /** Ease-out: fast start, soft landing (Material "emphasized decelerate"); no overshoot. */
    val EASE: Interpolator = Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0)

    /** Largest slide any helper makes, px. */
    const val MAX_SLIDE = 12.0
    /** Default slide of appearing text / panels, px. */
    const val SLIDE = 8.0
    /** Slide of dropdowns (towards their anchor), px. */
    const val MENU_SLIDE = 6.0
    const val PRESS_SCALE = 0.97
    /** Start scale of an appearing modal card. */
    const val POP_SCALE = 0.98
    /** Extra brightness of a hovered main button (ColorAdjust). */
    const val HOVER_BRIGHTNESS = 0.06

    // ---- the switch ----

    /** on / off value of the property or the settings key; null = not given / not understood. */
    fun parse(value: String?): Boolean? = when (value?.trim()?.lowercase()) {
        "on", "true", "yes", "1" -> true
        "off", "false", "no", "0" -> false
        else -> null
    }

    /** Settings value of `animations`: missing or unknown = on. */
    fun parseSetting(value: String?): Boolean = parse(value) ?: true

    fun settingValue(on: Boolean): String = if (on) "on" else "off"

    /** Effective switch: the property (if it says on / off) wins over the setting. */
    fun resolve(property: String?, setting: Boolean): Boolean = parse(property) ?: setting

    private val enabledWrapper = ReadOnlyBooleanWrapper(this, "enabled", true)

    /** From `-Dlauncher.animations` at start; the screenshot tour sets false. null = the setting decides. */
    @Volatile var override: Boolean? = parse(System.getProperty(PROPERTY))
        set(value) { field = value; refresh() }

    /** The user's «Анимации» setting (LauncherApp loads it, SettingsController changes it). */
    @Volatile var setting: Boolean = true
        set(value) { field = value; refresh() }

    val enabled: Boolean get() = override ?: setting
    /** Follows [enabled] (the wallpaper drift starts / stops with it). */
    val enabledProperty: ReadOnlyBooleanProperty get() = enabledWrapper.readOnlyProperty

    /** -Dlauncher.wallpaper.drift=on (the showcase sets it); still only while [enabled]. */
    val driftRequested: Boolean get() = parse(System.getProperty(DRIFT_PROPERTY)) ?: false

    init { refresh() }

    private fun refresh() { enabledWrapper.set(enabled) }

    // ---- bookkeeping: one running animation per node and channel ----

    private const val KEY = "dev.launcherfork.motion."
    /** Show / hide transitions (opacity, translate, scale of appearing nodes). */
    const val SHOW = "show"
    private const val PRESS = "press"
    private const val HOVER = "hover"
    private const val PROGRESS = "progress"
    private const val THUMB = "thumb"
    private const val MARKER = "marker"
    /** The wallpaper drift ([drift]). */
    const val DRIFT = "drift"
    /** In-flight refresh indicator ([spin]). */
    const val SPIN = "spin"

    /** The running animation of [node] on [channel] (tests, the screenshot tour), null when idle. */
    fun running(node: Node, channel: String = SHOW): Animation? = node.properties[KEY + channel] as? Animation

    fun isAnimating(node: Node, channel: String = SHOW): Boolean = running(node, channel) != null

    /** Stops [node]'s animation on [channel] where it is (no end action); true if one was running. */
    fun stop(node: Node, channel: String = SHOW): Boolean {
        val anim = node.properties.remove(KEY + channel) as? Animation ?: return false
        anim.stop()
        return true
    }

    /** Jumps [node]'s animation on [channel] to its end (end state + end action), e.g. before a snapshot. */
    fun finish(node: Node, channel: String = SHOW) {
        val anim = node.properties.remove(KEY + channel) as? Animation ?: return
        val total = anim.totalDuration
        if (total.isIndefinite) { anim.stop(); return }
        anim.jumpTo(total)
        anim.stop()
        anim.onFinished?.handle(null)
    }

    private fun start(node: Node, channel: String, anim: Animation, onEnd: () -> Unit = {}) {
        node.properties[KEY + channel] = anim
        anim.setOnFinished {
            if (node.properties[KEY + channel] === anim) node.properties.remove(KEY + channel)
            onEnd()
        }
        anim.play()
    }

    private fun kv(p: DoubleProperty, v: Double, i: Interpolator = EASE) = KeyValue(p, v, i)

    private fun tween(duration: Duration, vararg values: KeyValue) = Timeline(KeyFrame(duration, *values))

    private fun clampSlide(d: Double) = d.coerceIn(-MAX_SLIDE, MAX_SLIDE)

    /**
     * Every "in" transition ends in the static layout: opacity 1, no translate, scale 1, whatever an interrupted
     * transition left behind (a node never stays half-faded or offset).
     */
    private fun toRest(node: Node, duration: Duration) = tween(duration,
        kv(node.opacityProperty(), 1.0), kv(slide(node).yProperty(), 0.0),
        kv(node.scaleXProperty(), 1.0), kv(node.scaleYProperty(), 1.0),
    )

    /**
     * The slide offset lives in Motion's own [Translate] at the end of the node's transforms, NOT in translateY:
     * stylesheets set -fx-translate-y on some nodes (the dropdown cards hang 6 px under their button) and an author
     * stylesheet beats values set from code, so animating translateY would fight the CSS and lose the gap.
     */
    private fun slide(node: Node): Translate =
        node.properties.getOrPut(KEY + "slide") { Translate().also { node.transforms += it } } as Translate

    /** Current slide offset of [node] (0 = in its laid-out place). */
    fun slideOffset(node: Node): Double = (node.properties[KEY + "slide"] as? Translate)?.y ?: 0.0

    private fun resetTransform(node: Node) {
        node.opacity = 1.0
        (node.properties[KEY + "slide"] as? Translate)?.y = 0.0
        node.scaleX = 1.0
        node.scaleY = 1.0
    }

    // ---- show / hide ----

    /** Makes [node] visible and fades it in (from where a running fade left it). */
    fun fadeIn(node: Node, duration: Duration = NORMAL) {
        val resumed = stop(node, SHOW)
        node.isVisible = true
        if (!enabled) { resetTransform(node); return }
        if (!resumed) node.opacity = 0.0
        start(node, SHOW, toRest(node, duration))
    }

    /** Fade in while sliding [dy] px (clamped to [MAX_SLIDE]) into place; positive = up from below. */
    fun fadeSlideIn(node: Node, dy: Double = SLIDE, duration: Duration = NORMAL) {
        val resumed = stop(node, SHOW)
        node.isVisible = true
        if (!enabled) { resetTransform(node); return }
        if (!resumed) { node.opacity = 0.0; slide(node).y = clampSlide(dy) }
        start(node, SHOW, toRest(node, duration))
    }

    /**
     * Dropdowns and modal cards: fade in, [dy] px slide towards the anchor (negative = from above) and a scale from
     * [scale] to 1. The end state is exactly the static layout (opacity 1, no translate, scale 1).
     */
    fun popIn(node: Node, dy: Double = -MENU_SLIDE, scale: Double = 1.0, duration: Duration = NORMAL) {
        val resumed = stop(node, SHOW)
        node.isVisible = true
        if (!enabled) { resetTransform(node); return }
        if (!resumed) {
            node.opacity = 0.0
            slide(node).y = clampSlide(dy)
            node.scaleX = scale
            node.scaleY = scale
        }
        start(node, SHOW, toRest(node, duration))
    }

    /**
     * Fades [node] out ([dy] px drift, default none), then runs [then] (hides it by default) and restores opacity /
     * translate / scale, so the next show starts clean. Already hidden or disabled: [then] at once.
     */
    fun fadeOut(node: Node, duration: Duration = FAST, dy: Double = 0.0, then: () -> Unit = { node.isVisible = false }) {
        stop(node, SHOW)
        if (!enabled || !node.isVisible || node.opacity <= 0.0) { then(); resetTransform(node); return }
        val values = mutableListOf(kv(node.opacityProperty(), 0.0))
        if (dy != 0.0) values += kv(slide(node).yProperty(), clampSlide(dy))
        start(node, SHOW, tween(duration, *values.toTypedArray())) { then(); resetTransform(node) }
    }

    /** Dropdown closing: fade out with half of its opening slide. */
    fun popOut(node: Node, dy: Double = -MENU_SLIDE / 2, then: () -> Unit = { node.isVisible = false }) =
        fadeOut(node, FAST, dy, then)

    /**
     * Crossfade of two overlapping nodes (e.g. home and settings in one StackPane): [new] fades in, [old] fades out
     * (faster) and is hidden at the end. Z-order is left alone.
     */
    fun crossfade(old: Node?, new: Node, duration: Duration = NORMAL) {
        fadeIn(new, duration)
        if (old != null && old !== new) fadeOut(old, FAST)
    }

    /**
     * True crossfade of a whole area that is rebuilt or restyled in place (screen switch, theme switch): a snapshot of
     * [host] is laid over it, [change] runs underneath, the snapshot fades out and goes away. Without a visible window
     * (or disabled) it just runs [change]. The snapshot is mouse-transparent and unmanaged (never moves the layout).
     */
    fun snapshotCrossfade(host: Pane, duration: Duration = NORMAL, change: () -> Unit) {
        val w = host.width
        val h = host.height
        val showing = host.scene?.window?.isShowing == true
        host.children.filter { it.properties[KEY + "snapshot"] == true }.forEach { stop(it); host.children.remove(it) }
        if (!enabled || !showing || w <= 0 || h <= 0) { change(); return }
        val image = try {
            host.snapshot(SnapshotParameters().apply { fill = Color.TRANSPARENT; viewport = Rectangle2D(0.0, 0.0, w, h) }, null)
        } catch (e: RuntimeException) {
            null
        }
        change()
        if (image == null) return
        val cover = ImageView(image).apply {
            isManaged = false
            isMouseTransparent = true
            properties[KEY + "snapshot"] = true
            relocate(0.0, 0.0)
        }
        host.children += cover
        start(cover, SHOW, tween(duration, kv(cover.opacityProperty(), 0.0))) { host.children.remove(cover) }
    }

    // ---- feedback ----

    /** ИГРАТЬ / primary buttons: scale to [PRESS_SCALE] while pressed, back on release. Installs once. */
    fun pressScale(node: Node, scale: Double = PRESS_SCALE) {
        if (node.properties.putIfAbsent(KEY + "press.installed", true) != null) return
        node.pressedProperty().addListener { _, _, pressed ->
            val target = if (pressed) scale else 1.0
            stop(node, PRESS)
            if (!enabled) { node.scaleX = 1.0; node.scaleY = 1.0; return@addListener }
            start(node, PRESS, tween(if (pressed) FAST else NORMAL, kv(node.scaleXProperty(), target), kv(node.scaleYProperty(), target)))
        }
    }

    /**
     * Hover brighten ([HOVER_BRIGHTNESS], ColorAdjust) faded in and out; leaves nodes that carry another effect alone
     * and removes its own effect at rest. Disabled nodes do not brighten.
     */
    fun hoverBrighten(node: Node, amount: Double = HOVER_BRIGHTNESS) {
        if (node.properties.putIfAbsent(KEY + "hover.installed", true) != null) return
        val adjust = ColorAdjust()
        fun update() {
            val target = if (node.isHover && !node.isDisabled) amount else 0.0
            stop(node, HOVER)
            if (node.effect != null && node.effect !== adjust) return
            if (!enabled || abs(adjust.brightness - target) < 1e-6) {
                adjust.brightness = target
                node.effect = if (target == 0.0) null else adjust
                return
            }
            node.effect = adjust
            start(node, HOVER, tween(FAST, kv(adjust.brightnessProperty(), target))) {
                if (adjust.brightness == 0.0 && node.effect === adjust) node.effect = null
            }
        }
        node.hoverProperty().addListener { _, _, _ -> update() }
        node.disabledProperty().addListener { _, _, _ -> update() }
    }

    /**
     * [fill] (a background layer painted by the theme) follows [state]: opacity 1 while true, 0 while false, faded
     * ([FAST]); CSS has no transitions, so hover backgrounds that should fade go through this.
     */
    fun fadeWith(fill: Node, state: ObservableBooleanValue, duration: Duration = FAST) {
        fun apply(on: Boolean) {
            val target = if (on) 1.0 else 0.0
            stop(fill, HOVER)
            if (!enabled) { fill.opacity = target; return }
            start(fill, HOVER, tween(duration, kv(fill.opacityProperty(), target)))
        }
        fill.opacity = if (state.get()) 1.0 else 0.0
        state.addListener { _, _, on -> apply(on) }
    }

    /** Selection marker of a list row: grows from the middle ([NORMAL]); deselecting is instant (CSS). */
    fun markerIn(marker: Node) {
        stop(marker, MARKER)
        if (!enabled) { marker.scaleY = 1.0; marker.opacity = 1.0; return }
        marker.scaleY = 0.3
        marker.opacity = 0.0
        start(marker, MARKER, tween(NORMAL, kv(marker.scaleYProperty(), 1.0), kv(marker.opacityProperty(), 1.0)))
    }

    /**
     * A progress value the UI can show: NaN / -Infinity / negative -> 0, +Infinity / above 1 -> 1. Services report
     * fractions; a broken one must never draw a bar past its track or "NaN%".
     */
    fun safeFraction(value: Double): Double = when {
        value.isNaN() -> 0.0
        value <= 0.0 -> 0.0
        value >= 1.0 -> 1.0
        else -> value
    }

    /**
     * Progress bars: glide to [value] ([SLOW]) between the service's ticks instead of jumping. The value is clamped
     * ([safeFraction]). Never lags where it matters: completion (1), reset (0), going back (a new download, another
     * game) and the disabled mode set the value at once, and a new value always starts from where the bar is.
     */
    fun progress(bar: ProgressBar, value: Double, duration: Duration = SLOW) {
        stop(bar, PROGRESS)
        val target = safeFraction(value)
        val from = bar.progress
        if (!enabled || target <= 0.0 || target >= 1.0 || from.isNaN() || from < 0 || target < from ||
            abs(target - from) < 1e-4 || bar.scene == null
        ) {
            bar.progress = target
            return
        }
        start(bar, PROGRESS, tween(duration, kv(bar.progressProperty(), target)))
    }

    /** The target of a running [progress] glide, else the bar's value (state checks must not see a mid-glide value). */
    fun progressTarget(bar: ProgressBar): Double =
        (running(bar, PROGRESS) as? Timeline)?.keyFrames?.lastOrNull()?.values?.firstOrNull()?.endValue as? Double ?: bar.progress

    /**
     * Switch (CheckBox .switch): the thumb (.mark) slides to its new side ([NORMAL]) instead of jumping; colours still
     * change at once (CSS). The final position is the CSS one (translate 0).
     */
    fun smoothSwitch(box: CheckBox) {
        if (box.properties.putIfAbsent(KEY + "switch.installed", true) != null) return
        box.selectedProperty().addListener { _, _, _ ->
            val mark = box.lookup(".mark") ?: return@addListener
            stop(mark, THUMB)
            if (!enabled || box.scene == null) { mark.translateX = 0.0; return@addListener }
            val oldX = mark.localToScene(0.0, 0.0).x
            box.applyCss()
            box.layout()
            val newX = mark.localToScene(0.0, 0.0).x - mark.translateX
            val delta = oldX - newX
            if (abs(delta) < 0.5) { mark.translateX = 0.0; return@addListener }
            mark.translateX = delta
            start(mark, THUMB, tween(NORMAL, kv(mark.translateXProperty(), 0.0)))
        }
    }

    /**
     * Spins [node] while [on] (one linear turn per [SPIN_TURN], forever). The idle angle is 0.
     * Calling again while it is already spinning leaves the current turn alone. With motion off, or when [on] is
     * false, the node rests at 0 and nothing is running — the same rule as the other helpers, so tests and the
     * screenshot tour stay on a settled frame.
     */
    fun spin(node: Node, on: Boolean) {
        if (!on || !enabled) {
            stop(node, SPIN)
            node.rotate = 0.0
            return
        }
        if (isAnimating(node, SPIN)) return
        node.rotate = 0.0
        val anim = Timeline(
            KeyFrame(Duration.ZERO, KeyValue(node.rotateProperty(), 0.0)),
            KeyFrame(SPIN_TURN, KeyValue(node.rotateProperty(), 360.0, Interpolator.LINEAR)),
        ).apply { cycleCount = Animation.INDEFINITE }
        node.properties[KEY + SPIN] = anim
        anim.play()
    }

    /**
     * The optional wallpaper drift: [node] scales 1.00 -> [DRIFT_SCALE] over [DRIFT_PERIOD] and back, forever, gently
     * (ease both). Plays only while [enabled]; returns null (and leaves scale 1) otherwise. The caller clips.
     */
    fun drift(node: Node): Animation? {
        stop(node, DRIFT)
        node.scaleX = 1.0
        node.scaleY = 1.0
        if (!enabled) return null
        val anim = Timeline(KeyFrame(DRIFT_PERIOD,
            KeyValue(node.scaleXProperty(), DRIFT_SCALE, Interpolator.EASE_BOTH),
            KeyValue(node.scaleYProperty(), DRIFT_SCALE, Interpolator.EASE_BOTH),
        )).apply { isAutoReverse = true; cycleCount = Animation.INDEFINITE }
        node.properties[KEY + DRIFT] = anim
        anim.play()
        return anim
    }
}

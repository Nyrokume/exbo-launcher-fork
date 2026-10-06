package dev.launcherfork.ui.components

import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyIntegerWrapper
import javafx.scene.AccessibleAttribute
import javafx.scene.AccessibleRole
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Region
import kotlin.math.roundToInt

/**
 * Fillable RAM bar of the settings page. The track is the computer's memory (0..[totalGb]); the filled part (accent,
 * red in the zone theme) ends at the thumb and is the allocation, so "6 ГБ из 16 ГБ" fills 6/16 of the track. The part
 * above [maxGb] (the system reserve) is hatched and unreachable, a small mark shows the recommended value, GB tick
 * labels sit under the track (the recommended one highlighted).
 *
 * Values are whole GB, steps of exactly 1 GB, always clamped to [minGb]..[maxGb]. Mouse: press / drag anywhere on the
 * control; keyboard (focusable): Left / Down -1, Right / Up +1, Page Down / Page Up -2 / +2, Home / End = min / max.
 * [onChange] fires only for user changes. Styling: .ram-bar, .ram-track, .ram-fill, .ram-reserve, .ram-rec-mark,
 * .ram-thumb, .ram-tick(.recommended) in layout.css; sizes of the track and the thumb come from CSS.
 */
class RamBar : Region() {
    private val valueWrapper = ReadOnlyIntegerWrapper(this, "value", 0)

    val track = Region().apply { styleClass.setAll("ram-track", "zone-radius-sm") }
    val reserve = Region().apply { styleClass.setAll("ram-reserve", "zone-radius-sm") }
    val fill = Region().apply { styleClass.setAll("ram-fill", "zone-radius-sm") }
    val recommendedMark = Region().apply { styleClass.setAll("ram-rec-mark") }
    val thumb = Region().apply { styleClass.setAll("ram-thumb") }
    /** Tick labels left to right; userData = GB value. */
    var tickLabels: List<Label> = emptyList()
        private set

    var totalGb: Int = 16
        private set
    var minGb: Int = 2
        private set
    var maxGb: Int = 14
        private set
    var recommendedGb: Int = 6
        private set

    /** Called after the user moved the bar (mouse / keyboard), with the new GB value. */
    var onChange: (Int) -> Unit = {}
    /** Text of a GB value for the last tick label and the accessible text ("16 ГБ"). */
    var formatter: (Int) -> String = { "$it GB" }

    val valueProperty: ReadOnlyIntegerProperty get() = valueWrapper.readOnlyProperty
    val value: Int get() = valueWrapper.get()

    init {
        styleClass += "ram-bar"
        isFocusTraversable = true
        accessibleRole = AccessibleRole.SLIDER
        children.setAll(track, reserve, fill, recommendedMark, thumb)
        listOf(track, reserve, fill, recommendedMark, thumb).forEach { it.isMouseTransparent = true }
        addEventHandler(MouseEvent.MOUSE_PRESSED) { e -> requestFocus(); setFromX(e.x); e.consume() }
        addEventHandler(MouseEvent.MOUSE_DRAGGED) { e -> setFromX(e.x); e.consume() }
        addEventHandler(KeyEvent.KEY_PRESSED, ::onKey)
        valueWrapper.addListener { _, _, _ -> requestLayout(); updateAccessibleText() }
    }

    /** Scale, bounds, recommended value and current value (clamped; not a user change). */
    fun configure(totalGb: Int, minGb: Int, maxGb: Int, recommendedGb: Int, value: Int, ticks: List<Int>) {
        require(minGb <= maxGb) { "min $minGb > max $maxGb" }
        this.totalGb = maxOf(totalGb, maxGb)
        this.minGb = minGb
        this.maxGb = maxGb
        this.recommendedGb = recommendedGb.coerceIn(minGb, maxGb)
        val values = (ticks + this.recommendedGb).distinct().sorted()
        tickLabels = values.map { gb ->
            Label(if (gb == values.last()) formatter(gb) else gb.toString()).apply {
                styleClass.setAll("ram-tick")
                if (gb == this@RamBar.recommendedGb) styleClass += "recommended"
                userData = gb
                isMouseTransparent = true
                minWidth = USE_PREF_SIZE
            }
        }
        children.setAll(listOf(track, reserve, fill, recommendedMark, thumb) + tickLabels)
        valueWrapper.set(value.coerceIn(minGb, maxGb))
        updateAccessibleText()
        requestLayout()
    }

    /** Programmatic or user change; the value is clamped and snapped to whole GB. Returns the stored value. */
    fun setValue(gb: Int, user: Boolean = false): Int {
        val v = gb.coerceIn(minGb, maxGb)
        if (v == value) return v
        valueWrapper.set(v)
        if (user) onChange(v)
        return v
    }

    /** Filled share of the track (allocation / total), what the fill's width shows. */
    val fillFraction: Double get() = if (totalGb <= 0) 0.0 else value.toDouble() / totalGb

    private fun fractionOf(gb: Int) = if (totalGb <= 0) 0.0 else gb.toDouble() / totalGb

    private val thumbSize: Double get() = snapSizeX(thumb.prefWidth(-1.0)).takeIf { it > 0 } ?: 16.0
    private val trackHeight: Double get() = snapSizeY(track.prefHeight(-1.0)).takeIf { it > 0 } ?: 6.0
    private val labelGap = 5.0

    /** Track geometry: left x and width (the thumb's centre can reach both ends). */
    private fun trackX(): Pair<Double, Double> {
        val ts = thumbSize
        val x0 = snappedLeftInset() + ts / 2
        return x0 to maxOf(0.0, width - snappedLeftInset() - snappedRightInset() - ts)
    }

    private fun setFromX(x: Double) {
        val (x0, tw) = trackX()
        if (tw <= 0) return
        setValue(((x - x0) / tw * totalGb).roundToInt(), user = true)
    }

    private fun onKey(e: KeyEvent) {
        val next = when (e.code) {
            KeyCode.LEFT, KeyCode.DOWN, KeyCode.KP_LEFT, KeyCode.KP_DOWN -> value - 1
            KeyCode.RIGHT, KeyCode.UP, KeyCode.KP_RIGHT, KeyCode.KP_UP -> value + 1
            KeyCode.PAGE_DOWN -> value - 2
            KeyCode.PAGE_UP -> value + 2
            KeyCode.HOME -> minGb
            KeyCode.END -> maxGb
            else -> return
        }
        setValue(next, user = true)
        e.consume()
    }

    private fun updateAccessibleText() {
        accessibleText = "${formatter(value)} / ${formatter(totalGb)}"
        notifyAccessibleAttributeChanged(AccessibleAttribute.VALUE)
    }

    override fun queryAccessibleAttribute(attribute: AccessibleAttribute, vararg parameters: Any?): Any? = when (attribute) {
        AccessibleAttribute.VALUE -> value.toDouble()
        AccessibleAttribute.MIN_VALUE -> minGb.toDouble()
        AccessibleAttribute.MAX_VALUE -> maxGb.toDouble()
        else -> super.queryAccessibleAttribute(attribute, *parameters)
    }

    private fun labelHeight(): Double = tickLabels.maxOfOrNull { it.prefHeight(-1.0) } ?: 0.0

    override fun computePrefHeight(width: Double): Double =
        snappedTopInset() + thumbSize + labelGap + labelHeight() + snappedBottomInset()

    override fun computeMinHeight(width: Double): Double = computePrefHeight(width)
    override fun computeMaxHeight(width: Double): Double = computePrefHeight(width)
    override fun computePrefWidth(height: Double): Double = 360.0
    override fun computeMinWidth(height: Double): Double = 160.0

    override fun layoutChildren() {
        val (x0, tw) = trackX()
        val ts = thumbSize
        val th = trackHeight
        val cy = snappedTopInset() + ts / 2
        val ty = snapPositionY(cy - th / 2)
        fun xOf(gb: Int) = x0 + tw * fractionOf(gb)

        track.resizeRelocate(snapPositionX(x0), ty, snapSizeX(tw), th)
        val fillEnd = xOf(value)
        fill.resizeRelocate(snapPositionX(x0), ty, snapSizeX(fillEnd - x0), th)
        val reserveStart = xOf(maxGb)
        reserve.isVisible = maxGb < totalGb
        reserve.resizeRelocate(snapPositionX(reserveStart), ty, snapSizeX(x0 + tw - reserveStart), th)
        val markW = snapSizeX(recommendedMark.prefWidth(-1.0).takeIf { it > 0 } ?: 2.0)
        val markH = snapSizeY(recommendedMark.prefHeight(-1.0).takeIf { it > 0 } ?: (th + 8))
        recommendedMark.resizeRelocate(snapPositionX(xOf(recommendedGb) - markW / 2), snapPositionY(cy - markH / 2), markW, markH)
        thumb.resizeRelocate(snapPositionX(fillEnd - ts / 2), snappedTopInset(), ts, ts)

        // tick labels centred under their value, kept inside the control; a label that would touch the recommended
        // one (always shown) is hidden
        val ly = snapPositionY(snappedTopInset() + ts + labelGap)
        val left = snappedLeftInset()
        val right = width - snappedRightInset()
        val placed = tickLabels.map { l ->
            val w = snapSizeX(l.prefWidth(-1.0))
            val cx = xOf(l.userData as Int)
            val x = (cx - w / 2).coerceIn(left, maxOf(left, right - w))
            Triple(l, x, w)
        }
        val rec = placed.firstOrNull { it.first.userData == recommendedGb }
        placed.forEach { (l, x, w) ->
            l.resizeRelocate(snapPositionX(x), ly, w, snapSizeY(l.prefHeight(-1.0)))
            l.isVisible = rec == null || l === rec.first ||
                (x + w + MIN_LABEL_GAP <= rec.second || x >= rec.second + rec.third + MIN_LABEL_GAP)
        }
    }

    private companion object {
        const val MIN_LABEL_GAP = 6.0
    }

}

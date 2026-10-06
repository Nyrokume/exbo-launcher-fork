package dev.launcherfork.ui.components

import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyIntegerWrapper
import javafx.css.PseudoClass
import javafx.scene.AccessibleAttribute
import javafx.scene.AccessibleRole
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Region
import kotlin.math.ceil

/**
 * RAM bar of the settings page, built like the original EXBO launcher's control (`progression_entry.fxml` +
 * `ProgressionController`: a 13 px bar of 14 equal segments with 2 px gaps, no thumb, filled with the accent up to the
 * value, click / drag anywhere sets it, the value as text under the bar). Here every segment is one GB of the
 * computer's memory (0..[totalGb]), so "6 ГБ из 16 ГБ" fills 6 of 16 segments, and every segment from [minGb] up to
 * the last one can be chosen; [RamScale] captions the min / max under the bar.
 *
 * Values are whole GB, steps of exactly 1 GB, always clamped to [minGb]..[maxGb]. Mouse: press / drag on segment N
 * selects N GB; keyboard (focusable): Left / Down -1, Right / Up +1, Page Down / Page Up -2 / +2, Home / End = min /
 * max. [onChange] fires only for user changes. Styling: .ram-bar, .ram-segment(:filled) in layout.css; the
 * segment height comes from CSS.
 */
class RamBar : Region() {
    private val valueWrapper = ReadOnlyIntegerWrapper(this, "value", 0)

    /** One region per GB of memory, left to right; segment i (0-based) stands for GB i + 1. */
    var segments: List<Region> = emptyList()
        private set

    var totalGb: Int = 16
        private set
    var minGb: Int = 2
        private set
    var maxGb: Int = 16
        private set
    var recommendedGb: Int = 6
        private set

    /** Called after the user moved the bar (mouse / keyboard), with the new GB value. */
    var onChange: (Int) -> Unit = {}
    /** Text of a GB value for the accessible text ("16 ГБ"). */
    var formatter: (Int) -> String = { "$it GB" }

    val valueProperty: ReadOnlyIntegerProperty get() = valueWrapper.readOnlyProperty
    val value: Int get() = valueWrapper.get()

    init {
        styleClass += "ram-bar"
        isFocusTraversable = true
        accessibleRole = AccessibleRole.SLIDER
        addEventHandler(MouseEvent.MOUSE_PRESSED) { e -> requestFocus(); setFromX(e.x); e.consume() }
        addEventHandler(MouseEvent.MOUSE_DRAGGED) { e -> setFromX(e.x); e.consume() }
        addEventHandler(KeyEvent.KEY_PRESSED, ::onKey)
        valueWrapper.addListener { _, _, _ -> paint(); updateAccessibleText() }
    }

    /** Scale, bounds, recommended value (kept for the hint) and current value (clamped; not a user change). */
    fun configure(totalGb: Int, minGb: Int, maxGb: Int, recommendedGb: Int, value: Int) {
        require(minGb <= maxGb) { "min $minGb > max $maxGb" }
        this.totalGb = maxOf(totalGb, maxGb, 1)
        this.minGb = minGb
        this.maxGb = maxGb
        this.recommendedGb = recommendedGb.coerceIn(minGb, maxGb)
        segments = (1..this.totalGb).map { gb ->
            Region().apply {
                styleClass.setAll("ram-segment")
                userData = gb
                isMouseTransparent = true
            }
        }
        children.setAll(segments)
        valueWrapper.set(value.coerceIn(minGb, maxGb))
        paint()
        updateAccessibleText()
        requestLayout()
    }

    /** Programmatic or user change; the value is clamped to whole GB in the bounds. Returns the stored value. */
    fun setValue(gb: Int, user: Boolean = false): Int {
        val v = gb.coerceIn(minGb, maxGb)
        if (v == value) return v
        valueWrapper.set(v)
        if (user) onChange(v)
        return v
    }

    /** Filled share of the bar (allocation / total memory) = filled segments / all segments. */
    val fillFraction: Double get() = if (totalGb <= 0) 0.0 else value.toDouble() / totalGb

    /** Segments that are painted filled (always [value] of them). */
    val filledSegments: List<Region> get() = segments.filter { it.pseudoClassStates.contains(FILLED) }

    private fun paint() = segments.forEach { s -> s.pseudoClassStateChanged(FILLED, (s.userData as Int) <= value) }

    /** Gap between segments: 2 px like the original, 1 px when memory is so large that segments get thin. */
    private fun gap(contentWidth: Double): Double = if (contentWidth / totalGb >= 10.0) 2.0 else 1.0

    private fun setFromX(x: Double) {
        val w = width - snappedLeftInset() - snappedRightInset()
        if (w <= 0) return
        // the press lands on segment N (1-based) -> N GB; the gap belongs to the segment on its left
        val n = ceil((x - snappedLeftInset()) / w * totalGb).toInt()
        setValue(n, user = true)
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

    private val segmentHeight: Double
        get() = snapSizeY(segments.firstOrNull()?.prefHeight(-1.0)?.takeIf { it > 0 } ?: 12.0)

    override fun computePrefHeight(width: Double): Double = snappedTopInset() + segmentHeight + snappedBottomInset()
    override fun computeMinHeight(width: Double): Double = computePrefHeight(width)
    override fun computeMaxHeight(width: Double): Double = computePrefHeight(width)
    override fun computePrefWidth(height: Double): Double = 360.0
    override fun computeMinWidth(height: Double): Double = 160.0

    /** Left / right edge (local x) of segment [i] (0-based): [i·pitch, (i+1)·pitch - gap], the last one ends flush. */
    private fun segmentSpan(i: Int): Pair<Double, Double> {
        val x0 = snappedLeftInset()
        val w = width - x0 - snappedRightInset()
        val pitch = w / totalGb
        val left = snapPositionX(x0 + i * pitch)
        val right = if (i == totalGb - 1) x0 + w else snapPositionX(x0 + (i + 1) * pitch) - gap(w)
        return left to maxOf(left + 1.0, right)
    }

    /** Centre (local x) of the segment that stands for [gb] (1-based); [RamScale] puts the min / max captions there. */
    fun segmentCenterX(gb: Int): Double = segmentSpan((gb - 1).coerceIn(0, totalGb - 1)).let { (l, r) -> (l + r) / 2 }

    override fun layoutChildren() {
        val y = snappedTopInset()
        val h = segmentHeight
        segments.forEachIndexed { i, s ->
            val (left, right) = segmentSpan(i)
            s.resizeRelocate(left, y, right - left, h)
        }
    }

    companion object {
        val FILLED: PseudoClass = PseudoClass.getPseudoClass("filled")
    }
}

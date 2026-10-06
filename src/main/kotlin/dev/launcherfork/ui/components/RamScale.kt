package dev.launcherfork.ui.components

import javafx.scene.control.Label
import javafx.scene.layout.Region

/**
 * One caption line under the [RamBar]: the lowest and the highest value that can be chosen («2 ГБ» … «14 ГБ»), each
 * centred under its segment (kept inside the line at the edges). When both are the same (a 4 GB computer: 2..2 GB)
 * only the min caption is shown. The reserve segments to the right of the max caption
 * thus read as "not available" without an extra legend. Must share the bar's x origin and width (both fill the RAM card).
 * Styling: .ram-scale-label in layout.css.
 */
class RamScale : Region() {
    val minLabel = Label().apply { styleClass += "ram-scale-label"; isMouseTransparent = true; minWidth = USE_PREF_SIZE }
    val maxLabel = Label().apply { styleClass += "ram-scale-label"; isMouseTransparent = true; minWidth = USE_PREF_SIZE }

    /** The bar the captions follow; set by the controller after [RamBar.configure]. */
    var bar: RamBar? = null
        set(value) { field = value; requestLayout() }

    init {
        styleClass += "ram-scale"
        isMouseTransparent = true
        children.setAll(minLabel, maxLabel)
    }

    /** Captions for [RamBar.minGb] / [RamBar.maxGb], already formatted ("2 ГБ"); one caption when they are equal. */
    fun setCaptions(min: String, max: String) {
        minLabel.text = min
        maxLabel.text = max
        maxLabel.isVisible = max != min
        requestLayout()
    }

    private val single: Boolean get() = !maxLabel.isVisible

    private val lineHeight: Double get() = minLabel.prefHeight(-1.0)

    override fun computePrefHeight(width: Double): Double = snappedTopInset() + lineHeight + snappedBottomInset()
    override fun computeMinHeight(width: Double): Double = computePrefHeight(width)
    override fun computeMaxHeight(width: Double): Double = computePrefHeight(width)
    override fun computePrefWidth(height: Double): Double = 160.0
    override fun computeMinWidth(height: Double): Double = minLabel.prefWidth(-1.0) + (if (single) 0.0 else maxLabel.prefWidth(-1.0) + 8.0)

    override fun layoutChildren() {
        val b = bar
        val h = lineHeight
        val y = snappedTopInset()
        val minW = minLabel.prefWidth(-1.0)
        val maxW = maxLabel.prefWidth(-1.0)
        // bar-local x -> local x (the bar and this line are siblings in the RAM card)
        val shift = if (b == null) 0.0 else b.layoutX - layoutX
        val minCentre = if (b == null) minW / 2 else shift + b.segmentCenterX(b.minGb)
        val maxCentre = if (b == null) width - maxW / 2 else shift + b.segmentCenterX(b.maxGb)
        val minX = snapPositionX((minCentre - minW / 2).coerceIn(0.0, maxOf(0.0, width - minW)))
        val maxX = snapPositionX((maxCentre - maxW / 2).coerceIn(minX + minW + 4.0, maxOf(minX + minW + 4.0, width - maxW)))
        minLabel.resizeRelocate(minX, y, minW, h)
        if (single) maxLabel.resizeRelocate(minX, y, 0.0, h) else maxLabel.resizeRelocate(maxX, y, maxW, h)
    }
}

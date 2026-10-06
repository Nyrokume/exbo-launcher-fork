package dev.launcherfork.ui.components

import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Segmented value bar (RAM in settings): [segments] equal pills, the first N are active.
 * [value] is 0..1 and snaps to whole segments (at least one stays active); click or drag to change it.
 * The text of the last active segment comes from [formatter]. Styling: .segmented-bar / .segment(.active) / .segment-value.
 */
class SegmentedBar : HBox() {
    private val cells = mutableListOf<StackPane>()
    private val valueLabel = Label().apply { styleClass += "segment-value"; isMouseTransparent = true }

    /** Called after the user changed the value (not on programmatic changes). */
    var onChange: (Double) -> Unit = {}
    var formatter: (Double) -> String = { "${(it * 100).roundToInt()}%" }
        set(v) { field = v; render() }

    val valueProperty: DoubleProperty = SimpleDoubleProperty(this, "value", 0.5)
    var value: Double
        get() = valueProperty.get()
        set(v) = valueProperty.set(snap(v))

    var segments: Int = 9
        set(v) {
            require(v > 0) { "segments must be > 0" }
            field = v
            rebuild()
        }

    /** Number of active segments for the current value. */
    val activeCount: Int get() = (value * segments).roundToInt().coerceIn(1, segments)

    init {
        styleClass += "segmented-bar"
        alignment = Pos.CENTER_LEFT
        valueProperty.addListener { _, _, _ -> render() }
        addEventHandler(MouseEvent.MOUSE_PRESSED) { e -> pick(e.x) }
        addEventHandler(MouseEvent.MOUSE_DRAGGED) { e -> pick(e.x) }
        rebuild()
    }

    private fun snap(v: Double): Double = (v.coerceIn(0.0, 1.0) * segments).roundToInt().coerceIn(1, segments).toDouble() / segments

    private fun pick(x: Double) {
        val w = width.takeIf { it > 0 } ?: return
        val n = ceil(x.coerceIn(0.0, w - 0.01) / w * segments).toInt().coerceIn(1, segments)
        val v = n.toDouble() / segments
        if (v != value) {
            value = v
            onChange(v)
        }
    }

    private fun rebuild() {
        cells.clear()
        repeat(segments) {
            cells += StackPane().apply {
                styleClass.addAll("segment", "zone-radius-sm")
                minWidth = 0.0
                prefWidth = 0.0
                maxWidth = Double.MAX_VALUE
                setHgrow(this, Priority.ALWAYS)
            }
        }
        children.setAll(cells)
        render()
    }

    private fun render() {
        val n = activeCount
        cells.forEachIndexed { i, cell ->
            cell.toggleClass("active", i < n)
            if (i == n - 1) cell.children.setAll(valueLabel) else cell.children.remove(valueLabel)
        }
        valueLabel.text = formatter(value)
    }
}

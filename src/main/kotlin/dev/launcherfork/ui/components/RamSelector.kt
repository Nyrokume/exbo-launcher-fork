package dev.launcherfork.ui.components

import javafx.beans.property.ReadOnlyIntegerProperty
import javafx.beans.property.ReadOnlyIntegerWrapper
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox

/**
 * RAM selector of the settings page: one labelled chip per option ("2 ГБ" … "12 ГБ"), exactly one selected, the
 * recommended one carries a caption under it. Options come from [dev.launcherfork.domain.RamOptions] (capped by the
 * computer's memory). Keyboard: Tab reaches the chips, Space/Enter selects, Left/Right moves the selection.
 * Styling: .ram-selector / .ram-option / .ram-chip(:selected, .recommended) / .ram-caption.
 */
class RamSelector : HBox() {
    private val group = ToggleGroup()
    private val selectedWrapper = ReadOnlyIntegerWrapper(this, "selected", 0)
    private var chips: List<ToggleButton> = emptyList()
    private var rebuilding = false

    /** Called after the user picked another option (not on programmatic changes). */
    var onChange: (Int) -> Unit = {}
    /** Chip text of an option (GB). */
    var formatter: (Int) -> String = { "$it GB" }
    /** Caption under the recommended chip. */
    var recommendedText: String = "recommended"

    var options: List<Int> = emptyList()
        private set
    var recommended: Int = 0
        private set

    val selectedProperty: ReadOnlyIntegerProperty get() = selectedWrapper.readOnlyProperty
    val selected: Int get() = selectedWrapper.get()
    /** Chip buttons in option order (tests, focus). */
    val buttons: List<ToggleButton> get() = chips

    init {
        styleClass += "ram-selector"
        alignment = Pos.TOP_LEFT
        group.selectedToggleProperty().addListener { _, old, new ->
            if (rebuilding) return@addListener
            if (new == null) { old?.isSelected = true; return@addListener } // never empty
            val gb = new.userData as Int
            if (gb != selectedWrapper.get()) {
                selectedWrapper.set(gb)
                onChange(gb)
            }
        }
    }

    /** Rebuilds the chips; [selected] must be one of [options]. Does not call [onChange]. */
    fun setOptions(options: List<Int>, recommended: Int, selected: Int) {
        require(options.isNotEmpty()) { "no RAM options" }
        require(selected in options) { "$selected is not an option of $options" }
        this.options = options
        this.recommended = recommended
        selectedWrapper.set(selected)
        rebuilding = true
        group.toggles.clear()
        chips = options.map { gb ->
            ToggleButton(formatter(gb)).apply {
                styleClass.addAll("ram-chip", "zone-radius-sm")
                if (gb == recommended) styleClass += "recommended"
                userData = gb
                toggleGroup = group
                isSelected = gb == selected
                maxWidth = Double.MAX_VALUE
                isMnemonicParsing = false
            }
        }
        rebuilding = false
        children.setAll(chips.map { chip ->
            val gb = chip.userData as Int
            val caption = Label(if (gb == recommended) recommendedText else "").apply {
                styleClass += "ram-caption"
                isMouseTransparent = true
            }
            VBox(chip, caption).apply {
                styleClass += "ram-option"
                alignment = Pos.TOP_CENTER
                minWidth = 0.0
                prefWidth = 0.0
                maxWidth = Double.MAX_VALUE
                setHgrow(this, Priority.ALWAYS)
            }
        })
        chips.forEachIndexed { i, chip ->
            chip.setOnKeyPressed { e ->
                val to = when (e.code) {
                    javafx.scene.input.KeyCode.LEFT -> i - 1
                    javafx.scene.input.KeyCode.RIGHT -> i + 1
                    else -> return@setOnKeyPressed
                }
                chips.getOrNull(to)?.let { it.isSelected = true; it.requestFocus() }
                e.consume()
            }
        }
    }

    /** Selects [gb] as if the user clicked it (fires [onChange] when it changes). */
    fun select(gb: Int) {
        chips.firstOrNull { it.userData == gb }?.isSelected = true
    }
}

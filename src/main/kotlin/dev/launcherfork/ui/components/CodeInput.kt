package dev.launcherfork.ui.components

import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanBinding
import javafx.beans.property.ReadOnlyStringProperty
import javafx.beans.property.ReadOnlyStringWrapper
import javafx.css.PseudoClass
import javafx.geometry.Pos
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import dev.launcherfork.i18n.text
import java.util.ResourceBundle

/**
 * One-time code as separate digit cells (stalzone.com 2FA): [cellCount] one-digit fields in a row.
 *  - typing a digit fills the cell and moves to the next one; letters are ignored;
 *  - pasting (or typing into a cell) several digits fills the cells from that one; a paste that has a whole code
 *    (at least [cellCount] digits, spaces/dashes are dropped: "558 966") fills all cells from the first;
 *  - Backspace clears the cell, on an empty cell it goes back and clears the previous one; arrows move; Delete clears;
 *  - [error] paints every cell (`:error`) until the code is edited.
 * Used by the authenticator panel (totp_auth_panel.fxml, 6 cells); generic, so other code screens can reuse it.
 * Styling: .code-input / .code-cell(:filled, :error) in zone-totp.css.
 */
class CodeInput : HBox() {
    private val cellList = mutableListOf<TextField>()
    private val codeWrapper = ReadOnlyStringWrapper(this, "code", "")

    /** Called once the last missing digit was entered (all cells filled). */
    var onComplete: (String) -> Unit = {}

    /** Number of digit cells (FXML: `cellCount="6"`). Changing it clears the code. */
    var cellCount: Int = 6
        set(v) {
            require(v in 1..12) { "cellCount must be 1..12" }
            field = v
            rebuild()
        }

    /** Strings for the cells' screen-reader labels ("Цифра N из M"); null -> English. */
    var strings: ResourceBundle? = null
        set(v) {
            field = v
            updateLabels()
        }

    val codeProperty: ReadOnlyStringProperty get() = codeWrapper.readOnlyProperty
    /** Digits typed so far, in cell order (empty cells are skipped). */
    val code: String get() = codeWrapper.get()
    /** True when every cell has a digit. */
    val completeProperty: BooleanBinding = Bindings.createBooleanBinding({ code.length == cellCount }, codeWrapper)
    val isComplete: Boolean get() = completeProperty.get()
    /** The cells (read-only view, for focus and tests). */
    val cells: List<TextField> get() = cellList

    var error: Boolean = false
        set(v) {
            field = v
            cellList.forEach { it.pseudoClassStateChanged(ERROR, v) }
        }

    init {
        styleClass += "code-input"
        alignment = Pos.CENTER_LEFT
        rebuild()
    }

    /** Puts [text] (its digits) into the cells as a paste into the first cell would. */
    fun setCode(text: String) = input(0, text)

    fun clear() {
        cellList.forEach { it.text = "" }
        sync()
    }

    fun focusFirstEmpty() = (cellList.firstOrNull { it.text.isEmpty() } ?: cellList.last()).requestFocus()

    /**
     * Text arriving in cell [index] (typed key, paste, IME): digits only. Returns the index that should get focus.
     * An empty [text] is a deletion of the cell's digit.
     */
    fun input(index: Int, text: String): Int {
        if (text.isEmpty()) {
            cellList[index].text = ""
            sync()
            return index
        }
        val digits = text.filter(Char::isDigit)
        if (digits.isEmpty()) return index
        val start = if (digits.length >= cellCount) 0 else index
        val wasComplete = isComplete
        digits.take(cellCount - start).forEachIndexed { k, d -> cellList[start + k].text = d.toString() }
        sync()
        val next = (start + digits.length).coerceAtMost(cellCount - 1)
        cellList[next].requestFocus()
        cellList[next].end()
        if (isComplete && !wasComplete) onComplete(code)
        return next
    }

    private fun sync() {
        cellList.forEach { it.pseudoClassStateChanged(FILLED, it.text.isNotEmpty()) }
        val joined = cellList.joinToString("") { it.text }
        if (joined != codeWrapper.get()) {
            codeWrapper.set(joined)
            error = false
        }
    }

    private fun rebuild() {
        cellList.clear()
        repeat(cellCount) { i -> cellList += Cell(i) }
        children.setAll(cellList)
        if (cellCount >= 9) {
            alignment = Pos.CENTER_LEFT
            cellList.forEach { cell ->
                cell.maxWidth = Double.MAX_VALUE
                HBox.setHgrow(cell, Priority.ALWAYS)
            }
        }
        updateLabels()
        // .cells-N lets a stylesheet size the cells by their number (zone-totp.css styles the 6-cell row)
        styleClass.removeIf { it.startsWith("cells-") }
        styleClass += "cells-$cellCount"
        sync()
    }

    private fun updateLabels() = cellList.forEachIndexed { i, cell ->
        cell.accessibleText = strings?.text(CELL_KEY, i + 1, cellCount) ?: "Digit ${i + 1} of $cellCount"
    }

    private fun back(index: Int) {
        val cell = cellList[index]
        if (cell.text.isNotEmpty()) {
            input(index, "")
        } else if (index > 0) {
            input(index - 1, "")
            cellList[index - 1].requestFocus()
        }
    }

    /** A one-character cell. All edits (keys, paste, IME) go through [replaceText] and land in [input]. */
    private inner class Cell(private val index: Int) : TextField() {
        init {
            styleClass.addAll("code-cell", "zone-radius-sm")
            alignment = Pos.CENTER
            prefColumnCount = 1
            minWidth = USE_PREF_SIZE
            maxWidth = USE_PREF_SIZE
            setHgrow(this, Priority.NEVER)
            addEventFilter(KeyEvent.KEY_PRESSED) { e ->
                when (e.code) {
                    KeyCode.BACK_SPACE -> { back(index); e.consume() }
                    KeyCode.DELETE -> { input(index, ""); e.consume() }
                    KeyCode.LEFT -> { if (index > 0) cellList[index - 1].requestFocus(); e.consume() }
                    KeyCode.RIGHT -> { if (index < cellCount - 1) cellList[index + 1].requestFocus(); e.consume() }
                    else -> Unit
                }
            }
            // the caret sits after the digit, so typing replaces it (all text selected on focus)
            focusedProperty().addListener { _, _, f -> if (f) javafx.application.Platform.runLater { selectAll() } }
        }

        override fun replaceText(start: Int, end: Int, text: String) {
            this@CodeInput.input(index, text)
        }

        override fun replaceSelection(replacement: String) {
            this@CodeInput.input(index, replacement)
        }
    }

    private companion object {
        val ERROR: PseudoClass = PseudoClass.getPseudoClass("error")
        val FILLED: PseudoClass = PseudoClass.getPseudoClass("filled")
        const val CELL_KEY = "fork.code.cell"
    }
}

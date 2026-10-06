package dev.launcherfork.ui.components

import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.scene.control.ToggleButton
import javafx.scene.control.Tooltip

/**
 * Eye toggle of the zone password fields (login, registration): [plain] is a plain-text twin of [password] (same
 * text, two-way), shown while [toggle] is selected; focus and caret follow the visible field. [label] gives the
 * accessible text / tooltip for the shown (true) and hidden (false) state.
 */
object PasswordReveal {
    fun wire(password: PasswordField, plain: TextField, toggle: ToggleButton, label: (shown: Boolean) -> String) {
        plain.textProperty().bindBidirectional(password.textProperty())
        plain.visibleProperty().bind(toggle.selectedProperty())
        password.visibleProperty().bind(toggle.selectedProperty().not())
        toggle.selectedProperty().addListener { _, _, shown ->
            val target = if (shown) plain else password
            target.requestFocus()
            target.positionCaret(target.text.length)
        }
        toggle.accessibleText = label(toggle.isSelected)
        toggle.tooltip = Tooltip(toggle.accessibleText)
        toggle.selectedProperty().addListener { _, _, shown ->
            toggle.accessibleText = label(shown)
            toggle.tooltip.text = toggle.accessibleText
        }
    }
}

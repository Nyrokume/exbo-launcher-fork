package dev.launcherfork.ui.screens.main

import dev.launcherfork.ui.UiContext
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox

/** main/client_settings.fxml: popup of the gear button next to the progress bar. */
class ClientSettingsController(private val ctx: UiContext) {
    @FXML lateinit var clientSettingsPane: Pane
    @FXML lateinit var entries: VBox

    var onDelete: () -> Unit = {}
    var onNotAvailable: () -> Unit = {}

    @FXML
    fun initialize() {
        val items = listOf(
            Triple<String, String, () -> Unit>("main.client.delete", "icon-trash", { onDelete() }),
            Triple<String, String, () -> Unit>("main.client.check_files", "icon-recheck", { onNotAvailable() }),
            Triple<String, String, () -> Unit>("main.client.open_folder", "icon-folder", { onNotAvailable() }),
            Triple<String, String, () -> Unit>("main.client.open_screenshots", "icon-folder", { onNotAvailable() }),
            Triple<String, String, () -> Unit>("main.client.create_shortcut", "icon-shortcut", { onNotAvailable() }),
        )
        entries.children.setAll(items.map { (key, icon, action) ->
            Button(ctx.t(key)).apply {
                styleClass.setAll("context-button", "font-display", icon)
                prefWidth = 204.0
                prefHeight = 22.0
                setOnAction { hide(); action() }
            }
        })
        clientSettingsPane.prefHeight = items.size * 22.0 + 4
    }

    val isVisible: Boolean get() = clientSettingsPane.isVisible

    fun toggle() {
        clientSettingsPane.isVisible = !clientSettingsPane.isVisible
    }

    fun hide() {
        clientSettingsPane.isVisible = false
    }
}

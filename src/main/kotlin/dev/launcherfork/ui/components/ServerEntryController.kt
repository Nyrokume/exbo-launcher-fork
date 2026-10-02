package dev.launcherfork.ui.components

import dev.launcherfork.domain.Server
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.screens.main.ClientState
import javafx.beans.value.ObservableValue
import javafx.fxml.FXML
import javafx.scene.control.ProgressBar
import javafx.scene.image.ImageView
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.text.Text

/** server_entry.fxml: one card in the sidebar. Selection/colors are CSS classes (.server-entry.selected). */
class ServerEntryController(private val ctx: UiContext) {
    @FXML lateinit var root: AnchorPane
    @FXML lateinit var image: Pane
    @FXML lateinit var status: Text
    @FXML lateinit var name: Text
    @FXML lateinit var progress: ProgressBar
    @FXML lateinit var icon: Region
    @FXML lateinit var downloadingStatus: Text
    @FXML lateinit var cursor: ImageView

    lateinit var server: Server
        private set

    @FXML
    fun initialize() {
        root.brightenOnHover(Theme.SERVER_ENTRY_HOVER_BRIGHTNESS)
        downloadingStatus.text = ""
    }

    fun bind(server: Server, state: ObservableValue<ClientState>) {
        this.server = server
        name.text = server.caption
        status.text = if (server.online) ctx.t("main.server_online", server.players) else ctx.t("main.server_offline")
        image.setImageBackground(loadImage(server.iconResource))
        render(state.value)
        state.addListener { _, _, s -> render(s) }
    }

    fun setSelected(selected: Boolean) {
        root.toggleClass("selected", selected)
        cursor.isVisible = selected
    }

    private fun render(s: ClientState) {
        val fraction = when (s) {
            is ClientState.Downloading -> s.progress.fraction
            is ClientState.Paused -> s.fraction
            else -> null
        }
        progress.isVisible = fraction != null
        progress.progress = fraction ?: 0.0
        downloadingStatus.text = fraction?.let { "${(it * 100).toInt()}%" } ?: ""
        icon.toggleClass("icon-download", s == ClientState.NotInstalled)
        icon.toggleClass("icon-play", s == ClientState.Ready || s == ClientState.Running || s == ClientState.Starting)
    }
}

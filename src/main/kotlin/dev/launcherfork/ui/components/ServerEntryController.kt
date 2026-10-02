package dev.launcherfork.ui.components

import dev.launcherfork.domain.Server
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.state.ClientState
import javafx.beans.value.ChangeListener
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
    private var state: ObservableValue<ClientState>? = null
    private val stateListener = ChangeListener<ClientState> { _, _, s -> render(s) }

    @FXML
    fun initialize() {
        root.brightenOnHover(Theme.SERVER_ENTRY_HOVER_BRIGHTNESS)
        downloadingStatus.text = ""
    }

    fun bind(server: Server, state: ObservableValue<ClientState>) {
        this.server = server
        name.text = server.caption
        status.text = if (server.online) ctx.t("main.server_online", server.players) else ctx.t("main.server_offline")
        image.setImageBackground(loadImage(server.iconUri))
        unbind()
        this.state = state
        render(state.value)
        state.addListener(stateListener)
    }

    /** The state lives in the session-scoped store, so the card must detach when its scene goes away. */
    fun unbind() {
        state?.removeListener(stateListener)
        state = null
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

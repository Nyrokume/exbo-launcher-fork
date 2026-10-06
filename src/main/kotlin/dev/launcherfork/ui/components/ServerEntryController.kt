package dev.launcherfork.ui.components

import dev.launcherfork.domain.Server
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.state.ClientState
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.fxml.FXML
import javafx.scene.control.ProgressBar
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.text.Text
import java.util.Locale

/**
 * server_entry.fxml: one game in the sidebar. Selection = red indicator (.server-entry.selected), state comes from
 * the session-scoped store: download progress + percent, download icon when not installed, red live dot while running.
 */
class ServerEntryController(private val ctx: UiContext) {
    @FXML lateinit var root: HBox
    @FXML lateinit var cursor: Region
    @FXML lateinit var image: Pane
    @FXML lateinit var status: Text
    @FXML lateinit var name: Text
    @FXML lateinit var progress: ProgressBar
    @FXML lateinit var liveDot: Region
    @FXML lateinit var icon: Region
    @FXML lateinit var downloadingStatus: Text

    lateinit var server: Server
        private set
    private var state: ObservableValue<ClientState>? = null
    private val stateListener = ChangeListener<ClientState> { _, _, s -> render(s) }

    @FXML
    fun initialize() {
        downloadingStatus.text = ""
    }

    fun bind(server: Server, state: ObservableValue<ClientState>) {
        this.server = server
        name.text = server.caption.uppercase(Locale.forLanguageTag(ctx.lang))
        val online = if (server.online) ctx.t("main.server_online", server.players) else ctx.t("main.server_offline")
        status.text = online
        Tooltip.install(root, Tooltip("${server.caption} - $online"))
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
    }

    private fun render(s: ClientState) {
        val fraction = when (s) {
            is ClientState.Downloading -> s.progress.fraction
            is ClientState.Paused -> s.fraction
            else -> null
        }
        progress.isVisible = fraction != null
        progress.isManaged = fraction != null
        progress.progress = fraction ?: 0.0
        // the status line is shown only for offline servers; the reference shows just the name
        status.isVisible = !server.online
        status.isManaged = !server.online
        downloadingStatus.text = fraction?.let { "${(it * 100).toInt()}%" } ?: ""
        liveDot.isVisible = s == ClientState.Running
        icon.toggleClass("icon-download", s == ClientState.NotInstalled)
    }
}

package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.Server
import dev.launcherfork.ui.Disposable
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.formatBytes
import dev.launcherfork.ui.components.toggleClass
import dev.launcherfork.ui.state.ClientState
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ProgressBar
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.text.Text
import java.util.Locale

/**
 * main/status_pane.fxml: status text + progress bar (stretches with the window), tools button and the
 * Play/Download CTA. State comes from [dev.launcherfork.ui.state.ClientStateStore].
 */
class StatusPaneController(private val ctx: UiContext) : Disposable {
    @FXML lateinit var defaultStatusPane: HBox
    @FXML lateinit var clientSettingsButton: Button
    @FXML lateinit var progressBar: ProgressBar
    @FXML lateinit var downloadButton: Button
    @FXML lateinit var downloadingStatus: Text

    var onPrimaryAction: (Server) -> Unit = {}
    var onClientSettings: () -> Unit = {}

    private var current: Server? = null
    private var currentState: ObservableValue<ClientState>? = null
    private val stateListener = ChangeListener<ClientState> { _, _, s -> render(s) }
    private val buttonStates = listOf("state-download", "state-play", "state-continue", "state-pause", "state-kill")

    @FXML
    fun initialize() {
        downloadingStatus.text = ""
        downloadButton.text = ""
        downloadButton.setOnAction { current?.let(onPrimaryAction) }
        clientSettingsButton.setOnAction { onClientSettings() }
        clientSettingsButton.tooltip = Tooltip(ctx.t("main.client.tools"))
    }

    fun showServer(server: Server, state: ObservableValue<ClientState>) {
        currentState?.removeListener(stateListener)
        current = server
        currentState = state
        state.addListener(stateListener)
        render(state.value)
    }

    override fun dispose() {
        currentState?.removeListener(stateListener)
        currentState = null
    }

    private fun render(s: ClientState) {
        val server = current ?: return
        val (cls, textKey) = when (s) {
            ClientState.NotInstalled -> "state-download" to "main.client_button.download"
            is ClientState.Downloading -> "state-pause" to "main.client_button.pause"
            is ClientState.Paused -> "state-continue" to "main.client_button.continue"
            ClientState.Ready, ClientState.Starting -> "state-play" to "main.client_button.play"
            ClientState.Running -> "state-kill" to "main.client_button.kill"
        }
        buttonStates.forEach { downloadButton.toggleClass(it, it == cls) }
        // UPPERCASE CTA (.zone-caps) - to be swapped to SpacedLabel after the redesign/auth merge
        downloadButton.text = ctx.t(textKey).uppercase(Locale.forLanguageTag(ctx.lang))
        downloadButton.isDisable = s == ClientState.Starting
        clientSettingsButton.isDisable = s == ClientState.NotInstalled
        downloadingStatus.text = when (s) {
            ClientState.NotInstalled -> ctx.t("main.status.ready", formatBytes(server.clientSizeBytes))
            is ClientState.Downloading -> ctx.t(
                "main.status.updating",
                formatBytes(s.progress.totalBytes - s.progress.doneBytes),
                formatBytes(s.progress.bytesPerSecond),
            )
            is ClientState.Paused -> ctx.t("main.status.paused", formatBytes(((1 - s.fraction) * s.totalBytes).toLong()))
            ClientState.Ready -> ctx.t("main.status.ready_to_launch")
            ClientState.Starting -> ctx.t("main.status.starting")
            ClientState.Running -> ctx.t("main.status.running")
        }
        progressBar.progress = when (s) {
            is ClientState.Downloading -> s.progress.fraction
            is ClientState.Paused -> s.fraction
            ClientState.Running, ClientState.Starting -> 1.0
            else -> 0.0
        }
    }
}

package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.Server
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.DownloadBar
import dev.launcherfork.ui.components.brightenOnHover
import dev.launcherfork.ui.components.formatBytes
import dev.launcherfork.ui.components.loadImage
import dev.launcherfork.ui.components.toggleClass
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.image.ImageView
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.text.Text

/**
 * main/status_pane.fxml: Play/Download button, download bar + status text, server description and the
 * main/news page switcher. State comes from [ClientStateStore].
 */
class StatusPaneController(private val ctx: UiContext) {
    @FXML lateinit var defaultStatusPane: AnchorPane
    @FXML lateinit var clientSettingsButton: Button
    @FXML lateinit var progressBar: ProgressBar
    @FXML lateinit var downloadButton: Button
    @FXML lateinit var downloadingStatus: Text
    @FXML lateinit var newsBg: AnchorPane
    @FXML lateinit var serverDesc: Pane
    @FXML lateinit var pagesPane: Pane

    var onPrimaryAction: (Server) -> Unit = {}
    var onClientSettings: () -> Unit = {}
    var onShowNews: () -> Unit = {}
    var onShowServer: () -> Unit = {}

    private lateinit var downloadBar: DownloadBar
    private val pageMain = Region().apply { styleClass += listOf("page-icon", "page-main"); setPrefSize(19.0, 16.0) }
    private val pageNews = Region().apply { styleClass += listOf("page-icon", "page-news"); setPrefSize(19.0, 16.0); layoutX = 28.0 }
    private var current: Server? = null
    private var currentState: ObservableValue<ClientState>? = null
    private val stateListener = ChangeListener<ClientState> { _, _, s -> render(s) }

    private val buttonStates = listOf("state-download", "state-play", "state-continue", "state-pause", "state-kill", "state-update")

    @FXML
    fun initialize() {
        downloadBar = DownloadBar(progressBar)
        downloadingStatus.text = ""
        downloadButton.text = ""
        downloadButton.setOnAction { current?.let(onPrimaryAction) }
        clientSettingsButton.setOnAction { onClientSettings() }
        clientSettingsButton.brightenOnHover()
        pagesPane.children.setAll(pageMain, pageNews)
        pageMain.setOnMouseClicked { onShowServer() }
        pageNews.setOnMouseClicked { onShowNews() }
        listOf(pageMain, pageNews).forEach { it.brightenOnHover() }
        setPage(news = true)
    }

    fun showServer(server: Server, state: ObservableValue<ClientState>) {
        currentState?.removeListener(stateListener)
        current = server
        currentState = state
        state.addListener(stateListener)
        render(state.value)
    }

    fun setPage(news: Boolean) {
        pageNews.toggleClass("selected", news)
        pageMain.toggleClass("selected", !news)
        serverDesc.isVisible = !news
    }

    /** Server caption + line + description, positioned like ServerPreviewSubController. */
    fun showDescription(server: Server) {
        serverDesc.children.clear()
        val height = server.description.size * 16 + 40
        var y = Theme.MAIN_HEIGHT / 2 - height / 2 - 70
        serverDesc.children += Label(server.caption).apply { styleClass += "server-caption"; layoutY = y }
        y += 50
        serverDesc.children += ImageView(loadImage(Theme.IMAGE_ROOT + "line_settings_main.png")).apply {
            fitHeight = 1.0; fitWidth = 407.0; layoutY = y
        }
        y += 25
        server.description.forEach { line ->
            serverDesc.children += Text(line).apply { styleClass += "server-desc"; layoutY = y }
            y += 16
        }
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
        downloadButton.text = ctx.t(textKey)
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
            ClientState.Ready -> ctx.t("main.status.completed")
            ClientState.Starting -> ctx.t("main.status.starting")
            ClientState.Running -> ctx.t("main.status.running")
        }
        downloadBar.setProgress(
            when (s) {
                ClientState.NotInstalled -> 0.0
                is ClientState.Downloading -> s.progress.fraction
                is ClientState.Paused -> s.fraction
                else -> 1.0
            },
        )
    }
}

package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.ClientOperation
import dev.launcherfork.domain.Server
import dev.launcherfork.ui.Disposable
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.formatBytes
import dev.launcherfork.ui.components.toggleClass
import dev.launcherfork.ui.motion.Motion
import dev.launcherfork.ui.state.ClientState
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ProgressBar
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.shape.SVGPath
import javafx.scene.text.Text
import java.util.Locale
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * main/status_pane.fxml: status text + progress bar (stretches with the window), the game-actions button with its
 * floating dropdown ([GameActionsController]) and the Play/Download CTA. State comes from
 * [dev.launcherfork.ui.state.ClientStateStore]; while a verify / reinstall / delete runs ([ClientState.Busy]) the CTA
 * is disabled and shows the operation.
 */
class StatusPaneController(private val ctx: UiContext) : Disposable {
    @FXML lateinit var defaultStatusPane: HBox
    @FXML lateinit var gameActionsButton: Button
    @FXML lateinit var gameActionsGlyph: SVGPath
    @FXML lateinit var progressBar: ProgressBar
    @FXML lateinit var downloadButton: Button
    @FXML lateinit var downloadingStatus: Text
    @FXML lateinit var progressPercent: Text
    @FXML lateinit var statusPill: javafx.scene.layout.StackPane
    @FXML lateinit var gameActionsController: GameActionsController

    var onPrimaryAction: (Server) -> Unit = {}

    private var current: Server? = null
    private var currentState: ObservableValue<ClientState>? = null
    private val stateListener = ChangeListener<ClientState> { _, _, s -> render(s) }
    private val buttonStates = listOf("state-download", "state-play", "state-continue", "state-pause", "state-kill", "state-busy")

    @FXML
    fun initialize() {
        downloadingStatus.text = ""
        downloadButton.text = ""
        downloadButton.setOnAction { current?.let(onPrimaryAction) }
        // ИГРАТЬ: a short press scale and a soft hover brighten (ui/motion/Motion); the actions button only presses
        Motion.pressScale(downloadButton)
        Motion.hoverBrighten(downloadButton)
        Motion.pressScale(gameActionsButton)

        val menu = gameActionsController
        menu.anchor = gameActionsButton
        gameActionsButton.tooltip = Tooltip(ctx.t("main.actions.button"))
        gameActionsButton.setOnAction { menu.toggle() }
        // Space fires the button by itself; Enter / Down open the menu with the first enabled row focused
        gameActionsButton.addEventFilter(KeyEvent.KEY_PRESSED) { e ->
            when (e.code) {
                KeyCode.ENTER -> if (menu.isShown) menu.hide() else menu.show(focusFirst = true)
                KeyCode.DOWN, KeyCode.UP -> menu.show(focusFirst = true)
                else -> return@addEventFilter
            }
            e.consume()
        }
        menu.gameActionsMenu.visibleProperty().addListener { _, _, open ->
            gameActionsButton.toggleClass("open", open)
            if (open) placeActionsMenu()
        }
        // follow the button (window resize, language / theme switch) and the card's own size
        gameActionsButton.boundsInParentProperty().addListener { _, _, _ -> placeActionsMenu() }
        menu.gameActionsMenu.layoutBoundsProperty().addListener { _, _, _ -> placeActionsMenu() }
        defaultStatusPane.localToSceneTransformProperty().addListener { _, _, _ -> placeActionsMenu() }
        // the status bar hides with the home screen (settings): the menu goes with it
        defaultStatusPane.visibleProperty().addListener { _, _, shown -> if (!shown) menu.hide() }
    }

    /**
     * Pref-size the unmanaged card and hang it ABOVE the game-actions button, [MENU_GAP] px over it, right edges
     * aligned (the card grows to the left over the progress bar), kept [WINDOW_MARGIN] px inside the window.
     */
    fun placeActionsMenu() {
        val card = gameActionsController.gameActionsMenu
        if (!card.isVisible) return
        if (card.scene != null) card.applyCss()
        card.autosize()
        val b = gameActionsButton.boundsInParent
        var x = b.maxX - card.width
        val left = defaultStatusPane.localToScene(x, 0.0).x
        x += max(0.0, WINDOW_MARGIN - left)
        val top = defaultStatusPane.localToScene(0.0, b.minY - MENU_GAP - card.height).y
        val y = b.minY - MENU_GAP - card.height + max(0.0, WINDOW_MARGIN - top)
        card.relocate(round(x), round(y))
    }

    fun showServer(server: Server, state: ObservableValue<ClientState>) {
        currentState?.removeListener(stateListener)
        current = server
        currentState = state
        state.addListener(stateListener)
        gameActionsController.setGame(server.caption)
        render(state.value)
    }

    override fun dispose() {
        currentState?.removeListener(stateListener)
        currentState = null
        gameActionsController.dispose()
    }

    private fun render(s: ClientState) {
        val server = current ?: return
        val (cls, textKey) = when (s) {
            ClientState.NotInstalled -> "state-download" to "main.client_button.download"
            is ClientState.Downloading -> "state-pause" to "main.client_button.pause"
            is ClientState.Paused -> "state-continue" to "main.client_button.continue"
            ClientState.Ready, ClientState.Starting -> "state-play" to "main.client_button.play"
            ClientState.Running -> "state-kill" to "main.client_button.kill"
            is ClientState.Busy -> "state-busy" to when (s.operation) {
                ClientOperation.VERIFY -> "main.client_button.verifying"
                ClientOperation.REINSTALL -> "main.client_button.reinstalling"
                ClientOperation.DELETE -> "main.client_button.deleting"
            }
        }
        buttonStates.forEach { downloadButton.toggleClass(it, it == cls) }
        // UPPERCASE CTA (.zone-caps) - to be swapped to SpacedLabel after the redesign/auth merge
        downloadButton.text = ctx.t(textKey).uppercase(Locale.forLanguageTag(ctx.lang))
        downloadButton.isDisable = s == ClientState.Starting || s is ClientState.Busy
        gameActionsController.update(s)
        downloadingStatus.text = when (s) {
            ClientState.NotInstalled -> ctx.t("main.status.ready", formatBytes(server.clientSizeBytes))
            is ClientState.Downloading -> ctx.t(
                "main.status.updating",
                formatBytes(s.progress.totalBytes - s.progress.doneBytes),
                formatBytes(s.progress.bytesPerSecond),
            )
            is ClientState.Paused -> ctx.t("main.status.paused", formatBytes(((1 - Motion.safeFraction(s.fraction)) * s.totalBytes).toLong()))
            ClientState.Ready -> ctx.t("main.status.ready_to_launch")
            ClientState.Starting -> ctx.t("main.status.starting")
            ClientState.Running -> ctx.t("main.status.running")
            is ClientState.Busy -> ctx.t("main.status.busy.${s.operation.name.lowercase()}") // the number is in the percentage slot
        }
        // the bar glides between the service's ticks instead of jumping (back to 0 / another game: at once)
        Motion.progress(progressBar, when (s) {
            is ClientState.Downloading -> s.progress.fraction
            is ClientState.Paused -> s.fraction
            is ClientState.Busy -> s.fraction
            ClientState.Running, ClientState.Starting -> 1.0
            else -> 0.0
        })
        // percentage inside the pill (right) while something is in progress
        val fraction = when (s) {
            is ClientState.Downloading -> s.progress.fraction
            is ClientState.Paused -> s.fraction
            is ClientState.Busy -> s.fraction // verify / reinstall / delete
            else -> null
        }
        progressPercent.text = fraction?.let { "${(Motion.safeFraction(it) * 100).roundToInt()}%" } ?: ""
        statusPill.toggleClass("in-progress", s is ClientState.Downloading || s is ClientState.Busy)
        statusPill.toggleClass("paused", s is ClientState.Paused)
        statusPill.toggleClass("running", s == ClientState.Running || s == ClientState.Starting)
    }

    private companion object {
        const val MENU_GAP = 8.0
        const val WINDOW_MARGIN = 8.0
    }
}

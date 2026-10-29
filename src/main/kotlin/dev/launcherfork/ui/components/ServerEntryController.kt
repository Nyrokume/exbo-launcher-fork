package dev.launcherfork.ui.components

import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.Server
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.motion.Motion
import dev.launcherfork.ui.state.ClientState
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.fxml.FXML
import javafx.scene.control.ProgressBar
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.shape.Rectangle
import javafx.scene.text.Text
import java.util.Locale

/**
 * server_entry.fxml: one game in the sidebar. Selection = red indicator (.server-entry.selected), state comes from
 * the session-scoped store: download progress + percent, download icon when not installed, red live dot while running.
 * Under the name: the online line, a green dot + «1 284 онлайн» (grey dot + «не в сети» when the count is unknown).
 */
class ServerEntryController(private val ctx: UiContext) {
    @FXML lateinit var root: HBox
    @FXML lateinit var cursor: Region
    @FXML lateinit var image: Pane
    @FXML lateinit var name: Text
    @FXML lateinit var onlineRow: HBox
    @FXML lateinit var onlineDot: Region
    @FXML lateinit var onlineText: Text
    @FXML lateinit var progress: ProgressBar
    @FXML lateinit var liveDot: Region
    @FXML lateinit var icon: Region
    @FXML lateinit var downloadingStatus: Text

    lateinit var server: Server
        private set
    private var state: ObservableValue<ClientState>? = null
    private val stateListener = ChangeListener<ClientState> { _, _, s -> render(s) }

    /** Players online, shown under the name; null (or negative) = unknown / offline. Fed by ServerRepository.watchOnline. */
    val onlineCount: ObjectProperty<Int?> = SimpleObjectProperty(this, "onlineCount", null)
    private var onlineWatch: Cancellable? = null
    /**
     * Bumped on every bind/unbind (FX thread only). A repository tick may queue `runLater` just before [unbind];
     * that late update carries the old generation and is dropped, so a re-bound card never shows the previous count.
     */
    private var onlineGeneration = 0L

    /**
     * Hover background that fades (CSS has no transitions): with animations on at load, the row's own :hover fill is
     * replaced (.motion, layout.css) by this unmanaged layer under the content, painted by the theme (.entry-hover-fill)
     * and faded by ui/motion/Motion.fadeWith. With animations off the plain CSS :hover stays.
     */
    val hoverFill = Region().apply {
        styleClass += "entry-hover-fill"
        isManaged = false
        isMouseTransparent = true
    }

    @FXML
    fun initialize() {
        if (Motion.enabled) {
            root.styleClass += "motion"
            root.children.add(0, hoverFill)
            root.layoutBoundsProperty().addListener { _, _, b -> hoverFill.resizeRelocate(0.0, 0.0, b.width, b.height) }
            Motion.fadeWith(hoverFill, root.hoverProperty())
        }
        downloadingStatus.text = ""
        val clip = Rectangle()
        root.clip = clip
        root.layoutBoundsProperty().addListener { _, _, b ->
            clip.width = b.width
            clip.height = b.height
        }
        onlineCount.addListener { _, _, n -> renderOnline(n) }
        renderOnline(null)
    }

    fun bind(server: Server, state: ObservableValue<ClientState>) {
        unbind()
        this.server = server
        name.text = server.caption.uppercase(Locale.forLanguageTag(ctx.lang))
        image.setImageBackground(loadImage(server.iconUri))
        // the count is always visible now, so the old «STALZONE - 1284 онлайн» hover tooltip is gone
        val generation = onlineGeneration
        onlineWatch = ctx.services.servers.watchOnline(server) { n -> fx { if (generation == onlineGeneration) onlineCount.set(n) } }
        renderOnline(onlineCount.get())
        this.state = state
        render(state.value)
        state.addListener(stateListener)
    }

    /** The state lives in the session-scoped store, so the card must detach when its scene goes away. */
    fun unbind() {
        state?.removeListener(stateListener)
        state = null
        onlineWatch?.cancel()
        onlineWatch = null
        onlineGeneration++
    }

    fun setSelected(selected: Boolean) {
        val was = "selected" in root.styleClass
        root.toggleClass("selected", selected)
        if (selected && !was) Motion.markerIn(cursor) // the red marker grows into the newly selected row
    }

    private fun renderOnline(n: Int?) {
        val online = n != null && n >= 0
        onlineText.text = if (online) ctx.t("main.games.online", formatCount(n!!, ctx.lang)) else ctx.t("main.games.offline")
        onlineRow.toggleClass("offline", !online)
        // screen readers get the whole entry as one phrase: «STALZONE, 1 284 онлайн»
        if (::server.isInitialized) root.accessibleText = "${server.caption}, ${onlineText.text}"
    }

    private fun render(s: ClientState) {
        val fraction = when (s) {
            is ClientState.Downloading -> s.progress.fraction
            is ClientState.Paused -> s.fraction
            else -> null
        }
        progress.isVisible = fraction != null
        progress.isManaged = fraction != null
        Motion.progress(progress, fraction ?: 0.0)
        downloadingStatus.text = fraction?.let { "${(Motion.safeFraction(it) * 100).toInt()}%" } ?: ""
        liveDot.isVisible = s == ClientState.Running
        val needsFiles = s == ClientState.NotInstalled ||
            (s == ClientState.Ready && ctx.services.servers.needsClientUpdate(server))
        icon.toggleClass("icon-download", needsFiles)
    }
}

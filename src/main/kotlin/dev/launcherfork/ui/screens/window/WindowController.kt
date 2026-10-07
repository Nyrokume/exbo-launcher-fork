package dev.launcherfork.ui.screens.window

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.InputBlock
import dev.launcherfork.ui.components.WallpaperLayer
import dev.launcherfork.ui.components.toggleClass
import dev.launcherfork.ui.motion.Motion
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane

/**
 * window.fxml: the frame of the MAIN launcher window - title bar (drag area + minimize/maximize/close),
 * the content host (main screen) and the overlay layer for in-window dialogs (docs/design/WINDOWS.md).
 * Update / auth / mail code are NOT here: they live in the separate pre-main window (auth_window.fxml).
 * Behind everything: this launch's wallpaper ([WallpaperLayer.forCurrent]), once per window, so it stays the same
 * picture through logout / login and language switches (only the content host is rebuilt).
 */
class WindowController(private val ctx: UiContext) {
    @FXML lateinit var windowRoot: StackPane
    @FXML lateinit var titleBar: HBox
    @FXML lateinit var dragArea: Region
    @FXML lateinit var hideButton: Button
    @FXML lateinit var maximizeButton: Button
    @FXML lateinit var closeButton: Button
    @FXML lateinit var contentHost: StackPane
    @FXML lateinit var overlayLayer: StackPane

    /** The window background picture (bottom child of [windowRoot]); null when no wallpaper was chosen (tests). */
    var wallpaper: WallpaperLayer? = null
        private set

    @FXML
    fun initialize() {
        // loads in the background; windowRoot gets .wallpaper-on once it shows (translucent panels, layout.css)
        wallpaper = WallpaperLayer.forCurrent(windowRoot)?.also { windowRoot.children.add(0, it) }
        hideButton.setOnAction { ctx.navigator.minimize() }
        maximizeButton.setOnAction { ctx.navigator.toggleMaximize() }
        closeButton.setOnAction { ctx.navigator.close() }
        hideOverlay()
    }

    /**
     * Puts [node] into the content host. [animate] (a rebuilt screen in a shown window: language switch) crossfades
     * the old content into the new one (ui/motion/Motion.snapshotCrossfade); otherwise it is swapped at once.
     */
    fun setContent(node: Node, animate: Boolean = true) {
        if (animate) Motion.snapshotCrossfade(contentHost) { contentHost.children.setAll(node) }
        else contentHost.children.setAll(node)
    }

    /** The window appears: its content fades in over the plain window / wallpaper background (no white flash). */
    fun appear() = Motion.fadeIn(contentHost)

    /** Restyle in place (theme switch): the old look crossfades into the new one over the whole window. */
    fun crossfade(change: () -> Unit) = Motion.snapshotCrossfade(windowRoot, change = change)

    val isOverlayShown: Boolean get() = overlayLayer.isVisible

    /** The title-bar buttons: the edge-resize filter must leave clicks on them alone. */
    val windowButtons: List<Node> get() = listOf(hideButton, maximizeButton, closeButton)

    /**
     * Shows [panel] centered over the blurred, dimmed main content. The content is input-blocked for mouse and
     * keyboard ([InputBlock]: disabled, so nothing in it can take focus) and cached while blurred; focus moves
     * into the panel.
     */
    fun showOverlay(panel: Node) {
        overlayLayer.children.setAll(panel)
        Motion.fadeIn(overlayLayer)
        Motion.popIn(panel, dy = Motion.SLIDE, scale = Motion.POP_SCALE)
        contentHost.effect = Theme.overlayBackdropEffect()
        contentHost.isMouseTransparent = true
        InputBlock.set(contentHost, true)
        InputBlock.focusInto(panel)
    }

    fun hideOverlay() {
        Motion.stop(overlayLayer)
        overlayLayer.opacity = 1.0
        overlayLayer.children.clear()
        overlayLayer.isVisible = false
        contentHost.effect = null
        contentHost.isMouseTransparent = false
        InputBlock.set(contentHost, false)
    }

    fun setMaximized(maximized: Boolean) {
        maximizeButton.toggleClass("maximized", maximized)
        windowRoot.toggleClass("maximized", maximized)
    }
}

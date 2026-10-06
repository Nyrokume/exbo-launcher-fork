package dev.launcherfork.ui.screens.window

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.InputBlock
import dev.launcherfork.ui.components.toggleClass
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

    @FXML
    fun initialize() {
        hideButton.setOnAction { ctx.navigator.minimize() }
        maximizeButton.setOnAction { ctx.navigator.toggleMaximize() }
        closeButton.setOnAction { ctx.navigator.close() }
        hideOverlay()
    }

    fun setContent(node: Node) {
        contentHost.children.setAll(node)
    }

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
        overlayLayer.isVisible = true
        contentHost.effect = Theme.overlayBackdropEffect()
        contentHost.isMouseTransparent = true
        InputBlock.set(contentHost, true)
        InputBlock.focusInto(panel)
    }

    fun hideOverlay() {
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

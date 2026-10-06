package dev.launcherfork.ui.screens.window

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.toggleClass
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane

/**
 * window.fxml: the frame of the single launcher window - title bar (drag area + minimize/maximize/close),
 * the content host (main screen) and the overlay layer (update / auth panels, see docs/design/OVERLAY.md).
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

    /** Shows [panel] centered over the blurred, dimmed and input-blocked main content. */
    fun showOverlay(panel: Node) {
        overlayLayer.children.setAll(panel)
        overlayLayer.isVisible = true
        contentHost.effect = Theme.overlayBackdropEffect()
        contentHost.isMouseTransparent = true
        contentHost.isFocusTraversable = false
        panel.requestFocus()
    }

    fun hideOverlay() {
        overlayLayer.children.clear()
        overlayLayer.isVisible = false
        contentHost.effect = null
        contentHost.isMouseTransparent = false
    }

    fun setMaximized(maximized: Boolean) {
        maximizeButton.toggleClass("maximized", maximized)
        windowRoot.toggleClass("maximized", maximized)
    }
}

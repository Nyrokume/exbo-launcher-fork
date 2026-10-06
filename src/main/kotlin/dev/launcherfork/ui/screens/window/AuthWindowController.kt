package dev.launcherfork.ui.screens.window

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.InputBlock
import dev.launcherfork.ui.components.LogoMark
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.shape.Rectangle

/**
 * auth_window.fxml: frame of the separate pre-main window (update / auth / mail code, docs/design/WINDOWS.md).
 * Hosts ONE panel node in [panelHost]; the panel is loaded from its own FXML and dropped in unchanged.
 */
class AuthWindowController(private val ctx: UiContext) {
    @FXML lateinit var authWindowRoot: StackPane
    @FXML lateinit var authTitleBar: HBox
    @FXML lateinit var authLogo: LogoMark
    @FXML lateinit var authDragArea: Region
    @FXML lateinit var authHideButton: Button
    @FXML lateinit var authCloseButton: Button
    @FXML lateinit var panelHost: StackPane

    @FXML
    fun initialize() {
        authHideButton.setOnAction { ctx.navigator.minimize() }
        authCloseButton.setOnAction { ctx.navigator.close() }
        // rounded window: the panel fills the host edge to edge, so the corners are cut by a clip, not by CSS
        authWindowRoot.clip = Rectangle().apply {
            arcWidth = Theme.AUTH_WINDOW_RADIUS * 2
            arcHeight = Theme.AUTH_WINDOW_RADIUS * 2
            widthProperty().bind(authWindowRoot.widthProperty())
            heightProperty().bind(authWindowRoot.heightProperty())
        }
    }

    val panel: Node? get() = panelHost.children.firstOrNull()

    val windowButtons: List<Node> get() = listOf(authHideButton, authCloseButton)

    /**
     * Puts [panel] into the host. A StackPane resizes its child to the host size clamped by the child's own
     * min/max, so a panel without fixed max sizes fills the whole 460x588 area, a fixed-size panel is centered.
     */
    fun setPanel(panel: Parent) {
        panelHost.children.setAll(panel)
        InputBlock.focusInto(panel)
    }

    fun clear() = panelHost.children.clear()
}

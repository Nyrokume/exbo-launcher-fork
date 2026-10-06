package dev.launcherfork.ui.screens.window

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.InputBlock
import dev.launcherfork.ui.components.LogoMark
import dev.launcherfork.ui.components.PanelHost
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
    @FXML lateinit var panelHost: PanelHost

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
     * Puts [panel] into the host ([PanelHost]): a stretchable panel gets exactly the 460x588 area (like a Scene
     * root), a fixed-size panel is centered.
     */
    fun setPanel(panel: Parent) {
        panelHost.children.setAll(panel)
        // a panel with its own big logo (login, update) gets no second small one in the title strip
        authLogo.isVisible = panel.lookup(".zone-logo") == null
        InputBlock.focusInto(panel)
    }

    /**
     * Height the window should have for the current panel. A compact panel (its root leaves prefHeight computed,
     * e.g. auth_panel.fxml / mail_auth_panel.fxml) gets title strip + its content height, clamped to
     * [Theme.AUTH_WINDOW_MIN_HEIGHT]..[Theme.AUTH_WINDOW_HEIGHT]; a fixed-size panel keeps the full height.
     */
    fun fittedWindowHeight(): Double = fittedWindowHeight(panel as? Region)

    fun clear() = panelHost.children.clear()

    companion object {
        /** See [fittedWindowHeight]; [panel] must already be in a scene for its CSS sizes to count. */
        fun fittedWindowHeight(panel: Region?): Double {
            if (panel == null || panel.prefHeight != Region.USE_COMPUTED_SIZE) return Theme.AUTH_WINDOW_HEIGHT
            if (panel.scene != null) panel.applyCss()
            val content = panel.prefHeight(Theme.AUTH_PANEL_WIDTH)
            return (Theme.AUTH_TITLEBAR_HEIGHT + content).coerceIn(Theme.AUTH_WINDOW_MIN_HEIGHT, Theme.AUTH_WINDOW_HEIGHT)
        }
    }
}

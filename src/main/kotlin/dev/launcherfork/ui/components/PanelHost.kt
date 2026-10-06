package dev.launcherfork.ui.components

import javafx.geometry.HPos
import javafx.geometry.VPos
import javafx.scene.layout.StackPane
import javafx.scene.shape.Rectangle

/**
 * Host of the panel in the pre-main window (auth_window.fxml, docs/design/WINDOWS.md). It sizes the panel the way
 * a Scene sizes its root:
 *  - a stretchable panel (max size >= host, e.g. auth_panel.fxml) gets EXACTLY the host size, even if its content
 *    asks for a slightly larger minimum, so it never pushes into the title strip;
 *  - a fixed-size panel (legacy 379x490 FXMLs, max = pref) keeps its size and is centered.
 * The host clips its content and reports a zero minimum, so a panel can never grow the window.
 */
class PanelHost : StackPane() {
    init {
        clip = Rectangle().also {
            it.widthProperty().bind(widthProperty())
            it.heightProperty().bind(heightProperty())
        }
    }

    override fun computeMinWidth(height: Double) = 0.0
    override fun computeMinHeight(width: Double) = 0.0

    override fun layoutChildren() {
        val w = width - snappedLeftInset() - snappedRightInset()
        val h = height - snappedTopInset() - snappedBottomInset()
        for (child in children.filter { it.isManaged }) {
            val cw = if (child.maxWidth(-1.0) >= w) w else child.prefWidth(-1.0).coerceIn(child.minWidth(-1.0), child.maxWidth(-1.0))
            val ch = if (child.maxHeight(-1.0) >= h) h else child.prefHeight(cw).coerceIn(child.minHeight(cw), child.maxHeight(cw))
            child.resize(snapSizeX(cw), snapSizeY(ch))
            positionInArea(child, snappedLeftInset(), snappedTopInset(), w, h, 0.0, HPos.CENTER, VPos.CENTER)
        }
    }
}

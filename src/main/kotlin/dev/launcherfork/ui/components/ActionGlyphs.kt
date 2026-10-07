package dev.launcherfork.ui.components

import dev.launcherfork.ui.state.GameAction
import javafx.scene.Group
import javafx.scene.layout.StackPane
import javafx.scene.shape.SVGPath

/**
 * Line icons of the game-actions menu, path data from Lucide 0.544.0 (https://lucide.dev,
 * https://cdn.jsdelivr.net/npm/lucide-static@0.544.0/icons/<name>.svg), licence ISC. 24x24 grid, drawn as strokes
 * (.ga-glyph: no fill, 2 px round stroke in the 24 grid). Subpaths of one icon are joined verbatim.
 */
object ActionGlyphs {
    const val SHIELD_CHECK = "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" +
        "M9 12l2 2 4-4"
    const val FOLDER_OPEN = "M6 14l1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2"
    /** Lucide file-text — log / notes folder row. */
    const val FILE_TEXT = "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z" +
        "M14 2v4a2 2 0 0 0 2 2h4M10 9H8M16 13H8M16 17H8"
    const val SLIDERS = "M10 5H3M12 19H3M14 3v4M16 17v4M21 12h-9M21 19h-5M21 5h-7M8 10v4M8 12H3"
    const val ROTATE_CCW = "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8M3 3v5h5"
    const val TRASH = "M10 11v6M14 11v6M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"

    fun path(action: GameAction): String = when (action) {
        GameAction.VERIFY -> SHIELD_CHECK
        GameAction.OPEN_FOLDER -> FOLDER_OPEN
        GameAction.OPEN_LOGS -> FILE_TEXT
        GameAction.SETTINGS -> SLIDERS
        GameAction.REINSTALL -> ROTATE_CCW
        GameAction.DELETE -> TRASH
    }

    /** [size] px square (CSS .ga-icon) with the 24-grid glyph scaled to it, centred. */
    fun icon(action: GameAction, size: Double = 16.0): StackPane {
        val glyph = SVGPath().apply {
            content = path(action)
            styleClass += listOf("ga-glyph", action.iconClass)
            scaleX = size / 24
            scaleY = size / 24
        }
        return StackPane(Group(glyph)).apply {
            styleClass += "ga-icon"
            setMinSize(size, size); setPrefSize(size, size); setMaxSize(size, size)
            isMouseTransparent = true
        }
    }
}

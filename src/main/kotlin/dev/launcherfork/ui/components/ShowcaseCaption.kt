package dev.launcherfork.ui.components

import dev.launcherfork.ui.motion.Motion
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane

/**
 * Showcase step caption: a small chip (accent dot + «Главное окно») in a window's title strip, NOT in the nav row.
 * It takes no clicks and no focus; colours come from the theme (layout.css «showcase»). A new text fades in
 * (ui/motion/Motion).
 */
class ShowcaseCaption : HBox(6.0) {
    val label = Label().apply { styleClass += "showcase-caption-text" }

    init {
        styleClass += "showcase-caption"
        id = "showcaseCaption"
        alignment = Pos.CENTER_LEFT
        isMouseTransparent = true
        isFocusTraversable = false
        maxWidth = Region.USE_PREF_SIZE
        maxHeight = Region.USE_PREF_SIZE
        children += Region().apply { styleClass += "showcase-caption-dot"; minWidth = 6.0; prefWidth = 6.0; maxWidth = 6.0; minHeight = 6.0; prefHeight = 6.0; maxHeight = 6.0 }
        children += label
    }

    var text: String
        get() = label.text
        set(value) {
            if (value == label.text) return
            label.text = value
            Motion.fadeIn(this, Motion.NORMAL)
        }

    /** Puts the chip into [root] (a window's root StackPane) at [pos] with [margin], above everything. */
    fun placeIn(root: StackPane, pos: Pos, margin: Insets): ShowcaseCaption {
        StackPane.setAlignment(this, pos)
        StackPane.setMargin(this, margin)
        root.children += this
        return this
    }
}

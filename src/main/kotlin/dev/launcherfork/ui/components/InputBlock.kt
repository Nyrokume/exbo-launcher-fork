package dev.launcherfork.ui.components

import javafx.scene.CacheHint
import javafx.scene.Node
import javafx.scene.Parent

/**
 * Blocks mouse AND keyboard input of a content node while a dialog / overlay covers it: the node is disabled
 * (disabled nodes can neither be clicked nor take focus, so Tab and Enter cannot reach them), cached as a bitmap
 * (the blur over it is not recomputed every frame) and marked with [BLOCKED_CLASS], which cancels the 0.4 opacity
 * that modena gives to disabled controls (layout.css), so the content keeps its look under the dialog.
 */
object InputBlock {
    const val BLOCKED_CLASS = "input-blocked"

    fun set(content: Node, blocked: Boolean) {
        content.isDisable = blocked
        content.cacheHint = if (blocked) CacheHint.SPEED else CacheHint.DEFAULT
        content.isCache = blocked
        content.toggleClass(BLOCKED_CLASS, blocked)
    }

    /** First visible, enabled, focus-traversable node in [root] (depth first), or null. */
    fun firstFocusable(root: Node): Node? {
        if (!root.isVisible || root.isDisabled) return null
        if (root.isFocusTraversable) return root
        if (root is Parent) root.childrenUnmodifiable.forEach { child -> firstFocusable(child)?.let { return it } }
        return null
    }

    /** Moves keyboard focus into [root]: its first focusable node, else [root] itself. */
    fun focusInto(root: Node) = (firstFocusable(root) ?: root).requestFocus()
}

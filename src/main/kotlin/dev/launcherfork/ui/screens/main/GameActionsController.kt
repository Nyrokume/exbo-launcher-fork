package dev.launcherfork.ui.screens.main

import dev.launcherfork.ui.Disposable
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.motion.Motion
import dev.launcherfork.ui.components.ActionGlyphs
import dev.launcherfork.ui.state.ClientState
import dev.launcherfork.ui.state.GameAction
import dev.launcherfork.ui.state.GameActions
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

/**
 * main/game_actions.fxml: dropdown of the game-actions button next to ИГРАТЬ. Rows follow [GameAction] (icon + text;
 * reinstall / delete red under a rule); a row is disabled when it makes no sense in the client state ([GameActions]).
 * A pick closes the menu and fires [onAction] (MainController asks for confirmation where needed). The menu is a
 * floating card: StatusPaneController places it; a press outside, Esc (MainController) or a pick close it.
 * Keyboard: Up / Down / Tab move through enabled rows (wrapping), Home / End, Enter / Space pick.
 */
class GameActionsController(private val ctx: UiContext) : Disposable {
    @FXML lateinit var gameActionsMenu: VBox
    @FXML lateinit var gameActionsCaption: Label

    var onAction: (GameAction) -> Unit = {}
    /** Called after the menu opened (StatusPaneController places it, MainController closes other menus). */
    var onShown: () -> Unit = {}
    /** The toggle button: presses on it are not "outside" (it toggles by itself). */
    var anchor: Node? = null

    private val rows = LinkedHashMap<GameAction, Button>()
    /** Menu rows in [GameAction] order. */
    val items: Map<GameAction, Button> get() = rows
    private var scene: Scene? = null
    private val outsideClick = EventHandler<MouseEvent> { e -> if (isShown && !isInside(e.target as? Node)) hide() }

    @FXML
    fun initialize() {
        val rows = GameAction.entries.map { action ->
            Button(ctx.t(action.textKey), ActionGlyphs.icon(action)).apply {
                id = "gameAction-${action.name.lowercase()}"
                styleClass += listOf("profile-menu-item", "game-action-item", "zone-radius-sm")
                if (action.destructive) styleClass += "danger"
                isMnemonicParsing = false
                maxWidth = Double.MAX_VALUE
                userData = action
                setOnAction { pick(action) }
            }.also { this.rows[action] = it }
        }
        val firstDestructive = rows.indexOfFirst { (it.userData as GameAction).destructive }
        val children = mutableListOf<Node>(gameActionsCaption)
        rows.forEachIndexed { i, b ->
            if (i == firstDestructive) children += Region().apply { styleClass += "profile-menu-rule" }
            children += b
        }
        gameActionsMenu.children.setAll(children)
        gameActionsMenu.addEventFilter(KeyEvent.KEY_PRESSED, ::onKey)
        gameActionsMenu.sceneProperty().addListener { _, old, new ->
            old?.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick)
            new?.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick)
            scene = new
        }
    }

    fun setGame(caption: String) {
        gameActionsCaption.text = caption.uppercase()
    }

    /** Enables / disables rows for [state]. */
    fun update(state: ClientState) {
        items.forEach { (action, b) -> b.isDisable = !GameActions.isEnabled(action, state) }
    }

    /** Logical state (the card may still be fading out for a moment after [hide]). */
    var isShown: Boolean = false
        private set

    fun show(focusFirst: Boolean = false) {
        if (!isShown) {
            isShown = true
            // the card opens ABOVE its button: it rises 6 px towards it while fading in
            Motion.popIn(gameActionsMenu, dy = Motion.MENU_SLIDE)
            onShown()
        }
        if (focusFirst) enabledItems().firstOrNull()?.requestFocus()
    }

    fun hide() {
        if (!isShown) return
        val focusInMenu = generateSequence(scene?.focusOwner) { it.parent }.any { it === gameActionsMenu }
        isShown = false
        Motion.popOut(gameActionsMenu, dy = Motion.MENU_SLIDE / 2)
        if (focusInMenu) anchor?.requestFocus()
    }

    fun toggle() = if (isShown) hide() else show()

    private fun pick(action: GameAction) {
        hide()
        onAction(action)
    }

    private fun enabledItems() = items.values.filter { !it.isDisable }

    private fun onKey(e: KeyEvent) {
        val list = enabledItems()
        if (list.isEmpty()) return
        val current = list.indexOfFirst { it.isFocused }
        fun focus(i: Int) = list[(i + list.size) % list.size].requestFocus()
        when (e.code) {
            KeyCode.DOWN -> focus(current + 1)
            KeyCode.UP -> focus(if (current < 0) list.size - 1 else current - 1)
            KeyCode.TAB -> if (e.isShiftDown) focus(if (current < 0) list.size - 1 else current - 1) else focus(current + 1)
            KeyCode.HOME -> focus(0)
            KeyCode.END -> focus(list.size - 1)
            KeyCode.ENTER -> list.getOrNull(current)?.fire() ?: return
            else -> return
        }
        e.consume()
    }

    private fun isInside(node: Node?): Boolean =
        generateSequence(node) { it.parent }.any { it === gameActionsMenu || it === anchor }

    override fun dispose() {
        scene?.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick)
    }
}

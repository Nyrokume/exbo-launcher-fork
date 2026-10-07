package dev.launcherfork.ui.screens.main

import dev.launcherfork.ui.Disposable
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.toggleClass
import dev.launcherfork.ui.motion.Motion
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import java.util.Locale

/**
 * main/profile.fxml: nickname + caret in the top-right corner of the main window and its dropdown card.
 * The dropdown closes on a pick, on a click anywhere else and on ESC (MainController).
 *
 * The card is a floating layer: it stays an UNMANAGED child of [profileRoot] (never counted in the top bar's
 * layout, so opening it cannot widen the row and push Forum / Support to the left) and is placed by hand right
 * under the nickname button, right edges aligned ([placeMenu]).
 */
class ProfileController(private val ctx: UiContext) : Disposable {
    @FXML lateinit var profileRoot: VBox
    @FXML lateinit var loginButton: Button
    @FXML lateinit var accountName: Label
    @FXML lateinit var profileCaret: Region
    @FXML lateinit var profileMenu: VBox
    @FXML lateinit var profile: Button
    @FXML lateinit var switchAccount: Button
    @FXML lateinit var logout: Button

    var onProfile: () -> Unit = {}
    var onSwitchAccount: () -> Unit = {}
    var onLogout: () -> Unit = {}
    /** Fired after the dropdown opened (MainController closes the communities menu: one menu at a time). */
    var onMenuShown: () -> Unit = {}

    private var scene: Scene? = null
    private val outsideClick = EventHandler<MouseEvent> { e ->
        if (isMenuShown && !isInside(e.target as? Node)) hideMenu()
    }

    @FXML
    fun initialize() {
        setLogin(ctx.services.auth.currentSession?.login)
        loginButton.setOnAction { if (isMenuShown) hideMenu() else showMenu() }
        profile.setOnAction { hideMenu(); onProfile() }
        switchAccount.setOnAction { hideMenu(); onSwitchAccount() }
        logout.setOnAction { hideMenu(); onLogout() }
        profileRoot.sceneProperty().addListener { _, old, new ->
            old?.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick)
            new?.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick)
            scene = new
        }
        // follow the button (window resize, nickname change) and the card's own size (theme / language switch)
        loginButton.boundsInParentProperty().addListener { _, _, _ -> placeMenu() }
        profileMenu.layoutBoundsProperty().addListener { _, _, _ -> placeMenu() }
    }

    // UPPERCASE nickname (.zone-caps) - to be swapped to SpacedLabel after the redesign/auth merge
    fun setLogin(login: String?) {
        accountName.text = (login ?: "player").uppercase(Locale.forLanguageTag(ctx.lang))
    }

    /**
     * Logical state: true from [showMenu] to [hideMenu]. The card itself may still be fading out for a moment after
     * [hideMenu] (ui/motion/Motion.popOut), so callers ask this, not the card's visibility.
     */
    var isMenuShown: Boolean = false
        private set

    fun showMenu() = setMenu(true)
    fun hideMenu() = setMenu(false)

    private fun setMenu(shown: Boolean) {
        val wasShown = isMenuShown
        isMenuShown = shown
        loginButton.toggleClass("open", shown)
        if (shown == wasShown) {
            if (shown) placeMenu()
            return
        }
        if (shown) {
            // drops 6 px from the button while fading in; closes with a quicker fade (Motion tokens)
            Motion.popIn(profileMenu, dy = -Motion.MENU_SLIDE)
            placeMenu()
            onMenuShown()
        } else {
            Motion.popOut(profileMenu)
        }
    }

    /** Pref-size the unmanaged card and hang it under the button, right-aligned with it (the parent never lays it out). */
    private fun placeMenu() {
        if (profileMenu.scene != null) profileMenu.applyCss()
        profileMenu.autosize()
        val button = loginButton.boundsInParent
        profileMenu.relocate(button.maxX - profileMenu.width, button.maxY)
    }

    private fun isInside(node: Node?): Boolean {
        var n = node
        while (n != null) {
            if (n === profileRoot) return true
            n = n.parent
        }
        return false
    }

    override fun dispose() {
        scene?.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick)
    }
}

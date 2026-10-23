package dev.launcherfork.ui.screens.main

import dev.launcherfork.ui.Disposable
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.toggleClass
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import java.util.Locale

/**
 * main/profile.fxml: nickname + caret and the dropdown (stalzone.com ЛК menu).
 * Donate card («Баланс» only while signed in, no amount), invite, personal account, logout.
 */
class ProfileController(private val ctx: UiContext) : Disposable {
    @FXML lateinit var profileRoot: VBox
    @FXML lateinit var loginButton: Button
    @FXML lateinit var accountName: Label
    @FXML lateinit var profileCaret: Region
    @FXML lateinit var profileMenu: VBox
    @FXML lateinit var balanceRow: HBox
    @FXML lateinit var balanceLabel: Label
    @FXML lateinit var topUp: Button
    @FXML lateinit var inviteFriend: Button
    @FXML lateinit var profile: Button
    @FXML lateinit var toolsAccounts: Button
    @FXML lateinit var logout: Button

    var onTopUp: () -> Unit = {}
    var onInviteFriend: () -> Unit = {}
    var onProfile: () -> Unit = {}
    var onAccounts: () -> Unit = {}
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
        topUp.setOnAction { hideMenu(); onTopUp() }
        inviteFriend.setOnAction { hideMenu(); onInviteFriend() }
        profile.setOnAction { hideMenu(); onProfile() }
        toolsAccounts.setOnAction { hideMenu(); onAccounts() }
        logout.setOnAction { hideMenu(); onLogout() }
        profileRoot.sceneProperty().addListener { _, old, new ->
            old?.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick)
            new?.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick)
            scene = new
        }
        loginButton.boundsInParentProperty().addListener { _, _, _ -> placeMenu() }
        profileMenu.layoutBoundsProperty().addListener { _, _, _ -> placeMenu() }
    }

    fun setLogin(login: String?) {
        accountName.text = (login ?: "player").uppercase(Locale.forLanguageTag(ctx.lang))
        val signedIn = !login.isNullOrBlank()
        balanceRow.isVisible = signedIn
        balanceRow.isManaged = signedIn
    }

    val isMenuShown: Boolean get() = profileMenu.isVisible

    fun showMenu() = setMenu(true)
    fun hideMenu() = setMenu(false)

    private fun setMenu(shown: Boolean) {
        val wasShown = isMenuShown
        loginButton.toggleClass("open", shown)
        profileMenu.isVisible = shown
        if (shown) placeMenu()
        if (shown && !wasShown) onMenuShown()
    }

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

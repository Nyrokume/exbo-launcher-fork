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
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import java.util.Locale

/**
 * main/profile.fxml: nickname + caret in the top-right corner of the main window and its dropdown card.
 * The dropdown closes on a pick, on a click anywhere else and on ESC (MainController).
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
    }

    // UPPERCASE nickname (.zone-caps) - to be swapped to SpacedLabel after the redesign/auth merge
    fun setLogin(login: String?) {
        accountName.text = (login ?: "player").uppercase(Locale.forLanguageTag(ctx.lang))
    }

    val isMenuShown: Boolean get() = profileMenu.isVisible

    fun showMenu() = setMenu(true)
    fun hideMenu() = setMenu(false)

    private fun setMenu(shown: Boolean) {
        profileMenu.isVisible = shown
        profileMenu.isManaged = shown
        loginButton.toggleClass("open", shown)
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

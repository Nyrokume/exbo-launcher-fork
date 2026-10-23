package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.CommunityLink
import dev.launcherfork.domain.CommunityLinks
import dev.launcherfork.ui.Disposable
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.BrandGlyphs
import dev.launcherfork.ui.components.toggleClass
import dev.launcherfork.ui.motion.Motion
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * main/header.fxml: СООБЩЕСТВА ▾ / ФОРУМ ↗ / ПОДДЕРЖКА ↗ in one row left of the profile button (main.fxml top bar).
 * Forum / Support have no browser in the fork ([onLink] -> "not available"); Profile is in the profile menu.
 *
 * СООБЩЕСТВА opens a dropdown with the STALZONE social links ([CommunityLinks], in that order). Like the profile
 * dropdown it is a floating layer: an UNMANAGED child of [headerPane], placed by hand under the link, left edges
 * aligned and kept inside the window ([placeMenu]), so opening it never moves the row. A pick opens the URL through
 * [dev.launcherfork.domain.LinkOpener] (HostServices in the app) and closes the menu; a press anywhere else and ESC
 * (MainController) close it too. Opening it closes the profile menu and vice versa (MainController via [onMenuShown]).
 *
 * Keyboard: the link is a focusable button, Enter / Space / Down open the menu with the first row focused; in the menu
 * Up / Down and Tab / Shift+Tab move through the rows (wrapping), Home / End jump, Enter / Space open the link.
 */
class HeaderController(private val ctx: UiContext) : Disposable {
    @FXML lateinit var headerPane: HBox
    @FXML lateinit var linksContainer: HBox
    @FXML lateinit var settingsNav: HBox
    @FXML lateinit var navJvm: Button
    @FXML lateinit var navTunnel: Button
    @FXML lateinit var navSens: Button
    @FXML lateinit var navAccounts: Button
    @FXML lateinit var communities: Button
    @FXML lateinit var communitiesCaret: Region
    @FXML lateinit var communityMenu: VBox
    @FXML lateinit var support: Label
    @FXML lateinit var forum: Label

    /** Fired with the link id ("forum", "support"); MainController shows "not available". */
    var onLink: (String) -> Unit = {}
    /** Fired after the communities menu opened (MainController closes the profile menu). */
    var onMenuShown: () -> Unit = {}
    /** Fired with a separate settings page id (`jvm`, `tunnel`, `sens`, `accounts`). */
    var onSettingsSection: (String) -> Unit = {}

    /** Menu rows in [CommunityLinks.all] order; each row's userData is its [CommunityLink]. */
    val communityItems: List<Button> get() = communityMenu.children.filterIsInstance<Button>()

    private var scene: Scene? = null
    private val outsideClick = EventHandler<MouseEvent> { e ->
        if (isCommunityMenuShown && !isInside(e.target as? Node)) hideCommunityMenu()
    }

    @FXML
    fun initialize() {
        mapOf(forum to "forum", support to "support").forEach { (label, id) ->
            label.setOnMouseClicked { onLink(id) }
        }
        sectionButtons.forEach { (id, button) -> button.setOnAction { onSettingsSection(id) } }

        communityMenu.children.setAll(CommunityLinks.all.map(::menuItem))
        communities.setOnAction { if (isCommunityMenuShown) hideCommunityMenu() else showCommunityMenu() }
        // Button only fires on Space by itself: Enter / Space / Down open the menu and focus its first row
        communities.addEventFilter(KeyEvent.KEY_PRESSED) { e ->
            when (e.code) {
                KeyCode.ENTER, KeyCode.SPACE -> if (isCommunityMenuShown) hideCommunityMenu() else showCommunityMenu(focusFirst = true)
                KeyCode.DOWN -> showCommunityMenu(focusFirst = true)
                else -> return@addEventFilter
            }
            e.consume()
        }
        communityMenu.addEventFilter(KeyEvent.KEY_PRESSED, ::onMenuKey)

        headerPane.sceneProperty().addListener { _, old, new ->
            old?.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick)
            new?.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick)
            scene = new
        }
        // if the links are ever hidden, the menu goes with them
        headerPane.visibleProperty().addListener { _, _, shown -> if (!shown) hideCommunityMenu() }
        // follow the link (row width, language / theme switch, window resize) and the card's own size
        communities.boundsInParentProperty().addListener { _, _, _ -> placeMenu() }
        linksContainer.boundsInParentProperty().addListener { _, _, _ -> placeMenu() }
        headerPane.localToSceneTransformProperty().addListener { _, _, _ -> placeMenu() }
        communityMenu.layoutBoundsProperty().addListener { _, _, _ -> placeMenu() }
    }

    /** Logical state (the card may still be fading out for a moment after [hideCommunityMenu]). */
    var isCommunityMenuShown: Boolean = false
        private set

    /**
     * Settings mode swaps the social links for JVM / tunnel / sensitivity / account swap. [section] gets the
     * accent (`selected`); the settings column (`ram` and the blocks in it) leaves every button unselected.
     */
    fun setSettingsNav(shown: Boolean, section: String) {
        linksContainer.isVisible = !shown
        linksContainer.isManaged = !shown
        settingsNav.isVisible = shown
        settingsNav.isManaged = shown
        if (shown) hideCommunityMenu()
        selectSection(section)
    }

    fun selectSection(section: String) {
        sectionButtons.forEach { (id, button) -> button.toggleClass("selected", id == section) }
    }

    private val sectionButtons: Map<String, Button>
        get() = linkedMapOf(
            "jvm" to navJvm,
            "tunnel" to navTunnel,
            "sens" to navSens,
            "accounts" to navAccounts,
        )

    fun showCommunityMenu(focusFirst: Boolean = false) {
        if (!isCommunityMenuShown) {
            isCommunityMenuShown = true
            communities.toggleClass("open", true)
            Motion.popIn(communityMenu, dy = -Motion.MENU_SLIDE) // drops 6 px from the link while fading in
            placeMenu()
            onMenuShown()
        }
        if (focusFirst) communityItems.firstOrNull()?.requestFocus()
    }

    fun hideCommunityMenu() {
        if (!isCommunityMenuShown) return
        // keyboard users land back on the link instead of on a hidden row
        val focusInMenu = generateSequence(scene?.focusOwner) { it.parent }.any { it === communityMenu }
        isCommunityMenuShown = false
        communities.toggleClass("open", false)
        Motion.popOut(communityMenu)
        if (focusInMenu) communities.requestFocus()
    }

    /** Row: icon tile (brand glyph), brand name, ↗; same item style / hover as the profile menu. */
    private fun menuItem(link: CommunityLink): Button {
        val name = Label(link.title).apply { styleClass += "community-item-name" }
        val arrow = Region().apply { styleClass += "external-arrow" }
        val row = HBox(BrandGlyphs.tile(link.id, GLYPH_SIZE), name, arrow).apply {
            styleClass += "community-item-row"
            alignment = Pos.CENTER_LEFT
            isMouseTransparent = true
        }
        HBox.setMargin(arrow, Insets(0.0, 0.0, 0.0, -2.0)) // name ↔ ↗ like "Профиль ↗" (8 px), tile ↔ name 10 px
        return Button().apply {
            id = "community-${link.id}"
            styleClass += listOf("profile-menu-item", "community-menu-item", "zone-radius-sm")
            graphic = row
            contentDisplay = ContentDisplay.GRAPHIC_ONLY
            isMnemonicParsing = false
            maxWidth = Double.MAX_VALUE
            userData = link
            accessibleText = ctx.t("main.community.item.a11y", link.accessibleName)
            setOnAction { open(link) }
        }
    }

    private fun open(link: CommunityLink) {
        hideCommunityMenu()
        ctx.services.links.open(link.url)
    }

    private fun onMenuKey(e: KeyEvent) {
        val items = communityItems
        if (items.isEmpty()) return
        val current = items.indexOfFirst { it.isFocused }
        fun focus(i: Int) = items[(i + items.size) % items.size].requestFocus()
        when (e.code) {
            KeyCode.DOWN -> focus(current + 1)
            KeyCode.UP -> focus(if (current < 0) items.size - 1 else current - 1)
            KeyCode.TAB -> if (e.isShiftDown) focus(if (current < 0) items.size - 1 else current - 1) else focus(current + 1)
            KeyCode.HOME -> focus(0)
            KeyCode.END -> focus(items.size - 1)
            KeyCode.ENTER -> items.getOrNull(current)?.fire() ?: return
            else -> return
        }
        e.consume()
    }

    /**
     * Pref-size the unmanaged card and hang it under the header row (top edge level with the profile dropdown's),
     * its left edge on the link's left edge, shifted left if needed so it stays [WINDOW_MARGIN] px inside the window.
     */
    private fun placeMenu() {
        if (!communityMenu.isVisible) return
        if (communityMenu.scene != null) communityMenu.applyCss()
        communityMenu.autosize()
        val link = headerPane.sceneToLocal(communities.localToScene(communities.layoutBounds)) ?: return
        var x = link.minX
        val sceneWidth = headerPane.scene?.width ?: 0.0
        if (sceneWidth > 0) {
            val left = headerPane.localToScene(x, 0.0).x
            val right = left + communityMenu.width
            val shift = min(0.0, sceneWidth - WINDOW_MARGIN - right) + max(0.0, WINDOW_MARGIN - left)
            x += shift
        }
        communityMenu.relocate(round(x), round(headerPane.height))
    }

    private fun isInside(node: Node?): Boolean {
        var n = node
        while (n != null) {
            if (n === communities || n === communityMenu) return true
            n = n.parent
        }
        return false
    }

    override fun dispose() {
        scene?.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick)
    }

    private companion object {
        const val GLYPH_SIZE = 14.0
        const val WINDOW_MARGIN = 8.0
    }
}

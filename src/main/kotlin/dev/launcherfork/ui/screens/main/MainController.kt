package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.ExternalLinks
import dev.launcherfork.domain.ClientOperation
import dev.launcherfork.domain.GameFolders
import dev.launcherfork.domain.Server
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Disposable
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.InputBlock
import dev.launcherfork.ui.components.formatBytes
import dev.launcherfork.ui.components.loadImage
import dev.launcherfork.ui.components.setImageBackground
import dev.launcherfork.ui.components.toggleClass
import dev.launcherfork.ui.motion.Motion
import dev.launcherfork.ui.screens.main.modals.DefenderModalController
import dev.launcherfork.ui.screens.main.modals.DeletionModalController
import dev.launcherfork.ui.screens.main.modals.DownloadWarnModalController
import dev.launcherfork.ui.screens.main.modals.ExceptionModalController
import dev.launcherfork.ui.screens.main.modals.InstallModalController
import dev.launcherfork.ui.screens.main.modals.LicenseModalController
import dev.launcherfork.ui.screens.main.modals.LoadingModalController
import dev.launcherfork.ui.screens.main.modals.LogoutModalController
import dev.launcherfork.ui.screens.main.modals.ModalController
import dev.launcherfork.ui.screens.main.modals.OfflineModalController
import dev.launcherfork.ui.screens.main.modals.UpdateModalController
import dev.launcherfork.ui.screens.settings.SettingsController
import dev.launcherfork.ui.state.ClientState
import dev.launcherfork.ui.state.ClientStateStore
import dev.launcherfork.ui.state.GameAction
import dev.launcherfork.ui.state.GameActions
import javafx.beans.value.ChangeListener
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane

/**
 * main.fxml: coordination between the included areas (sidebar, links, profile, status bar, settings, modals).
 * Each area has its own FXML and controller. Layout is adaptive (BorderPane + anchors). [centerPane] holds the
 * selected game's banner text ([GameBannerController]); its top follows the selected game's row in the sidebar
 * ([alignBanner]), so the description starts on the line of its menu item.
 */
class MainController(private val ctx: UiContext) : Disposable {
    @FXML lateinit var root: StackPane
    @FXML lateinit var layout: BorderPane
    @FXML lateinit var main: StackPane
    @FXML lateinit var background: Region
    @FXML lateinit var backgroundShade: Region
    @FXML lateinit var homePane: AnchorPane
    @FXML lateinit var centerPane: StackPane
    @FXML lateinit var settingsPane: AnchorPane
    /** The nav row (communities / forum / support + profile): nothing else is placed on its level. */
    @FXML lateinit var topBar: javafx.scene.layout.HBox
    @FXML lateinit var profileLayer: AnchorPane

    // included areas: FXMLLoader injects <fx:id>Controller
    @FXML lateinit var serversListController: ServersListController
    @FXML lateinit var profileBarController: ProfileController
    @FXML lateinit var headerController: HeaderController
    @FXML lateinit var defaultStatusPaneController: StatusPaneController
    @FXML lateinit var gameBannerController: GameBannerController
    @FXML lateinit var settingsController: SettingsController
    @FXML lateinit var logoutPaneController: LogoutModalController
    @FXML lateinit var updatePaneController: UpdateModalController
    @FXML lateinit var licensePaneController: LicenseModalController
    @FXML lateinit var offlinePaneController: OfflineModalController
    @FXML lateinit var downloadWarnPaneController: DownloadWarnModalController
    @FXML lateinit var defenderPaneController: DefenderModalController
    @FXML lateinit var exceptionPaneController: ExceptionModalController
    @FXML lateinit var loadingPaneController: LoadingModalController
    @FXML lateinit var deletionPaneController: DeletionModalController
    @FXML lateinit var installPaneController: InstallModalController

    /** Session-scoped (AppContext); this controller only observes it. */
    val store: ClientStateStore get() = ctx.clientStates
    var selected: Server? = null
        private set
    var servers: List<Server> = emptyList()
        private set

    @FXML
    fun initialize() {
        background.effect = Theme.gameArtEffect()

        // profile menu (top-right): account / referral open in the browser; logout confirms then leaveToAuth
        profileBarController.onProfile = { ctx.services.links.open(ExternalLinks.ACCOUNT) }
        profileBarController.onInviteFriend = { ctx.services.links.open(ExternalLinks.REFERRAL) }
        // the ONLY way to the logout confirmation: profile menu "Выйти из аккаунта" (window close never asks)
        profileBarController.onLogout = { logoutPaneController.show() }
        logoutPaneController.onConfirm = ::leaveToAuth
        updatePaneController.onConfirm = { ctx.navigator.showLauncherUpdate(afterUpdate = ctx.navigator::showMain) }

        serversListController.onServerSelected = { server -> selectServer(server, showHome = true) }
        serversListController.onSettingsClicked = { setSettingsVisible(!isSettingsShown) }

        val status = defaultStatusPaneController
        status.onPrimaryAction = ::onPrimaryAction
        // game-actions menu next to ИГРАТЬ: one dropdown at a time with the top-bar menus
        val actions = status.gameActionsController
        actions.onShown = { profileBarController.hideMenu(); headerController.hideCommunityMenu() }
        actions.onAction = ::onGameAction

        headerController.onLink = { notAvailable() }
        // one dropdown at a time: opening the communities menu closes the profile menu and vice versa
        headerController.onMenuShown = { profileBarController.hideMenu(); actions.hide() }
        profileBarController.onMenuShown = { headerController.hideCommunityMenu(); actions.hide() }
        // the top bar (links + profile) is the same nav row on every screen: it stays on the settings screen too, and
        // nothing else (page titles, content) is placed on its level (settings start below it, see main.fxml)
        // same height as the profile button, so the links stay centred on it (the dropdown floats, see ProfileController)
        val buttonHeight = profileBarController.loginButton.heightProperty()
        headerController.headerPane.minHeightProperty().bind(buttonHeight)
        headerController.headerPane.maxHeightProperty().bind(buttonHeight)


        // the banner starts on the line of its game in the sidebar: re-placed whenever the row (followRow), the content
        // area or the title moves, and after every layout of the title (a new font moves its text line inside the label
        // only then); never higher than the FXML top (under the nav row)
        bannerMinTop = AnchorPane.getTopAnchor(centerPane) ?: 0.0
        homePane.localToSceneTransformProperty().addListener(alignOnChange)
        gameBannerController.title.localToSceneTransformProperty().addListener(alignOnChange)
        gameBannerController.title.needsLayoutProperty().addListener(alignOnChange)

        servers = ctx.services.servers.servers()
        serversListController.setServers(servers, store)
        servers.firstOrNull()?.let {
            serversListController.select(it, notify = false)
            selectServer(it, showHome = false)
        }

        // a modal layer blocks mouse AND keyboard of the screen under it (disabled + cached), focus goes into it
        modals.forEach { m -> m.layer.visibleProperty().addListener { _, _, _ -> onModalVisibility(m) } }

        root.sceneProperty().addListener { _, old, scene ->
            old?.removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter)
            scene?.addEventFilter(KeyEvent.KEY_PRESSED, keyFilter)
        }
    }

    private val keyFilter = javafx.event.EventHandler<KeyEvent> { onKey(it) }

    // ChangeListener, not InvalidationListener: the transforms are revalidated on every change, so none is missed
    private val alignOnChange = ChangeListener<Any?> { _, _, _ -> alignBanner() }
    private var bannerMinTop = 0.0
    private var alignedRow: Node? = null

    /** Lines the banner up with [row] (the selected game's row) from now on. */
    private fun followRow(row: Node?) {
        if (row !== alignedRow) {
            alignedRow?.localToSceneTransformProperty()?.removeListener(alignOnChange)
            alignedRow = row
            row?.localToSceneTransformProperty()?.addListener(alignOnChange)
        }
        alignBanner()
    }

    /**
     * Puts the centre slot so that the top of the banner title's letters is on the top edge of the selected game's
     * row (= its red marker): the description visually starts on the line of its menu item. Derived from the live
     * layout (row position in the scene, title font metrics), so it holds in every theme and window size and follows
     * a changed logo / sidebar spacing; no offset constant. Idempotent: only a real difference moves the slot.
     */
    private fun alignBanner() {
        val row = alignedRow ?: return
        if (row.scene == null || row.scene !== homePane.scene) return
        val inkOffset = gameBannerController.titleInkOffset() ?: return
        val rowTop = homePane.sceneToLocal(row.localToScene(0.0, 0.0)).y
        val bannerTopInSlot = centerPane.sceneToLocal(gameBannerController.scroll.localToScene(0.0, 0.0)).y
        val top = maxOf(bannerMinTop, rowTop - bannerTopInSlot - inkOffset)
        val current = AnchorPane.getTopAnchor(centerPane)
        if (current == null || Math.abs(top - current) > 0.1) AnchorPane.setTopAnchor(centerPane, top)
    }

    private val modals: List<ModalController> by lazy {
        listOf(
            updatePaneController, licensePaneController, offlinePaneController, defenderPaneController,
            downloadWarnPaneController, exceptionPaneController, loadingPaneController, logoutPaneController,
            deletionPaneController, installPaneController,
        )
    }

    val isModalShown: Boolean get() = modals.any { it.isShowing }

    private fun onModalVisibility(changed: ModalController) {
        val blocked = isModalShown
        InputBlock.set(layout, blocked)
        if (blocked) hideMenus()
        if (changed.isShowing) changed.initialFocus?.requestFocus() ?: InputBlock.focusInto(changed.layer)
    }

    /** Confirmed logout: end the session and go back to the separate auth window. */
    private fun leaveToAuth() {
        ctx.services.auth.logout()
        store.shutdown()
        ctx.navigator.showAuth()
    }

    /** True once [dispose] ran (the navigator disposes the screen before it leaves the main window). */
    var isDisposed = false
        private set

    /** Leaving the scene: detach from the session-scoped store and the scene, but keep downloads running. */
    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        root.scene?.removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter)
        followRow(null)
        serversListController.dispose()
        defaultStatusPaneController.dispose()
        profileBarController.dispose()
        headerController.dispose()
    }

    /** Closes every dropdown (profile, communities, game actions). */
    fun hideMenus() {
        profileBarController.hideMenu()
        headerController.hideCommunityMenu()
        defaultStatusPaneController.gameActionsController.hide()
    }

    /**
     * A row of the game-actions menu. Verify starts at once (after a confirmation if the game runs: it gets closed);
     * reinstall / delete always ask first and only the confirm button starts them (mock operations, nothing on disk).
     * The folder is the selected game's folder from the settings (GameFolders), opened through FolderOpener.
     */
    fun onGameAction(action: GameAction) {
        val server = selected ?: return
        val state = store.state(server).get()
        if (!GameActions.isEnabled(action, state)) return
        val operation = when (action) {
            GameAction.OPEN_FOLDER -> {
                ctx.services.folders.open(GameFolders.dirOf(ctx.services.settings.load(), server))
                return
            }
            GameAction.SETTINGS -> { setSettingsVisible(true); return }
            GameAction.VERIFY -> ClientOperation.VERIFY
            GameAction.REINSTALL -> ClientOperation.REINSTALL
            GameAction.DELETE -> ClientOperation.DELETE
        }
        if (!GameActions.needsConfirmation(action, state)) {
            store.runOperation(server, operation)
            return
        }
        val key = "main.confirm." + operation.name.lowercase()
        deletionPaneController.ask(
            title = ctx.t("$key.title"),
            text = ctx.t("$key.text"),
            confirm = ctx.t("$key.confirm"),
            cancel = ctx.t("main.confirm.cancel"),
        ) { store.runOperation(server, operation) }
    }

    fun selectServer(server: Server, showHome: Boolean) {
        selected = server
        defaultStatusPaneController.gameActionsController.hide()
        background.setImageBackground(loadImage(server.backgroundUri, background = true), cover = true)
        defaultStatusPaneController.showServer(server, store.state(server))
        gameBannerController.show(server)
        followRow(serversListController.selectedEntry?.root)
        if (showHome) setSettingsVisible(false)
    }

    /** Logical screen: settings or home (the other pane may still be fading out for a moment). */
    var isSettingsShown: Boolean = false
        private set

    fun setSettingsVisible(visible: Boolean) {
        val changed = visible != isSettingsShown
        isSettingsShown = visible
        // home <-> settings crossfade (ui/motion/Motion); a repeated call only makes sure the end state is right
        if (changed) Motion.crossfade(old = if (visible) homePane else settingsPane, new = if (visible) settingsPane else homePane)
        else if (!Motion.isAnimating(if (visible) settingsPane else homePane)) {
            settingsPane.isVisible = visible
            homePane.isVisible = !visible
        }
        root.toggleClass("settings-mode", visible)
        serversListController.setSettingsActive(visible)
        if (visible) defaultStatusPaneController.gameActionsController.hide()
        headerController.hideCommunityMenu() // a screen switch closes the open dropdown, the links themselves stay
    }

    private fun onPrimaryAction(server: Server) {
        val state = store.state(server).get()
        when {
            state == ClientState.NotInstalled -> {
                installPaneController.onConfirm = { startDownloadChecked(server) }
                installPaneController.show(
                    ctx.services.settings.load().gameRoot.resolve(server.id).toString(),
                    formatBytes(server.clientSizeBytes),
                    formatBytes(512L * 1024 * 1024 * 1024),
                )
            }
            state is ClientState.Paused -> startDownloadChecked(server)
            else -> store.primaryAction(server)
        }
    }

    private fun startDownloadChecked(server: Server) {
        if (store.isAnyDownloading(except = server)) {
            downloadWarnPaneController.onConfirm = { store.startDownload(server) }
            downloadWarnPaneController.show()
        } else {
            store.startDownload(server)
        }
    }

    private fun notAvailable() = exceptionPaneController.show(ctx.t("fork.not_available"), ctx.t("fork.hint"))

    /**
     * ESC closes modals. F1-F7 are dev helpers to look at modal layers that the fork never triggers by itself;
     * they only work with -Dlauncher.dev=true (see README).
     */
    private fun onKey(e: KeyEvent) {
        if (!ctx.devMode && e.code != KeyCode.ESCAPE) return
        when (e.code) {
            KeyCode.F1 -> updatePaneController.show()
            KeyCode.F2 -> licensePaneController.show()
            KeyCode.F3 -> offlinePaneController.show()
            KeyCode.F4 -> defenderPaneController.show()
            KeyCode.F5 -> downloadWarnPaneController.show()
            KeyCode.F6 -> exceptionPaneController.show("Exception title", "Exception description placeholder")
            KeyCode.F7 -> loadingPaneController.apply { if (isShowing) hide() else show() }
            KeyCode.ESCAPE -> {
                hideMenus()
                modals.forEach { it.hide() }
            }
            else -> return
        }
        e.consume()
    }
}

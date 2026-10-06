package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.Server
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Disposable
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.formatBytes
import dev.launcherfork.ui.components.loadImage
import dev.launcherfork.ui.components.setImageBackground
import dev.launcherfork.ui.components.toggleClass
import dev.launcherfork.ui.screens.main.modals.DefenderModalController
import dev.launcherfork.ui.screens.main.modals.DeletionModalController
import dev.launcherfork.ui.screens.main.modals.DownloadWarnModalController
import dev.launcherfork.ui.screens.main.modals.ExceptionModalController
import dev.launcherfork.ui.screens.main.modals.InstallModalController
import dev.launcherfork.ui.screens.main.modals.LicenseModalController
import dev.launcherfork.ui.screens.main.modals.LoadingModalController
import dev.launcherfork.ui.screens.main.modals.LogoutModalController
import dev.launcherfork.ui.screens.main.modals.OfflineModalController
import dev.launcherfork.ui.screens.main.modals.UpdateModalController
import dev.launcherfork.ui.screens.settings.SettingsController
import dev.launcherfork.ui.state.ClientState
import dev.launcherfork.ui.state.ClientStateStore
import javafx.fxml.FXML
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane

/**
 * main.fxml: coordination between the included areas (sidebar, hero, links, news, article, status bar,
 * settings, modals). Each area has its own FXML and controller. Layout is adaptive (BorderPane + anchors).
 */
class MainController(private val ctx: UiContext) : Disposable {
    @FXML lateinit var root: StackPane
    @FXML lateinit var layout: BorderPane
    @FXML lateinit var main: StackPane
    @FXML lateinit var background: Region
    @FXML lateinit var backgroundShade: Region
    @FXML lateinit var homePane: AnchorPane
    @FXML lateinit var settingsPane: AnchorPane

    // included areas: FXMLLoader injects <fx:id>Controller
    @FXML lateinit var serversListController: ServersListController
    @FXML lateinit var heroController: HeroController
    @FXML lateinit var headerController: HeaderController
    @FXML lateinit var advancedNewsPaneController: NewsController
    @FXML lateinit var articleBgController: ArticleController
    @FXML lateinit var defaultStatusPaneController: StatusPaneController
    @FXML lateinit var clientSettingsPaneController: ClientSettingsController
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

        serversListController.onAccountClicked = { logoutPaneController.show() }
        logoutPaneController.onConfirm = {
            ctx.services.auth.logout()
            store.shutdown()
            ctx.navigator.showAuth()
        }
        updatePaneController.onConfirm = { ctx.navigator.showLauncherUpdate(afterUpdate = ctx.navigator::showMain) }

        serversListController.onServerSelected = { server -> selectServer(server, showHome = true) }
        serversListController.onSettingsClicked = { setSettingsVisible(!settingsPane.isVisible) }

        val status = defaultStatusPaneController
        status.onPrimaryAction = ::onPrimaryAction
        status.onClientSettings = { clientSettingsPaneController.toggle() }

        headerController.onLink = { notAvailable() }
        advancedNewsPaneController.onOpenArticle = { item -> articleBgController.open(item) }
        articleBgController.onClose = {}

        clientSettingsPaneController.onDelete = { deletionPaneController.show() }
        clientSettingsPaneController.onNotAvailable = { notAvailable() }
        deletionPaneController.onConfirm = { notAvailable() }

        servers = ctx.services.servers.servers()
        serversListController.setServers(servers, store)
        servers.firstOrNull()?.let {
            serversListController.select(it, notify = false)
            selectServer(it, showHome = false)
        }

        root.sceneProperty().addListener { _, _, scene -> scene?.addEventFilter(KeyEvent.KEY_PRESSED, ::onKey) }
    }

    /** Leaving the scene: detach from the session-scoped store, but keep downloads running. */
    override fun dispose() {
        serversListController.dispose()
        defaultStatusPaneController.dispose()
    }

    fun selectServer(server: Server, showHome: Boolean) {
        selected = server
        clientSettingsPaneController.hide()
        background.setImageBackground(loadImage(server.backgroundUri, background = true), cover = true)
        heroController.show(server)
        defaultStatusPaneController.showServer(server, store.state(server))
        if (showHome) {
            setSettingsVisible(false)
            articleBgController.hide()
        }
    }

    fun setSettingsVisible(visible: Boolean) {
        settingsPane.isVisible = visible
        homePane.isVisible = !visible
        root.toggleClass("settings-mode", visible)
        serversListController.setSettingsActive(visible)
        if (visible) clientSettingsPaneController.hide()
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
            KeyCode.ESCAPE -> listOf(
                updatePaneController, licensePaneController, offlinePaneController, defenderPaneController,
                downloadWarnPaneController, exceptionPaneController, loadingPaneController, logoutPaneController,
                deletionPaneController, installPaneController,
            ).forEach { it.hide() }
            else -> return
        }
        e.consume()
    }
}

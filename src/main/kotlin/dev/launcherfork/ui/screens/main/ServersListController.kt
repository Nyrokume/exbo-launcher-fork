package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.Server
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Disposable
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.LogoMark
import dev.launcherfork.ui.components.ServerEntryController
import dev.launcherfork.ui.components.toggleClass
import dev.launcherfork.ui.state.ClientStateStore
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import java.util.Locale

/**
 * main/servers_list.fxml: sidebar with the EXBO logo, game list and Settings.
 * Width is 253 px at the 1024 px window and follows the window between [MIN_WIDTH] and [MAX_WIDTH].
 */
class ServersListController(private val ctx: UiContext) : Disposable {
    @FXML lateinit var sidebar: VBox
    @FXML lateinit var logo: LogoMark
    @FXML lateinit var gamesLabel: HBox
    @FXML lateinit var gamesLabelText: Label
    @FXML lateinit var serversPane: ScrollPane
    @FXML lateinit var settingsButton: Button

    var onServerSelected: (Server) -> Unit = {}
    var onSettingsClicked: () -> Unit = {}

    private val entries = mutableListOf<ServerEntryController>()
    /** Cards of the games list, in order (tests, screenshot tour). */
    val entryControllers: List<ServerEntryController> get() = entries
    private val list = VBox(6.0)
    /** Row of the selected game (MainController lines the game banner up with it). */
    val selectedEntry: ServerEntryController? get() = entries.firstOrNull { "selected" in it.root.styleClass }

    @FXML
    fun initialize() {
        serversPane.content = list
        // UPPERCASE nodes (.zone-caps) - to be swapped to SpacedLabel after the redesign/auth merge
        settingsButton.text = settingsButton.text.uppercase(locale())
        gamesLabelText.text = gamesLabelText.text.uppercase(locale())
        settingsButton.setOnAction { onSettingsClicked() }
    }

    /** Keeps the sidebar and the logo in proportion with the main window. 253 px at 1024. */
    fun followWindow(windowWidth: Double) {
        if (windowWidth < 100.0) return
        val width = widthFor(windowWidth)
        sidebar.minWidth = width
        sidebar.prefWidth = width
        sidebar.maxWidth = width
        logo.logoWidth = logoWidthFor(width)
    }

    private fun locale(): Locale = Locale.forLanguageTag(ctx.lang)

    fun setServers(servers: List<Server>, store: ClientStateStore) {
        dispose()
        list.children.clear()
        servers.forEach { server ->
            val loaded = Fxml.load<ServerEntryController>(ctx, "server_entry.fxml")
            loaded.controller.bind(server, store.state(server))
            loaded.root.setOnMouseClicked { select(server) }
            entries += loaded.controller
            list.children += loaded.root
        }
    }

    fun select(server: Server, notify: Boolean = true) {
        entries.forEach { it.setSelected(it.server.id == server.id) }
        if (notify) onServerSelected(server)
    }

    fun setSettingsActive(active: Boolean) = settingsButton.toggleClass("active", active)

    companion object {
        const val DESIGN_WIDTH = 253.0
        const val MIN_WIDTH = 220.0
        const val MAX_WIDTH = 340.0
        private const val DESIGN_LOGO = 170.0

        /** Sidebar width for a main-window width. 253 px at [Theme.WINDOW_WIDTH]. */
        fun widthFor(windowWidth: Double): Double =
            (windowWidth * DESIGN_WIDTH / Theme.WINDOW_WIDTH).coerceIn(MIN_WIDTH, MAX_WIDTH)

        fun logoWidthFor(sidebarWidth: Double): Double =
            (DESIGN_LOGO * sidebarWidth / DESIGN_WIDTH).coerceIn(140.0, 220.0)
    }

    /** Unbinds all cards from the session-scoped store. */
    override fun dispose() {
        entries.forEach { it.unbind() }
        entries.clear()
    }
}

package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.Server
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

/** main/servers_list.fxml: fixed-width sidebar with the EXBO logo, game list and Settings. */
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

    /** Unbinds all cards from the session-scoped store. */
    override fun dispose() {
        entries.forEach { it.unbind() }
        entries.clear()
    }
}

package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.Server
import dev.launcherfork.ui.Disposable
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.UiContext
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

/** main/servers_list.fxml: fixed-width sidebar with the EXBO wordmark, game list, account card and Settings. */
class ServersListController(private val ctx: UiContext) : Disposable {
    @FXML lateinit var sidebar: VBox
    @FXML lateinit var logo: Label
    @FXML lateinit var gamesLabel: HBox
    @FXML lateinit var gamesLabelText: Label
    @FXML lateinit var serversPane: ScrollPane
    @FXML lateinit var loginButton: Button
    @FXML lateinit var accountName: Label
    @FXML lateinit var accountAction: Label
    @FXML lateinit var settingsButton: Button

    var onServerSelected: (Server) -> Unit = {}
    var onSettingsClicked: () -> Unit = {}
    var onAccountClicked: () -> Unit = {}

    private val entries = mutableListOf<ServerEntryController>()
    private val list = VBox(6.0)

    @FXML
    fun initialize() {
        serversPane.content = list
        // UPPERCASE nodes (.zone-caps) - to be swapped to SpacedLabel after the redesign/auth merge
        settingsButton.text = settingsButton.text.uppercase(locale())
        gamesLabelText.text = gamesLabelText.text.uppercase(locale())
        settingsButton.setOnAction { onSettingsClicked() }
        loginButton.setOnAction { onAccountClicked() }
        setLogin(ctx.services.auth.currentSession?.login)
    }

    private fun locale(): Locale = Locale.forLanguageTag(ctx.lang)

    fun setLogin(login: String?) {
        accountName.text = (login ?: "player").uppercase(locale())
    }

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

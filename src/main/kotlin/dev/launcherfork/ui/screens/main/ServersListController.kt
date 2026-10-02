package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.Server
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.ServerEntryController
import dev.launcherfork.ui.components.brightenOnHover
import dev.launcherfork.ui.components.toggleClass
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ScrollPane
import javafx.scene.image.ImageView
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.VBox

/** main/servers_list.fxml: logo, Settings button and the list of server cards. */
class ServersListController(private val ctx: UiContext) {
    @FXML lateinit var sidebar: AnchorPane
    @FXML lateinit var logo: ImageView
    @FXML lateinit var serversPane: ScrollPane
    @FXML lateinit var settingsButton: Button

    var onServerSelected: (Server) -> Unit = {}
    var onSettingsClicked: () -> Unit = {}

    private val entries = mutableListOf<ServerEntryController>()
    private val list = VBox(1.0)

    @FXML
    fun initialize() {
        serversPane.content = list
        settingsButton.brightenOnHover()
        settingsButton.setOnAction { onSettingsClicked() }
    }

    fun setServers(servers: List<Server>, store: ClientStateStore) {
        entries.clear()
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
}

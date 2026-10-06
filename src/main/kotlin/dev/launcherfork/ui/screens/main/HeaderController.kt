package dev.launcherfork.ui.screens.main

import dev.launcherfork.ui.UiContext
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.layout.VBox

/** main/header.fxml: Forum / Support links with the ↗ arrow (no browser in the fork); Profile is in the profile menu. */
class HeaderController(private val ctx: UiContext) {
    @FXML lateinit var headerPane: VBox
    @FXML lateinit var linksContainer: VBox
    @FXML lateinit var support: Label
    @FXML lateinit var forum: Label

    /** Fired with the link id ("forum", "support"); MainController shows "not available". */
    var onLink: (String) -> Unit = {}

    @FXML
    fun initialize() {
        mapOf(forum to "forum", support to "support").forEach { (label, id) ->
            label.setOnMouseClicked { onLink(id) }
        }
    }
}

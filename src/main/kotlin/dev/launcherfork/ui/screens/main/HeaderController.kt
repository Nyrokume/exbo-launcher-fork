package dev.launcherfork.ui.screens.main

import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.brightenOnHover
import javafx.fxml.FXML
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.Pane

/** main/header.fxml: Support / Profile / Forum links (no browser in the fork, hover only). */
class HeaderController(@Suppress("unused") private val ctx: UiContext) {
    @FXML lateinit var headerPane: AnchorPane
    @FXML lateinit var linksContainer: Pane
    @FXML lateinit var support: Pane
    @FXML lateinit var profile: Pane
    @FXML lateinit var forum: Pane

    @FXML
    fun initialize() {
        listOf(support, profile, forum).forEach { it.brightenOnHover() }
        linksContainer.widthProperty().addListener { _, _, w -> layoutLinks(w.toDouble()) }
    }

    /** Same formula as HeaderSubController.onResize. */
    private fun layoutLinks(width: Double) {
        forum.layoutX = width / 2 - 30
        support.layoutX = width / 4 - 50
        profile.layoutX = width * 3 / 4 - 30
    }
}

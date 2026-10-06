package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.GameDescription
import dev.launcherfork.domain.Server
import dev.launcherfork.ui.UiContext
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox

/**
 * main/game_banner.fxml: the selected game's description in the centre slot of the main screen, like the original
 * EXBO launcher: title, lead, subheading, body and a closing line. The online count is not repeated here: it is shown
 * once, under the game name in the games list (ServerEntryController). All text comes with the game
 * ([Server.description], from ServerRepository): i18n keys for mock data or ready text from a server; nothing is
 * written here or in the FXML. The banner is a transparent ScrollPane inside the anchored centre slot, so long text
 * wraps and, in a small window, scrolls inside the slot: it never pushes the status row or ИГРАТЬ. It watches nothing, so there is nothing to
 * dispose.
 */
class GameBannerController(private val ctx: UiContext) {
    @FXML lateinit var scroll: ScrollPane
    @FXML lateinit var content: VBox
    @FXML lateinit var title: Label
    @FXML lateinit var lead: Label
    @FXML lateinit var subheading: Label
    @FXML lateinit var body: Label
    @FXML lateinit var closing: Label

    @FXML
    fun initialize() {
        scroll.isVisible = false
    }

    /** Shows [server]'s description, or hides the banner when it has none. */
    fun show(server: Server?) {
        val texts = server?.description?.let(::resolve)
        scroll.isVisible = texts != null
        if (texts == null) return
        title.text = texts.title
        lead.text = texts.lead
        subheading.text = texts.subheading
        body.text = texts.body
        closing.text = texts.closing
        scroll.vvalue = 0.0
    }

    private fun resolve(d: GameDescription): GameDescription.Text = when (d) {
        is GameDescription.Text -> d
        is GameDescription.Keys -> GameDescription.Text(
            title = ctx.t("${d.prefix}.title"),
            lead = ctx.t("${d.prefix}.lead"),
            subheading = ctx.t("${d.prefix}.subheading"),
            body = ctx.t("${d.prefix}.body"),
            closing = ctx.t("${d.prefix}.closing"),
        )
    }
}

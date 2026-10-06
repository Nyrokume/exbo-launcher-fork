package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.GameDescription
import dev.launcherfork.domain.Server
import dev.launcherfork.ui.UiContext
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.scene.text.TextBoundsType

/**
 * main/game_banner.fxml: the selected game's description in the centre slot of the main screen, like the original
 * EXBO launcher: title, lead, subheading, body and a closing line. The online count is not repeated here: it is shown
 * once, under the game name in the games list (ServerEntryController). All text comes with the game
 * ([Server.description], from ServerRepository): i18n keys for mock data or ready text from a server; nothing is
 * written here or in the FXML. The banner is a transparent ScrollPane inside the anchored centre slot, so long text
 * wraps and, in a small window, scrolls inside the slot: it never pushes the status row or ИГРАТЬ. It watches nothing, so there is nothing to
 * dispose. Where the slot starts is up to MainController: the title's letters on the line of the selected game's row
 * in the sidebar ([titleInkOffset]).
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

    /**
     * Distance from the top of the banner (the ScrollPane) to the top of the title's letters (the cap line of
     * «STALZONE», not the line box above it), with the text scrolled to the top: viewport top + the title's text line
     * inside the scrolled column + the font's gap between the line top and the ink. Independent of the scroll position
     * and of where the slot is placed; null until the title has a skin (first CSS pass).
     */
    fun titleInkOffset(): Double? {
        val line = title.lookup(".text") as? Text ?: return null
        val column = scroll.content ?: return null
        val viewport = scroll.lookup(".viewport") ?: return null
        if (title.text.isNullOrEmpty()) return null
        val viewportTop = scroll.sceneToLocal(viewport.localToScene(0.0, 0.0)).y
        val lineTop = column.sceneToLocal(line.localToScene(line.layoutBounds)).minY
        val logical = Text(title.text).apply { font = line.font }
        val ink = Text(title.text).apply { font = line.font; boundsType = TextBoundsType.VISUAL }
        return viewportTop + lineTop + (ink.layoutBounds.minY - logical.layoutBounds.minY)
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

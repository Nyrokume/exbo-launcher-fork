package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.Cancellable
import dev.launcherfork.domain.GameDescription
import dev.launcherfork.domain.Server
import dev.launcherfork.i18n.plural
import dev.launcherfork.ui.Disposable
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.formatCount
import dev.launcherfork.ui.components.fx
import dev.launcherfork.ui.components.toggleClass
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox

/**
 * main/game_banner.fxml: the selected game's description in the centre slot of the main screen, like the original
 * EXBO launcher: title, lead, «1 284 игрока онлайн» (green dot, same value as the games list), subheading, body and a
 * closing line. All text comes with the game ([Server.description], from ServerRepository): i18n keys for mock data
 * or ready text from a server; nothing is written here or in the FXML. The banner is a transparent ScrollPane inside
 * the anchored centre slot, so long text wraps and, in a small window, scrolls inside the slot: it never pushes the
 * status row or ИГРАТЬ.
 */
class GameBannerController(private val ctx: UiContext) : Disposable {
    @FXML lateinit var scroll: ScrollPane
    @FXML lateinit var content: VBox
    @FXML lateinit var title: Label
    @FXML lateinit var lead: Label
    @FXML lateinit var onlineRow: HBox
    @FXML lateinit var onlineDot: Region
    @FXML lateinit var onlineText: Label
    @FXML lateinit var subheading: Label
    @FXML lateinit var body: Label
    @FXML lateinit var closing: Label

    private var onlineWatch: Cancellable? = null
    /** Bumped on every [show] / [dispose]: a late repository tick of the previous game is dropped (FX thread only). */
    private var generation = 0L

    @FXML
    fun initialize() {
        scroll.isVisible = false
    }

    /** Shows [server]'s description (or hides the banner when it has none) and follows its online count. */
    fun show(server: Server?) {
        stopWatching()
        val texts = server?.description?.let(::resolve)
        scroll.isVisible = texts != null
        if (server == null || texts == null) return
        title.text = texts.title
        lead.text = texts.lead
        subheading.text = texts.subheading
        body.text = texts.body
        closing.text = texts.closing
        scroll.vvalue = 0.0
        val gen = generation
        renderOnline(server.onlineCount)
        onlineWatch = ctx.services.servers.watchOnline(server) { n -> fx { if (gen == generation) renderOnline(n) } }
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

    /** «1 284 игрока онлайн» with the locale's grouping and the right plural form; «не в сети» (grey dot) if unknown. */
    private fun renderOnline(n: Int?) {
        val online = n != null && n >= 0
        onlineText.text = if (online) ctx.strings.plural("main.game.players", n!!.toLong(), ctx.lang, formatCount(n, ctx.lang))
        else ctx.t("main.games.offline")
        onlineRow.toggleClass("offline", !online)
    }

    private fun stopWatching() {
        onlineWatch?.cancel()
        onlineWatch = null
        generation++
    }

    override fun dispose() = stopWatching()
}

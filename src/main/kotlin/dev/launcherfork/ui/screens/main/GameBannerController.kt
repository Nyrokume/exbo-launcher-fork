package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.GameDescription
import dev.launcherfork.domain.Server
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.motion.Motion
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.scene.text.TextBoundsType
import java.util.Locale

/**
 * main/game_banner.fxml: the selected game's description in the centre slot of the main screen, like the original
 * EXBO launcher: title, lead, subheading, body and a closing line. The online count is not repeated here: it is shown
 * once, under the game name in the games list (ServerEntryController). All text comes with the game
 * ([Server.description], from ServerRepository): i18n keys for mock data or ready text from a server; nothing is
 * written here or in the FXML. The banner is a transparent ScrollPane inside the anchored centre slot. The column
 * fills that slot: type grows with the width (baseline is the CSS size at 560 px) and shrinks when a long server text
 * would otherwise run past the page, so it never pushes the status row or ИГРАТЬ. It watches nothing, so there is
 * nothing to dispose. Where the slot starts is up to MainController: the title's letters on the line of the selected
 * game's row in the sidebar ([titleInkOffset]).
 */
class GameBannerController(private val ctx: UiContext) {
    /** CSS sizes are designed for a column this wide; a wider slot scales type up from here. */
    private companion object {
        const val WIDTH_AT_SCALE_1 = 560.0
        const val MIN_SCALE = 0.55
        const val MAX_SCALE = 2.0
    }

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
        scroll.viewportBoundsProperty().addListener { _, _, _ -> fit() }
        content.widthProperty().addListener { _, _, _ -> fit() }
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
        fittedW = -1.0
        fittedH = -1.0
        fit()
        // a new game's text rises into place (fade + 8 px); only the text column moves, the slot stays where
        // MainController aligned it, so the title ends exactly on the selected row's line
        Motion.fadeSlideIn(content, Motion.SLIDE)
    }

    /**
     * The column already fills the slot. Type scale follows that width (1 at 560 px, up to [MAX_SCALE] on a wide
     * window) so the inscription grows into the free space. A longer server text shrinks the title, the
     * body and the gaps together until the column is no taller than the slot. CSS sizes are the baseline (zone's
     * display title is larger than the shared 40 px rule); inline sizes are only set when the scale leaves 1.
     */
    private fun fit() {
        if (fitting || !scroll.isVisible) return
        val viewH = scroll.viewportBounds.height
        val width = scroll.viewportBounds.width
        if (viewH <= 1.0 || width <= 1.0) return
        // same slot as the last fit: leave the labels' styles alone. A later layout (the contrast snapshot hides
        // text by appending to those styles) must not have this rewrite the fill back.
        if (fittedW == width && fittedH == viewH) return
        fitting = true
        try {
            resetType()
            content.applyCss()
            val base = TypeBase(title.font.size, lead.font.size, subheading.font.size, body.font.size, closing.font.size)
            val wanted = (width / WIDTH_AT_SCALE_1).coerceIn(MIN_SCALE, MAX_SCALE)
            if (fits(base, wanted, width, viewH)) {
                fittedW = width
                fittedH = viewH
                return
            }
            var lo = MIN_SCALE
            var hi = wanted
            repeat(8) {
                val mid = (lo + hi) / 2
                if (fits(base, mid, width, viewH)) lo = mid else hi = mid
            }
            if (!fits(base, lo, width, viewH)) applyScale(base, MIN_SCALE)
            fittedW = width
            fittedH = viewH
        } finally {
            fitting = false
        }
    }

    /** Viewport the current inline sizes were fitted to; -1 until the first successful fit. */
    private var fittedW = -1.0
    private var fittedH = -1.0

    private fun fits(base: TypeBase, scale: Double, width: Double, viewH: Double): Boolean {
        applyScale(base, scale)
        content.applyCss()
        return measured(width) <= viewH + 0.5
    }

    private fun measured(width: Double) = content.prefHeight(width)

    private fun resetType() {
        content.spacing = 6.0
        title.restyle()
        lead.restyle()
        subheading.restyle()
        body.restyle()
        closing.restyle()
    }

    /** [scale] 1 keeps the theme CSS. Anything else multiplies the measured CSS sizes, the gaps and the line spacing. */
    private fun applyScale(base: TypeBase, scale: Double) {
        if (scale > 0.995 && scale < 1.005) {
            resetType()
            return
        }
        content.spacing = 6.0 * scale
        title.restyle(px(base.title * scale))
        lead.restyle(px(base.lead * scale), "-fx-line-spacing: ${pxNum(scale)}")
        subheading.restyle(px(base.sub * scale), "-fx-padding: ${pxNum(2.0 * scale)} 0 0 0")
        val bodyStyle = arrayOf(px(base.body * scale), "-fx-line-spacing: ${pxNum(scale)}")
        body.restyle(*bodyStyle)
        closing.restyle(px(base.closing * scale), "-fx-line-spacing: ${pxNum(scale)}")
    }

    /**
     * Replaces only the size declarations this fitter owns. Anything else on the label (a contrast check hiding the
     * text, for example) stays, so a layout pass in the middle of that check cannot paint the glyphs back.
     */
    private fun Label.restyle(vararg owned: String) {
        val managed = arrayOf("-fx-font-size", "-fx-line-spacing", "-fx-padding")
        val kept = style.orEmpty().split(';').map { it.trim() }.filter { part ->
            part.isNotEmpty() && managed.none { part.startsWith(it) }
        }
        style = (owned.map { it.trim().trimEnd(';') }.filter { it.isNotEmpty() } + kept).joinToString("; ")
    }

    private fun px(size: Double) = "-fx-font-size: ${pxNum(size)}px;"

    private fun pxNum(size: Double) = String.format(Locale.US, "%.2f", size)

    private data class TypeBase(val title: Double, val lead: Double, val sub: Double, val body: Double, val closing: Double)

    private var fitting = false

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
        // minus the column's slide (Motion.fadeSlideIn): the offset is that of the settled layout, so the slot does not
        // follow the text while it rises and the alignment holds at the end of the animation
        return viewportTop + lineTop - Motion.slideOffset(content) + (ink.layoutBounds.minY - logical.layoutBounds.minY)
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

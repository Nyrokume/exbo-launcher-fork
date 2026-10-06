package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.NewsItem
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.loadImage
import javafx.fxml.FXML
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.shape.Rectangle

/** main/news.fxml: news cards from [dev.launcherfork.domain.NewsRepository] in a scroll column + red scroll indicator. */
class NewsController(private val ctx: UiContext) {
    @FXML lateinit var advancedNewsPane: VBox
    @FXML lateinit var mainLink: Label
    @FXML lateinit var newsScroll: ScrollPane
    @FXML lateinit var newsList: VBox
    @FXML lateinit var scrollTrack: Pane
    @FXML lateinit var scrollThumb: Region

    var onOpenArticle: (NewsItem) -> Unit = {}
    val cards = mutableListOf<StackPane>()

    @FXML
    fun initialize() {
        scrollThumb.styleClass += "zone-radius-sm"
        ctx.services.news.latest().forEach { item -> cards += card(item) }
        newsList.children.setAll(cards)
        listOf(newsScroll.vvalueProperty(), newsScroll.viewportBoundsProperty(), newsList.heightProperty(), scrollTrack.heightProperty())
            .forEach { it.addListener { _, _, _ -> layoutThumb() } }
    }

    private fun card(item: NewsItem): StackPane {
        val image = ImageView(loadImage(item.imageUri, background = true)).apply {
            fitWidth = CARD_W - 2
            fitHeight = CARD_H - 2
            clip = Rectangle(CARD_W - 2, CARD_H - 2).apply { arcWidth = 18.0; arcHeight = 18.0 }
        }
        return StackPane(image).apply {
            styleClass.addAll("news-card", "card", "zone-radius-md")
            setMinSize(CARD_W, CARD_H)
            setPrefSize(CARD_W, CARD_H)
            setMaxSize(CARD_W, CARD_H)
            alignment = Pos.CENTER
            accessibleText = item.title
            setOnMouseClicked { onOpenArticle(item) }
        }
    }

    /** Thumb height = visible share of the list, position = scroll value (no track drawn, as in the reference). */
    private fun layoutThumb() {
        val track = scrollTrack.height
        val content = newsList.height
        val visible = newsScroll.viewportBounds.height
        if (track <= 0 || content <= 0) return
        val h = (track * (visible / content).coerceIn(0.0, 1.0)).coerceAtLeast(24.0)
        scrollThumb.resize(4.0, h)
        scrollThumb.relocate(0.0, (track - h) * newsScroll.vvalue)
        scrollThumb.isVisible = content > visible + 1
    }

    fun setVisible(visible: Boolean) {
        advancedNewsPane.isVisible = visible
    }

    companion object {
        const val CARD_W = 295.0
        const val CARD_H = 165.0
    }
}

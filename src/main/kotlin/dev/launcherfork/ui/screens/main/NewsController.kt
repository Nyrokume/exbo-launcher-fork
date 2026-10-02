package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.NewsItem
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.brightenOnHover
import dev.launcherfork.ui.components.loadImage
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.Pane
import javafx.scene.text.Text
import javafx.scene.text.TextFlow

/** main/news.fxml: four news cards from [dev.launcherfork.domain.NewsRepository] and social links. */
class NewsController(private val ctx: UiContext) {
    @FXML lateinit var advancedNewsPane: AnchorPane
    @FXML lateinit var firstNewsPane: AnchorPane
    @FXML lateinit var secondNewsPane: AnchorPane
    @FXML lateinit var thirdNewsPane: AnchorPane
    @FXML lateinit var fourthNewsPane: AnchorPane
    @FXML lateinit var firstNewsBg: ImageView
    @FXML lateinit var secondNewsBg: ImageView
    @FXML lateinit var thirdNewsBg: ImageView
    @FXML lateinit var fourthNewsBg: ImageView
    @FXML lateinit var firstNewsDescPane: Pane
    @FXML lateinit var secondNewsDesc: TextFlow
    @FXML lateinit var thirdNewsDesc: TextFlow
    @FXML lateinit var fourthNewsDesc: TextFlow
    @FXML lateinit var discord: Pane
    @FXML lateinit var youtube: Pane
    @FXML lateinit var twitch: Pane
    @FXML lateinit var instagram: Pane
    @FXML lateinit var vk: Pane
    @FXML lateinit var bigLogo: ImageView
    @FXML lateinit var mainLink: Label

    var onOpenArticle: (NewsItem) -> Unit = {}

    @FXML
    fun initialize() {
        listOf(firstNewsPane, secondNewsPane, thirdNewsPane, fourthNewsPane, mainLink).forEach { it.brightenOnHover() }
        // social links: hover overlay is pure CSS (#discord:hover ...); no browser in the fork
        val news = ctx.services.news.latest()
        val cards = listOf(firstNewsPane to firstNewsBg, secondNewsPane to secondNewsBg, thirdNewsPane to thirdNewsBg, fourthNewsPane to fourthNewsBg)
        cards.forEachIndexed { i, (pane, bg) ->
            val item = news.getOrNull(i)
            pane.isVisible = item != null
            if (item != null) {
                bg.image = loadImage(item.imageUri, background = true)
                pane.setOnMouseClicked { onOpenArticle(item) }
            }
        }
        news.getOrNull(0)?.let { first ->
            firstNewsDescPane.children.setAll(
                Label(first.title).apply { styleClass += listOf("news-title", "font-display"); isWrapText = true; maxWidth = 300.0 },
                Label(trim(first.summary, 200)).apply { styleClass += listOf("news-text", "font-display"); isWrapText = true; maxWidth = 300.0 },
            )
        }
        listOf(secondNewsDesc, thirdNewsDesc, fourthNewsDesc).forEachIndexed { i, flow ->
            val item = news.getOrNull(i + 1)
            flow.children.setAll(listOfNotNull(item?.let { Text(trim(it.title, 40)).apply { styleClass += listOf("news-text", "font-display") } }))
        }
    }

    fun setVisible(visible: Boolean) {
        advancedNewsPane.isVisible = visible
    }

    private fun trim(s: String, length: Int): String = if (s.length < length + 3) s else s.take(length).trimEnd() + "..."
}

package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.NewsItem
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.loadImage
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.image.ImageView
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.shape.Rectangle

/** main/article.fxml: a single news item opened inside the window (card panel over the hero area). */
class ArticleController(private val ctx: UiContext) {
    @FXML lateinit var articleBg: VBox
    @FXML lateinit var articleCloseButton: Button
    @FXML lateinit var articleCaptionUnderline: Region
    @FXML lateinit var articleCaption: Label
    @FXML lateinit var articleScroll: ScrollPane
    @FXML lateinit var articleContent: VBox

    var onClose: () -> Unit = {}

    @FXML
    fun initialize() {
        articleCloseButton.setOnAction { hide(); onClose() }
    }

    fun open(item: NewsItem) {
        articleCaption.text = item.title.uppercase()
        val image = ImageView(loadImage(item.imageUri)).apply {
            fitWidth = 400.0
            isPreserveRatio = true
            clip = Rectangle(400.0, 224.0).apply { arcWidth = 10.0; arcHeight = 10.0 }
        }
        articleContent.children.setAll(
            image,
            Label(item.summary).apply { styleClass += "article-text"; isWrapText = true },
        )
        articleScroll.vvalue = 0.0
        articleBg.isVisible = true
    }

    val isOpen: Boolean get() = articleBg.isVisible

    fun hide() {
        articleBg.isVisible = false
    }
}

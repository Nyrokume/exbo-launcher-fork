package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.NewsItem
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.brightenOnHover
import dev.launcherfork.ui.components.loadImage
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.VBox

/** main/article.fxml: a single news item opened inside the window. */
class ArticleController(private val ctx: UiContext) {
    @FXML lateinit var articleBg: AnchorPane
    @FXML lateinit var articleCloseButton: Button
    @FXML lateinit var articleCaptionUnderline: ImageView
    @FXML lateinit var articleCaption: Label
    @FXML lateinit var articleContent: AnchorPane

    var onClose: () -> Unit = {}

    @FXML
    fun initialize() {
        articleCloseButton.brightenOnHover()
        articleCloseButton.setOnAction { hide(); onClose() }
    }

    fun open(item: NewsItem) {
        articleCaption.text = item.title
        val body = VBox(12.0,
            ImageView(loadImage(item.imageUri)).apply { fitWidth = 400.0; isPreserveRatio = true },
            Label(item.summary).apply { styleClass += listOf("news-text", "font-display"); isWrapText = true; maxWidth = 560.0 },
        )
        AnchorPane.setLeftAnchor(body, 0.0)
        AnchorPane.setTopAnchor(body, 10.0)
        articleContent.children.setAll(body)
        articleBg.isVisible = true
    }

    fun hide() {
        articleBg.isVisible = false
    }
}

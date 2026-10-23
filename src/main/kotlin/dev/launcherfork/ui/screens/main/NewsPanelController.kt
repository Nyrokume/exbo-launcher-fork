package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.LinkPolicy
import dev.launcherfork.ui.UiContext
import dev.launcherfork.ui.components.loadImage
import dev.launcherfork.ui.components.setImageBackground
import dev.launcherfork.ui.motion.Motion
import javafx.animation.Interpolator
import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.PauseTransition
import javafx.animation.Timeline
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.shape.Rectangle
import javafx.util.Duration

/**
 * Patchnote poster stack: winter cover art, ПАТЧНОУТ + date overlay (STALCRAFT topic style).
 * Hover flips; click opens the topic URL. Mock feed only — no network.
 */
class NewsPanelController(private val ctx: UiContext) {
    @FXML lateinit var root: VBox
    @FXML lateinit var stage: StackPane
    @FXML lateinit var card: StackPane
    @FXML lateinit var art: Region
    @FXML lateinit var clip: Rectangle
    @FXML lateinit var brand: Label
    @FXML lateinit var title: Label
    @FXML lateinit var date: Label
    @FXML lateinit var dots: HBox

    private val items: List<NewsItem> = listOf(
        NewsItem(
            brandKey = "fork.news.brand",
            titleKey = "fork.news.1.title",
            dateKey = "fork.news.1.date",
            art = "/dev/launcherfork/ui/news/05.jpg",
            url = "https://vk.ru/@stalcraft_official-patchnout-04022026-wg97538",
        ),
        NewsItem(
            brandKey = "fork.news.brand",
            titleKey = "fork.news.2.title",
            dateKey = "fork.news.2.date",
            art = "/dev/launcherfork/ui/news/01.jpg",
            url = "https://vk.ru/@stalcraft_official-patchnout-04022026-wg97538",
        ),
        NewsItem(
            brandKey = "fork.news.brand",
            titleKey = "fork.news.3.title",
            dateKey = "fork.news.3.date",
            art = "/dev/launcherfork/ui/news/03.jpg",
            url = "https://vk.ru/@stalcraft_official-patchnout-04022026-wg97538",
        ),
        NewsItem(
            brandKey = "fork.news.brand",
            titleKey = "fork.news.4.title",
            dateKey = "fork.news.4.date",
            art = "/dev/launcherfork/ui/news/04.jpg",
            url = "https://vk.ru/@stalcraft_official-patchnout-04022026-wg97538",
        ),
    )
    private var index = 0
    private var flipping = false
    private var hoverFlip: PauseTransition? = null
    private var flipAnim: Timeline? = null

    @FXML
    fun initialize() {
        clip.widthProperty().bind(stage.widthProperty())
        clip.heightProperty().bind(stage.heightProperty())
        show(index, animate = false)
        rebuildDots()

        stage.setOnMouseEntered {
            armHoverFlip()
        }
        stage.setOnMouseExited {
            hoverFlip?.stop()
            hoverFlip = null
        }
        // click opens the topic; hover-hold advances the stack
        stage.setOnMouseClicked {
            if (flipping) return@setOnMouseClicked
            openTopic(items[index])
        }
    }

    private fun armHoverFlip() {
        hoverFlip?.stop()
        hoverFlip = PauseTransition(Duration.seconds(2.6)).apply {
            setOnFinished {
                if (stage.isHover) {
                    flipNext()
                    armHoverFlip()
                }
            }
            play()
        }
    }

    fun flipNext() {
        if (flipping || items.isEmpty()) return
        val next = (index + 1) % items.size
        if (!Motion.enabled) {
            show(next, animate = false)
            return
        }
        flipping = true
        flipAnim?.stop()
        val half = Motion.NORMAL
        val out = Timeline(
            KeyFrame(Duration.ZERO, KeyValue(card.scaleXProperty(), 1.0)),
            KeyFrame(half, KeyValue(card.scaleXProperty(), 0.02, Motion.EASE)),
        )
        out.setOnFinished {
            show(next, animate = false)
            card.scaleX = 0.02
            val inn = Timeline(
                KeyFrame(Duration.ZERO, KeyValue(card.scaleXProperty(), 0.02)),
                KeyFrame(half, KeyValue(card.scaleXProperty(), 1.0, Interpolator.SPLINE(0.2, 0.0, 0.0, 1.0))),
            )
            inn.setOnFinished { flipping = false; flipAnim = null }
            flipAnim = inn
            inn.play()
        }
        flipAnim = out
        out.play()
    }

    private fun openTopic(item: NewsItem) {
        if (!LinkPolicy.isAllowed(item.url)) return
        ctx.services.links.open(item.url)
    }

    private fun show(i: Int, animate: Boolean) {
        index = i
        val item = items[i]
        brand.text = ctx.t(item.brandKey)
        title.text = ctx.t(item.titleKey)
        date.text = ctx.t(item.dateKey)
        art.setImageBackground(loadImage(item.art, background = true), cover = true)
        rebuildDots()
        if (animate) Motion.fadeSlideIn(card, Motion.SLIDE)
    }

    private fun rebuildDots() {
        dots.children.setAll(items.indices.map { i ->
            Region().apply {
                styleClass.setAll(if (i == index) listOf("news-dot", "active") else listOf("news-dot"))
                prefWidth = 6.0; prefHeight = 6.0
                minWidth = 6.0; minHeight = 6.0
                maxWidth = 6.0; maxHeight = 6.0
            }
        })
    }

    private data class NewsItem(
        val brandKey: String,
        val titleKey: String,
        val dateKey: String,
        val art: String,
        val url: String,
    )
}

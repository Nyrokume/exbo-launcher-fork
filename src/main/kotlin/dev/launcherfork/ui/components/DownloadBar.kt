package dev.launcherfork.ui.components

import dev.launcherfork.theme.Theme
import javafx.geometry.Insets
import javafx.scene.control.ProgressBar
import javafx.scene.image.Image
import javafx.scene.layout.Border
import javafx.scene.layout.BorderImage
import javafx.scene.layout.BorderRepeat
import javafx.scene.layout.BorderWidths
import javafx.scene.layout.Region

/**
 * Port of UpdatingSubController.setProgress: the bar is a tiled body image plus an 82px right cap that
 * appears only in the last 82px, and the whole bar is desaturated until it approaches 100%.
 * Implemented with BorderImage because JavaFX CSS cannot express the width-dependent cap.
 */
class DownloadBar(private val bar: ProgressBar) {
    private val body: Image by lazy { loadImage(Theme.IMAGE_ROOT + "download_progress_bar.png") }
    private val cap: Image by lazy { loadImage(Theme.IMAGE_ROOT + "download_progress_bar_right.png") }
    private var progress = 0.0

    init {
        bar.widthProperty().addListener { _, _, _ -> apply() }
        bar.skinProperty().addListener { _, _, _ -> apply() }
    }

    fun setProgress(value: Double) {
        progress = value.coerceIn(0.0, 1.0)
        apply()
    }

    private fun apply() {
        bar.effect = Theme.progressEffect(progress)
        bar.progress = progress
        val fill = bar.lookup(".bar") as? Region ?: return
        val width = bar.width
        val capW = Theme.PROGRESS_CAP_WIDTH
        val target = progress * width
        val left = target.coerceIn(0.0, (width - capW).coerceAtLeast(0.0))
        val right = (target - width + capW).coerceAtLeast(0.0).toInt().toDouble()
        fill.border = Border(
            BorderImage(body, BorderWidths(0.0), Insets(0.0, right, 0.0, 0.0), BorderWidths(0.0), true, BorderRepeat.REPEAT, BorderRepeat.STRETCH),
            BorderImage(cap, BorderWidths(0.0, 0.0, 0.0, right), Insets(0.0, 0.0, 0.0, left), BorderWidths(0.0, 0.0, 0.0, right), false, BorderRepeat.ROUND, BorderRepeat.ROUND),
        )
    }
}

package dev.launcherfork.ui.components

import dev.launcherfork.theme.Theme
import javafx.scene.Node
import javafx.scene.image.Image
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundImage
import javafx.scene.layout.BackgroundPosition
import javafx.scene.layout.BackgroundRepeat
import javafx.scene.layout.BackgroundSize
import javafx.scene.layout.Region
import java.net.URI

/** Original `makeBrightnessable`: +brightness while hovered. */
fun <T : Node> T.brightenOnHover(amount: Double = Theme.HOVER_BRIGHTNESS): T {
    hoverProperty().addListener { _, _, hover -> effect = if (hover) Theme.hoverEffect(amount) else null }
    return this
}

fun loadImage(resource: String, background: Boolean = false): Image =
    Image(requireNotNull(Theme::class.java.getResource(resource)) { "Missing image $resource" }.toExternalForm(), background)

/** Image described by the domain as a URI (file:, http(s):, jar:, ...). */
fun loadImage(uri: URI, background: Boolean = false): Image = Image(uri.toString(), background)

/** Dynamic image as a region background (server icons/backgrounds come from data, so they can't be in CSS). */
fun Region.setImageBackground(image: Image, cover: Boolean = false) {
    // percentages in BackgroundSize are fractions: 1.0 == 100%
    val size = BackgroundSize(1.0, 1.0, true, true, false, cover)
    background = Background(BackgroundImage(image, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, size))
}

/** Toggles a CSS style class (state styling goes through CSS, never through setStyle). */
fun Node.toggleClass(name: String, on: Boolean) {
    if (on) { if (name !in styleClass) styleClass.add(name) } else styleClass.remove(name)
}

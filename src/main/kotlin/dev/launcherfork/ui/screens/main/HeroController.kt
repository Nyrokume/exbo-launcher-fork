package dev.launcherfork.ui.screens.main

import dev.launcherfork.domain.Server
import dev.launcherfork.ui.UiContext
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import java.util.Locale

/** main/hero.fxml: "WELCOME" + red tagline block + description of the selected game. */
class HeroController(private val ctx: UiContext) {
    @FXML lateinit var heroPane: VBox
    @FXML lateinit var heroTitle: Label
    @FXML lateinit var heroTagline: Label
    @FXML lateinit var heroDescription: Label

    fun show(server: Server) {
        val locale = Locale.forLanguageTag(ctx.lang)
        heroTagline.text = (server.tagline ?: server.caption).uppercase(locale)
        heroDescription.text = server.description.joinToString("\n")
    }
}

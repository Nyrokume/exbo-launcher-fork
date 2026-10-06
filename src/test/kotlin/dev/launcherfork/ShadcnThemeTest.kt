package dev.launcherfork

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import javafx.css.CssParser
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Labeled
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** The shadcn restyle of the 379x490 windows: CSS parses cleanly and every captioned button gets real text. */
class ShadcnThemeTest {
    private val sheets = listOf("theme.css", "theme-shadcn.css", "common.css", "auth.css", Theme.SHADCN_COMPONENTS)

    @Test
    fun stylesheetsParseWithoutErrors() {
        CssParser.errorsProperty().clear()
        sheets.forEach { CssParser().parse(Theme.resource("style/$it")) }
        assertTrue(CssParser.errorsProperty().isEmpty(), "CSS parse errors: ${CssParser.errorsProperty().map { it.message }}")
    }

    @TestFactory
    fun authWindowsRenderWithShadcnComponents(): List<DynamicTest> =
        listOf("ru", "en").flatMap { lang ->
            listOf("initial_auth.fxml", "mail_auth.fxml", "update.fxml").map { path ->
                DynamicTest.dynamicTest("$lang: $path") {
                    FxTestSupport.onFx {
                        val loaded = Fxml.load<Any?>(FxTestSupport.context(lang), path)
                        loaded.root.stylesheets.add(Theme.stylesheet(Theme.SHADCN_COMPONENTS))
                        val scene = Scene(loaded.root, Theme.AUTH_WIDTH, Theme.AUTH_HEIGHT, Color.TRANSPARENT)
                        Theme.apply(scene, lang)
                        scene.stylesheets.add(1, Theme.stylesheet("theme-shadcn.css"))
                        loaded.root.applyCss()
                        loaded.root.layout()
                        val buttons = loaded.root.lookupAll(".button").filterIsInstance<Button>()
                            .filter { it.id in setOf("enterButton", "enterSteam", "register", "restore") }
                        buttons.forEach { b ->
                            assertTrue(b.text.isNotBlank(), "${b.id} has no caption")
                            assertEquals(javafx.scene.control.ContentDisplay.TEXT_ONLY, b.contentDisplay, "${b.id} caption hidden")
                        }
                        val brand = loaded.root.lookup(".window-brand") as Labeled
                        assertTrue(brand.isVisible, "brand label hidden in shadcn theme")
                    }
                }
            }
        }
}

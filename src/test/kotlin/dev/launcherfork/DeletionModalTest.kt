package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.screens.window.WindowController
import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Deletion confirmation: flow layout keeps the buttons inside the card in every theme/language; Esc/focus. */
class DeletionModalTest {
    private class Live(val app: AppContext, val nav: StageNavigator, var exits: Int = 0, var deletionShown: Int = 0)

    private fun mainOnScreen(lang: String = "ru"): Live {
        val app = AppContext.mock(authDelayMs = 0)
        return FxTestSupport.onFx {
            val nav = StageNavigator(Stage(StageStyle.UNDECORATED), app, lang)
            val live = Live(app, nav)
            nav.exit = { live.exits++ }
            nav.showMain()
            nav.mainStage.show()
            nav.mainController!!.deletionPaneController.layer.visibleProperty().addListener { _, _, v -> if (v) live.deletionShown++ }
            live
        }
    }

    private fun window(nav: StageNavigator): WindowController {
        val f = StageNavigator::class.java.getDeclaredField("windowController").apply { isAccessible = true }
        return f.get(nav) as WindowController
    }

    private fun Node.sceneBounds(): Bounds = localToScene(layoutBounds)

    private fun assertInside(inner: Bounds, outer: Bounds, what: String) {
        assertTrue(
            inner.minX >= outer.minX - 0.5 && inner.maxX <= outer.maxX + 0.5 &&
                inner.minY >= outer.minY - 0.5 && inner.maxY <= outer.maxY + 0.5,
            "$what $inner is not inside $outer",
        )
    }

    @Test
    fun buttonsStayInsideTheCardInEveryThemeAndLanguage() {
        listOf("ru", "en").forEach { lang ->
            val live = mainOnScreen(lang)
            listOf("exbo", "shadcn", "zone").forEach { theme ->
                FxTestSupport.onFx {
                    val scene = live.nav.mainStage.scene
                    val sheets = mutableListOf(Theme.stylesheet("theme.css"))
                    if (theme == "shadcn") sheets += Theme.stylesheet("theme-shadcn.css")
                    if (theme == "zone") { Theme.zoneFonts; sheets += Theme.stylesheet("theme-zone.css") }
                    sheets += Theme.stylesheet(if (lang == "en") "lang_en.css" else "lang_ru.css")
                    scene.stylesheets.setAll(sheets)
                    val m = live.nav.mainController!!.deletionPaneController
                    m.show()
                    scene.root.applyCss(); scene.root.layout()
                    val card = m.deletionCard.sceneBounds()
                    val where = "$theme/$lang"
                    assertTrue(card.height > 0 && card.width > 0, "$where: card not laid out")
                    // 1 px border + padding: the buttons have real air to the card edges, not just "touching"
                    listOf(m.deletionYes, m.deletionNo).forEach { b ->
                        val bb = b.sceneBounds()
                        assertInside(bb, card, "$where: button '${b.text}'")
                        assertTrue(card.maxY - bb.maxY >= 12.0, "$where: no bottom padding under '${b.text}' (${card.maxY - bb.maxY})")
                        assertTrue(card.maxX - bb.maxX >= 12.0 || b === m.deletionYes, "$where: no right padding")
                        assertTrue(b.width >= b.prefWidth(-1.0) - 0.5, "$where: '${b.text}' is squeezed (text clipped)")
                    }
                    assertInside(m.deletionTitle.sceneBounds(), card, "$where: title")
                    assertInside(m.deletionDesc.sceneBounds(), card, "$where: text")
                    assertTrue(m.deletionDesc.sceneBounds().maxY <= m.deletionYes.sceneBounds().minY, "$where: text overlaps the buttons")
                    // the card is sized by its content: no empty band under the buttons
                    assertTrue(card.maxY - m.deletionNo.sceneBounds().maxY <= 40.0, "$where: card taller than its content")
                    assertInside(card, javafx.geometry.BoundingBox(0.0, 0.0, scene.width, scene.height), "$where: card")
                    if (theme == "zone" && lang == "ru") {
                        val dir = File(System.getProperty("zone.preview", "build/zone-preview")).apply { mkdirs() }
                        val img = scene.snapshot(null)
                        val out = BufferedImage(img.width.toInt(), img.height.toInt(), BufferedImage.TYPE_INT_ARGB)
                        for (y in 0 until out.height) for (x in 0 until out.width) out.setRGB(x, y, img.pixelReader.getArgb(x, y))
                        ImageIO.write(out, "png", File(dir, "deletion_modal.png"))
                    }
                    m.hide()
                }
            }
            FxTestSupport.onFx { live.nav.close() }
            live.app.shutdown()
        }
    }

    private fun press(node: Node, code: KeyCode) =
        node.fireEvent(KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))

    @Test
    fun focusStartsOnCancelEscCancelsPrimaryStaysRed() {
        val live = mainOnScreen()
        FxTestSupport.onFx {
            val main = live.nav.mainController!!
            val m = main.deletionPaneController
            val scene = live.nav.mainStage.scene
            var confirmed = 0; var cancelled = 0
            m.onConfirm = { confirmed++ }; m.onCancel = { cancelled++ }
            m.show()
            assertTrue(m.isShowing)
            assertSame(m.deletionNo, scene.focusOwner, "focus must start on Cancel")
            press(scene.focusOwner, KeyCode.ESCAPE)
            assertFalse(m.isShowing, "Esc cancels")
            assertEquals(0, confirmed, "Esc must not confirm the deletion")
            assertTrue("btn-primary" in m.deletionYes.styleClass && "btn-outline" in m.deletionNo.styleClass)
            m.show()
            m.deletionNo.fire()
            assertFalse(m.isShowing); assertEquals(1, cancelled); assertEquals(0, confirmed)
            m.show()
            m.deletionYes.fire()
            assertFalse(m.isShowing); assertEquals(1, confirmed)
            live.nav.close()
        }
        live.app.shutdown()
    }
}

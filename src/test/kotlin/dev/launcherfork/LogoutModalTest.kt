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
import javafx.stage.WindowEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * "Выход из аккаунта" confirmation of the main window: buttons inside the card in every theme and language,
 * Enter / Esc / initial focus, and the only way to open it is the profile menu item (never the window close).
 */
class LogoutModalTest {
    private class Live(val app: AppContext, val nav: StageNavigator, var exits: Int = 0, var logoutShown: Int = 0)

    private fun mainOnScreen(lang: String = "ru"): Live {
        val app = AppContext.mock(authDelayMs = 0)
        return FxTestSupport.onFx {
            val nav = StageNavigator(Stage(StageStyle.UNDECORATED), app, lang)
            val live = Live(app, nav)
            nav.exit = { live.exits++ }
            nav.showMain()
            nav.mainStage.show()
            nav.mainController!!.logoutPaneController.layer.visibleProperty().addListener { _, _, v -> if (v) live.logoutShown++ }
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
                    val m = live.nav.mainController!!.logoutPaneController
                    m.show()
                    scene.root.applyCss(); scene.root.layout()
                    val card = m.logoutCard.sceneBounds()
                    val where = "$theme/$lang"
                    assertTrue(card.height > 0 && card.width > 0, "$where: card not laid out")
                    // 1 px border + padding: the buttons have real air to the card edges, not just "touching"
                    listOf(m.logoutButtonRight, m.logoutButtonLeft).forEach { b ->
                        val bb = b.sceneBounds()
                        assertInside(bb, card, "$where: button '${b.text}'")
                        assertTrue(card.maxY - bb.maxY >= 12.0, "$where: no bottom padding under '${b.text}' (${card.maxY - bb.maxY})")
                        assertTrue(card.maxX - bb.maxX >= 12.0 || b === m.logoutButtonRight, "$where: no right padding")
                        assertTrue(b.width >= b.prefWidth(-1.0) - 0.5, "$where: '${b.text}' is squeezed (text clipped)")
                    }
                    assertInside(m.logoutTitle.sceneBounds(), card, "$where: title")
                    assertInside(m.logoutDesc.sceneBounds(), card, "$where: text")
                    assertTrue(m.logoutDesc.sceneBounds().maxY <= m.logoutButtonRight.sceneBounds().minY, "$where: text overlaps the buttons")
                    // the card is sized by its content: no empty band under the buttons
                    assertTrue(card.maxY - m.logoutButtonLeft.sceneBounds().maxY <= 40.0, "$where: card taller than its content")
                    assertInside(card, javafx.geometry.BoundingBox(0.0, 0.0, scene.width, scene.height), "$where: card")
                    if (theme == "zone" && lang == "ru") {
                        val dir = File(System.getProperty("zone.preview", "build/zone-preview")).apply { mkdirs() }
                        val img = scene.snapshot(null)
                        val out = BufferedImage(img.width.toInt(), img.height.toInt(), BufferedImage.TYPE_INT_ARGB)
                        for (y in 0 until out.height) for (x in 0 until out.width) out.setRGB(x, y, img.pixelReader.getArgb(x, y))
                        ImageIO.write(out, "png", File(dir, "logout_modal.png"))
                    }
                    m.hide()
                }
            }
            FxTestSupport.onFx { live.nav.close() }
            live.app.shutdown()
        }
    }

    @Test
    fun titleAndButtonsSayLogOut() {
        val live = mainOnScreen()
        FxTestSupport.onFx {
            val m = live.nav.mainController!!.logoutPaneController
            assertEquals("Выход из аккаунта", m.logoutTitle.text)
            assertEquals("ВЫЙТИ", m.logoutButtonRight.text)
            assertEquals("ОТМЕНА", m.logoutButtonLeft.text)
            assertEquals("Выйти из аккаунта", live.nav.mainController!!.profileBarController.logout.text)
            assertTrue("btn-primary" in m.logoutButtonRight.styleClass && "btn-outline" in m.logoutButtonLeft.styleClass)
            assertTrue(listOf(m.logoutTitle, m.logoutButtonRight, m.logoutButtonLeft).all { "font-display" in it.styleClass })
            live.nav.close()
        }
        live.app.shutdown()
    }

    private fun press(node: Node, code: KeyCode) =
        node.fireEvent(KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))

    @Test
    fun focusStartsOnCancelEscCancelsEnterLogsOut() {
        val live = mainOnScreen()
        FxTestSupport.onFx {
            val main = live.nav.mainController!!
            val m = main.logoutPaneController
            val scene = live.nav.mainStage.scene
            main.profileBarController.loginButton.fire()
            main.profileBarController.logout.fire()
            assertTrue(m.isShowing)
            assertSame(m.logoutButtonLeft, scene.focusOwner, "focus must start on Cancel")
            press(scene.focusOwner, KeyCode.ESCAPE)
            assertFalse(m.isShowing, "Esc cancels")
            assertSame(main, live.nav.mainController, "Esc must not log out")

            main.profileBarController.logout.fire()
            assertSame(m.logoutButtonLeft, scene.focusOwner)
            press(scene.focusOwner, KeyCode.ENTER) // Enter = confirm, even with the focus on Cancel
        }
        assertTrue(FxTestSupport.waitFx { live.nav.mainController == null && live.nav.authStage.isShowing }, "Enter must log out")
        FxTestSupport.onFx { live.nav.close() }
        live.app.shutdown()
    }

    @Test
    fun closingTheWindowNeverAsksToLogOut() {
        // the title-bar X
        val byButton = mainOnScreen()
        FxTestSupport.onFx { window(byButton.nav).closeButton.fire() }
        FxTestSupport.onFx {
            assertEquals(1, byButton.exits, "X exits the launcher")
            assertEquals(0, byButton.logoutShown, "X must not open the logout confirmation")
            assertFalse(byButton.nav.mainStage.isShowing)
        }
        byButton.app.shutdown()

        // Alt+F4 / taskbar "Close window" = a close request of the stage
        val byRequest = mainOnScreen()
        FxTestSupport.onFx {
            val stage = byRequest.nav.mainStage
            stage.fireEvent(WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST))
        }
        FxTestSupport.onFx {
            assertEquals(1, byRequest.exits)
            assertEquals(0, byRequest.logoutShown, "a close request must not open the logout confirmation")
        }
        byRequest.app.shutdown()
    }

    @Test
    fun onlyTheProfileLogoutItemOpensIt() {
        val live = mainOnScreen()
        FxTestSupport.onFx {
            val main = live.nav.mainController!!
            // Esc and the other window buttons do not open it
            press(main.root, KeyCode.ESCAPE)
            window(live.nav).hideButton.fire()
            assertEquals(0, live.logoutShown)
            main.profileBarController.loginButton.fire()
            main.profileBarController.logout.fire()
            assertEquals(1, live.logoutShown, "profile menu 'Выйти из аккаунта' asks once")
            main.logoutPaneController.logoutButtonLeft.fire()
            assertFalse(main.logoutPaneController.isShowing)
            // "Сменить аккаунт" leaves at once, without the confirmation
            main.profileBarController.loginButton.fire()
            main.profileBarController.switchAccount.fire()
        }
        assertTrue(FxTestSupport.waitFx { live.nav.mainController == null })
        FxTestSupport.onFx {
            assertEquals(1, live.logoutShown, "switch account must not ask")
            live.nav.close()
        }
        live.app.shutdown()
    }
}

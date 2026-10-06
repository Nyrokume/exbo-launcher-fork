package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.theme.Theme
import javafx.event.Event
import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Region
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Top-right row of the main window: ФОРУМ ↗ / ПОДДЕРЖКА ↗ / nickname. The profile dropdown floats under the
 * nickname (right-aligned with it) and must not move anything in the row; the gaps between the three are equal.
 * Checked in every theme with localToScene bounds.
 */
class HeaderMenuLayoutTest {
    private fun Node.sceneBounds(): Bounds = localToScene(layoutBounds)

    private fun withMain(check: (StageNavigator, String) -> Unit) {
        val app = AppContext.mock(authDelayMs = 0)
        val nav = FxTestSupport.onFx {
            StageNavigator(Stage(StageStyle.UNDECORATED), app, "ru").also { it.exit = {}; it.showMain(); it.mainStage.show() }
        }
        try {
            listOf("exbo", "shadcn", "zone").forEach { theme ->
                FxTestSupport.onFx {
                    val scene = nav.mainStage.scene
                    val sheets = mutableListOf(Theme.stylesheet("theme.css"))
                    if (theme == "shadcn") sheets += Theme.stylesheet("theme-shadcn.css")
                    if (theme == "zone") { Theme.zoneFonts; sheets += Theme.stylesheet("theme-zone.css") }
                    sheets += Theme.stylesheet("lang_ru.css")
                    scene.stylesheets.setAll(sheets)
                    relayout(nav)
                    check(nav, theme)
                }
            }
        } finally {
            FxTestSupport.onFx { nav.close() }
            app.shutdown()
        }
    }

    // second pass: the links row height is bound to the profile button height measured in the first
    private fun relayout(nav: StageNavigator) = nav.mainStage.scene.root.run { applyCss(); layout(); layout() }

    private fun topBar(nav: StageNavigator) = nav.mainController!!.root.lookup("#topBar") as Region

    private data class Row(val forum: Bounds, val support: Bounds, val nick: Bounds)

    private fun row(nav: StageNavigator): Row {
        val main = nav.mainController!!
        return Row(
            main.headerController.forum.sceneBounds(),
            main.headerController.support.sceneBounds(),
            main.profileBarController.loginButton.sceneBounds(),
        )
    }

    @Test
    fun openingTheProfileMenuDoesNotMoveTheHeaderLinks() = withMain { nav, theme ->
        val profile = nav.mainController!!.profileBarController
        val closed = row(nav)
        val barWidth = topBar(nav).width

        profile.showMenu()
        relayout(nav)
        assertTrue(profile.isMenuShown, "$theme: menu did not open")
        val open = row(nav)
        listOf(
            Triple("ФОРУМ", closed.forum, open.forum),
            Triple("ПОДДЕРЖКА", closed.support, open.support),
            Triple("nickname", closed.nick, open.nick),
        ).forEach { (what, c, o) ->
            assertEquals(c.minX, o.minX, 0.5, "$theme: $what moved left/right when the menu opened ($c -> $o)")
            assertEquals(c.maxX, o.maxX, 0.5, "$theme: $what changed width when the menu opened ($c -> $o)")
            assertEquals(c.centerY, o.centerY, 0.5, "$theme: $what moved up/down when the menu opened ($c -> $o)")
        }
        assertEquals(barWidth, topBar(nav).width, 0.5, "$theme: the header row grew with the open menu")

        // the menu floats under the nickname, right edges aligned
        val menu = profile.profileMenu.sceneBounds()
        assertTrue(menu.width > 100 && menu.height > 60, "$theme: menu not laid out: $menu")
        assertEquals(open.nick.maxX, menu.maxX, 0.5, "$theme: menu $menu is not right-aligned with the nickname ${open.nick}")
        assertTrue(menu.minY >= open.nick.maxY - 0.5, "$theme: menu $menu overlaps the nickname ${open.nick}")
        assertTrue(menu.minY - open.nick.maxY <= 12.0, "$theme: menu $menu hangs too far below the nickname ${open.nick}")
        // still clickable: every ancestor's picking bounds cover the floating card (nothing clips it away)
        generateSequence(profile.profileMenu.parent) { it.parent }.forEach { p ->
            val pb = p.localToScene(p.boundsInLocal)
            assertTrue(pb.contains(menu.centerX, menu.centerY), "$theme: ${p.id ?: p} does not cover the menu ($pb vs $menu)")
            assertTrue(p.clip == null, "$theme: ${p.id ?: p} clips the menu")
        }
        profile.hideMenu()
        relayout(nav)
    }

    @Test
    fun gapsBetweenLinksAndNicknameAreEqual() = withMain { nav, theme ->
        val profile = nav.mainController!!.profileBarController
        listOf(false, true).forEach { menuOpen ->
            if (menuOpen) profile.showMenu() else profile.hideMenu()
            relayout(nav)
            val r = row(nav)
            val linkGap = r.support.minX - r.forum.maxX
            val nickGap = r.nick.minX - r.support.maxX
            val where = "$theme, menu ${if (menuOpen) "open" else "closed"}"
            assertTrue(linkGap > 4.0, "$where: ФОРУМ / ПОДДЕРЖКА overlap ($linkGap)")
            assertEquals(linkGap, nickGap, 1.0, "$where: ФОРУМ↔ПОДДЕРЖКА $linkGap vs ПОДДЕРЖКА↔nickname $nickGap")
        }
        profile.hideMenu()
        relayout(nav)
    }

    @Test
    fun menuStillClosesOnEscAndOutsideClick() = withMain { nav, theme ->
        val main = nav.mainController!!
        val profile = main.profileBarController
        val scene = nav.mainStage.scene

        profile.loginButton.fire()
        assertTrue(profile.isMenuShown, "$theme: button opens the menu")
        main.root.fireEvent(KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false))
        assertFalse(profile.isMenuShown, "$theme: Esc closes the menu")

        profile.loginButton.fire()
        relayout(nav)
        // a press inside the floating menu keeps it open, a press elsewhere closes it
        Event.fireEvent(profile.switchAccount, press(scene, profile.switchAccount))
        assertTrue(profile.isMenuShown, "$theme: a press inside the menu must not close it")
        Event.fireEvent(main.centerPane, press(scene, main.centerPane))
        assertFalse(profile.isMenuShown, "$theme: an outside press closes the menu")
    }

    @Test
    fun hiddenHeaderLinksLeaveTheLayout() = withMain { nav, theme ->
        val main = nav.mainController!!
        val header = main.headerController.headerPane
        val nick = main.profileBarController.loginButton.sceneBounds()
        main.setSettingsVisible(true)
        relayout(nav)
        assertFalse(header.isVisible, "$theme: links must hide on the settings screen")
        assertFalse(header.isManaged, "$theme: hidden links must not take space in the row")
        assertEquals(nick.maxX, main.profileBarController.loginButton.sceneBounds().maxX, 0.5, "$theme: nickname moved")
        main.setSettingsVisible(false)
        relayout(nav)
        assertTrue(header.isVisible && header.isManaged, "$theme: links back on the home screen")
    }

    private fun press(scene: Scene, target: Node): MouseEvent {
        val b = target.sceneBounds()
        return MouseEvent(
            scene, target, MouseEvent.MOUSE_PRESSED, b.centerX, b.centerY, b.centerX, b.centerY, MouseButton.PRIMARY, 1,
            false, false, false, false, true, false, false, true, false, false, null,
        )
    }
}

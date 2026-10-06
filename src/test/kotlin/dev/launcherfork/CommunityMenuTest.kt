package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.domain.CommunityLink
import dev.launcherfork.domain.CommunityLinks
import dev.launcherfork.domain.LinkOpener
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.components.BrandGlyphs
import dev.launcherfork.ui.screens.main.HeaderController
import javafx.event.Event
import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Region
import javafx.scene.shape.SVGPath
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Properties

/**
 * Header «СООБЩЕСТВА ▾» dropdown (top-right row: СООБЩЕСТВА ▾ / ФОРУМ ↗ / ПОДДЕРЖКА ↗ / nickname). Same rules as the
 * profile dropdown (HeaderMenuLayoutTest): a floating card that never moves the row, equal 20 px gaps, inside the
 * window, closes on Esc / outside press, one menu at a time; rows follow CommunityLinks and open their URL through
 * the injected LinkOpener. Checked in every theme with localToScene bounds.
 */
class CommunityMenuTest {
    private fun Node.sceneBounds(): Bounds = localToScene(layoutBounds)

    private val opened = mutableListOf<String>()

    private fun withMain(check: (StageNavigator, String) -> Unit) {
        val app = AppContext.mock(authDelayMs = 0, links = LinkOpener { opened += it })
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
                    nav.mainController!!.hideMenus()
                    relayout(nav)
                }
            }
        } finally {
            FxTestSupport.onFx { nav.close() }
            app.shutdown()
        }
    }

    // second pass: the links row height is bound to the profile button height measured in the first
    private fun relayout(nav: StageNavigator) = nav.mainStage.scene.root.run { applyCss(); layout(); layout() }

    private fun header(nav: StageNavigator): HeaderController = nav.mainController!!.headerController
    private fun topBar(nav: StageNavigator) = nav.mainController!!.root.lookup("#topBar") as Region

    /** СООБЩЕСТВА, ФОРУМ, ПОДДЕРЖКА, nickname - left to right. */
    private fun row(nav: StageNavigator): List<Pair<String, Bounds>> {
        val main = nav.mainController!!
        return listOf(
            "СООБЩЕСТВА" to main.headerController.communities.sceneBounds(),
            "ФОРУМ" to main.headerController.forum.sceneBounds(),
            "ПОДДЕРЖКА" to main.headerController.support.sceneBounds(),
            "nickname" to main.profileBarController.loginButton.sceneBounds(),
        )
    }

    private fun key(code: KeyCode, shift: Boolean = false) =
        KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false)

    @Test
    fun openingTheCommunitiesMenuDoesNotMoveTheHeader() = withMain { nav, theme ->
        val header = header(nav)
        val closed = row(nav)
        val barWidth = topBar(nav).width

        header.showCommunityMenu()
        relayout(nav)
        assertTrue(header.isCommunityMenuShown, "$theme: menu did not open")
        assertTrue(header.communities.styleClass.contains("open"), "$theme: the caret does not flip (no .open)")
        row(nav).zip(closed).forEach { (o, c) ->
            val what = o.first
            assertEquals(c.second.minX, o.second.minX, 0.5, "$theme: $what moved left/right (${c.second} -> ${o.second})")
            assertEquals(c.second.maxX, o.second.maxX, 0.5, "$theme: $what changed width (${c.second} -> ${o.second})")
            assertEquals(c.second.centerY, o.second.centerY, 0.5, "$theme: $what moved up/down (${c.second} -> ${o.second})")
        }
        assertEquals(barWidth, topBar(nav).width, 0.5, "$theme: the header row grew with the open menu")
    }

    @Test
    fun gapsStayEqual() = withMain { nav, theme ->
        val main = nav.mainController!!
        listOf("closed", "communities open", "profile open").forEach { state ->
            main.hideMenus()
            if (state == "communities open") main.headerController.showCommunityMenu()
            if (state == "profile open") main.profileBarController.showMenu()
            relayout(nav)
            val r = row(nav)
            assertTrue(r.zipWithNext().all { (a, b) -> a.second.maxX <= b.second.minX }, "$theme, $state: row out of order $r")
            r.zipWithNext().forEach { (a, b) ->
                assertEquals(20.0, b.second.minX - a.second.maxX, 1.0, "$theme, $state: gap ${a.first} ↔ ${b.first}")
            }
            // the toggle reads as one of the links: same vertical centre as ФОРУМ
            assertEquals(r[1].second.centerY, r[0].second.centerY, 1.0, "$theme, $state: СООБЩЕСТВА not level with ФОРУМ")
        }
    }

    @Test
    fun menuFloatsUnderTheLinkInsideTheWindow() = withMain { nav, theme ->
        val header = header(nav)
        val scene = nav.mainStage.scene
        listOf(Theme.WINDOW_WIDTH to Theme.WINDOW_HEIGHT, Theme.WINDOW_MIN_WIDTH to Theme.WINDOW_MIN_HEIGHT).forEach { (w, h) ->
            nav.mainStage.width = w
            nav.mainStage.height = h
            relayout(nav)
            header.showCommunityMenu()
            relayout(nav)
            val link = header.communities.sceneBounds()
            val menu = header.communityMenu.sceneBounds()
            val where = "$theme ${w.toInt()}x${h.toInt()}"
            assertTrue(menu.width > 120 && menu.height > 150, "$where: menu not laid out: $menu")
            assertTrue(menu.minX >= 0 && menu.maxX <= scene.width && menu.minY >= 0 && menu.maxY <= scene.height,
                "$where: menu $menu outside the window ${scene.width}x${scene.height}")
            assertEquals(link.minX, menu.minX, 1.0, "$where: menu $menu not left-aligned with СООБЩЕСТВА $link")
            assertTrue(menu.minY >= link.maxY, "$where: menu $menu overlaps the link $link")
            assertTrue(menu.minY - link.maxY <= 24.0, "$where: menu $menu hangs too far below the link $link")
            // same top as the profile dropdown would have (both hang under the header row)
            val profile = nav.mainController!!.profileBarController
            profile.showMenu(); relayout(nav)
            val profileTop = profile.profileMenu.sceneBounds().minY
            header.showCommunityMenu(); relayout(nav)
            assertEquals(profileTop, header.communityMenu.sceneBounds().minY, 1.0, "$where: tops of the two dropdowns differ")
            // clickable: every ancestor's picking bounds cover the card, nothing clips it
            generateSequence(header.communityMenu.parent) { it.parent }.forEach { p ->
                val pb = p.localToScene(p.boundsInLocal)
                assertTrue(pb.contains(menu.centerX, menu.centerY), "$where: ${p.id ?: p} does not cover the menu ($pb vs $menu)")
                assertTrue(p.clip == null, "$where: ${p.id ?: p} clips the menu")
            }
            // rows: icon tile left, all tiles in one column, square, inside their row
            val tiles = header.communityItems.map { it.lookup(".community-icon-tile") as Region }
            tiles.forEach { t ->
                val b = t.sceneBounds()
                assertEquals(b.width, b.height, 0.5, "$where: tile not square $b")
                assertTrue(b.width in 24.0..28.0, "$where: tile size ${b.width}")
                assertEquals(tiles[0].sceneBounds().minX, b.minX, 0.5, "$where: tiles not in one column")
                assertTrue(menu.contains(b), "$where: tile $b sticks out of the card $menu")
            }
            header.hideCommunityMenu()
        }
        nav.mainStage.width = Theme.WINDOW_WIDTH
        nav.mainStage.height = Theme.WINDOW_HEIGHT
    }

    @Test
    fun itemsFollowCommunityLinksInOrder() = withMain { nav, theme ->
        assertEquals(
            listOf(
                "Discord" to "https://discord.gg/stalzone",
                "YouTube" to "https://www.youtube.com/@stalzone_eng",
                "Reddit" to "https://www.reddit.com/r/stalzone/",
                "Telegram" to "https://t.me/stalzone_eng",
            ),
            CommunityLinks.all.map { it.title to it.url },
        )
        val items = header(nav).communityItems
        assertEquals(CommunityLinks.all, items.map { it.userData as CommunityLink }, "$theme: rows not in CommunityLinks order")
        items.zip(CommunityLinks.all).forEach { (item, link) ->
            assertEquals("community-${link.id}", item.id)
            assertEquals(link.title, (item.lookup(".community-item-name") as Label).text, "$theme: row name")
            assertNotNull(item.lookup(".external-arrow"), "$theme: ${link.id} has no ↗")
            val glyph = item.lookup(".community-glyph") as SVGPath
            assertEquals(BrandGlyphs.path(link.id), glyph.content, "$theme: ${link.id} glyph")
            val g = glyph.boundsInParent // scaled
            assertTrue(g.width > 8 && g.height > 8 && maxOf(g.width, g.height) <= 14.5, "$theme: ${link.id} glyph size $g")
            assertTrue(item.isFocusTraversable, "$theme: ${link.id} row not focusable")
            assertTrue(item.accessibleText.contains(link.title), "$theme: ${link.id} accessible text '${item.accessibleText}'")
        }
        assertTrue(header(nav).communities.isFocusTraversable, "$theme: СООБЩЕСТВА not focusable")
        assertEquals("Сообщества STALZONE", header(nav).communities.accessibleText)
    }

    @Test
    fun glyphsAreCentredInTheirTiles() = withMain { nav, theme ->
        val header = header(nav)
        header.showCommunityMenu()
        relayout(nav)
        header.communityItems.forEach { item ->
            val tile = (item.lookup(".community-icon-tile") as Region).sceneBounds()
            val glyph = item.lookup(".community-glyph")!!.let { it.localToScene(it.boundsInLocal) }
            assertEquals(tile.centerX, glyph.centerX, 0.75, "$theme: ${item.id} glyph off-centre horizontally")
            assertEquals(tile.centerY, glyph.centerY, 0.75, "$theme: ${item.id} glyph off-centre vertically")
        }
    }

    @Test
    fun onlyOneMenuIsOpenAtATime() = withMain { nav, theme ->
        val main = nav.mainController!!
        val header = main.headerController
        val profile = main.profileBarController

        header.communities.fire()
        assertTrue(header.isCommunityMenuShown && !profile.isMenuShown, "$theme: communities open")
        profile.loginButton.fire()
        assertTrue(profile.isMenuShown, "$theme: profile open")
        assertFalse(header.isCommunityMenuShown, "$theme: opening the profile menu must close the communities menu")
        header.communities.fire()
        assertTrue(header.isCommunityMenuShown, "$theme: communities open again")
        assertFalse(profile.isMenuShown, "$theme: opening the communities menu must close the profile menu")
        header.communities.fire()
        assertFalse(header.isCommunityMenuShown, "$theme: the link toggles the menu")
        assertFalse(header.communities.styleClass.contains("open"), "$theme: caret stays flipped")
    }

    @Test
    fun escAndOutsideClickClose() = withMain { nav, theme ->
        val main = nav.mainController!!
        val header = main.headerController
        val scene = nav.mainStage.scene

        header.communities.fire()
        main.root.fireEvent(key(KeyCode.ESCAPE))
        assertFalse(header.isCommunityMenuShown, "$theme: Esc closes the menu")

        header.communities.fire()
        relayout(nav)
        Event.fireEvent(header.communityItems[2], press(scene, header.communityItems[2]))
        assertTrue(header.isCommunityMenuShown, "$theme: a press inside the menu must not close it")
        Event.fireEvent(header.forum, press(scene, header.forum))
        assertFalse(header.isCommunityMenuShown, "$theme: a press on ФОРУМ closes the menu")

        header.communities.fire()
        relayout(nav)
        Event.fireEvent(main.centerPane, press(scene, main.centerPane))
        assertFalse(header.isCommunityMenuShown, "$theme: an outside press closes the menu")

        header.communities.fire()
        main.setSettingsVisible(true)
        assertFalse(header.isCommunityMenuShown, "$theme: switching to settings closes the open menu")
        assertTrue(header.headerPane.isVisible, "$theme: the links stay in the nav row on settings")
        main.setSettingsVisible(false)
    }

    @Test
    fun clickingAnItemOpensItsUrl() = withMain { nav, theme ->
        val header = header(nav)
        CommunityLinks.all.forEachIndexed { i, link ->
            opened.clear()
            header.communities.fire()
            relayout(nav)
            header.communityItems[i].fire()
            assertEquals(listOf(link.url), opened, "$theme: ${link.id}")
            assertFalse(header.isCommunityMenuShown, "$theme: a pick closes the menu")
        }
    }

    @Test
    fun keyboardOpensMovesAndPicks() = withMain { nav, theme ->
        val header = header(nav)
        val scene = nav.mainStage.scene
        val items = header.communityItems
        opened.clear()

        header.communities.requestFocus()
        Event.fireEvent(header.communities, key(KeyCode.ENTER))
        assertTrue(header.isCommunityMenuShown, "$theme: Enter opens the menu")
        assertSame(items[0], scene.focusOwner, "$theme: first row focused")
        Event.fireEvent(items[0], key(KeyCode.DOWN))
        assertSame(items[1], scene.focusOwner, "$theme: Down moves to YouTube")
        Event.fireEvent(items[1], key(KeyCode.TAB))
        assertSame(items[2], scene.focusOwner, "$theme: Tab moves to Reddit")
        Event.fireEvent(items[2], key(KeyCode.TAB, shift = true))
        assertSame(items[1], scene.focusOwner, "$theme: Shift+Tab back to YouTube")
        Event.fireEvent(items[1], key(KeyCode.UP))
        Event.fireEvent(items[0], key(KeyCode.UP))
        assertSame(items.last(), scene.focusOwner, "$theme: Up wraps to Telegram")
        Event.fireEvent(items.last(), key(KeyCode.ENTER))
        assertEquals(listOf(CommunityLinks.TELEGRAM.url), opened, "$theme: Enter opens the focused link")
        assertFalse(header.isCommunityMenuShown, "$theme: Enter closes the menu")
        assertSame(header.communities, scene.focusOwner, "$theme: focus back on СООБЩЕСТВА")

        Event.fireEvent(header.communities, key(KeyCode.SPACE))
        assertTrue(header.isCommunityMenuShown, "$theme: Space opens the menu")
        Event.fireEvent(scene.focusOwner, key(KeyCode.ESCAPE))
        assertFalse(header.isCommunityMenuShown, "$theme: Esc closes it")
        assertSame(header.communities, scene.focusOwner, "$theme: Esc returns focus to СООБЩЕСТВА")
    }

    /** Strings in all three bundles (default = ru, ru, en). */
    @Test
    fun stringsInEveryBundle() {
        val keys = listOf("main.link.communities", "main.community.a11y", "main.community.a11y.help",
            "main.community.menu.a11y", "main.community.item.a11y")
        listOf("messages.properties", "messages_ru.properties", "messages_en.properties").forEach { file ->
            val props = Properties()
            javaClass.getResourceAsStream("/dev/launcherfork/ui/i18n/$file")!!.reader(Charsets.UTF_8).use(props::load)
            keys.forEach { k -> assertTrue(!props.getProperty(k).isNullOrBlank(), "$file misses $k") }
            assertTrue(props.getProperty("main.community.item.a11y").contains("%s"), "$file: item text needs %s")
        }
    }

    private fun press(scene: Scene, target: Node): MouseEvent {
        val b = target.sceneBounds()
        return MouseEvent(
            scene, target, MouseEvent.MOUSE_PRESSED, b.centerX, b.centerY, b.centerX, b.centerY, MouseButton.PRIMARY, 1,
            false, false, false, false, true, false, false, true, false, false, null,
        )
    }
}

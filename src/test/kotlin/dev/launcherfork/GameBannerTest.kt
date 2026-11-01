package dev.launcherfork

import dev.launcherfork.UiTestKit.THEMES
import dev.launcherfork.UiTestKit.applyTheme
import dev.launcherfork.UiTestKit.mainOnScreen
import dev.launcherfork.UiTestKit.preview
import dev.launcherfork.UiTestKit.relayout
import dev.launcherfork.UiTestKit.resize
import dev.launcherfork.UiTestKit.sceneBounds
import dev.launcherfork.app.AppContext
import dev.launcherfork.data.mock.MockData
import dev.launcherfork.data.mock.MockServerRepository
import dev.launcherfork.domain.GameDescription
import dev.launcherfork.i18n.Strings
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.screens.main.GameBannerController
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.Labeled
import javafx.scene.text.Text
import javafx.scene.text.TextBoundsType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Properties

/**
 * The STALZONE banner on the main screen: text from the game model (Server.description -> i18n keys, ru + en), no
 * second online count (it is shown once, in the games list), the layout (the column fills the centre slot and the
 * type scales with that width, never over the nav row, the status row or ИГРАТЬ; long text scales down onto one page) and the title's
 * letters starting on the line of the selected game row in the sidebar, at the minimum, the default, 1366x800 and a
 * maximized (1920x1080) window in every theme. Colors: SettingsContrastTest.gameBannerTextIsReadable.
 */
class GameBannerTest {
    private val stalzone = MockData.servers.single()

    private fun contains(outer: Bounds, inner: Bounds, slack: Double = 0.5) =
        inner.minX >= outer.minX - slack && inner.maxX <= outer.maxX + slack &&
            inner.minY >= outer.minY - slack && inner.maxY <= outer.maxY + slack

    /**
     * Scene Y of the top of [label]'s letters (the cap line of «STALZONE»): the label's text node gives the line box,
     * the font metrics (logical vs visual bounds of the same text) the distance from the line top to the ink.
     */
    private fun inkTop(label: Label): Double {
        val line = label.lookup(".text") as Text
        val logical = Text(label.text).apply { font = line.font }
        val visual = Text(label.text).apply { font = line.font; boundsType = TextBoundsType.VISUAL }
        return line.sceneBounds().minY + (visual.layoutBounds.minY - logical.layoutBounds.minY)
    }

    private fun overlaps(a: Bounds, b: Bounds) = a.minX < b.maxX - 0.5 && b.minX < a.maxX - 0.5 && a.minY < b.maxY - 0.5 && b.minY < a.maxY - 0.5

    @Test
    fun stringsAreInEveryBundle() {
        val keys = listOf("title", "lead", "subheading", "body", "closing").map { "game.stalzone.$it" }
        listOf("messages.properties", "messages_ru.properties", "messages_en.properties").forEach { file ->
            val props = Properties()
            javaClass.getResourceAsStream("/dev/launcherfork/ui/i18n/$file")!!.reader(Charsets.UTF_8).use(props::load)
            keys.forEach { k -> assertTrue(!props.getProperty(k).isNullOrBlank(), "$file misses $k") }
            // the banner's own online line is gone, and so are its plural strings
            assertTrue(props.stringPropertyNames().none { it.startsWith("main.game.players") }, "$file: leftover main.game.players.*")
            assertEquals("STALZONE", props.getProperty("game.stalzone.title"))
            assertFalse(props.values.any { "Starcraft" in it.toString() }, "$file: STALZONE, not Starcraft")
        }
        val ru = Strings.bundle("ru")
        assertTrue(ru.getString("game.stalzone.lead").startsWith("STALZONE — это extraction-шутер"))
        assertTrue(ru.getString("game.stalzone.closing").contains("Зона ждет"))
        assertTrue(Strings.bundle("en").getString("game.stalzone.closing").contains("The Zone is waiting"))
        assertEquals("STALZONE OTS", ru.getString("game.stalzone.ots.title"))
        assertEquals("STALZONE OTS", Strings.bundle("en").getString("game.stalzone.ots.title"))
    }

    @Test
    fun descriptionComesWithTheGameModel() {
        assertEquals(GameDescription.Keys("game.stalzone"), stalzone.description)
        val app = AppContext.mock(authDelayMs = 0)
        try {
            FxTestSupport.onFx {
                val c = Fxml.load<GameBannerController>(FxTestSupport.context("ru", app), "main/game_banner.fxml").controller
                // ready text from a (future) real server is shown as is
                c.show(stalzone.copy(description = GameDescription.Text("T", "L", "S", "B", "C")))
                assertTrue(c.scroll.isVisible)
                assertEquals(listOf("T", "L", "S", "B", "C"), listOf(c.title, c.lead, c.subheading, c.body, c.closing).map { it.text })
                // no description: no banner
                c.show(stalzone.copy(description = null))
                assertFalse(c.scroll.isVisible)
                c.show(stalzone)
                assertEquals("STALZONE", c.title.text)
                assertTrue(c.lead.text.startsWith("STALZONE — это extraction-шутер"))
                assertTrue(c.subheading.text.startsWith("Тебя ждет Чернобыльская Зона Отчуждения"))
                assertTrue(c.closing.text.contains("сталкер"))
            }
        } finally {
            app.shutdown()
        }
    }

    /** Visible text nodes of the scene that show [count] (with the locale's grouping, e.g. «1 284»). */
    private fun visibleTextsWith(root: Parent, count: String): List<String> {
        fun visible(n: Node): Boolean = generateSequence(n) { it.parent }.all { it.isVisible }
        fun walk(n: Node): Sequence<Node> = sequenceOf(n) + ((n as? Parent)?.childrenUnmodifiable?.asSequence()?.flatMap(::walk) ?: emptySequence())
        return walk(root).filter(::visible).mapNotNull { (it as? Labeled)?.text ?: (it as? Text)?.text }.filter { count in it }.toList()
    }

    @Test
    fun onlineCountIsShownOnlyOnceInTheGamesList() {
        val app = AppContext.mock(authDelayMs = 0)
        val nav = mainOnScreen(app, "ru")
        try {
            val repo = app.services.servers as MockServerRepository
            FxTestSupport.onFx {
                val banner = nav.mainController!!.gameBannerController
                assertTrue(banner.scroll.isVisible)
                assertNull(banner.scroll.lookup("#gameOnline")); assertNull(banner.scroll.lookup("#gameOnlineText"))
                assertTrue(banner.scroll.lookupAll(".game-banner-online").isEmpty(), "banner still has an online line")
                val bannerTexts = listOf(banner.title, banner.lead, banner.subheading, banner.body, banner.closing).map { it.text }
                assertTrue(bannerTexts.none { "1284" in it || Regex("""\d\s+игрок\S* онлайн""").containsMatchIn(it) }, "online count in the banner: $bannerTexts")
                val sidebar = nav.mainStage.scene.root.lookup("#entryOnlineText") as Text
                assertEquals("1284 онлайн", sidebar.text)
                assertEquals(listOf("1284 онлайн"), visibleTextsWith(nav.mainStage.scene.root, "1284"), "count shown more than once")
            }
            // a live change still reaches the games list, and still only there
            repo.setOnline(stalzone.id, 1301)
            check(FxTestSupport.waitFx { (nav.mainStage.scene.root.lookup("#entryOnlineText") as Text).text == "1301 онлайн" })
            FxTestSupport.onFx { assertEquals(1, visibleTextsWith(nav.mainStage.scene.root, "1301").size) }
        } finally {
            FxTestSupport.onFx { nav.close() }
            app.shutdown()
        }
    }

    @Test
    fun bannerFitsTheCentreSlotAndNeverCoversTheStatusRow() {
        listOf("ru", "en").forEach { lang ->
            val app = AppContext.mock(language = lang, authDelayMs = 0)
            val nav = mainOnScreen(app, lang)
            try {
                THEMES.forEach { theme ->
                    listOf(
                        Theme.WINDOW_MIN_WIDTH to Theme.WINDOW_MIN_HEIGHT, Theme.WINDOW_WIDTH to Theme.WINDOW_HEIGHT,
                        1366.0 to 800.0, 1920.0 to 1080.0, // 1920x1080: a maximized window on a Full HD screen
                    ).forEach { (w, h) ->
                        FxTestSupport.onFx { applyTheme(nav.mainStage.scene, theme, lang) }
                        resize(nav, w, h)
                        FxTestSupport.onFx {
                            val where = "$theme/$lang ${w.toInt()}x${h.toInt()}"
                            val main = nav.mainController!!
                            val b = main.gameBannerController
                            val scene = nav.mainStage.scene
                            val window = BoundingBox(0.0, 0.0, scene.width, scene.height)
                            val slot = main.centerPane.sceneBounds()
                            val banner = b.scroll.sceneBounds()
                            val status = main.defaultStatusPaneController
                            val play = status.downloadButton.sceneBounds()
                            val pill = status.statusPill.sceneBounds()
                            val bar = status.defaultStatusPane.sceneBounds()
                            val navRow = main.topBar.sceneBounds()
                            assertTrue(b.scroll.isVisible, "$where: banner hidden")
                            // ИГРАТЬ and the status row stay fully inside the window, the banner stays in its slot
                            listOf(play, pill, bar).forEach { assertTrue(contains(window, it), "$where: $it outside the window") }
                            assertTrue(contains(slot, banner), "$where: banner $banner outside the centre slot $slot")
                            listOf(play, pill, bar, navRow).forEach { assertFalse(overlaps(banner, it), "$where: banner $banner overlaps $it") }
                            assertTrue(banner.maxY <= bar.minY, "$where: banner reaches into the status row")
                            assertTrue(banner.minY >= navRow.maxY, "$where: banner reaches into the nav row")
                            // the description starts on the line of its game in the sidebar: the top of the title's
                            // letters on the top edge of the selected row (= the red marker), ±2 px
                            val row = main.serversListController.entryControllers.single { "selected" in it.root.styleClass }
                            val rowTop = row.root.sceneBounds().minY
                            assertEquals(row.cursor.sceneBounds().minY, rowTop, 0.5, "$where: marker not at the row top")
                            assertEquals(0.0, b.scroll.vvalue, "$where: banner scrolled")
                            assertEquals(rowTop, inkTop(b.title), 2.0, "$where: title top ${inkTop(b.title)} vs game row top $rowTop")
                            assertTrue(b.title.sceneBounds().minY >= banner.minY - 0.5, "$where: title clipped by the slot top")
                            // the column fills the slot; type is larger on a wide window than at the 560 px baseline. No truncation
                            val content = b.content.sceneBounds()
                            assertEquals(banner.width, content.width, 8.0, "$where: text column ${content.width} vs slot ${banner.width}")
                            val titlePx = (b.title.lookup(".text") as Text).font.size
                            if (w >= 1900) assertTrue(titlePx >= 52.0, "$where: title stayed $titlePx px")
                            assertEquals(slot.minX + 9, b.title.sceneBounds().minX, 1.0, "$where: title not at the left edge")
                            val labels = listOf(b.title, b.lead, b.subheading, b.body, b.closing)
                            labels.forEach { l: Label ->
                                assertTrue(l.text.isNotBlank(), "$where: ${l.id} empty")
                                assertEquals(b.title.sceneBounds().minX, l.sceneBounds().minX, 1.0, "$where: ${l.id} not left-aligned")
                                if (l.isWrapText) assertTrue(l.height >= l.prefHeight(l.width) - 0.5, "$where: '${l.id}' cut")
                                else assertTrue(l.width >= l.prefWidth(-1.0) - 0.5, "$where: '${l.id}' truncated")
                                assertTrue(l.sceneBounds().maxX <= banner.maxX + 0.5, "$where: ${l.id} wider than the banner")
                            }
                            // the whole description is one page at every window size: nothing cut, nothing to scroll
                            assertTrue(content.maxY <= banner.maxY + 0.5, "$where: text cut at ${w.toInt()}x${h.toInt()}")
                            assertEquals(0.0, b.scroll.vvalue, "$where: banner scrolled")
                            if (lang == "ru") preview(scene, "banner-$theme-${w.toInt()}")
                        }
                        // when the text is taller than the slot it scrolls inside it: the last line comes into view,
                        // still above the status row; the title goes back on the row line once scrolled back
                        FxTestSupport.onFx {
                            val main = nav.mainController!!
                            val b = main.gameBannerController
                            val where = "$theme/$lang ${w.toInt()}x${h.toInt()} scrolled"
                            b.scroll.vvalue = 1.0
                            relayout(nav)
                            val banner = b.scroll.sceneBounds()
                            val closing = b.closing.sceneBounds()
                            assertTrue(closing.maxY <= banner.maxY + 0.5, "$where: closing line $closing below the banner $banner")
                            assertTrue(closing.maxY <= main.defaultStatusPaneController.defaultStatusPane.sceneBounds().minY, "$where: closing line over the status row")
                            b.scroll.vvalue = 0.0
                            relayout(nav)
                            val rowTop = main.serversListController.entryControllers.single { "selected" in it.root.styleClass }.root.sceneBounds().minY
                            assertEquals(rowTop, inkTop(b.title), 2.0, "$where: title off the row line after scrolling back")
                        }
                    }
                }
            }             finally {
                FxTestSupport.onFx { nav.close() }
                app.shutdown()
            }
        }
    }

    /** A long server description (several paragraphs) still lands on one page at the minimum and the default size. */
    @Test
    fun longServerDescriptionStaysOnOnePage() {
        val app = AppContext.mock(language = "ru", authDelayMs = 0)
        val nav = mainOnScreen(app, "ru")
        try {
            val line = "Исследуй открытый мир, выполняй задания, участвуй в событиях и собирай отряды. "
            val text = GameDescription.Text("STALZONE", line.repeat(3), line.repeat(2), line.repeat(8), line.repeat(2))
            listOf(
                Theme.WINDOW_MIN_WIDTH to Theme.WINDOW_MIN_HEIGHT,
                Theme.WINDOW_WIDTH to Theme.WINDOW_HEIGHT,
            ).forEach { (w, h) ->
                resize(nav, w, h)
                FxTestSupport.onFx {
                    val b = nav.mainController!!.gameBannerController
                    b.show(stalzone.copy(description = text))
                    relayout(nav)
                    val where = "${w.toInt()}x${h.toInt()}"
                    val banner = b.scroll.sceneBounds()
                    val content = b.content.sceneBounds()
                    assertEquals(0.0, b.scroll.vvalue, "$where: scrolled")
                    assertTrue(content.maxY <= banner.maxY + 0.5, "$where: text ${content.maxY} below the slot ${banner.maxY}")
                    assertTrue(b.closing.sceneBounds().maxY <= banner.maxY + 0.5, "$where: closing line cut")
                    assertTrue(b.title.sceneBounds().minY >= banner.minY - 0.5, "$where: title clipped")
                }
            }
        } finally {
            FxTestSupport.onFx { nav.close() }
            app.shutdown()
        }
    }
}

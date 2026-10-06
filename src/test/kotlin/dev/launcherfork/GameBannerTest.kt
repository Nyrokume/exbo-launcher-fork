package dev.launcherfork

import dev.launcherfork.UiTestKit.THEMES
import dev.launcherfork.UiTestKit.applyTheme
import dev.launcherfork.UiTestKit.mainOnScreen
import dev.launcherfork.UiTestKit.preview
import dev.launcherfork.UiTestKit.resize
import dev.launcherfork.UiTestKit.sceneBounds
import dev.launcherfork.app.AppContext
import dev.launcherfork.data.mock.MockData
import dev.launcherfork.data.mock.MockServerRepository
import dev.launcherfork.domain.GameDescription
import dev.launcherfork.i18n.PluralForm
import dev.launcherfork.i18n.Strings
import dev.launcherfork.i18n.plural
import dev.launcherfork.i18n.pluralForm
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.screens.main.GameBannerController
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.scene.Scene
import javafx.scene.control.Label
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Properties

/**
 * The STALZONE banner on the main screen: text from the game model (Server.description -> i18n keys, ru + en), the
 * online line with the games list's value and the right plural form, the layout (left column <= 560 px inside the
 * centre slot, never over the nav row, the status row or ИГРАТЬ; long text scrolls inside the slot) at the minimum and
 * the default window size in every theme. Colors: SettingsContrastTest.gameBannerTextIsReadable.
 */
class GameBannerTest {
    private val nbsp = '\u00A0'
    private val stalzone = MockData.servers.single()

    private fun contains(outer: Bounds, inner: Bounds, slack: Double = 0.5) =
        inner.minX >= outer.minX - slack && inner.maxX <= outer.maxX + slack &&
            inner.minY >= outer.minY - slack && inner.maxY <= outer.maxY + slack

    private fun overlaps(a: Bounds, b: Bounds) = a.minX < b.maxX - 0.5 && b.minX < a.maxX - 0.5 && a.minY < b.maxY - 0.5 && b.minY < a.maxY - 0.5

    @Test
    fun russianAndEnglishPluralForms() {
        val ru = mapOf(
            PluralForm.ONE to listOf(1L, 21, 31, 101, 1001, 1_000_001),
            PluralForm.FEW to listOf(2L, 3, 4, 22, 23, 24, 102, 1284, 1_002),
            PluralForm.MANY to listOf(0L, 5, 9, 10, 11, 12, 13, 14, 15, 19, 20, 25, 100, 111, 112, 114, 1000, 1285),
        )
        ru.forEach { (form, ns) -> ns.forEach { assertEquals(form, pluralForm(it, "ru"), "ru $it") } }
        assertEquals(PluralForm.ONE, pluralForm(1, "en"))
        listOf(0L, 2, 11, 21, 1284).forEach { assertEquals(PluralForm.MANY, pluralForm(it, "en"), "en $it") }

        val ruB = Strings.bundle("ru"); val enB = Strings.bundle("en")
        assertEquals("1 игрок онлайн", ruB.plural("main.game.players", 1, "ru", "1"))
        assertEquals("21 игрок онлайн", ruB.plural("main.game.players", 21, "ru", "21"))
        assertEquals("3 игрока онлайн", ruB.plural("main.game.players", 3, "ru", "3"))
        assertEquals("1${nbsp}284 игрока онлайн", ruB.plural("main.game.players", 1284, "ru", "1${nbsp}284"))
        assertEquals("11 игроков онлайн", ruB.plural("main.game.players", 11, "ru", "11"))
        assertEquals("1${nbsp}285 игроков онлайн", ruB.plural("main.game.players", 1285, "ru", "1${nbsp}285"))
        assertEquals("1 player online", enB.plural("main.game.players", 1, "en", "1"))
        assertEquals("1,284 players online", enB.plural("main.game.players", 1284, "en", "1,284"))
    }

    @Test
    fun stringsAreInEveryBundle() {
        val keys = listOf("title", "lead", "subheading", "body", "closing").map { "game.stalzone.$it" } +
            listOf("one", "few", "many").map { "main.game.players.$it" }
        listOf("messages.properties", "messages_ru.properties", "messages_en.properties").forEach { file ->
            val props = Properties()
            javaClass.getResourceAsStream("/dev/launcherfork/ui/i18n/$file")!!.reader(Charsets.UTF_8).use(props::load)
            keys.forEach { k -> assertTrue(!props.getProperty(k).isNullOrBlank(), "$file misses $k") }
            listOf("one", "few", "many").forEach { assertTrue(props.getProperty("main.game.players.$it").contains("%s"), "$file: $it has no number") }
            assertEquals("STALZONE", props.getProperty("game.stalzone.title"))
            assertFalse(props.values.any { "Starcraft" in it.toString() }, "$file: STALZONE, not Starcraft")
        }
        val ru = Strings.bundle("ru")
        assertTrue(ru.getString("game.stalzone.lead").startsWith("STALZONE — это массовый многопользовательский онлайн-шутер"))
        assertEquals("Зона ждет.", ru.getString("game.stalzone.closing"))
        assertEquals("The Zone awaits.", Strings.bundle("en").getString("game.stalzone.closing"))
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
                assertEquals("Сможете ли Вы выжить в этом мире?", c.subheading.text)
                c.dispose()
            }
        } finally {
            app.shutdown()
        }
    }

    @Test
    fun onlineLineFollowsTheGamesListValue() {
        val app = AppContext.mock(authDelayMs = 0)
        val nav = mainOnScreen(app, "ru")
        try {
            val repo = app.services.servers as MockServerRepository
            FxTestSupport.onFx {
                val banner = nav.mainController!!.gameBannerController
                assertEquals("1${nbsp}284 игрока онлайн", banner.onlineText.text)
                val sidebar = nav.mainStage.scene.root.lookup("#entryOnlineText") as javafx.scene.text.Text
                assertTrue(sidebar.text.startsWith("1${nbsp}284"), "same value as the games list: ${sidebar.text}")
                assertFalse("offline" in banner.onlineRow.styleClass)
            }
            repo.setOnline(stalzone.id, 1301)
            check(FxTestSupport.waitFx { nav.mainController!!.gameBannerController.onlineText.text == "1${nbsp}301 игрок онлайн" })
            repo.setOnline(stalzone.id, 1305)
            check(FxTestSupport.waitFx { nav.mainController!!.gameBannerController.onlineText.text == "1${nbsp}305 игроков онлайн" })
            repo.setOnline(stalzone.id, null)
            check(FxTestSupport.waitFx { nav.mainController!!.gameBannerController.onlineText.text == "не в сети" })
            FxTestSupport.onFx { assertTrue("offline" in nav.mainController!!.gameBannerController.onlineRow.styleClass) }
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
                    listOf(Theme.WINDOW_MIN_WIDTH to Theme.WINDOW_MIN_HEIGHT, Theme.WINDOW_WIDTH to Theme.WINDOW_HEIGHT).forEach { (w, h) ->
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
                            // left column, <= 560 px, every text line inside the visible viewport width; no truncation
                            val content = b.content.sceneBounds()
                            assertTrue(content.width <= 560.5, "$where: text column ${content.width} px")
                            assertEquals(slot.minX + 9, b.title.sceneBounds().minX, 1.0, "$where: title not at the left edge")
                            val labels = listOf(b.title, b.lead, b.subheading, b.body, b.closing, b.onlineText)
                            labels.forEach { l: Label ->
                                assertTrue(l.text.isNotBlank(), "$where: ${l.id} empty")
                                assertEquals(b.title.sceneBounds().minX, if (l === b.onlineText) b.onlineRow.sceneBounds().minX else l.sceneBounds().minX, 1.0, "$where: ${l.id} not left-aligned")
                                if (l.isWrapText) assertTrue(l.height >= l.prefHeight(l.width) - 0.5, "$where: '${l.id}' cut")
                                else assertTrue(l.width >= l.prefWidth(-1.0) - 0.5, "$where: '${l.id}' truncated")
                                assertTrue(l.sceneBounds().maxX <= banner.maxX + 0.5, "$where: ${l.id} wider than the banner")
                            }
                            // all of it readable without scrolling at the default size; at the minimum size it may scroll
                            if (w == Theme.WINDOW_WIDTH) assertTrue(content.maxY <= banner.maxY + 0.5, "$where: text cut at the default size")
                            if (lang == "ru") preview(scene, "banner-$theme-${w.toInt()}")
                        }
                    }
                }
            } finally {
                FxTestSupport.onFx { nav.close() }
                app.shutdown()
            }
        }
    }
}

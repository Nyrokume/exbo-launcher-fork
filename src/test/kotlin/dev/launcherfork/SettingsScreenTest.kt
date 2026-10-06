package dev.launcherfork

import dev.launcherfork.UiTestKit.THEMES
import dev.launcherfork.UiTestKit.applyTheme
import dev.launcherfork.UiTestKit.key
import dev.launcherfork.UiTestKit.mainOnScreen
import dev.launcherfork.UiTestKit.mouse
import dev.launcherfork.UiTestKit.preview
import dev.launcherfork.UiTestKit.relayout
import dev.launcherfork.UiTestKit.resize
import dev.launcherfork.UiTestKit.sceneBounds
import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.data.mock.InMemorySettingsStore
import dev.launcherfork.data.mock.MockSystemInfo
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.domain.RamOptions
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.screens.settings.SettingsController
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Control
import javafx.scene.control.Labeled
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Properties

/**
 * The rebuilt settings page: everything on ONE screen at the minimum window size (900x580) in every theme and both
 * languages (localToScene bounds: no scrolling, nothing clipped, no truncated text, columns side by side), the
 * minimal look (no cards, no helper lines), the RAM bar like the original launcher's (equal segments, one per GB,
 * filled up to the value, no thumb; label follows the bar, 1 GB steps, both bounds) and that mouse / keyboard changes
 * land in the SettingsStore.
 */
class SettingsScreenTest {
    private val gb = 1024L

    private fun contains(outer: Bounds, inner: Bounds, slack: Double = 0.5) =
        inner.minX >= outer.minX - slack && inner.maxX <= outer.maxX + slack &&
            inner.minY >= outer.minY - slack && inner.maxY <= outer.maxY + slack

    private fun visibleDescendants(root: Parent): List<Node> = root.childrenUnmodifiable.flatMap { n ->
        if (!n.isVisible) emptyList() else listOf(n) + if (n is Parent && n !is Control) visibleDescendants(n) else emptyList()
    }

    @Test
    fun minimumWindowIs900x580() {
        assertEquals(900.0, Theme.WINDOW_MIN_WIDTH)
        assertEquals(580.0, Theme.WINDOW_MIN_HEIGHT)
    }

    @Test
    fun everythingFitsOneScreenAtTheMinimumSizeInEveryThemeAndLanguage() {
        listOf("ru", "en").forEach { lang ->
            val app = AppContext.mock(language = lang, authDelayMs = 0)
            val nav = mainOnScreen(app, lang)
            try {
                THEMES.forEach { theme ->
                    listOf(Theme.WINDOW_MIN_WIDTH to Theme.WINDOW_MIN_HEIGHT, Theme.WINDOW_WIDTH to Theme.WINDOW_HEIGHT).forEach { (w, h) ->
                        FxTestSupport.onFx {
                            applyTheme(nav.mainStage.scene, theme, lang)
                            nav.mainController!!.setSettingsVisible(true)
                        }
                        resize(nav, w, h)
                        FxTestSupport.onFx { checkFits(nav, theme, lang, w, h) }
                    }
                }
            } finally {
                FxTestSupport.onFx { nav.close() }
                app.shutdown()
            }
        }
    }

    private fun checkFits(nav: StageNavigator, theme: String, lang: String, w: Double, h: Double) {
        val where = "$theme/$lang ${w.toInt()}x${h.toInt()}"
        val main = nav.mainController!!
        val scene = nav.mainStage.scene
        assertEquals(w, scene.width, 1.0, "$where: scene width")
        assertEquals(h, scene.height, 1.0, "$where: scene height")
        val s = main.settingsController
        val viewport = s.scroll.viewportBounds
        val content = s.settingsContent
        // no scrolling: the content is not taller than the viewport (and the bar is hidden anyway)
        assertTrue(content.height <= viewport.height + 0.5, "$where: content ${content.height} taller than viewport ${viewport.height}")
        assertTrue(content.prefHeight(content.width) <= viewport.height + 0.5, "$where: content pref height ${content.prefHeight(content.width)} > ${viewport.height}")
        assertEquals(0.0, s.scroll.vvalue, 1e-9)
        val window = BoundingBox(0.0, 0.0, scene.width, scene.height)
        val area = s.scroll.sceneBounds()
        val sections = listOf(s.gameSection, s.updatesSection, s.othersSection)
        sections.forEach { c ->
            val b = c.sceneBounds()
            assertTrue(b.width > 200 && b.height > 80, "$where: section ${c.id} not laid out: $b")
            assertTrue(contains(area, b), "$where: section ${c.id} $b outside the settings area $area")
            assertTrue(contains(window, b), "$where: section ${c.id} $b outside the window")
            assertTrue(b.height >= c.prefHeight(c.width) - 0.5, "$where: section ${c.id} squeezed (${b.height} < ${c.prefHeight(c.width)})")
            // every visible node of the section stays inside it, text is not cut
            visibleDescendants(c).forEach { n ->
                val nb = n.sceneBounds()
                assertTrue(contains(b, nb), "$where: ${n.id ?: n.styleClass} $nb sticks out of section ${c.id} $b")
                if (n is Labeled && n.text.isNotEmpty()) {
                    if (n.isWrapText) assertTrue(n.height >= n.prefHeight(n.width) - 0.5, "$where: '${n.text}' cut (wrapped)")
                    else assertTrue(n.width >= n.prefWidth(-1.0) - 0.5, "$where: '${n.text}' truncated (${n.width} < ${n.prefWidth(-1.0)})")
                }
            }
        }
        // ИГРА on top, ОБНОВЛЕНИЕ | ПРОЧЕЕ side by side under it, same top, no overlap
        val game = s.gameSection.sceneBounds(); val upd = s.updatesSection.sceneBounds(); val oth = s.othersSection.sceneBounds()
        assertTrue(game.maxY <= upd.minY && game.maxY <= oth.minY, "$where: ИГРА overlaps the columns")
        assertEquals(upd.minY, oth.minY, 0.5, "$where: columns not top-aligned")
        assertTrue(upd.maxX <= oth.minX, "$where: columns overlap")
        assertEquals(upd.width, oth.width, 1.0, "$where: columns not equal")
        // the title shares the profile row and does not run under PLAYER
        val title = s.settingsTitle.sceneBounds()
        val profile = main.profileBarController.loginButton.sceneBounds()
        assertTrue(title.maxX < profile.minX, "$where: title under the profile button")
        assertTrue(game.minY >= profile.maxY - 0.5, "$where: ИГРА $game under the profile button $profile")
        assertTrue(game.maxX <= scene.width, "$where: ИГРА outside")
        if (w == Theme.WINDOW_MIN_WIDTH) preview(scene, "settings-$theme-$lang-min")
        else if (lang == "ru") preview(scene, "settings-$theme")
    }

    private fun settingsWith(settings: LauncherSettings, totalMb: Long = 16 * gb, lang: String = "ru"): Pair<SettingsController, InMemorySettingsStore> {
        val store = InMemorySettingsStore(settings)
        val base = AppContext.mock(speed = 50.0, authDelayMs = 0)
        val app = AppContext(base.services.copy(settings = store, system = MockSystemInfo(totalMb)))
        val c = FxTestSupport.onFx {
            val c = Fxml.load<SettingsController>(FxTestSupport.context(lang, app), "settings.fxml").controller
            // lay it out at the size the page gets at 1024x640
            val scene = javafx.scene.Scene(c.settingsRoot, 771.0, 612.0)
            applyTheme(scene, "zone", lang)
            c.settingsRoot.applyCss(); c.settingsRoot.layout()
            c
        }
        return c to store
    }

    /** [gbValue] of [totalGb] segments are filled, from the left, and they cover gbValue/totalGb of the bar. */
    private fun assertFill(c: SettingsController, gbValue: Int, totalGb: Int, why: String) {
        val bar = c.ramBar
        bar.applyCss(); bar.layout()
        assertEquals(gbValue, bar.value, why)
        assertEquals(gbValue.toDouble() / totalGb, bar.fillFraction, 1e-9, "$why: fill fraction")
        assertEquals(totalGb, bar.segments.size, "$why: one segment per GB")
        assertEquals(bar.segments.take(gbValue), bar.filledSegments, "$why: filled segments")
        val barB = bar.sceneBounds()
        assertTrue(barB.width > 200, "$why: bar ${barB.width}")
        val filledEnd = bar.segments[gbValue - 1].sceneBounds().maxX
        assertEquals(gbValue.toDouble() / totalGb, (filledEnd - barB.minX) / barB.width, 3.0 / barB.width, "$why: filled part")
        assertEquals(barB.minX, bar.segments.first().sceneBounds().minX, 0.5, "$why: fill starts at the left edge")
        assertEquals(barB.maxX, bar.segments.last().sceneBounds().maxX, 0.5, "$why: last segment ends at the right edge")
    }

    @Test
    fun ramBarLooksLikeTheOriginalSegmentedBar() {
        val (c, _) = settingsWith(LauncherSettings(ramFraction = 0.5))
        FxTestSupport.onFx {
            val bar = c.ramBar
            assertEquals(16, bar.totalGb); assertEquals(2, bar.minGb); assertEquals(14, bar.maxGb); assertEquals(6, bar.recommendedGb)
            assertFill(c, 2, 16, "legacy 0.5 = 2304 MB -> 2 GB")
            assertEquals("Выделено: 2 ГБ из 16 ГБ", c.ramSummary.text)
            for (v in listOf(6, 9, 14)) {
                bar.setValue(v)
                assertFill(c, v, 16, "value $v")
                assertEquals("Выделено: $v ГБ из 16 ГБ", c.ramSummary.text)
            }
            // equal segments with a small gap, a bar (not a slider): no thumb, no tick labels
            val seg = bar.segments.map { it.sceneBounds() }
            seg.zipWithNext { a, b -> assertTrue(b.minX - a.maxX in 1.0..3.0, "gap ${b.minX - a.maxX}") }
            seg.dropLast(1).forEach { assertEquals(seg[0].width, it.width, 1.0, "segments not equal") }
            assertTrue(seg.all { it.height in 10.0..14.0 }, "segment height ${seg[0].height}")
            assertTrue(bar.childrenUnmodifiable.all { "ram-segment" in it.styleClass }, "only segments inside the bar")
            // the label sits under the bar, like the original's "2304 MB"
            assertTrue(c.ramSummary.sceneBounds().minY >= bar.sceneBounds().maxY, "value label not under the bar")
            // the system reserve (15, 16 GB) is marked and cannot be reached
            assertEquals(listOf(15, 16), bar.segments.filter { it.pseudoClassStates.any { p -> p.pseudoClassName == "reserve" } }.map { it.userData })
            // the recommended value is explained in the tooltip / accessible help, not in an extra line
            assertTrue(bar.accessibleHelp.contains("6 ГБ"), bar.accessibleHelp)
        }
    }

    @Test
    fun keyboardChangesAreClampedSteppedAndSaved() {
        val (c, store) = settingsWith(LauncherSettings(ramMb = 6 * 1024))
        FxTestSupport.onFx {
            val bar = c.ramBar
            assertTrue(bar.isFocusTraversable)
            key(bar, KeyCode.RIGHT)
            assertEquals(7, bar.value, "one step = 1 GB")
            assertEquals(7 * 1024, store.load().ramMb)
            assertNull(store.load().ramFraction)
            assertEquals("Выделено: 7 ГБ из 16 ГБ", c.ramSummary.text)
            key(bar, KeyCode.LEFT); key(bar, KeyCode.LEFT)
            assertEquals(5, bar.value); assertEquals(5 * 1024, store.load().ramMb)
            key(bar, KeyCode.END)
            assertEquals(14, bar.value); assertEquals(14 * 1024, store.load().ramMb)
            key(bar, KeyCode.RIGHT); key(bar, KeyCode.PAGE_UP)
            assertEquals(14, bar.value, "upper bound: total minus reserve")
            assertEquals("Выделено: 14 ГБ из 16 ГБ", c.ramSummary.text)
            key(bar, KeyCode.HOME)
            assertEquals(2, bar.value); assertEquals(2 * 1024, store.load().ramMb)
            key(bar, KeyCode.LEFT); key(bar, KeyCode.PAGE_DOWN)
            assertEquals(2, bar.value, "lower bound: minimum")
            assertEquals(2 * 1024, store.load().ramMb)
            assertEquals("Выделено: 2 ГБ из 16 ГБ", c.ramSummary.text)
        }
    }

    @Test
    fun mouseChangesSnapToWholeGbClampAndSave() {
        val (c, store) = settingsWith(LauncherSettings())
        FxTestSupport.onFx {
            val bar = c.ramBar
            assertEquals(6, bar.value, "nothing stored -> recommended")
            val b = bar.sceneBounds()
            val y = b.centerY
            fun segment(gb: Int) = bar.segments[gb - 1].sceneBounds()
            mouse(bar, MouseEvent.MOUSE_PRESSED, segment(10).centerX, y)
            assertEquals(10, bar.value, "press on the 10th segment = 10 GB")
            assertEquals(10 * 1024, store.load().ramMb)
            assertFill(c, 10, 16, "after click")
            mouse(bar, MouseEvent.MOUSE_DRAGGED, segment(9).minX + 1, y)
            assertEquals(9, bar.value, "left edge of a segment still selects it")
            mouse(bar, MouseEvent.MOUSE_DRAGGED, segment(9).maxX - 1, y)
            assertEquals(9, bar.value, "right edge of a segment still selects it")
            assertEquals(9 * 1024, store.load().ramMb)
            assertEquals("Выделено: 9 ГБ из 16 ГБ", c.ramSummary.text)
            mouse(bar, MouseEvent.MOUSE_DRAGGED, segment(16).centerX, y)
            assertEquals(14, bar.value, "reserve segment: clamped to the max")
            mouse(bar, MouseEvent.MOUSE_DRAGGED, b.maxX + 100, y)
            assertEquals(14, bar.value, "dragged past the end: max")
            mouse(bar, MouseEvent.MOUSE_DRAGGED, segment(1).centerX, y)
            assertEquals(2, bar.value, "first segment: clamped to the min")
            mouse(bar, MouseEvent.MOUSE_DRAGGED, b.minX - 100, y)
            assertEquals(2, bar.value, "dragged past the start: min")
            assertEquals(2 * 1024, store.load().ramMb)
        }
    }

    @Test
    fun storedValueOutsideTheBoundsIsClampedOnLoad() {
        val (big, _) = settingsWith(LauncherSettings(ramMb = 64 * 1024))
        val (small, _) = settingsWith(LauncherSettings(ramMb = 256))
        FxTestSupport.onFx {
            assertEquals(14, big.ramBar.value)
            assertEquals("Выделено: 14 ГБ из 16 ГБ", big.ramSummary.text)
            assertEquals(2, small.ramBar.value)
            assertEquals("Выделено: 2 ГБ из 16 ГБ", small.ramSummary.text)
        }
    }

    @Test
    fun smallMachineInEnglish() {
        val (c, _) = settingsWith(LauncherSettings(), totalMb = 8 * gb, lang = "en")
        FxTestSupport.onFx {
            assertEquals(8, c.ramBar.totalGb); assertEquals(6, c.ramBar.maxGb)
            assertEquals(4, c.ramBar.value, "nothing stored -> recommended")
            assertEquals("Allocated: 4 GB of 8 GB", c.ramSummary.text)
            assertTrue(c.ramBar.accessibleHelp.contains("4 GB"), c.ramBar.accessibleHelp)
            assertFill(c, 4, 8, "8 GB machine")
        }
    }

    @Test
    fun otherOptionsStillSaveToTheStore() {
        val (c, store) = settingsWith(LauncherSettings())
        FxTestSupport.onFx {
            c.exitOnStart.isSelected = true
            assertEquals(dev.launcherfork.domain.ExitMode.ON_GAME_START, store.load().exitMode)
            c.p2pNever.isSelected = true
            assertEquals(dev.launcherfork.domain.P2pMode.NEVER, store.load().p2pMode)
            c.downloadLimitField.text = "512"; c.uploadLimitField.text = "64"
            assertEquals(512, store.load().downloadLimitKb); assertEquals(64, store.load().uploadLimitKb)
            val tray = store.load().hideToTray
            c.trayToggle.isSelected = !tray
            assertEquals(!tray, store.load().hideToTray)
            c.autostartToggle.isSelected = true; c.defenderToggle.isSelected = false; c.startOnDownloadedToggle.isSelected = false
            val s = store.load()
            assertTrue(s.runOnStartUp); assertFalse(s.defenderHint); assertFalse(s.startOnDownloaded)
            // the old preset-button RAM row is gone; minimal: no cards, no helper lines
            assertTrue(c.settingsRoot.lookupAll(".ram-chip").isEmpty())
            assertTrue(c.settingsRoot.lookupAll(".ram-selector").isEmpty())
            assertTrue(c.settingsRoot.lookupAll(".card").isEmpty(), "settings should not use cards")
            assertTrue(c.settingsRoot.lookupAll(".settings-hint").isEmpty(), "no helper lines")
            assertTrue(c.settingsRoot.lookupAll(".heading-rule").isEmpty(), "no decorative rules")
            // on/off options are switches, distinct from the radios
            listOf(c.trayToggle, c.autostartToggle, c.defenderToggle, c.startOnDownloadedToggle).forEach { t ->
                assertTrue("switch" in t.styleClass, "${t.accessibleText} is not a switch")
            }
            // grouped like the original launcher: Defender and start-after-download under ОБНОВЛЕНИЕ, tray/autostart under ПРОЧЕЕ
            fun under(n: javafx.scene.Node, p: javafx.scene.Node) = generateSequence(n) { it.parent }.any { it === p }
            assertTrue(under(c.defenderToggle, c.updatesSection) && under(c.startOnDownloadedToggle, c.updatesSection))
            assertTrue(under(c.trayToggle, c.othersSection) && under(c.autostartToggle, c.othersSection))
            assertTrue(under(c.ramBar, c.gameSection) && under(c.exitNever, c.gameSection))
        }
    }

    @Test
    fun newStringsInEveryBundle() {
        val keys = listOf("settings.page.title", "settings.language.short", "settings.ram.summary",
            "settings.ram.hint", "settings.unit.gb")
        listOf("messages.properties", "messages_ru.properties", "messages_en.properties").forEach { file ->
            val props = Properties()
            javaClass.getResourceAsStream("/dev/launcherfork/ui/i18n/$file")!!.reader(Charsets.UTF_8).use(props::load)
            keys.forEach { k -> assertTrue(!props.getProperty(k).isNullOrBlank(), "$file misses $k") }
            assertNull(props.getProperty("settings.ram.recommended"), "$file: stale chip caption key")
            assertNull(props.getProperty("settings.ram.recommended_value"), "$file: stale recommended line")
            assertTrue(props.getProperty("settings.ram.hint").contains("%s"), "$file: the hint names the recommended value")
        }
    }
}

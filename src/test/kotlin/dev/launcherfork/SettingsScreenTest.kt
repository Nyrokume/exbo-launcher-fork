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
        val sections = listOf(s.gameSection, s.updatesSection, s.languageSection, s.behaviourSection)
        sections.forEach { c ->
            val b = c.sceneBounds()
            assertTrue(b.width > 140 && b.height > 50, "$where: section ${c.id} not laid out: $b")
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
        // one list: ПАМЯТЬ, ПОВЕДЕНИЕ, then ОБНОВЛЕНИЕ | ЯЗЫК side by side (same top), all within the same left/right edges
        val game = s.gameSection.sceneBounds(); val upd = s.updatesSection.sceneBounds(); val lng = s.languageSection.sceneBounds()
        val beh = s.behaviourSection.sceneBounds()
        assertTrue(game.maxY <= beh.minY, "$where: ПАМЯТЬ overlaps ПОВЕДЕНИЕ")
        assertTrue(beh.maxY <= upd.minY && beh.maxY <= lng.minY, "$where: ПОВЕДЕНИЕ overlaps the last row")
        assertEquals(upd.minY, lng.minY, 0.5, "$where: ОБНОВЛЕНИЕ | ЯЗЫК not top-aligned")
        assertTrue(upd.maxX <= lng.minX, "$where: ОБНОВЛЕНИЕ and ЯЗЫК overlap")
        assertEquals(game.minX, beh.minX, 0.5); assertEquals(game.maxX, beh.maxX, 0.5, "$where: ПОВЕДЕНИЕ not as wide as ПАМЯТЬ")
        assertEquals(game.minX, upd.minX, 0.5); assertEquals(game.maxX, lng.maxX, 0.5, "$where: last row not as wide as the list")
        checkRows(s, where)
        // the nav row (profile + links) holds only the nav: the whole settings page starts below it, no page title
        val nav = main.topBar.sceneBounds()
        assertTrue(main.headerController.headerPane.isVisible, "$where: nav links must stay on the settings screen")
        assertTrue(s.settingsRoot.sceneBounds().minY >= nav.maxY, "$where: settings ${s.settingsRoot.sceneBounds()} reach into the nav row $nav")
        assertTrue(s.settingsRoot.lookupAll(".settings-title").isEmpty(), "$where: no page title")
        assertTrue(s.settingsRoot.lookupAll(".settings-divider").isEmpty(), "$where: no dividers between blocks")
        if (w == Theme.WINDOW_MIN_WIDTH) preview(scene, "settings-$theme-$lang-min")
        else if (lang == "ru") preview(scene, "settings-$theme")
    }

    /**
     * Every card is a list of uniform rows: 34 px (7 rows in ПОВЕДЕНИЕ must fit 900x580), label at the card's left
     * padding, control ending at its right padding, one line (no wrapped or cut labels); auto-exit, P2P, theme and
     * language are segmented controls: a 28 px box (like the limit fields) whose 26 px parts sit edge to edge on one
     * line, the selected one filled over the whole part height (no inset pill).
     */
    private fun checkRows(s: SettingsController, where: String) {
        val behaviourRows = listOf(s.exitRow, s.p2pRow, s.themeRow, s.startOnDownloadedRow, s.defenderRow, s.trayRow, s.autostartRow)
        assertEquals(behaviourRows, s.behaviourCard.children.toList(), "$where: ПОВЕДЕНИЕ is one column of rows in this order")
        val rows = behaviourRows + listOf(s.limitsRow, s.languageRow)
        rows.forEach { row ->
            val rb = row.sceneBounds()
            assertEquals(34.0, rb.height, 0.5, "$where: ${row.id} height")
            val card = row.parent as javafx.scene.layout.Region
            val cb = card.sceneBounds()
            val inner = cb.minX + card.snappedLeftInset() to cb.maxX - card.snappedRightInset()
            val first = row.children.first().sceneBounds(); val last = row.children.last().sceneBounds()
            assertEquals(inner.first, first.minX, 1.0, "$where: ${row.id} does not start at the card padding")
            if (row !== s.languageRow) assertEquals(inner.second, last.maxX, 1.0, "$where: ${row.id} control not at the right edge")
            assertEquals(rb.centerY, last.centerY, 1.0, "$where: ${row.id} control not centred")
            row.children.filterIsInstance<Labeled>().forEach { l -> assertTrue(!l.isWrapText, "$where: '${l.text}' may wrap") }
        }
        // switches of the four toggle rows share one right edge
        val switches = listOf(s.startOnDownloadedToggle, s.defenderToggle, s.trayToggle, s.autostartToggle).map { it.sceneBounds() }
        switches.forEach { assertEquals(switches[0].maxX, it.maxX, 0.5, "$where: switches not aligned") }
        // segmented controls: parts on one line, edge to edge, the selected one painted with the accent over its full height
        listOf(listOf(s.exitOnStart, s.exitOnGameExit, s.exitNever), listOf(s.p2pAlways, s.p2pNotMobile, s.p2pNever),
            listOf(s.themeDark, s.themeLight), listOf(s.langRu, s.langEn))
            .forEach { parts ->
                val b = parts.map { it.sceneBounds() }
                b.forEach { assertEquals(b[0].centerY, it.centerY, 0.5, "$where: segments not on one line") }
                b.zipWithNext { x, y -> assertEquals(0.0, y.minX - x.maxX, 0.5, "$where: segments apart (${y.minX - x.maxX})") }
                b.forEach { assertEquals(26.0, it.height, 0.5, "$where: segment height ${it.height}") }
                parts.forEach { assertTrue("segment" in it.styleClass, "$where: ${it.text} is not a segment") }
                val group = parts[0].parent.sceneBounds(); val row = parts[0].parent.parent.sceneBounds()
                assertEquals(28.0, group.height, 0.5, "$where: segmented control height")
                assertTrue(group.height <= row.height - 6, "$where: segmented control ${group.height} fills the ${row.height} px row")
                // the parts fill the box inside its 1 px border: no inset pill
                assertEquals(group.minY + 1, b[0].minY, 0.5, "$where: segment not at the top of the box")
                assertEquals(group.maxY - 1, b[0].maxY, 0.5, "$where: segment not at the bottom of the box")
                assertEquals(group.minX + 1, b.first().minX, 0.5, "$where: first segment inset"); assertEquals(group.maxX - 1, b.last().maxX, 0.5, "$where: last segment inset")
                val sel = parts.single { it.isSelected }
                assertTrue(sel.background != null && sel.background.fills.isNotEmpty() && sel.background.fills.last().fill != javafx.scene.paint.Color.TRANSPARENT,
                    "$where: selected segment '${sel.text}' not filled")
            }
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

    private fun segmentCentre(c: SettingsController, gb: Int) = c.ramBar.segments[gb - 1].sceneBounds().centerX

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
            assertEquals(16, bar.totalGb); assertEquals(2, bar.minGb); assertEquals(16, bar.maxGb); assertEquals(6, bar.recommendedGb)
            assertFill(c, 2, 16, "legacy 0.5 = 2304 MB -> 2 GB")
            assertEquals("Выделено: 2 ГБ из 16 ГБ", c.ramSummary.text)
            for (v in listOf(6, 9, 14, 16)) {
                bar.setValue(v)
                assertFill(c, v, 16, "value $v")
                assertEquals("Выделено: $v ГБ из 16 ГБ", c.ramSummary.text)
            }
            // equal segments with a small gap, a bar (not a slider): no thumb, no tick labels
            val seg = bar.segments.map { it.sceneBounds() }
            seg.zipWithNext { a, b -> assertTrue(b.minX - a.maxX in 1.0..3.0, "gap ${b.minX - a.maxX}") }
            seg.dropLast(1).forEach { assertEquals(seg[0].width, it.width, 1.0, "segments not equal") }
            assertTrue(seg.all { it.height in 14.0..18.0 }, "segment height ${seg[0].height}")
            assertTrue(bar.childrenUnmodifiable.all { "ram-segment" in it.styleClass }, "only segments inside the bar")
            // the value label sits in the RAM card, right above the bar
            assertTrue(c.ramSummary.sceneBounds().maxY <= bar.sceneBounds().minY, "value label not above the bar")
            // min / max captions right under the bar, on one line, centred under the first / last selectable segment (2, 16 GB)
            val scale = c.ramScale
            assertEquals("2 ГБ", scale.minLabel.text); assertEquals("16 ГБ", scale.maxLabel.text)
            val minB = scale.minLabel.sceneBounds(); val maxB = scale.maxLabel.sceneBounds()
            assertTrue(minB.minY >= bar.sceneBounds().maxY, "captions not under the bar")
            assertEquals(minB.centerY, maxB.centerY, 0.5, "captions not on one line")
            assertEquals(segmentCentre(c, 2), minB.centerX, 1.0, "min caption not under the 2 GB segment")
            assertEquals(segmentCentre(c, 16), maxB.centerX, 1.0, "max caption not under the 16 GB (last) segment")
            assertTrue(maxB.maxX <= bar.sceneBounds().maxX + 0.5, "max caption sticks out past the bar")
            // no system reserve any more: every segment is a plain selectable one (2..16 GB)
            assertTrue(bar.segments.none { it.pseudoClassStates.any { p -> p.pseudoClassName == "reserve" } }, "reserve segments left")
            // the recommended value is explained in the tooltip / accessible help, not in an extra line
            assertTrue(bar.accessibleHelp.contains("6 ГБ"), bar.accessibleHelp)
        }
    }

    /** «Мало памяти останется системе» only for the last 2 GB (15, 16 of 16), next to the summary, without moving anything. */
    @Test
    fun lowSystemMemoryNoteForTheLastTwoGb() {
        val (c, _) = settingsWith(LauncherSettings(ramMb = 6 * 1024))
        FxTestSupport.onFx {
            val bar = c.ramBar; val note = c.ramWarning
            fun lay() { c.settingsRoot.applyCss(); c.settingsRoot.layout() }
            assertEquals("Мало памяти останется системе", note.text)
            assertFalse(note.isVisible, "6 of 16 GB: no note")
            assertTrue(note.isManaged, "the note keeps its place (no layout shift)")
            lay()
            // the summary's width follows its digits («6» / «16»); its line, the bar, the captions and the card stay put
            fun frame() = listOf(c.ramSummary.sceneBounds().let { listOf(it.minX, it.minY, it.height) }) +
                listOf(bar, c.ramScale, c.ramCard).map { it.sceneBounds().let { b -> listOf(b.minX, b.minY, b.width, b.height) } }
            val before = frame()
            val hint = bar.accessibleHelp
            assertFalse(hint.contains(note.text), "hidden note not in the help")
            for ((v, shown) in listOf(13 to false, 14 to false, 15 to true, 16 to true, 14 to false, 2 to false, 16 to true)) {
                bar.setValue(v); lay()
                assertEquals(shown, note.isVisible, "$v of 16 GB: note visible")
                // screen readers (and the tooltip) hear the note exactly while it is shown
                assertEquals(shown, bar.accessibleHelp.contains("Мало памяти останется системе"), "$v GB: help '${bar.accessibleHelp}'")
                assertTrue(bar.accessibleHelp.startsWith(hint), "$v GB: the recommendation stays in the help")
                assertEquals(before, frame(), "$v GB: the RAM card moved")
            }
            // shown: on the summary line, after it, inside the card, not cut
            val sb = c.ramSummary.sceneBounds(); val nb = note.sceneBounds(); val card = c.ramCard.sceneBounds()
            assertTrue(nb.minX >= sb.maxX, "note overlaps the summary")
            assertEquals(sb.centerY, nb.centerY, 2.0, "note not on the summary line")
            assertTrue(nb.maxX <= card.maxX && nb.maxY <= bar.sceneBounds().minY, "note outside the card / over the bar")
            assertTrue(note.width >= note.prefWidth(-1.0) - 0.5, "note truncated")
            assertTrue("ram-warning" in note.styleClass)
            preview(c.settingsRoot.scene, "settings-ram-low-system")
        }
        val (en, _) = settingsWith(LauncherSettings(ramMb = 8 * 1024), totalMb = 8 * gb, lang = "en")
        FxTestSupport.onFx {
            assertEquals("Little memory left for the system", en.ramWarning.text)
            assertTrue(en.ramWarning.isVisible, "8 of 8 GB: note")
            en.ramBar.setValue(6); assertFalse(en.ramWarning.isVisible, "6 of 8 GB: no note")
            en.ramBar.setValue(7); assertTrue(en.ramWarning.isVisible, "7 of 8 GB: note")
            assertTrue(en.ramBar.accessibleHelp.endsWith("Little memory left for the system."), en.ramBar.accessibleHelp)
            en.ramBar.setValue(6); assertFalse(en.ramBar.accessibleHelp.contains("Little memory"), en.ramBar.accessibleHelp)
        }
    }

    /** A machine reporting 15.6 GB: «из 16 ГБ», but only whole GB that fit in the memory: 2..15, 15 segments, no dead one. */
    @Test
    fun maxNeverExceedsThePhysicalMemory() {
        val (c, store) = settingsWith(LauncherSettings(ramMb = 16 * 1024), totalMb = (15.6 * gb).toLong())
        FxTestSupport.onFx {
            val bar = c.ramBar
            assertEquals(15, bar.maxGb); assertEquals(15, bar.totalGb, "scale = selectable range"); assertEquals(15, bar.segments.size)
            assertEquals(15, bar.value, "stored 16 GB clamped to 15")
            assertEquals("Выделено: 15 ГБ из 16 ГБ", c.ramSummary.text)
            assertEquals("15 ГБ", c.ramScale.maxLabel.text)
            assertEquals(segmentCentre(c, 15), c.ramScale.maxLabel.sceneBounds().centerX, 1.0, "max caption under the last segment")
            key(bar, KeyCode.LEFT); key(bar, KeyCode.END); key(bar, KeyCode.RIGHT)
            assertEquals(15, bar.value, "END / RIGHT stop at 15")
            assertEquals(15 * 1024, store.load().ramMb)
            // the note follows the new max: 14 and 15 of 15
            assertTrue(c.ramWarning.isVisible)
            bar.setValue(14); assertTrue(c.ramWarning.isVisible, "14 of max 15: note")
            bar.setValue(13); assertFalse(c.ramWarning.isVisible, "13 of max 15: no note")
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
            assertEquals(16, bar.value); assertEquals(16 * 1024, store.load().ramMb)
            key(bar, KeyCode.RIGHT); key(bar, KeyCode.PAGE_UP)
            assertEquals(16, bar.value, "upper bound: the whole memory")
            assertEquals("Выделено: 16 ГБ из 16 ГБ", c.ramSummary.text)
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
            assertEquals(16, bar.value, "last segment = the whole memory")
            assertEquals(16 * 1024, store.load().ramMb)
            mouse(bar, MouseEvent.MOUSE_DRAGGED, b.maxX + 100, y)
            assertEquals(16, bar.value, "dragged past the end: max")
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
            assertEquals(16, big.ramBar.value)
            assertEquals("Выделено: 16 ГБ из 16 ГБ", big.ramSummary.text)
            assertEquals(2, small.ramBar.value)
            assertEquals("Выделено: 2 ГБ из 16 ГБ", small.ramSummary.text)
        }
    }

    @Test
    fun smallMachineInEnglish() {
        val (c, _) = settingsWith(LauncherSettings(), totalMb = 8 * gb, lang = "en")
        FxTestSupport.onFx {
            assertEquals(8, c.ramBar.totalGb); assertEquals(8, c.ramBar.maxGb)
            assertEquals(4, c.ramBar.value, "nothing stored -> recommended")
            assertEquals("Allocated: 4 GB of 8 GB", c.ramSummary.text)
            assertTrue(c.ramBar.accessibleHelp.contains("4 GB"), c.ramBar.accessibleHelp)
            assertFill(c, 4, 8, "8 GB machine")
            assertEquals("2 GB", c.ramScale.minLabel.text); assertEquals("8 GB", c.ramScale.maxLabel.text)
            assertEquals(segmentCentre(c, 8), c.ramScale.maxLabel.sceneBounds().centerX, 1.0, "max caption under the 8 GB (last) segment")
        }
    }

    @Test
    fun twoGbMachineShowsOneCaption() {
        listOf(2 * gb, 1 * gb).forEach { total ->
            val (c, _) = settingsWith(LauncherSettings(), totalMb = total)
            FxTestSupport.onFx {
                assertEquals(2, c.ramBar.minGb); assertEquals(2, c.ramBar.maxGb, "$total MB: only 2 GB can be chosen")
                val scale = c.ramScale
                assertEquals("2 ГБ", scale.minLabel.text)
                assertFalse(scale.maxLabel.isVisible, "$total MB: «2 ГБ … 2 ГБ» shown twice")
                assertEquals(1, scale.childrenUnmodifiable.count { it.isVisible }, "$total MB: one caption")
                assertEquals(segmentCentre(c, 2), scale.minLabel.sceneBounds().centerX, 1.0, "$total MB: caption not under the 2 GB segment")
            }
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
            assertTrue(c.settingsRoot.lookupAll(".settings-hint").isEmpty(), "no helper lines")
            assertTrue(c.settingsRoot.lookupAll(".heading-rule").isEmpty(), "no decorative rules")
            // on/off options are switches, distinct from the radios
            listOf(c.trayToggle, c.autostartToggle, c.defenderToggle, c.startOnDownloadedToggle).forEach { t ->
                assertTrue("switch" in t.styleClass, "${t.accessibleText} is not a switch")
            }
            // blocks: ПАМЯТЬ = RAM only, ОБНОВЛЕНИЕ = speed limits only, ЯЗЫК, and every behaviour option in ПОВЕДЕНИЕ
            fun under(n: javafx.scene.Node, p: javafx.scene.Node) = generateSequence(n) { it.parent }.any { it === p }
            listOf(c.exitOnStart, c.exitOnGameExit, c.exitNever, c.p2pAlways, c.p2pNotMobile, c.p2pNever,
                c.startOnDownloadedToggle, c.defenderToggle, c.trayToggle, c.autostartToggle).forEach { n ->
                assertTrue(under(n, c.behaviourSection), "${n.id ?: n.accessibleText} not in ПОВЕДЕНИЕ")
            }
            assertTrue(under(c.ramBar, c.gameSection) && under(c.ramSummary, c.gameSection))
            assertTrue(c.gameSection.lookupAll(".radio-button").isEmpty() && c.gameSection.lookupAll(".check-box").isEmpty(), "ПАМЯТЬ holds only the RAM bar")
            assertTrue(under(c.downloadLimitField, c.updatesSection) && under(c.uploadLimitField, c.updatesSection))
            assertTrue(c.updatesSection.lookupAll(".radio-button").isEmpty() && c.updatesSection.lookupAll(".check-box").isEmpty(), "ОБНОВЛЕНИЕ holds only the limits")
            assertTrue(under(c.langRu, c.languageSection) && under(c.langEn, c.languageSection))
            assertEquals("ПОВЕДЕНИЕ", c.behaviourHeading.text); assertEquals("ЯЗЫК", c.languageHeading.text)
        }
    }

    @Test
    fun newStringsInEveryBundle() {
        val keys = listOf("settings.heading.ram", "settings.ram.summary", "settings.ram.low_system", "settings.ram.hint_low_system", "settings.ram.hint", "settings.unit.gb",
            "settings.heading.behaviour", "settings.heading.language", "settings.language.interface", "settings.exit.short", "settings.p2p.short")
        listOf("messages.properties", "messages_ru.properties", "messages_en.properties").forEach { file ->
            val props = Properties()
            javaClass.getResourceAsStream("/dev/launcherfork/ui/i18n/$file")!!.reader(Charsets.UTF_8).use(props::load)
            keys.forEach { k -> assertTrue(!props.getProperty(k).isNullOrBlank(), "$file misses $k") }
            assertNull(props.getProperty("settings.ram.recommended"), "$file: stale chip caption key")
            assertNull(props.getProperty("settings.ram.recommended_value"), "$file: stale recommended line")
            assertNull(props.getProperty("settings.heading.others"), "$file: ПРОЧЕЕ is gone")
            assertNull(props.getProperty("settings.page.title"), "$file: no page title any more")
            assertTrue(props.getProperty("settings.ram.hint").contains("%s"), "$file: the hint names the recommended value")
        }
    }
}

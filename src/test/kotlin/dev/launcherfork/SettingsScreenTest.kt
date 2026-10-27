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
import dev.launcherfork.stalzone.ExboSessions
import dev.launcherfork.stalzone.TunnelOverrides
import dev.launcherfork.stalzone.TunnelProbeOutcome
import dev.launcherfork.stalzone.MemoryRegistry
import dev.launcherfork.stalzone.PingResult
import dev.launcherfork.stalzone.ServerBlock
import dev.launcherfork.stalzone.StalzoneDesk
import dev.launcherfork.stalzone.TunnelCatalog
import dev.launcherfork.stalzone.TunnelCatalogs
import dev.launcherfork.stalzone.TunnelFirewall
import dev.launcherfork.stalzone.TunnelRegion
import dev.launcherfork.ui.screens.settings.TunnelServices
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.screens.settings.SettingsController
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.Control
import javafx.scene.control.Labeled
import javafx.scene.control.ToggleButton
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.util.Properties

/**
 * The gear opens one scrollable column: memory, behaviour, updates and language, full width,
 * stacked with no empty band under the RAM bar. JVM, tunnel, sensitivity and account swap are separate pages;
 * the header switch opens those four and nothing else. Tunnel fills the settings viewport and scrolls its
 * server list inside the page. The column may extend past the minimum window (900x580).
 * The RAM bar matches the original launcher (equal segments, one per GB, filled up to the value, no thumb; label
 * follows the bar, 1 GB steps, both bounds) and mouse / keyboard changes land in the SettingsStore.
 */
class SettingsScreenTest {
    private val gb = 1024L

    /** Settings tests must not open sockets or write the user's overrides file. */
    @BeforeEach
    fun stubTunnelIo(@TempDir dir: Path) {
        TunnelServices.directory = dir
        TunnelServices.inline = true
        TunnelServices.fetch = { region -> TunnelFixtures.catalog(region) }
        TunnelServices.probe = { TunnelProbeOutcome.NoReply }
    }

    @AfterEach
    fun restoreTunnelIo() {
        TunnelServices.reset()
    }

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
    fun settingsColumnFillsThePageInEveryThemeAndLanguage() {
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
        val header = main.headerController
        val window = BoundingBox(0.0, 0.0, scene.width, scene.height)
        assertEquals(0.0, s.scroll.vvalue, 1e-9)
        assertEquals("ram", s.currentSection, "$where: gear did not open the column")
        assertTrue(header.settingsNav.isVisible && header.settingsNav.isManaged, "$where: settings nav hidden")
        assertFalse(header.linksContainer.isManaged, "$where: social links stay in the nav on settings")
        assertTrue(s.settingsRoot.lookupAll(".settings-pagebar").isEmpty(), "$where: a second tab switch is still on the page")
        assertNull(header.settingsNav.lookup("#settingsNavRam"), "$where: memory still has its own tab")
        val tools = listOf(header.navJvm, header.navTunnel, header.navSens, header.navAccounts)
        assertEquals(tools, header.settingsNav.children.toList(), "$where: the switch is not JVM, tunnel, sensitivity and accounts")
        tools.forEach { assertFalse("selected" in it.styleClass, "$where: ${it.text} selected on the column") }
        tools.zipWithNext { a, b ->
            assertTrue(a.sceneBounds().maxX <= b.sceneBounds().minX + 0.5, "$where: ${a.text} overlaps ${b.text}")
        }
        val profile = main.profileBarController.loginButton.sceneBounds()
        assertTrue(tools.last().sceneBounds().maxX <= profile.minX + 0.5, "$where: settings nav overlaps the profile")
        tools.forEach { assertTrue(contains(window, it.sceneBounds()), "$where: ${it.text} outside the window") }
        val area = s.scroll.sceneBounds()
        val column = listOf(s.gameSection, s.behaviourSection, s.updatesSection, s.languageSection)
        assertTrue(s.launcherPage.isManaged && !s.clientContent.isManaged, "$where: the column is not the open page")
        assertEquals(column, s.launcherPage.children.toList(), "$where: column order")
        assertFalse(s.launcherPage.children.contains(s.tunnelPage), "$where: tunnel is still in the column")
        column.forEach { assertTrue(it.isManaged && it.isVisible, "$where: ${it.id} hidden in the column") }
        listOf(s.jvmPage, s.tunnelPage, s.accountsPage, s.sensPage).forEach {
            assertFalse(it.isManaged, "$where: ${it.id} open on the column")
        }
        val pageWidth = s.launcherPage.sceneBounds().width
        column.zipWithNext { upper, lower ->
            val gap = lower.sceneBounds().minY - upper.sceneBounds().maxY
            assertTrue(gap in 0.0..12.0, "$where: empty band between ${upper.id} and ${lower.id} ($gap)")
        }
        column.forEach { c ->
            val b = c.sceneBounds()
            assertTrue(b.width > 140 && b.height > 40, "$where: section ${c.id} not laid out: $b")
            assertEquals(pageWidth, b.width, 2.0, "$where: ${c.id} is not a full-width row")
            assertTrue(b.minX >= window.minX - 0.5 && b.maxX <= window.maxX + 0.5, "$where: ${c.id} wider than the window")
            assertTrue(b.height >= c.prefHeight(c.width) - 0.5, "$where: section ${c.id} squeezed (${b.height} < ${c.prefHeight(c.width)})")
        }
        listOf(s.gameSection, s.behaviourSection, s.updatesSection, s.languageSection).forEach { c ->
            val b = c.sceneBounds()
            visibleDescendants(c).forEach { n ->
                val nb = n.sceneBounds()
                assertTrue(contains(b, nb), "$where: ${n.id ?: n.styleClass} $nb sticks out of section ${c.id} $b")
                if (n is Labeled && n.text.isNotEmpty()) {
                    if (n.isWrapText) assertTrue(n.height >= n.prefHeight(n.width) - 0.5, "$where: '${n.text}' cut (wrapped)")
                    else assertTrue(n.width >= n.prefWidth(-1.0) - 0.5, "$where: '${n.text}' truncated (${n.width} < ${n.prefWidth(-1.0)})")
                }
            }
        }
        val view = s.scroll.sceneBounds()
        assertTrue(s.behaviourSection.sceneBounds().minY < view.maxY, "$where: only the RAM bar is on the first screen")
        checkRows(s, where)
        val pages = listOf("jvm" to s.jvmPage, "tunnel" to s.tunnelPage, "sens" to s.sensPage, "accounts" to s.accountsPage)
        val navOf = mapOf(
            "jvm" to header.navJvm,
            "tunnel" to header.navTunnel,
            "sens" to header.navSens,
            "accounts" to header.navAccounts,
        )
        pages.forEach { (id, page) ->
            s.showSection(id)
            layoutSettings(nav)
            assertEquals(id, s.currentSection, "$where: page")
            assertTrue("selected" in navOf.getValue(id).styleClass, "$where: ${navOf.getValue(id).text} not selected")
            tools.filter { it !== navOf.getValue(id) }.forEach { button ->
                assertFalse("selected" in button.styleClass, "$where: ${button.text} stays selected on $id")
            }
            assertTrue(page.isManaged && !s.launcherPage.isManaged, "$where: ${page.id} did not replace the column")
            pages.map { it.second }.filter { it !== page }.forEach { other ->
                assertFalse(other.isManaged, "$where: ${other.id} stays open with ${page.id}")
            }
            val b = page.sceneBounds()
            assertTrue(b.minY >= area.minY - 1.0 && b.minY < area.maxY, "$where: ${page.id} not in the settings area")
            assertTrue(b.minX >= window.minX - 0.5 && b.maxX <= window.maxX + 0.5, "$where: ${page.id} wider than the window")
            if (id == "tunnel") {
                val viewH = s.scroll.viewportBounds.height
                assertTrue(
                    s.tunnelPage.height >= viewH - 28.0,
                    "$where: tunnel page does not fill the settings area (${s.tunnelPage.height} vs $viewH)",
                )
                assertTrue(
                    s.scroll.content.layoutBounds.height <= viewH + 2.0,
                    "$where: tunnel still scrolls with the column (${s.scroll.content.layoutBounds.height} vs $viewH)",
                )
                val poolsH = s.tunnelPools.prefHeight(s.tunnelScroll.width)
                assertTrue(
                    poolsH > s.tunnelScroll.viewportBounds.height + 1.0,
                    "$where: server list does not scroll inside the tunnel page",
                )
            }
        }
        s.showSection("ram")
        layoutSettings(nav)
        assertEquals(0.0, s.scroll.vvalue, 1e-9)
        assertTrue(s.launcherPage.isManaged && !s.tunnelPage.isManaged && !s.jvmPage.isManaged, "$where: column did not return")
        // the nav row (profile + links) holds only the nav: the whole settings page starts below it, no page title
        val nav = main.topBar.sceneBounds()
        assertTrue(main.headerController.headerPane.isVisible, "$where: nav links must stay on the settings screen")
        assertTrue(s.settingsRoot.sceneBounds().minY >= nav.maxY, "$where: settings ${s.settingsRoot.sceneBounds()} reach into the nav row $nav")
        assertTrue(s.settingsRoot.lookupAll(".settings-title").isEmpty(), "$where: no page title")
        assertTrue(s.settingsRoot.lookupAll(".settings-divider").isEmpty(), "$where: no dividers between blocks")
        if (w == Theme.WINDOW_MIN_WIDTH) preview(scene, "settings-$theme-$lang-min")
        else if (lang == "ru") preview(scene, "settings-$theme")
    }

    private fun layoutSettings(nav: StageNavigator) {
        val root = nav.mainStage.scene.root
        root.applyCss(); root.layout(); root.layout()
    }

    /**
     * Every card is a list of uniform rows: 34 px, label at the card's left padding, control ending at its right
     * padding, one line (no wrapped or cut labels); auto-exit, P2P and language are segmented controls: a 28 px box
     * (like the limit fields) whose 26 px parts sit edge to edge on one line, the selected one filled over the whole
     * part height (no inset pill). Measured together on the one column.
     */
    private fun checkRows(s: SettingsController, where: String) {
        val behaviourRows = listOf(
            s.exitRow, s.p2pRow, s.startOnDownloadedRow, s.defenderRow, s.animationsRow, s.trayRow, s.autostartRow,
        )
        assertEquals(behaviourRows, s.behaviourCard.children.toList(), "$where: ПОВЕДЕНИЕ is one column of rows in this order")
        measureRows(behaviourRows, s, where)
        val switches = listOf(
            s.startOnDownloadedToggle, s.defenderToggle, s.animationsToggle, s.trayToggle, s.autostartToggle,
        ).map { it.sceneBounds() }
        switches.forEach { assertEquals(switches[0].maxX, it.maxX, 0.5, "$where: switches not aligned") }
        measureSegments(listOf(listOf(s.exitOnStart, s.exitOnGameExit, s.exitNever), listOf(s.p2pAlways, s.p2pNotMobile, s.p2pNever)), where)
        measureRows(listOf(s.limitsRow), s, where)
        measureRows(listOf(s.languageRow), s, where)
        measureSegments(listOf(listOf(s.langRu, s.langEn)), where)
    }

    private fun measureRows(rows: List<javafx.scene.layout.HBox>, s: SettingsController, where: String) {
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
    }

    private fun measureSegments(groups: List<List<javafx.scene.control.RadioButton>>, where: String) {
        groups.forEach { parts ->
            val b = parts.map { it.sceneBounds() }
            b.forEach { assertEquals(b[0].centerY, it.centerY, 0.5, "$where: segments not on one line") }
            b.zipWithNext { x, y -> assertEquals(0.0, y.minX - x.maxX, 0.5, "$where: segments apart (${y.minX - x.maxX})") }
            b.forEach { assertEquals(26.0, it.height, 0.5, "$where: segment height ${it.height}") }
            parts.forEach { assertTrue("segment" in it.styleClass, "$where: ${it.text} is not a segment") }
            val group = parts[0].parent.sceneBounds(); val row = parts[0].parent.parent.sceneBounds()
            assertEquals(28.0, group.height, 0.5, "$where: segmented control height")
            assertTrue(group.height <= row.height - 6, "$where: segmented control ${group.height} fills the ${row.height} px row")
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

    /** «из N ГБ», the max and the number of segments come from one function; every segment can be chosen. */
    @Test
    fun displayedTotalMaxAndSegmentsAgree() {
        mapOf(15_974L to 15, 16_250L to 16, 16_384L to 16, 8_100L to 8, 2_048L to 2).forEach { (mb, n) ->
            val (c, _) = settingsWith(LauncherSettings(ramMb = 2 * 1024), totalMb = mb)
            FxTestSupport.onFx {
                val bar = c.ramBar
                assertEquals(n, bar.maxGb, "$mb MB: max"); assertEquals(n, bar.totalGb, "$mb MB: scale")
                assertEquals(n, bar.segments.size, "$mb MB: segments")
                assertEquals("Выделено: 2 ГБ из $n ГБ", c.ramSummary.text, "$mb MB: summary")
                key(bar, KeyCode.END)
                assertEquals(n, bar.value, "$mb MB: the last segment is reachable")
                assertEquals("Выделено: $n ГБ из $n ГБ", c.ramSummary.text)
            }
        }
    }

    /** A 16 GB PC reporting 16 250 MB (hardware-reserved memory): still 2..16 GB, 16 segments, «16 ГБ». */
    @Test
    fun sixteenGbPcReportingABitLessKeepsSixteen() {
        val (c, _) = settingsWith(LauncherSettings(ramMb = 16 * 1024), totalMb = 16_250)
        FxTestSupport.onFx {
            assertEquals(16, c.ramBar.maxGb); assertEquals(16, c.ramBar.segments.size); assertEquals(16, c.ramBar.value)
            assertEquals("16 ГБ", c.ramScale.maxLabel.text)
            assertEquals("Выделено: 16 ГБ из 16 ГБ", c.ramSummary.text)
            assertTrue(c.ramWarning.isVisible, "16 of 16: note")
        }
    }

    /** A machine reporting 15.6 GB (0.4 GB below 16): «из 15 ГБ», 2..15, 15 segments, no dead one. */
    @Test
    fun maxNeverExceedsThePhysicalMemory() {
        val (c, store) = settingsWith(LauncherSettings(ramMb = 16 * 1024), totalMb = (15.6 * gb).toLong())
        FxTestSupport.onFx {
            val bar = c.ramBar
            assertEquals(15, bar.maxGb); assertEquals(15, bar.totalGb, "scale = selectable range"); assertEquals(15, bar.segments.size)
            assertEquals(15, bar.value, "stored 16 GB clamped to 15")
            assertEquals("Выделено: 15 ГБ из 15 ГБ", c.ramSummary.text)
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
            assertEquals(
                listOf(c.gameSection, c.behaviourSection, c.updatesSection, c.languageSection),
                c.launcherPage.children.toList(),
                "the column is memory, behaviour, updates, language",
            )
            assertFalse(c.launcherPage.children.contains(c.tunnelPage), "tunnel is still in the column")
            assertTrue(c.launcherPage.isManaged && c.behaviourSection.isManaged && !c.tunnelPage.isManaged)
            assertFalse(c.jvmPage.isManaged || c.sensPage.isManaged || c.accountsPage.isManaged)
        }
    }

    @Test
    fun newStringsInEveryBundle() {
        val keys = listOf("settings.heading.ram", "settings.ram.summary", "settings.ram.low_system", "settings.ram.hint_low_system", "settings.ram.hint", "settings.unit.gb",
            "settings.heading.behaviour", "settings.heading.language", "settings.language.interface", "settings.exit.short", "settings.p2p.short",
            "settings.nav.jvm", "settings.nav.tunnel", "settings.nav.sens", "settings.nav.accounts",
            "settings.page.launcher", "settings.page.client", "settings.heading.jvm", "settings.heading.tunnel",
            "settings.heading.accounts", "settings.heading.sens", "settings.jvm.active", "settings.accounts.save",
            "settings.accounts.keys", "settings.accounts.login", "settings.accounts.current", "settings.accounts.empty", "settings.accounts.no_keys",
            "settings.sens.write", "settings.sens.row", "settings.sens.down", "settings.sens.up",
            "settings.client.jvm", "settings.client.tunnel", "settings.client.accounts", "settings.client.sens",
            "settings.jvm.saved", "settings.jvm.heap", "settings.preset.v112", "settings.preset.aikar", "settings.tunnel.now",
            "settings.tunnel.measure", "settings.tunnel.find", "settings.tunnel.refresh",
            "settings.tunnel.exclude", "settings.tunnel.exclude_group", "settings.tunnel.excluded_group",
            "settings.tunnel.exclude.confirm", "settings.tunnel.block_node", "settings.tunnel.unblock_node",
            "settings.tunnel.no_reply", "settings.tunnel.limit",
            "settings.tunnel.best", "settings.tunnel.pool_meta", "settings.tunnel.no_working",
            "settings.tunnel.col.client", "settings.tunnel.col.server", "settings.tunnel.col.sum",
            "settings.tunnel.metrics", "settings.tunnel.metrics.ping", "settings.tunnel.metrics.client",
            "settings.tunnel.metrics.server", "settings.tunnel.metrics.stability", "settings.tunnel.winner",
            "settings.tunnel.scale.good", "settings.tunnel.scale.mid", "settings.tunnel.scale.bad",
            "settings.tunnel.pick", "settings.tunnel.quality")
        listOf("messages.properties", "messages_ru.properties", "messages_en.properties").forEach { file ->
            val props = Properties()
            javaClass.getResourceAsStream("/dev/launcherfork/ui/i18n/$file")!!.reader(Charsets.UTF_8).use(props::load)
            keys.forEach { k -> assertTrue(!props.getProperty(k).isNullOrBlank(), "$file misses $k") }
            assertNull(props.getProperty("settings.ram.recommended"), "$file: stale chip caption key")
            assertNull(props.getProperty("settings.ram.recommended_value"), "$file: stale recommended line")
            assertNull(props.getProperty("settings.heading.others"), "$file: ПРОЧЕЕ is gone")
            assertNull(props.getProperty("settings.page.title"), "$file: no page title any more")
            listOf("settings.nav.ram", "settings.nav.behaviour", "settings.nav.updates", "settings.nav.language").forEach { k ->
                assertNull(props.getProperty(k), "$file: $k still names a tab")
            }
            assertTrue(props.getProperty("settings.ram.hint").contains("%s"), "$file: the hint names the recommended value")
            assertNull(props.getProperty("settings.tunnel.game"), "$file: game choice caption")
            assertNull(props.getProperty("settings.tunnel.game_now"), "$file: game choice status")
            assertNull(props.getProperty("settings.tunnel.game.hint"), "$file: game choice hint")
            assertNull(props.getProperty("settings.tunnel.legend"), "$file: RTT legend")
            assertNull(props.getProperty("settings.tunnel.mode.game"), "$file: game weight mode")
            val measure = props.getProperty("settings.tunnel.measure")
            assertFalse(measure.contains("снова") || measure.contains("again"), "$file: measure is still a repeat caption")
        }
    }

    /** Tunnel is its own page. JVM, account swap and sensitivity each replace the column too. Tunnels stay grouped by pool. */
    @Test
    fun clientPageFitsAndKeepsTheTools() {
        val app = AppContext.mock(language = "ru", authDelayMs = 0)
        val nav = mainOnScreen(app, "ru")
        try {
            listOf(
                Theme.WINDOW_MIN_WIDTH to Theme.WINDOW_MIN_HEIGHT,
                Theme.WINDOW_WIDTH to Theme.WINDOW_HEIGHT,
            ).forEach { (w, h) ->
                resize(nav, w, h)
                FxTestSupport.onFx {
                    val main = nav.mainController!!
                    val s = main.settingsController
                    main.settingsController.showSection("ram")
                    main.setSettingsVisible(true)
                    relayout(nav)
                    val where = "${w.toInt()}x${h.toInt()}"
                    val window = BoundingBox(0.0, 0.0, nav.mainStage.scene.width, nav.mainStage.scene.height)
                    assertNull(main.profileBarController.profileMenu.lookup("#profileMenuSens"), "$where: sensitivity stayed in the profile menu")
                    assertTrue(main.profileBarController.profileMenu.lookup("#profileMenuAccounts") != null, "$where: accounts left the profile menu")
                    assertTrue(s.settingsRoot.lookupAll(".settings-pagebar").isEmpty(), "$where: tab switch still on the page")
                    assertTrue(main.headerController.settingsNav.isManaged, "$where: settings nav hidden")
                    assertEquals(
                        listOf(main.headerController.navJvm, main.headerController.navTunnel, main.headerController.navSens, main.headerController.navAccounts),
                        main.headerController.settingsNav.children.toList(),
                        "$where: switch is not JVM, tunnel, sensitivity, accounts",
                    )
                    assertTrue(s.launcherPage.isManaged && !s.clientContent.isManaged, "$where: settings did not open on the column")
                    assertTrue(
                        s.gameSection.isManaged && s.behaviourSection.isManaged && s.updatesSection.isManaged &&
                            s.languageSection.isManaged && !s.tunnelPage.isManaged,
                        "$where: the column is missing a block or still holds the tunnel",
                    )
                    assertFalse(s.jvmPage.isManaged || s.tunnelPage.isManaged || s.sensPage.isManaged || s.accountsPage.isManaged, "$where: a tool page is open on the column")

                    s.showClientSection("jvm")
                    relayout(nav)
                    assertTrue(s.jvmGrid.children.size > 1, "$where: presets missing")
                    assertTrue(s.jvmPage.sceneBounds().minY >= -1.0, "$where: jvm section not scrolled into view")
                    assertFalse(s.launcherPage.isManaged, "$where: launcher groups stay under JVM")
                    assertFalse(s.tunnelPage.isManaged, "$where: tunnel stays on the JVM section")
                    assertTrue("selected" in main.headerController.navJvm.styleClass, "$where: JVM not selected")
                    s.showClientSection("tunnel")
                    relayout(nav)
                    assertTrue(s.tunnelPage.isManaged && !s.launcherPage.isManaged, "$where: tunnel did not replace the column")
                    assertFalse(s.jvmPage.isManaged || s.sensPage.isManaged || s.accountsPage.isManaged, "$where: a tool page stayed open with the tunnel")
                    assertTrue("selected" in main.headerController.navTunnel.styleClass, "$where: tunnel not selected")
                    val viewH = s.scroll.viewportBounds.height
                    assertTrue(s.tunnelPage.height >= viewH - 28.0, "$where: tunnel page does not fill the settings area")
                    assertTrue(s.scroll.content.layoutBounds.height <= viewH + 2.0, "$where: tunnel still scrolls with the column")
                    assertTrue(
                        s.tunnelPage.lookupAll(".labeled").filterIsInstance<Labeled>().none {
                            it.text == "Выбор игры" || it.text == "Game choice" ||
                                it.text == "Клиент · сервер · сумма" || it.text == "Client · server · sum"
                        },
                        "$where: game choice or the RTT legend is still on the page",
                    )
                    assertTrue(s.tunnelPage.lookupAll(".tunnel-chip").isEmpty(), "$where: region is still a chip row")
                    assertTrue("segmented" in s.regionRu.parent.styleClass, "$where: region switch is not the segmented control")
                    val switches = s.tunnelPools.lookupAll(".tunnel-block-switch").filterIsInstance<CheckBox>()
                    assertTrue(switches.isNotEmpty() && switches.all { "switch" in it.styleClass && !it.isSelected }, "$where: a server row has no block switch")
                    assertTrue(switches.all { it.text.isEmpty() }, "$where: the block control is still a text chip")
                    assertFalse("primary" in s.tunnelMeasure.styleClass, "$where: Measure is a primary action")
                    assertTrue("primary" in s.tunnelFindBest.styleClass, "$where: Find best lost the accent")
                    assertEquals("Измерить", s.tunnelMeasure.text)
                    listOf(s.tunnelMeasure, s.tunnelFindBest, s.tunnelRefresh).forEach { button ->
                        assertEquals(34.0, button.height, 0.5, "$where: '${button.text}' is not the settings action height")
                        assertEquals(s.tunnelMeasure.padding, button.padding, "$where: '${button.text}' padding")
                        assertEquals(s.tunnelMeasure.font.size, button.font.size, 0.01, "$where: '${button.text}' font")
                    }
                    assertNotEquals(
                        s.tunnelMeasure.background.fills.last().fill,
                        s.tunnelFindBest.background.fills.last().fill,
                        "$where: Find best looks like an outline",
                    )
                    val head = s.regionRu.parent.parent as javafx.scene.layout.Region
                    assertTrue("tunnel-head" in head.styleClass, "$where: heading and regions are not one row")
                    assertTrue(s.tunnelMeasure.parent !== head, "$where: the long actions are squeezed onto the region row")
                    assertEquals(s.tunnelPage.width, head.width, 1.0, "$where: toolbar does not span the page")
                    val heading = (head as Parent).childrenUnmodifiable.filterIsInstance<Labeled>().single()
                    assertEquals("ТУННЕЛЬ", heading.text, "$where: tunnel heading left the head row")
                    assertTrue(heading.localToScene(heading.boundsInLocal).maxX <= s.regionRu.localToScene(s.regionRu.boundsInLocal).minX + 1.0, "$where: heading overlaps the regions")
                    val actions = s.tunnelMeasure.parent as Parent
                    assertEquals(
                        listOf(s.tunnelMeasure, s.tunnelFindBest, s.tunnelRefresh, s.tunnelMode),
                        actions.childrenUnmodifiable.filter { it === s.tunnelMeasure || it === s.tunnelFindBest || it === s.tunnelRefresh || it === s.tunnelMode },
                        "$where: the action row is not Measure, Find best, Refresh, mode",
                    )
                    assertTrue(s.tunnelRefresh.localToScene(s.tunnelRefresh.boundsInLocal).maxX <= s.tunnelMode.localToScene(s.tunnelMode.boundsInLocal).minX + 1.0, "$where: mode is not at the end of the action row")
                    assertEquals("Обновить", s.tunnelRefresh.text)
                    assertFalse("primary" in s.tunnelRefresh.styleClass, "$where: Refresh is a primary action")
                    val nodes = s.tunnelPools.lookupAll(".tunnel-node").filterIsInstance<Button>()
                    assertTrue(nodes.size >= 2, "$where: server rows missing")
                    nodes.forEach { node ->
                        assertEquals(34.0, node.height, 0.5, "$where: server row height")
                        assertEquals(3, node.lookupAll(".tunnel-ping").size, "$where: a row is missing a ping figure")
                        val pool = generateSequence(node as javafx.scene.Node) { it.parent }.firstOrNull { "pool" in it.styleClass }
                        assertNotNull(pool, "$where: a server row is outside its pool card")
                        assertTrue(pool!!.lookupAll(".pool-title").isNotEmpty(), "$where: a pool card has no name")
                    }
                    val pools = s.tunnelPools.lookupAll(".pool-title").map { (it as javafx.scene.control.Label).text }
                    assertTrue("MSK1" in pools && "MSK2" in pools, "$where: RU pools $pools")
                    assertTrue("GAME-EU" !in pools, "$where: EU pool leaked into RU")
                    assertTrue(pools.none { it.startsWith("CLOUD-") }, "$where: dead cloud pools are listed: $pools")
                    val msk1 = s.tunnelPools.children.first { pool ->
                        pool.lookupAll(".pool-title").filterIsInstance<Labeled>().any { it.text == "MSK1" }
                    } as Parent
                    assertEquals(
                        listOf("MSK1-1", "MSK1-2", "MSK1-3", "MSK1-4", "MSK1-5"),
                        msk1.lookupAll(".tunnel-row").map { (it as Labeled).text },
                        "$where: MSK1 does not hold its own servers",
                    )
                    val exclude = msk1.lookupAll(".tunnel-pool-lock").filterIsInstance<Button>().single()
                    assertEquals("Исключить группу", exclude.text, "$where: group action is not labeled")
                    assertTrue(exclude.lookupAll(".tunnel-state").isEmpty(), "$where: exclude is still an icon lock")
                    assertTrue(exclude.width >= exclude.prefWidth(-1.0) - 1.0, "$where: '${exclude.text}' truncated")
                    val poolBox = msk1.localToScene(msk1.boundsInLocal)
                    val excludeBox = exclude.localToScene(exclude.boundsInLocal)
                    assertTrue(
                        excludeBox.minX >= poolBox.minX - 1.0 && excludeBox.maxX <= poolBox.maxX + 1.0 &&
                            excludeBox.minY >= poolBox.minY - 1.0 && excludeBox.maxY <= poolBox.maxY + 1.0,
                        "$where: exclude sits outside the MSK1 section",
                    )
                    val legend = s.tunnelPools.lookupAll(".tunnel-legend").filterIsInstance<Labeled>()
                    assertEquals(listOf("До вас", "До игры", "Пинг"), legend.map { it.text }, "$where: value legend")
                    assertTrue(s.tunnelPools.lookupAll(".tunnel-metrics").isEmpty(), "$where: the metrics paragraph is still on the page")
                    assertTrue(s.tunnelPools.lookupAll(".tunnel-scale").isEmpty(), "$where: the color caption row is still on the page")
                    assertTrue("decision" in legend.last().styleClass, "$where: ping is not the column find-best uses")
                    nodes.forEach { row ->
                        val block = (row.parent as Parent).lookupAll(".tunnel-block-switch").filterIsInstance<CheckBox>().single()
                        assertFalse(block.isSelected, "$where: an available server is already blocked")
                        assertTrue("switch" in block.styleClass, "$where: block control is not the zone switch")
                        assertEquals(36.0, block.width, 1.0, "$where: block switch is not the compact control")
                        val card = generateSequence(row as javafx.scene.Node) { it.parent }.first { "pool" in it.styleClass }
                        val cardBox = card.localToScene(card.boundsInLocal)
                        val blockBox = block.localToScene(block.boundsInLocal)
                        assertTrue(
                            blockBox.minX >= cardBox.minX - 1.0 && blockBox.maxX <= cardBox.maxX + 1.0,
                            "$where: block control leaves its pool",
                        )
                    }
                    val node = nodes.first { (it.lookup(".tunnel-row") as Labeled).text == "MSK1-1" }
                    val pings = node.lookupAll(".tunnel-ping").filterIsInstance<Labeled>()
                    legend.zip(pings).forEach { (caption, ping) ->
                        val capBox = caption.localToScene(caption.boundsInLocal)
                        val pingBox = ping.localToScene(ping.boundsInLocal)
                        assertEquals(pingBox.centerX, capBox.centerX, 1.0, "$where: '${caption.text}' is not over its column")
                        assertTrue(capBox.maxY <= pingBox.minY + 1.0, "$where: '${caption.text}' is not above the values")
                        assertTrue(contains(window, capBox), "$where: '${caption.text}' outside")
                    }
                    val line = node.parent as javafx.scene.layout.Region
                    val list = line.parent as javafx.scene.layout.Region
                    val inner = list.width - list.snappedLeftInset() - list.snappedRightInset()
                    assertEquals(inner, line.width, 1.0, "$where: server row does not fill the pool")
                    assertTrue(line.localToScene(line.boundsInLocal).maxX <= poolBox.maxX + 1.0, "$where: server row leaves the pool")
                    assertTrue(s.tunnelPage.lookupAll(".combo-box").isEmpty(), "$where: native mode menu")
                    assertTrue(s.tunnelPage.lookupAll(".tunnel-lock").isEmpty(), "$where: lock buttons are still on the page")
                    val modeButton = s.tunnelPage.lookup(".tunnel-mode-button") as Button
                    assertEquals(s.tunnelMeasure.parent, s.tunnelMode.parent, "$where: mode control left the action row")
                    assertEquals(s.tunnelMode, modeButton.parent, "$where: mode button left its control")
                    assertEquals(34.0, modeButton.height, 0.5, "$where: mode control height")
                    assertTrue("settings-mini" in modeButton.styleClass, "$where: mode control is not a settings action")
                    modeButton.fire()
                    relayout(nav)
                    val menu = s.tunnelPage.lookup(".tunnel-mode-list") as javafx.scene.layout.Region
                    assertTrue(menu.isVisible, "$where: mode list did not open")
                    val items = menu.lookupAll(".tunnel-mode-item").filterIsInstance<Button>()
                    assertEquals(listOf("Пинг", "RTT клиента", "RTT сервера", "Стабильность"), items.map { it.text })
                    val regionBottom = s.regionRu.localToScene(s.regionRu.boundsInLocal).maxY
                    val cardFill = (msk1 as javafx.scene.layout.Region).background.fills.last().fill
                    assertEquals(cardFill, menu.background.fills.last().fill, "$where: mode list is not the card surface")
                    items.forEach { item ->
                        val box = item.localToScene(item.boundsInLocal)
                        assertTrue(box.minY >= regionBottom - 1.0, "$where: mode list covers the region switch")
                        assertTrue(contains(window, box), "$where: '${item.text}' outside")
                    }
                    items.first { it.text == "Пинг" }.fire()
                    assertFalse(menu.isVisible, "$where: mode list stayed open")
                    listOf(s.regionRu, s.regionEu, s.regionNa, s.regionSea, s.regionNea, s.tunnelMeasure, s.tunnelFindBest, s.tunnelRefresh, modeButton).forEach { b ->
                        assertTrue(b.width >= b.prefWidth(-1.0) - 0.5, "$where: '${b.text}' truncated")
                        assertTrue(contains(window, b.sceneBounds()), "$where: '${b.text}' outside")
                    }
                    assertTrue(s.tunnelFindBest.sceneBounds().maxY <= s.tunnelScroll.sceneBounds().minY + 1.0, "$where: actions sit in the scrolling list")
                    assertFalse(generateSequence(s.tunnelMeasure as javafx.scene.Node) { it.parent }.any { it === s.tunnelScroll }, "$where: measure scrolls away")
                    assertTrue(s.tunnelPage.isManaged && !s.jvmPage.isManaged && !s.launcherPage.isManaged, "$where: tunnel fell back into the column")
                    s.showClientSection("accounts")
                    relayout(nav)
                    assertTrue(s.accountsPage.lookupAll(".list-view").isEmpty(), "$where: accounts still a bare list")
                    assertTrue(contains(window, s.sessionScroll.sceneBounds()), "$where: session cards outside")
                    assertTrue(contains(window, s.sessionName.sceneBounds()), "$where: session name outside")
                    assertEquals(34.0, s.captureSession.height, 0.5, "$where: Сохранить is not the settings action height")
                    assertFalse("primary" in s.captureSession.styleClass, "$where: Сохранить is a primary action")
                    assertEquals(s.tunnelMeasure.padding, s.captureSession.padding, "$where: Сохранить padding")
                    assertTrue(s.captureSession.width >= s.captureSession.prefWidth(-1.0) - 0.5, "$where: '${s.captureSession.text}' truncated")
                    assertTrue(contains(window, s.captureSession.sceneBounds()), "$where: '${s.captureSession.text}' outside")
                    assertTrue(s.accountsPage.isManaged && !s.launcherPage.isManaged && !s.tunnelPage.isManaged, "$where: accounts did not replace the column")
                    assertTrue("selected" in main.headerController.navAccounts.styleClass, "$where: accounts not selected")
                    s.showClientSection("sens")
                    relayout(nav)
                    listOf(s.calcSens, s.writeSens).forEach { b ->
                        assertEquals(34.0, b.height, 0.5, "$where: '${b.text}' is not the settings action height")
                        assertEquals(s.tunnelMeasure.padding, b.padding, "$where: '${b.text}' padding")
                        assertTrue(b.width >= b.prefWidth(-1.0) - 0.5, "$where: '${b.text}' truncated")
                        assertTrue(contains(window, b.sceneBounds()), "$where: '${b.text}' outside")
                    }
                    assertFalse("primary" in s.calcSens.styleClass, "$where: Посчитать is a primary action")
                    assertTrue("primary" in s.writeSens.styleClass, "$where: Записать lost the accent")
                    assertTrue(s.sensPage.isManaged && !s.accountsPage.isManaged && !s.launcherPage.isManaged, "$where: sensitivity did not replace accounts")
                    assertTrue("selected" in main.headerController.navSens.styleClass, "$where: sensitivity not selected")
                    if (w == Theme.WINDOW_WIDTH) {
                        s.showClientSection("tunnel")
                        relayout(nav)
                        preview(nav.mainStage.scene, "settings-tunnel")
                        s.showClientSection("jvm")
                        relayout(nav)
                        preview(nav.mainStage.scene, "settings-jvm")
                    }
                }
            }
            val s = nav.mainController!!.settingsController
            TunnelServices.probe = { endpoint ->
                when (endpoint.name) {
                    "MSK1-1" -> TunnelProbeOutcome.Reply(java.time.Duration.ofMillis(12), java.time.Duration.ofMillis(8), false)
                    "MSK1-2" -> TunnelProbeOutcome.Reply(java.time.Duration.ofMillis(5), java.time.Duration.ofMillis(4), true)
                    "MSK1-3" -> TunnelProbeOutcome.Reply(java.time.Duration.ofMillis(4), java.time.Duration.ofMillis(3), false)
                    "GAME-EU-2" -> TunnelProbeOutcome.Reply(java.time.Duration.ofMillis(15), java.time.Duration.ofMillis(5), false)
                    else -> TunnelProbeOutcome.NoReply
                }
            }
            val fetches = dev.launcherfork.stalzone.TunnelCatalogs.fetchAttempts.get()
            val probeAttempts = dev.launcherfork.stalzone.TunnelProbe.attempts.get()
            fun clickPool(name: String) {
                val title = s.tunnelPools.lookupAll(".pool-title").filterIsInstance<javafx.scene.control.Label>().first { it.text == name }
                title.onMouseClicked.handle(
                    MouseEvent(
                        MouseEvent.MOUSE_CLICKED, 1.0, 1.0, 1.0, 1.0, javafx.scene.input.MouseButton.PRIMARY, 1,
                        false, false, false, false, true, false, false, true, false, false, null,
                    ),
                )
            }
            fun nodeNamed(name: String) = s.tunnelPools.lookupAll(".tunnel-node").filterIsInstance<Button>().first { button ->
                val row = button.graphic as? javafx.scene.Parent
                row?.childrenUnmodifiable?.filterIsInstance<Labeled>()?.any { it.text == name } == true
            }
            fun pingText(button: Button, kind: String): String {
                val row = button.graphic as javafx.scene.Parent
                return row.childrenUnmodifiable.filterIsInstance<Labeled>().first { kind in it.styleClass }.text
            }
            FxTestSupport.onFx {
                s.showClientSection("tunnel")
                relayout(nav)
                assertEquals(probeAttempts, dev.launcherfork.stalzone.TunnelProbe.attempts.get(), "opening the page probed a node")
                clickPool("MSK1")
                val first = nodeNamed("MSK1-1")
                assertEquals("12.0", pingText(first, "client"))
                assertEquals("8.0", pingText(first, "server"))
                assertEquals("20.0", pingText(first, "sum"))
                val limited = nodeNamed("MSK1-2")
                assertTrue(limited.isDisable, "a node at its connection limit can be selected")
                first.fire()
                val saved = java.nio.file.Files.readString(TunnelServices.overridesFile())
                assertTrue(saved.contains("85.119.149.7:29450"))
                limited.fire()
                assertEquals(saved, java.nio.file.Files.readString(TunnelServices.overridesFile()), "a limited node was written")
                s.tunnelMeasure.fire()
                assertEquals(saved, java.nio.file.Files.readString(TunnelServices.overridesFile()), "Измерить changed the override")
                assertEquals("4.0", pingText(nodeNamed("MSK1-3"), "client"))
                assertEquals("3.0", pingText(nodeNamed("MSK1-3"), "server"))
                assertEquals("7.0", pingText(nodeNamed("MSK1-3"), "sum"))
                s.tunnelFindBest.fire()
                val picked = java.nio.file.Files.readString(TunnelServices.overridesFile())
                assertTrue(picked.contains("85.119.149.9:29450"), "find best did not write MSK1-3")
                assertFalse(picked.contains("85.119.149.7:29450"), "find best left the slower node")
                assertFalse(picked.contains("85.119.149.8:29450"), "find best wrote the limited node")
                val winner = nodeNamed("MSK1-3")
                assertTrue("selected" in winner.styleClass, "find best did not mark the winner")
                assertTrue("best" in winner.styleClass, "find best did not mark the recommendation")
                val winnerRow = winner.graphic as javafx.scene.Parent
                val winnerLabels = winnerRow.childrenUnmodifiable.filterIsInstance<Labeled>()
                val dot = winnerRow.childrenUnmodifiable.first { "tunnel-best-dot" in it.styleClass }
                assertFalse("idle" in dot.styleClass, "find best did not mark the recommendation")
                val winnerBlock = (winner.parent as Parent).lookupAll(".tunnel-block-switch").filterIsInstance<CheckBox>().single()
                assertFalse(winnerBlock.isSelected, "the recommended server is already blocked")
                assertTrue("switch" in winnerBlock.styleClass, "the recommended server has no block switch")
                assertTrue("good" in winnerLabels.last { "tunnel-ping" in it.styleClass }.styleClass)
                assertTrue("limited" in limited.styleClass, "a node at its limit looks open")
                val winnerPool = generateSequence(winner as javafx.scene.Node) { it.parent }.first { "pool" in it.styleClass }
                assertEquals("MSK1", winnerPool.lookupAll(".pool-title").filterIsInstance<Labeled>().first().text)
                assertTrue(
                    s.tunnelPools.lookupAll(".pool-title").filterIsInstance<Labeled>().none { it.text == "Лучшие" || it.text == "Best" },
                    "find best still shows a preview list",
                )
                s.tunnelPools.lookupAll(".tunnel-node").forEach { node ->
                    assertNotNull(
                        generateSequence(node) { it.parent }.firstOrNull { "pool" in it.styleClass },
                        "a measured node is outside its pool card",
                    )
                }
                s.regionEu.fire()
                s.tunnelFindBest.fire()
                val after = java.nio.file.Files.readString(TunnelServices.overridesFile())
                assertTrue(after.contains("85.119.149.9:29450"), "find best on EU cleared RU")
                assertTrue(after.contains("79.127.241.67:29450"), "find best did not write EU")
                val args = TunnelOverrides.commandWithOverrides(listOf("java"), TunnelServices.overridesFile())
                assertEquals(fetches, dev.launcherfork.stalzone.TunnelCatalogs.fetchAttempts.get(), "launch fetched a catalog")
                assertEquals(probeAttempts, dev.launcherfork.stalzone.TunnelProbe.attempts.get(), "launch probed a node")
                assertTrue(args.any { it == "-Droxy_address_override.ru=85.119.149.9:29450" })
                assertTrue(args.any { it == "-Droxy_address_override.eu=79.127.241.67:29450" })
                assertFalse(java.nio.file.Files.readString(TunnelServices.overridesFile()).contains("STALZONE-SB"))
                s.showClientSection("jvm")
                val card = s.jvmGrid.children.filterIsInstance<Button>()
                    .first { it.userData == dev.launcherfork.stalzone.JvmPresets.CURRENT }
                card.fire()
                assertEquals(dev.launcherfork.stalzone.JvmPresets.CURRENT, app.desk.activeProfile())
                s.showClientSection("sens")
                s.baseSens.text = "5"
                s.calcSens.fire()
                assertEquals(dev.launcherfork.stalzone.ScopeSens.MAGNIFICATIONS.size, s.sensList.children.size, "sensitivity rows")
                val sensRow = s.sensList.children[0] as javafx.scene.Parent
                assertEquals("Чувствительность в прицелах кратностью 1x", (sensRow.lookup(".sens-name") as Labeled).text)
                assertEquals("5.00", (sensRow.lookup(".sens-value") as Labeled).text)
                assertNotNull(sensRow.lookup(".sens-slider"))
                assertEquals(2, sensRow.lookupAll(".sens-step").size)
                val zoomRow = s.sensList.children[1] as javafx.scene.Parent
                assertEquals("Чувствительность в прицелах кратностью 1.25x", (zoomRow.lookup(".sens-name") as Labeled).text)
                assertTrue(s.clientStatus.text.isNotBlank())
            }
        } finally {
            FxTestSupport.onFx { nav.close() }
            app.shutdown()
        }
    }

    @Test
    fun savedOverrideStaysOnItsRegion() {
        TunnelOverrides.saveSettings(
            TunnelServices.overridesFile(),
            dev.launcherfork.stalzone.TunnelSettings(
                overrides = mapOf(
                    dev.launcherfork.stalzone.TunnelRegion.RU to dev.launcherfork.stalzone.TunnelSelection(
                        dev.launcherfork.stalzone.TunnelRegion.RU, "MSK1", "MSK1-1", "85.119.149.7:29450",
                    ),
                    dev.launcherfork.stalzone.TunnelRegion.EU to dev.launcherfork.stalzone.TunnelSelection(
                        dev.launcherfork.stalzone.TunnelRegion.EU, "GAME-EU", "GAME-EU-2", "79.127.241.67:29450",
                    ),
                ),
            ),
        )
        val (c, _) = settingsWith(LauncherSettings())
        FxTestSupport.onFx {
            c.showClientSection("tunnel")
            c.settingsRoot.applyCss()
            c.settingsRoot.layout()
            val ru = c.tunnelPools.lookupAll(".tunnel-node").filterIsInstance<Button>()
                .first { (it.lookup(".tunnel-row") as Labeled).text == "MSK1-1" }
            assertTrue("selected" in ru.styleClass)
            assertTrue(ru.isDisable, "a saved choice is selectable before a fresh measurement")
            c.regionEu.fire()
            val before = java.nio.file.Files.readString(TunnelServices.overridesFile())
            c.tunnelMeasure.fire()
            assertEquals(before, java.nio.file.Files.readString(TunnelServices.overridesFile()), "Измерить changed a saved override")
            TunnelServices.probe = { endpoint ->
                if (endpoint.name == "GAME-EU-3") {
                    TunnelProbeOutcome.Reply(java.time.Duration.ofMillis(6), java.time.Duration.ofMillis(4), false)
                } else {
                    TunnelProbeOutcome.NoReply
                }
            }
            c.tunnelFindBest.fire()
            val text = java.nio.file.Files.readString(TunnelServices.overridesFile())
            assertTrue(text.contains("85.119.149.7:29450"), "find best on EU cleared RU")
            assertTrue(text.contains("79.127.241.68:29450"), "find best did not write the EU winner")
            assertFalse(text.contains("79.127.241.67"), "find best left the previous EU node")
            assertFalse(text.contains("New-NetFirewallRule") || text.contains("RunAs"))
        }
    }

    @Test
    fun refreshKeepsFailedMeasuresAndHidesNewSilentServers() {
        val (c, _) = settingsWith(LauncherSettings())
        FxTestSupport.onFx {
            c.showClientSection("tunnel")
            c.settingsRoot.applyCss()
            c.settingsRoot.layout()
            fun names(): List<String> = c.tunnelPools.lookupAll(".tunnel-node").filterIsInstance<Button>().mapNotNull { button ->
                val row = button.graphic as? javafx.scene.Parent ?: return@mapNotNull null
                row.childrenUnmodifiable.filterIsInstance<Labeled>().firstOrNull { "tunnel-row" in it.styleClass }?.text
            }
            assertTrue("MSK1-1" in names())
            assertTrue("MSK1-5" in names())
            TunnelServices.probe = { endpoint ->
                when (endpoint.name) {
                    "MSK1-1", "NEW-1" -> TunnelProbeOutcome.Reply(java.time.Duration.ofMillis(10), java.time.Duration.ofMillis(5), false)
                    else -> TunnelProbeOutcome.NoReply
                }
            }
            c.tunnelMeasure.fire()
            val measured = names()
            assertTrue("MSK1-1" in measured, "measured=$measured")
            assertTrue("MSK1-5" in measured, "a failed measure removed a listed server: $measured")
            assertTrue(measured.any { it.startsWith("SPB3") }, "a failed measure removed a listed pool: $measured")
            TunnelServices.fetch = { region ->
                if (region != TunnelRegion.RU) TunnelFixtures.catalog(region)
                else TunnelCatalogs.parse(
                    """
                    {"mode":"roxy","clientToTunnelRttWeight":1,"pools":[
                      {"name":"CLOUD-RU-7","tunnels":[
                        {"name":"SRVMGR-ACCESSOR-OFT-RU-7A","address":"176.114.88.147:29450"}
                      ]},
                      {"name":"CLOUD-EU-9","tunnels":[
                        {"name":"CLOUD-EU-9A","address":"198.51.100.20:29450"}
                      ]},
                      {"name":"MSK1","tunnels":[
                        {"name":"MSK1-1","address":"85.119.149.7:29450"},
                        {"name":"MSK1-2","address":"85.119.149.8:29450"}
                      ]},
                      {"name":"NEW","tunnels":[
                        {"name":"NEW-1","address":"203.0.113.50:29450"},
                        {"name":"NEW-2","address":"203.0.113.51:29450"}
                      ]}
                    ]}
                    """.trimIndent(),
                )
            }
            c.tunnelRefresh.fire()
            assertEquals(setOf("MSK1-1", "MSK1-2", "NEW-1"), names().toSet())
            assertEquals(
                setOf("MSK1", "NEW"),
                c.tunnelPools.lookupAll(".pool-title").filterIsInstance<Labeled>().map { it.text }.toSet(),
            )
            assertTrue(names().none { it.contains("SRVMGR") || it.startsWith("CLOUD") }, "a dead cloud node was listed")
            c.tunnelFindBest.fire()
            val picked = java.nio.file.Files.readString(TunnelServices.overridesFile())
            assertFalse(picked.contains("176.114.88.147"), "find best wrote CLOUD-RU-7")
            assertFalse(picked.contains("198.51.100.20"), "find best wrote CLOUD-EU-9")
        }
    }

    @Test
    fun groupExcludeConfirmsBeforeFirewallAndHidesInvalidNodes() {
        val calls = mutableListOf<List<String>>()
        TunnelServices.applyBlocks = { ips ->
            calls += ips.toList()
            Result.success("ok")
        }
        TunnelServices.clearBlocks = {
            calls.add(emptyList())
            Result.success(0)
        }
        val (c, store) = settingsWith(LauncherSettings())
        FxTestSupport.onFx {
                c.showClientSection("tunnel")
                c.settingsRoot.applyCss()
                c.settingsRoot.layout()
                fun excludeNamed(pool: String) = c.tunnelPools.children.first { card ->
                    card.lookupAll(".pool-title").filterIsInstance<Labeled>().any { it.text == pool }
                }.lookupAll(".tunnel-pool-lock").filterIsInstance<Button>().single()
                fun confirmBar() = c.tunnelPage.lookup(".tunnel-confirm") as javafx.scene.layout.Region
                fun blockNamed(name: String): CheckBox {
                    val node = c.tunnelPools.lookupAll(".tunnel-node").filterIsInstance<Button>().first { button ->
                        val row = button.graphic as? Parent
                        row?.childrenUnmodifiable?.filterIsInstance<Labeled>()?.any { it.text == name } == true
                    }
                    return (node.parent as Parent).childrenUnmodifiable.filterIsInstance<CheckBox>()
                        .single { "tunnel-block-switch" in it.styleClass }
                }
                val exclude = excludeNamed("MSK1")
                assertEquals("Исключить группу", exclude.text)
                exclude.fire()
                assertTrue(calls.isEmpty(), "group exclude fired the firewall before confirm")
                val bar = confirmBar()
                assertTrue(bar.isVisible, "confirm did not open")
                assertEquals("Заблокировать 5 нод группы?", (bar.lookup(".tunnel-confirm-text") as Labeled).text)
                (bar.lookup(".tunnel-confirm-no") as Button).fire()
                assertFalse(bar.isVisible, "cancel left the confirm open")
                assertTrue(calls.isEmpty(), "cancel wrote firewall rules")
                assertEquals("Исключить группу", excludeNamed("MSK1").text)
                val overrides = TunnelServices.overridesFile()
                assertFalse(
                    java.nio.file.Files.exists(overrides) && java.nio.file.Files.readString(overrides).contains("excluded_pools"),
                    "cancel stored a group exclusion",
                )
                excludeNamed("MSK1").fire()
                (confirmBar().lookup(".tunnel-confirm-yes") as Button).fire()
                assertEquals("Группа исключена", excludeNamed("MSK1").text)
                assertEquals(1, calls.size, "confirm did not write the firewall once: $calls")
                assertEquals(calls.single(), ServerBlock.uniqueValidIpv4(calls.single()))
                assertEquals(
                    listOf("85.119.149.7", "85.119.149.8", "85.119.149.9", "85.119.149.10", "85.119.149.25"),
                    calls.single(),
                )
                assertFalse(java.nio.file.Files.readString(overrides).contains("New-NetFirewallRule"))
                assertTrue(java.nio.file.Files.readString(overrides).contains("MSK1"))
                assertTrue(store.load().serverBlock.blocked.containsAll(listOf("MSK1::MSK1-1", "MSK1::MSK1-5")))
                val unblock = blockNamed("MSK1-1")
                assertTrue(unblock.isSelected, "group block did not turn the switch on")
                assertEquals("Снять блок", unblock.accessibleText)
                unblock.fire()
                assertFalse(confirmBar().isVisible, "a single server opened the group confirm")
                assertEquals(2, calls.size, "a single server did not sync the firewall")
                assertEquals(calls.last(), ServerBlock.uniqueValidIpv4(calls.last()))
                assertFalse("85.119.149.7" in calls.last())
                val cleared = blockNamed("MSK1-1")
                assertFalse(cleared.isSelected, "unblocking a server left the switch on")
                assertEquals("Блок", cleared.accessibleText)
                assertFalse("MSK1::MSK1-1" in store.load().serverBlock.blocked)
                TunnelServices.probe = { endpoint ->
                    if (endpoint.name == "HOST") {
                        TunnelProbeOutcome.Reply(Duration.ofMillis(1), Duration.ofMillis(1), true)
                    } else {
                        TunnelProbeOutcome.Reply(Duration.ofMillis(12), Duration.ofMillis(4), false)
                    }
                }
                TunnelServices.fetch = { region ->
                    if (region != TunnelRegion.RU) TunnelFixtures.catalog(region)
                    else TunnelCatalogs.parse(
                        """
                        {"mode":"roxy","clientToTunnelRttWeight":1,"pools":[
                          {"name":"MIX","tunnels":[
                            {"name":"GOOD","address":"203.0.113.9:29450"},
                            {"name":"HOST","address":"tunnel.example:29450"},
                            {"name":"BAD","address":"999.1.1.1:29450"}
                          ]}
                        ]}
                        """.trimIndent(),
                    )
                }
                c.tunnelRefresh.fire()
                val shown = c.tunnelPools.lookupAll(".tunnel-node").filterIsInstance<Button>().mapNotNull { button ->
                    val row = button.graphic as? Parent
                    row?.childrenUnmodifiable?.filterIsInstance<Labeled>()?.firstOrNull { "tunnel-row" in it.styleClass }?.text
                }
                assertTrue("GOOD" in shown, "a valid node was hidden: $shown")
                assertFalse("HOST" in shown, "a limit node without an IPv4 was shown: $shown")
                assertFalse("BAD" in shown, "an invalid address was shown: $shown")
                assertFalse(shown.any { it == "—" || it.isBlank() })
                excludeNamed("MIX").fire()
                assertEquals("Заблокировать 1 нод группы?", (confirmBar().lookup(".tunnel-confirm-text") as Labeled).text)
                assertEquals(2, calls.size, "opening confirm firewalled the group")
                (confirmBar().lookup(".tunnel-confirm-yes") as Button).fire()
                assertTrue("203.0.113.9" in calls.last(), "group confirm dropped the valid node: ${calls.last()}")
                assertTrue(calls.last().none { !ServerBlock.isValidIpv4(it) }, "firewall received a non-IPv4: ${calls.last()}")
                assertEquals(calls.last(), ServerBlock.uniqueValidIpv4(calls.last()))
            }
    }

    @Test
    fun accountSessionsAreCardsWithKeysAndDirectApply(@TempDir dir: Path) {
        val registry = MemoryRegistry()
        registry.writeValues(ExboSessions.KEY_PATH, mapOf("EXBO_LOGIN" to "Nyrokume", "EXBO_SESSION" to "token-a"))
        val desk = StalzoneDesk(registry, dir.resolve("sessions.json"), dir.resolve("jvm"), dir.resolve("options.json"))
        desk.captureCurrent("btw rusty")
        registry.writeValues(ExboSessions.KEY_PATH, mapOf("EXBO_LOGIN" to "Zlyka", "EXBO_SESSION" to "token-b"))
        desk.captureCurrent("Zlyka")
        val store = InMemorySettingsStore(LauncherSettings())
        val base = AppContext.mock(speed = 50.0, authDelayMs = 0)
        val app = AppContext(base.services.copy(settings = store, system = MockSystemInfo(16 * gb)), desk = desk)
        try {
            FxTestSupport.onFx {
                val c = Fxml.load<SettingsController>(FxTestSupport.context("ru", app), "settings.fxml").controller
                val scene = javafx.scene.Scene(c.settingsRoot, 771.0, 612.0)
                applyTheme(scene, "zone", "ru")
                c.showClientSection("accounts")
                c.settingsRoot.applyCss(); c.settingsRoot.layout()
                c.settingsRoot.applyCss(); c.settingsRoot.layout()
                val cards = c.sessionCards.children.filter { "session-card" in it.styleClass }
                assertEquals(2, cards.size)
                val first = cards[0] as Parent
                val second = cards[1] as Parent
                assertTrue(first.layoutBounds.width > 400, "card should use the page width (${first.layoutBounds.width})")
                val textNode = first.lookupAll(".session-text").single()
                val actionNode = first.lookupAll(".session-actions").single()
                val text = textNode.localToScene(textNode.boundsInLocal)
                val actions = actionNode.localToScene(actionNode.boundsInLocal)
                val card = first.localToScene(first.boundsInLocal)
                assertTrue(text.maxX <= actions.minX + 0.5, "title column overlaps the action buttons")
                assertTrue(actions.maxX <= card.maxX + 0.5, "actions stick out of the card")
                assertTrue(first.lookupAll(".jvm-preset-title").any { (it as Labeled).text == "btw rusty" })
                assertTrue(first.lookupAll(".jvm-preset-hint").any { (it as Labeled).text.contains("Nyrokume") })
                assertTrue("selected" in second.styleClass, "live login should mark Zlyka")
                assertFalse("selected" in first.styleClass)
                val keys = first.lookupAll(".session-keys").single()
                assertFalse(keys.isVisible)
                val keysToggle = first.lookupAll(".session-keys-toggle").single() as ToggleButton
                assertEquals(34.0, keysToggle.height, 0.5, "key hit box height")
                val apply = first.lookup(".session-apply") as Button
                val delete = first.lookup(".session-delete") as Button
                assertEquals(34.0, apply.height, 0.5, "Применить height")
                assertEquals(apply.height, delete.height, 0.5, "Удалить height")
                assertTrue("primary" in apply.styleClass)
                assertFalse("primary" in delete.styleClass)
                keysToggle.fire()
                c.settingsRoot.applyCss(); c.settingsRoot.layout()
                c.settingsRoot.applyCss(); c.settingsRoot.layout()
                assertTrue(keys.isVisible)
                val row = c.accountsRow.localToScene(c.accountsRow.boundsInLocal)
                val list = c.sessionScroll.localToScene(c.sessionScroll.boundsInLocal)
                assertTrue(list.maxY <= row.minY + 0.5, "session list overlaps the name row")
                assertTrue(contains(BoundingBox(0.0, 0.0, scene.width, scene.height), row), "name row outside the page")
                val keysBox = keys as Parent
                val names = keysBox.lookupAll(".session-key-name").map { (it as Labeled).text }
                val values = keysBox.lookupAll(".session-key-value").map { (it as Labeled).text }
                assertEquals(listOf("EXBO_LOGIN", "EXBO_SESSION"), names)
                assertEquals(listOf("Nyrokume", "token-a"), values)
                (first.lookupAll(".session-apply").single() as Button).fire()
                val live = registry.readValues(ExboSessions.KEY_PATH)
                assertEquals("Nyrokume", live["EXBO_LOGIN"])
                assertEquals("token-a", live["EXBO_SESSION"])
                assertEquals("true", live["EXBO_AUTO_AUTH"])
                val after = c.sessionCards.children.filter { "session-card" in it.styleClass }
                assertTrue("selected" in after[0].styleClass)
                assertFalse("selected" in after[1].styleClass)
                assertTrue(after[0].lookupAll(".session-keys").single().isVisible, "open keys stay open after apply")
                (after[1].lookupAll(".session-delete").single() as Button).fire()
                assertEquals(1, c.sessionCards.children.count { "session-card" in it.styleClass })
                assertEquals("Сессия удалена", c.clientStatus.text)
                assertTrue(c.clientStatus.isVisible)
                c.showClientSection("jvm")
                assertFalse(c.clientStatus.isVisible, "a session note followed the JVM page")
                assertFalse(c.clientStatus.isManaged)
                c.showClientSection("accounts")
                assertTrue(c.clientStatus.isVisible)
                assertEquals("Сессия удалена", c.clientStatus.text)
                assertTrue(contains(BoundingBox(0.0, 0.0, scene.width, scene.height), c.captureSession.sceneBounds()))
            }
        } finally {
            app.shutdown()
        }
    }

    /** Saving a session rebuilds the list. Every card stays full height and the name row stays under the cards. */
    @Test
    fun savingASessionKeepsEveryCardFullHeight(@TempDir dir: Path) {
        val registry = MemoryRegistry()
        registry.writeValues(ExboSessions.KEY_PATH, mapOf("EXBO_LOGIN" to "zlyk_zlykov"))
        val desk = StalzoneDesk(registry, dir.resolve("sessions.json"), dir.resolve("jvm"), dir.resolve("options.json"))
        desk.captureCurrent("Zlyka")
        registry.writeValues(ExboSessions.KEY_PATH, mapOf("EXBO_LOGIN" to "huska"))
        val store = InMemorySettingsStore(LauncherSettings())
        val base = AppContext.mock(language = "ru", speed = 50.0, authDelayMs = 0)
        val app = AppContext(base.services.copy(settings = store, system = MockSystemInfo(16 * gb)), desk = desk)
        val nav = mainOnScreen(app, "ru")
        try {
            resize(nav, Theme.WINDOW_WIDTH, Theme.WINDOW_HEIGHT)
            FxTestSupport.onFx {
                val main = nav.mainController!!
                val c = main.settingsController
                main.setSettingsVisible(true)
                c.showClientSection("accounts")
                relayout(nav)
                val before = c.sessionCards.children.filter { "session-card" in it.styleClass }
                assertEquals(1, before.size)
                val beforeHeight = (before[0] as javafx.scene.layout.Region).height
                assertTrue(beforeHeight >= 48.0, "card was already short ($beforeHeight)")
                c.sessionName.text = "Хуська"
                c.captureSession.fire()
                relayout(nav)
                val cards = c.sessionCards.children.filter { "session-card" in it.styleClass }
                assertEquals(2, cards.size, "save did not add a card")
                val row = c.accountsRow.sceneBounds()
                val name = c.sessionName.sceneBounds()
                val save = c.captureSession.sceneBounds()
                assertEquals("Сессия сохранена", c.clientStatus.text)
                assertTrue(c.clientStatus.sceneBounds().minY >= row.maxY - 0.5, "status overlaps the name row")
                cards.forEach { node ->
                    val card = node as javafx.scene.layout.Region
                    val bounds = card.sceneBounds()
                    assertEquals(beforeHeight, card.height, 1.0, "saved card height ${card.height} vs $beforeHeight")
                    assertTrue(card.height >= 48.0, "card collapsed to ${card.height}")
                    assertTrue(bounds.maxY <= row.minY + 0.5, "card overlaps the name row $bounds vs $row")
                    assertTrue(bounds.maxY <= name.minY + 0.5, "card overlaps the name field")
                    assertTrue(bounds.maxY <= save.minY + 0.5, "card overlaps Сохранить")
                }
                val apply = cards[0].lookup(".session-apply") as Button
                val delete = cards[0].lookup(".session-delete") as Button
                val keys = cards[0].lookup(".session-keys-toggle") as ToggleButton
                assertEquals(34.0, apply.height, 0.5, "Применить height")
                assertEquals(apply.height, delete.height, 0.5, "Удалить height")
                assertEquals(apply.height, c.captureSession.height, 0.5, "Сохранить height")
                assertEquals(apply.height, keys.height, 0.5, "key hit box height")
                assertTrue("primary" in apply.styleClass)
                assertFalse("primary" in delete.styleClass)
                assertFalse("primary" in c.captureSession.styleClass)
            }
        } finally {
            FxTestSupport.onFx { nav.close() }
            app.shutdown()
        }
    }

    /** Three session cards and the name/save row sit inside the page at the normal window and at 900×580. */
    @Test
    fun accountCardsFitInsideThePage(@TempDir dir: Path) {
        val registry = MemoryRegistry()
        val desk = StalzoneDesk(registry, dir.resolve("sessions.json"), dir.resolve("jvm"), dir.resolve("options.json"))
        listOf("btw rusty" to "RustyQQ", "Zlyka" to "zlyk_zlykov", "Хуська" to "huska").forEach { (name, login) ->
            registry.writeValues(ExboSessions.KEY_PATH, mapOf("EXBO_LOGIN" to login))
            desk.captureCurrent(name)
        }
        val store = InMemorySettingsStore(LauncherSettings())
        val base = AppContext.mock(language = "ru", speed = 50.0, authDelayMs = 0)
        val app = AppContext(base.services.copy(settings = store, system = MockSystemInfo(16 * gb)), desk = desk)
        val nav = mainOnScreen(app, "ru")
        try {
            listOf(Theme.WINDOW_WIDTH to Theme.WINDOW_HEIGHT, Theme.WINDOW_MIN_WIDTH to Theme.WINDOW_MIN_HEIGHT).forEach { (w, h) ->
                resize(nav, w, h)
                FxTestSupport.onFx {
                    val main = nav.mainController!!
                    val c = main.settingsController
                    main.setSettingsVisible(true)
                    c.showClientSection("accounts")
                    relayout(nav)
                    val where = "${w.toInt()}x${h.toInt()}"
                    val window = BoundingBox(0.0, 0.0, nav.mainStage.scene.width, nav.mainStage.scene.height)
                    val cards = c.sessionCards.children.filter { "session-card" in it.styleClass }
                    assertEquals(3, cards.size, "$where: expected three session cards")
                    val viewport = c.sessionScroll.lookup(".viewport") as javafx.scene.Node
                    val view = viewport.localToScene(viewport.boundsInLocal)
                    val row = c.accountsRow.localToScene(c.accountsRow.boundsInLocal)
                    assertTrue(contains(window, row), "$where: name row outside the window")
                    assertTrue(contains(window, c.captureSession.sceneBounds()), "$where: save button outside")
                    assertTrue(c.captureSession.width >= c.captureSession.prefWidth(-1.0) - 0.5, "$where: save label truncated")
                    cards.forEach { cardNode ->
                        val card = cardNode as Parent
                        val b = card.localToScene(card.boundsInLocal)
                        assertTrue(contains(window, b), "$where: session card outside the window $b")
                        assertTrue(contains(view, b), "$where: session card clipped by the list $b vs $view")
                        assertTrue(b.maxY <= row.minY + 0.5, "$where: session card overlaps the name row")
                        val actions = card.lookup(".session-actions") as javafx.scene.Node
                        val ab = actions.localToScene(actions.boundsInLocal)
                        assertTrue(ab.maxX <= b.maxX + 0.5, "$where: actions stick out of the card")
                        assertTrue(contains(window, ab), "$where: actions outside the window")
                        card.lookupAll(".settings-mini").filterIsInstance<Labeled>().forEach { button ->
                            assertTrue(button.width >= button.prefWidth(-1.0) - 0.5, "$where: '${button.text}' truncated")
                        }
                    }
                }
            }
        } finally {
            FxTestSupport.onFx { nav.close() }
            app.shutdown()
        }
    }
}

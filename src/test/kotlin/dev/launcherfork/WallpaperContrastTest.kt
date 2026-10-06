package dev.launcherfork

import dev.launcherfork.ContrastKit.Rgba
import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.theme.Theme
import dev.launcherfork.wallpaper.Wallpapers
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Labeled
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextInputControl
import javafx.scene.paint.Color
import javafx.scene.text.Text
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Text over the wallpaper, measured on the real main window for EACH bundled picture and EACH theme (exbo, shadcn, zone,
 * zone-light), home screen at 900x580 / 1024x640 / 1366x800 and the settings page: JavaFX itself composites the
 * picture, the theme's scrim layers and the panels (WallpaperLayer + layout.css + theme tokens). Every visible text
 * node is hidden (transparent fill, same layout), the window is snapshotted, and the text's own colour is composed
 * over the pixels behind it: sidebar games list, nav links, banner title / lead / body, status bar, settings.
 *
 * Worst case, not average: light text (dark themes) is checked against the 95th-percentile BRIGHTEST pixel behind it,
 * dark text (white scheme) against the 5th-percentile DARKEST one; WCAG AA: >= 4.5:1 for body text, >= 3:1 for large
 * text (>= 24 px, or >= 18.66 px bold: the Bebas banner title, ИГРАТЬ). The single most extreme pixel is reported too
 * (build/wallpaper-contrast.txt) and must stay >= 3:1 for every text. Same helpers as [SettingsContrastTest]
 * ([ContrastKit]); no exception list: the scrims are tuned until every case passes.
 */
class WallpaperContrastTest {
    private data class Screen(val name: String, val w: Double, val h: Double, val settings: Boolean)

    private val screens = listOf(
        Screen("home 1024x640", Theme.WINDOW_WIDTH, Theme.WINDOW_HEIGHT, false),
        Screen("home 900x580", Theme.WINDOW_MIN_WIDTH, Theme.WINDOW_MIN_HEIGHT, false),
        Screen("home 1366x800", 1366.0, 800.0, false),
        Screen("settings 1024x640", Theme.WINDOW_WIDTH, Theme.WINDOW_HEIGHT, true),
    )

    /** One measured text node: worst-case ratio (percentile) and the ratio on the single most extreme pixel. */
    data class Sample(val region: String, val text: String, val p95: Double, val extreme: Double, val min: Double)

    @TestFactory
    fun textOverEveryWallpaperIsReadableInEveryTheme(): List<DynamicTest> =
        UiTestKit.THEMES.flatMap { theme -> Wallpapers.all.flatMap { wp -> screens.map { screen ->
            DynamicTest.dynamicTest("$theme: wallpaper %02d, %s".format(wp.number, screen.name)) {
                FxTestSupport.onFx {
                    UiTestKit.applyTheme(nav.mainStage.scene, theme, "ru")
                    nav.mainController!!.setSettingsVisible(screen.settings)
                    nav.wallpaperLayer!!.show(wp, background = false)
                }
                UiTestKit.resize(nav, screen.w, screen.h)
                val samples = FxTestSupport.onFx {
                    val scene = nav.mainStage.scene
                    assertTrue(scene.root.styleClass.contains("wallpaper-on"), "the wallpaper is not on")
                    assertEquals(wp.number, nav.wallpaperLayer!!.wallpaper!!.number)
                    measure(scene).also {
                        // the main window over every picture (build/ui-preview/wallpaper-<theme>-<nn>.png), for review
                        if (screen.name == "home 1024x640") UiTestKit.preview(scene, "wallpaper-$theme-%02d".format(wp.number))
                    }
                }
                val regions = samples.map { it.region }.toSet()
                val expected = if (screen.settings) setOf("sidebar", "nav", "settings") else setOf("sidebar", "nav", "banner", "status")
                assertTrue(regions.containsAll(expected), "regions found: $regions, expected $expected")
                samples.groupBy { it.region }.forEach { (region, list) ->
                    val worst = list.minBy { it.p95 / it.min }
                    report.merge(Triple(theme, wp.number, region), worst) { a, b -> if (b.p95 / b.min < a.p95 / a.min) b else a }
                }
                val failures = samples.filter { it.p95 < it.min || it.extreme < 3.0 }
                assertTrue(failures.isEmpty(), "$theme, wallpaper ${wp.number}, ${screen.name}: " + failures.joinToString("; ") {
                    "${it.region} «${it.text.take(30)}» p95 ${"%.2f".format(it.p95)}:1 (needs ${it.min}), extreme pixel ${"%.2f".format(it.extreme)}:1"
                })
            }
        } } }

    /** Every visible text of the main window over the pixels behind it (see the class comment). */
    private fun measure(scene: Scene): List<Sample> {
        data class Probe(val text: Text, val owner: Node, val region: String, val fill: Rgba, val x0: Int, val y0: Int, val x1: Int, val y1: Int, val min: Double)
        val probes = mutableListOf<Probe>()
        fun visit(node: Node) {
            if (!node.isVisible || node.opacity == 0.0) return
            if (node is Text && !node.text.isNullOrBlank()) {
                val fill = node.fill as? Color
                if (fill != null && fill.opacity > 0) {
                    var b = node.localToScene(node.boundsInLocal)
                    var x0 = max(0.0, b.minX); var y0 = max(0.0, b.minY); var x1 = min(scene.width, b.maxX); var y1 = min(scene.height, b.maxY)
                    // clipped by a scroll pane (banner, settings): only the visible part
                    generateSequence(node.parent) { it.parent }.filterIsInstance<ScrollPane>().forEach { sp ->
                        b = sp.localToScene(sp.layoutBounds)
                        x0 = max(x0, b.minX); y0 = max(y0, b.minY); x1 = min(x1, b.maxX); y1 = min(y1, b.maxY)
                    }
                    if (x1 - x0 >= 2 && y1 - y0 >= 2) {
                        // the node whose CSS colours the text: a label / button, a text field (its skin's Text), else the Text itself
                        val owner: Node = generateSequence(node.parent) { it.parent }.take(4)
                            .firstOrNull { it is TextInputControl || (it is Labeled && node.javaClass.simpleName == "LabeledText") } ?: node
                        val large = node.font.size >= 24.0 || (node.font.size >= 18.66 && node.font.style.contains("Bold", ignoreCase = true))
                        probes += Probe(node, owner, regionOf(node), Rgba(fill.red, fill.green, fill.blue, fill.opacity),
                            floor(x0).toInt(), floor(y0).toInt(), ceil(x1).toInt(), ceil(y1).toInt(), if (large) 3.0 else 4.5)
                    }
                }
            }
            if (node is Parent) node.childrenUnmodifiable.forEach(::visit)
        }
        visit(scene.root)

        // hide the texts (transparent fill, layout unchanged) and take the picture behind them
        val styles = probes.map { it.owner }.distinct().associateWith { it.style }
        styles.keys.forEach { it.style = (it.style ?: "") + "; -fx-text-fill: transparent; -fx-fill: transparent;" }
        scene.root.applyCss(); scene.root.layout()
        val shot = scene.snapshot(null)
        styles.forEach { (n, s) -> n.style = s }
        scene.root.applyCss(); scene.root.layout()
        val px = shot.pixelReader

        return probes.map { p ->
            val bg = ArrayList<Rgba>((p.x1 - p.x0) * (p.y1 - p.y0))
            for (y in p.y0 until min(p.y1, shot.height.toInt())) for (x in p.x0 until min(p.x1, shot.width.toInt())) bg += Rgba.ofArgb(px.getArgb(x, y))
            val sorted = bg.sortedBy { it.luminance() }
            val median = sorted[sorted.size / 2]
            val lightText = p.fill.over(median).luminance() > median.luminance()
            // light text: the brightest pixels are the worst; dark text: the darkest ones
            val worst = if (lightText) sorted[(ceil(sorted.size * 0.95).toInt() - 1).coerceIn(0, sorted.lastIndex)] else sorted[(sorted.size * 0.05).toInt()]
            val extreme = if (lightText) sorted.last() else sorted.first()
            Sample(p.region, p.text.text, ContrastKit.ratio(p.fill.over(worst), worst), ContrastKit.ratio(p.fill.over(extreme), extreme), p.min)
        }
    }

    private fun regionOf(node: Node): String {
        for (n in generateSequence(node) { it.parent }) when {
            "sidebar" in n.styleClass -> return "sidebar"
            n.id == "topBar" -> return "nav"
            "game-banner" in n.styleClass -> return "banner"
            "status-bar" in n.styleClass -> return "status"
            "settings-root" in n.styleClass -> return "settings"
        }
        return "other"
    }

    companion object {
        private lateinit var nav: StageNavigator
        /** (theme, wallpaper, region) -> the text with the lowest p95 ratio (relative to its minimum) over every screen. */
        private val report = sortedMapOf<Triple<String, Int, String>, Sample>(compareBy({ it.first }, { it.second }, { it.third }))

        @JvmStatic
        @BeforeAll
        fun openMainWindow() {
            Wallpapers.resetForTests()
            Wallpapers.choose(null, Random(0), forced = 1)
            nav = UiTestKit.mainOnScreen(AppContext.mock(authDelayMs = 0))
            check(FxTestSupport.waitFx { nav.wallpaperLayer?.isLoaded == true }) { "wallpaper did not load" }
        }

        @JvmStatic
        @AfterAll
        fun closeAndReport() {
            FxTestSupport.onFx { nav.close() }
            Wallpapers.resetForTests()
            val lines = report.map { (k, v) ->
                "%-10s %02d %-8s p95 %5.2f:1 (needs %.1f)  extreme pixel %5.2f:1  «%s»".format(k.first, k.second, k.third, v.p95, v.min, v.extreme, v.text.take(24).replace('\n', ' '))
            }
            lines.forEach(::println)
            File("build").mkdirs()
            File("build/wallpaper-contrast.txt").writeText(lines.joinToString("\n") + "\n")
        }
    }
}

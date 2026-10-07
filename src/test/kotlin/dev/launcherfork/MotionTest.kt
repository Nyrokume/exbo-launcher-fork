package dev.launcherfork

import dev.launcherfork.UiTestKit.mainOnScreen
import dev.launcherfork.UiTestKit.relayout
import dev.launcherfork.UiTestKit.sceneBounds
import dev.launcherfork.app.AppContext
import dev.launcherfork.app.ScreenshotTour
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.data.local.AppearanceFileStore
import dev.launcherfork.data.local.SettingsFile
import dev.launcherfork.data.mock.InMemorySettingsStore
import dev.launcherfork.data.mock.MockData
import dev.launcherfork.data.mock.MockServerRepository
import dev.launcherfork.domain.GameDescription
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.ui.motion.Motion
import javafx.animation.Interpolator
import javafx.scene.control.CheckBox
import javafx.scene.control.ProgressBar
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.text.Text
import javafx.scene.text.TextBoundsType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * ui/motion/Motion: tokens, the on / off switch (setting + -Dlauncher.animations), the disabled mode (every helper ends
 * at once: tests and the screenshot tour), interrupted transitions (one animation per node, always the right end
 * state, nothing left half-faded or offset), progress guards, and the animated main screen keeping its layout (banner
 * aligned with the selected row) at the end of every animation. Tests run with -Dlauncher.animations=off
 * (build.gradle.kts); the "animated" tests switch motion on for themselves and back off afterwards.
 */
class MotionTest {
    @AfterEach
    fun motionOffAgain() {
        Motion.override = false
        Motion.setting = true
    }

    /** Runs [block] with motion on (the property override lifted), back off afterwards. */
    private fun <T> animated(block: () -> T): T {
        Motion.override = null
        Motion.setting = true
        try { return block() } finally { Motion.override = false }
    }

    private fun settle(vararg nodes: javafx.scene.Node) =
        assertTrue(FxTestSupport.waitFx(3_000) { nodes.none { Motion.isAnimating(it) } }, "animations did not end")

    // ---- switch ----

    @Test
    fun parsesOnOffValues() {
        listOf("on", "ON", " true ", "yes", "1").forEach { assertEquals(true, Motion.parse(it), it) }
        listOf("off", "Off", "false", "no", "0").forEach { assertEquals(false, Motion.parse(it), it) }
        listOf(null, "", "maybe", "2").forEach { assertNull(Motion.parse(it), "$it") }
        assertTrue(Motion.parseSetting(null), "missing setting = on")
        assertTrue(Motion.parseSetting("garbage"), "unknown setting = on")
        assertFalse(Motion.parseSetting("off"))
        assertEquals("on", Motion.settingValue(true)); assertEquals("off", Motion.settingValue(false))
        assertEquals("animations", Motion.SETTINGS_KEY)
        assertTrue(LauncherSettings().animations, "animations are on by default")
    }

    @Test
    fun systemPropertyWinsOverTheSetting() {
        assertTrue(Motion.resolve(null, true)); assertFalse(Motion.resolve(null, false))
        assertFalse(Motion.resolve("off", true)); assertTrue(Motion.resolve("on", false))
        assertTrue(Motion.resolve("nonsense", true), "an unknown property value leaves the setting in charge")
        // the test JVM runs with -Dlauncher.animations=off: the override holds whatever the setting says
        assertEquals("off", System.getProperty(Motion.PROPERTY))
        assertEquals(false, Motion.override)
        Motion.setting = true
        assertFalse(Motion.enabled)
        assertFalse(Motion.enabledProperty.get())
        Motion.override = null
        assertTrue(Motion.enabled); assertTrue(Motion.enabledProperty.get())
        Motion.setting = false
        assertFalse(Motion.enabled)
    }

    @Test
    fun durationTokensAreShortAndEaseOut() {
        assertEquals(120.0, Motion.FAST.toMillis()); assertEquals(200.0, Motion.NORMAL.toMillis()); assertEquals(280.0, Motion.SLOW.toMillis())
        listOf(Motion.FAST, Motion.NORMAL, Motion.SLOW).forEach { assertTrue(it.toMillis() in 120.0..280.0, "$it") }
        assertTrue(Motion.FAST.lessThan(Motion.NORMAL) && Motion.NORMAL.lessThan(Motion.SLOW))
        assertTrue(Motion.MAX_SLIDE <= 12.0 && Motion.SLIDE <= Motion.MAX_SLIDE && Motion.MENU_SLIDE <= Motion.MAX_SLIDE)
        assertEquals(0.97, Motion.PRESS_SCALE)
        assertTrue(Motion.DRIFT_SCALE <= 1.04 && Motion.DRIFT_PERIOD.toSeconds() >= 30, "the drift is very slow and small")
        // ease-out, never past the target (no bounce): monotonic, inside 0..1, more than half way at mid-time
        val samples = (0..100).map { Motion.EASE.interpolate(0.0, 1.0, it / 100.0) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a - 1e-9 }, "not monotonic")
        assertTrue(samples.all { it in -1e-9..1.0 + 1e-9 }, "overshoots")
        assertTrue(samples[50] > 0.5, "not ease-out")
        assertTrue(Motion.EASE != Interpolator.LINEAR)
    }

    // ---- disabled: end state at once ----

    @Test
    fun disabledHelpersApplyTheEndStateInstantly() = FxTestSupport.onFx {
        assertFalse(Motion.enabled)
        val a = Region().apply { isVisible = false; opacity = 0.3 }
        Motion.fadeIn(a); assertTrue(a.isVisible); assertEquals(1.0, a.opacity); assertEquals(0.0, Motion.slideOffset(a)); assertFalse(Motion.isAnimating(a))
        Motion.fadeOut(a); assertFalse(a.isVisible); assertEquals(1.0, a.opacity); assertFalse(Motion.isAnimating(a))
        Motion.fadeSlideIn(a, 10.0); assertTrue(a.isVisible); assertEquals(0.0, Motion.slideOffset(a)); assertEquals(1.0, a.opacity)
        Motion.popOut(a); assertFalse(a.isVisible)
        Motion.popIn(a, dy = -6.0, scale = 0.9); assertTrue(a.isVisible); assertEquals(1.0, a.scaleX); assertEquals(0.0, Motion.slideOffset(a))
        val b = Region().apply { isVisible = false }
        Motion.crossfade(a, b); assertTrue(b.isVisible); assertFalse(a.isVisible); assertEquals(1.0, b.opacity)
        Motion.markerIn(a); assertEquals(1.0, a.scaleY); assertEquals(1.0, a.opacity)
        assertNull(Motion.drift(a), "no drift while disabled"); assertEquals(1.0, a.scaleX)
        var ran = false
        Motion.snapshotCrossfade(StackPane()) { ran = true }
        assertTrue(ran)
        val bar = ProgressBar(0.0)
        StackPane(bar).let { javafx.scene.Scene(it) }
        Motion.progress(bar, 0.5); assertEquals(0.5, bar.progress); assertFalse(Motion.isAnimating(bar, "progress"))
    }

    @Test
    fun screenshotTourForcesMotionOff(@TempDir dir: Path) {
        Motion.override = null
        Motion.setting = true
        assertTrue(Motion.enabled)
        val nav = FxTestSupport.onFx {
            StageNavigator(javafx.stage.Stage(), AppContext.mock(speed = 50.0, authDelayMs = 0), "ru").also { it.exit = {} }
        }
        // install only: the tour's own steps are not started (they end with Platform.exit)
        FxTestSupport.onFx { ScreenshotTour(nav, dir.toFile()).install(); nav.onScreenChanged = {} }
        assertFalse(Motion.enabled, "the tour shoots settled frames only")
        assertEquals(false, Motion.override)
        // with motion off a shown menu is fully there at once: the same picture every run
        FxTestSupport.onFx { nav.showMain() }
        FxTestSupport.onFx {
            val profile = nav.mainController!!.profileBarController
            profile.showMenu()
            assertTrue(profile.profileMenu.isVisible); assertEquals(1.0, profile.profileMenu.opacity)
            assertEquals(0.0, Motion.slideOffset(profile.profileMenu)); assertFalse(Motion.isAnimating(profile.profileMenu))
            nav.close()
        }
    }

    // ---- progress ----

    @Test
    fun progressValuesAreClamped() {
        assertEquals(0.0, Motion.safeFraction(Double.NaN))
        assertEquals(0.0, Motion.safeFraction(Double.NEGATIVE_INFINITY))
        assertEquals(1.0, Motion.safeFraction(Double.POSITIVE_INFINITY))
        assertEquals(0.0, Motion.safeFraction(-0.3))
        assertEquals(0.0, Motion.safeFraction(-1.0))
        assertEquals(1.0, Motion.safeFraction(1.7))
        assertEquals(0.42, Motion.safeFraction(0.42))
        FxTestSupport.onFx {
            val bar = ProgressBar(0.3)
            listOf(Double.NaN to 0.0, Double.POSITIVE_INFINITY to 1.0, -5.0 to 0.0, 3.0 to 1.0).forEach { (v, want) ->
                Motion.progress(bar, v); assertEquals(want, bar.progress, "progress($v)")
            }
        }
    }

    @Test
    fun progressGlidesButSnapsAtCompletionAndReset() = animated {
        val bar = FxTestSupport.onFx {
            ProgressBar(0.0).also { val root = StackPane(it); javafx.scene.Scene(root, 200.0, 50.0) }
        }
        FxTestSupport.onFx {
            Motion.progress(bar, 0.5)
            assertTrue(Motion.isAnimating(bar, "progress"), "a step in the middle glides")
            assertEquals(0.5, Motion.progressTarget(bar))
            assertTrue(bar.progress < 0.5)
            Motion.progress(bar, 1.0)
            assertEquals(1.0, bar.progress, "completion is shown at once"); assertFalse(Motion.isAnimating(bar, "progress"))
            Motion.progress(bar, 0.6)
            assertEquals(0.6, bar.progress, "going back is at once")
            Motion.progress(bar, 0.0)
            assertEquals(0.0, bar.progress, "reset is at once")
            Motion.progress(bar, Double.NaN)
            assertEquals(0.0, bar.progress)
        }
        FxTestSupport.onFx { Motion.progress(bar, 0.3) }
        assertTrue(FxTestSupport.waitFx(2_000) { !Motion.isAnimating(bar, "progress") })
        FxTestSupport.onFx { assertEquals(0.3, bar.progress, 1e-9, "the glide ends on the value") }
    }

    // ---- interrupted transitions ----

    @Test
    fun rapidShowHideAlwaysEndsInTheLastState() = animated {
        val node = FxTestSupport.onFx { Region().apply { isVisible = false }.also { javafx.scene.Scene(StackPane(it)) } }
        repeat(10) { i ->
            FxTestSupport.onFx { if (i % 2 == 0) Motion.popIn(node, dy = -6.0, scale = 0.98) else Motion.popOut(node) }
            Thread.sleep(15)
        }
        // 10 calls, the last one (i = 9) hides
        settle(node)
        FxTestSupport.onFx { assertFalse(node.isVisible); assertEquals(1.0, node.opacity); assertEquals(0.0, Motion.slideOffset(node)); assertEquals(1.0, node.scaleX) }
        repeat(11) { i ->
            FxTestSupport.onFx { if (i % 2 == 0) Motion.fadeSlideIn(node, 12.0) else Motion.fadeOut(node, dy = 4.0) }
            Thread.sleep(10)
        }
        settle(node)
        FxTestSupport.onFx {
            assertTrue(node.isVisible, "the last call shows"); assertEquals(1.0, node.opacity)
            assertEquals(0.0, Motion.slideOffset(node), "never left offset"); assertEquals(1.0, node.scaleY)
            assertTrue(node.properties.keys.count { it.toString().startsWith("dev.launcherfork.motion.show") } == 0, "nothing left running")
        }
    }

    @Test
    fun newTransitionReplacesTheRunningOne() = animated {
        FxTestSupport.onFx {
            val node = Region()
            javafx.scene.Scene(StackPane(node))
            Motion.fadeOut(node)
            val first = Motion.running(node)!!
            Motion.fadeIn(node)
            val second = Motion.running(node)!!
            assertTrue(first !== second)
            assertEquals(javafx.animation.Animation.Status.STOPPED, first.status, "the first one stopped, not stacked")
            assertFalse(node.isMouseTransparent, "clicks still reach a node while it animates")
            assertFalse(node.isDisable)
            Motion.finish(node)
            assertTrue(node.isVisible); assertEquals(1.0, node.opacity); assertFalse(Motion.isAnimating(node))
        }
    }

    @Test
    fun switchThumbEndsWhereCssPutsIt() = animated {
        val box = FxTestSupport.onFx {
            CheckBox().apply { styleClass += "switch" }.also { cb ->
                val scene = javafx.scene.Scene(StackPane(cb), 200.0, 60.0)
                scene.stylesheets.setAll(dev.launcherfork.theme.Theme.stylesheets("zone", dev.launcherfork.domain.Appearance.DARK, "ru"))
                scene.stylesheets += dev.launcherfork.theme.Theme.stylesheet("layout.css")
                Motion.smoothSwitch(cb)
                cb.applyCss(); scene.root.layout()
            }
        }
        repeat(5) { FxTestSupport.onFx { box.isSelected = !box.isSelected }; Thread.sleep(20) }
        val mark = FxTestSupport.onFx { box.lookup(".mark")!! }
        assertTrue(FxTestSupport.waitFx(2_000) { !Motion.isAnimating(mark, "thumb") })
        FxTestSupport.onFx { assertTrue(box.isSelected); assertEquals(0.0, mark.translateX, "thumb settled on its CSS place") }
    }

    // ---- settings ----

    @Test
    fun animationsSettingSurvivesARestart(@TempDir dir: Path) {
        val file = dir.resolve("settings.properties")
        val store = AppearanceFileStore(InMemorySettingsStore(), file)
        assertTrue(store.load().animations)
        assertFalse(Files.exists(file), "nothing written until something changes")
        store.save(store.load().copy(animations = false))
        assertEquals("off", SettingsFile.read(file, "animations"))
        assertFalse(AppearanceFileStore(InMemorySettingsStore(), file).load().animations, "read back after a restart")
        store.save(store.load().copy(animations = true))
        assertEquals("on", SettingsFile.read(file, "animations"))
        SettingsFile.write(file, "animations", "garbage")
        assertTrue(AppearanceFileStore(InMemorySettingsStore(), file).load().animations, "unknown value = on")
        assertEquals(null, AppearanceFileStore.readAnimations(file))
    }

    @Test
    fun settingsToggleSwitchesMotion() {
        val app = AppContext.mock(speed = 50.0, authDelayMs = 0)
        val nav = mainOnScreen(app)
        FxTestSupport.onFx {
            val s = nav.mainController!!.settingsController
            // forced off by -Dlauncher.animations: the switch shows it and cannot be changed
            assertFalse(s.animationsToggle.isSelected)
            assertTrue(s.animationsToggle.isDisable)
            nav.close()
        }
        animated {
            val nav2 = mainOnScreen(app)
            FxTestSupport.onFx {
                val s = nav2.mainController!!.settingsController
                assertTrue(s.animationsToggle.isSelected); assertFalse(s.animationsToggle.isDisable)
                s.animationsToggle.isSelected = false
                assertFalse(app.services.settings.load().animations, "saved")
                assertFalse(Motion.setting); assertFalse(Motion.enabled)
                s.animationsToggle.isSelected = true
                assertTrue(app.services.settings.load().animations); assertTrue(Motion.enabled)
                assertTrue(s.animationsRow.parent === s.behaviourCard, "«Анимации» sits in «Поведение»")
                nav2.close()
            }
        }
    }

    // ---- the animated main screen ----

    private val gameA = MockData.servers.single()
    private val gameB = gameA.copy(
        id = "second", caption = "Second",
        description = GameDescription.Text("SECOND", "Lead", "Sub", "Body", "Closing"),
    )

    private fun twoGames(): AppContext {
        val base = AppContext.mock(speed = 50.0, authDelayMs = 0)
        return AppContext(base.services.copy(servers = MockServerRepository(listOf(gameA, gameB))))
    }

    private fun inkTop(label: javafx.scene.control.Label): Double {
        val line = label.lookup(".text") as Text
        val logical = Text(label.text).apply { font = line.font }
        val visual = Text(label.text).apply { font = line.font; boundsType = TextBoundsType.VISUAL }
        return line.sceneBounds().minY + (visual.layoutBounds.minY - logical.layoutBounds.minY)
    }

    @Test
    fun rapidGameSwitchingEndsAlignedOnTheLastGame() = animated {
        val nav = mainOnScreen(twoGames())
        val main = FxTestSupport.onFx { nav.mainController!! }
        val list = main.serversListController
        repeat(10) { i ->
            FxTestSupport.onFx { list.select(if (i % 2 == 0) gameB else gameA) }
            Thread.sleep(25)
        }
        FxTestSupport.onFx { list.select(gameB) }
        val content = FxTestSupport.onFx { main.gameBannerController.content }
        val markers = FxTestSupport.onFx { list.entryControllers.map { it.cursor } }
        assertTrue(FxTestSupport.waitFx(3_000) { !Motion.isAnimating(content) && markers.none { Motion.isAnimating(it, "marker") } })
        FxTestSupport.onFx { relayout(nav) }
        FxTestSupport.onFx {
            val b = main.gameBannerController
            assertEquals("SECOND", b.title.text)
            assertEquals(gameB, main.selected)
            assertEquals(1.0, content.opacity); assertEquals(0.0, Motion.slideOffset(content), "banner text never left offset")
            val selected = list.entryControllers.filter { "selected" in it.root.styleClass }
            assertEquals(listOf("second"), selected.map { it.server.id })
            assertEquals(1.0, selected.single().cursor.scaleY); assertEquals(1.0, selected.single().cursor.opacity)
            // PR #15 alignment holds at the END of the animation: title letters on the selected row's top
            assertEquals(selected.single().root.sceneBounds().minY, inkTop(b.title), 2.0, "title off the selected row")
            nav.close()
        }
    }

    @Test
    fun rapidMenuAndScreenTogglingEndsInTheLastState() = animated {
        val nav = mainOnScreen(twoGames())
        val main = FxTestSupport.onFx { nav.mainController!! }
        val profile = main.profileBarController
        val header = main.headerController
        val actions = main.defaultStatusPaneController.gameActionsController
        repeat(10) { i ->
            FxTestSupport.onFx {
                if (i % 2 == 0) { profile.showMenu(); header.showCommunityMenu(); actions.show() }
                else { profile.hideMenu(); header.hideCommunityMenu(); actions.hide() }
            }
            Thread.sleep(15)
        }
        FxTestSupport.onFx { profile.showMenu() } // the last word: the profile menu open (closes the others)
        val cards = listOf(profile.profileMenu, header.communityMenu, actions.gameActionsMenu)
        assertTrue(FxTestSupport.waitFx(3_000) { cards.none { Motion.isAnimating(it) } })
        FxTestSupport.onFx {
            assertTrue(profile.isMenuShown && profile.profileMenu.isVisible)
            assertEquals(1.0, profile.profileMenu.opacity); assertEquals(0.0, Motion.slideOffset(profile.profileMenu))
            assertEquals(6.0, profile.profileMenu.translateY, "the stylesheet's 6 px gap under the button is untouched")
            assertFalse(header.isCommunityMenuShown || header.communityMenu.isVisible, "communities closed")
            assertFalse(actions.isShown || actions.gameActionsMenu.isVisible, "actions closed")
            cards.forEach { assertEquals(1.0, it.opacity); assertEquals(0.0, Motion.slideOffset(it)); assertEquals(1.0, it.scaleX) }
        }
        repeat(11) { i -> FxTestSupport.onFx { main.setSettingsVisible(i % 2 == 0) }; Thread.sleep(20) }
        assertTrue(FxTestSupport.waitFx(3_000) { !Motion.isAnimating(main.settingsPane) && !Motion.isAnimating(main.homePane) })
        FxTestSupport.onFx {
            assertTrue(main.isSettingsShown)
            assertTrue(main.settingsPane.isVisible); assertFalse(main.homePane.isVisible)
            assertEquals(1.0, main.settingsPane.opacity); assertEquals(1.0, main.homePane.opacity)
        }
        // a modal opened and closed quickly ends closed, the screen unblocked
        repeat(6) { i -> FxTestSupport.onFx { if (i % 2 == 0) main.logoutPaneController.show() else main.logoutPaneController.hide() }; Thread.sleep(15) }
        val layer = main.logoutPaneController.layer
        assertTrue(FxTestSupport.waitFx(3_000) { !Motion.isAnimating(layer) })
        FxTestSupport.onFx {
            assertFalse(main.logoutPaneController.isShowing); assertFalse(layer.isVisible); assertFalse(main.isModalShown)
            assertFalse(main.layout.isDisable, "screen unblocked after the modal faded out")
            nav.close()
        }
    }

    @Test
    fun wallpaperDriftOnlyWhenAskedAndAnimated() {
        val layer = FxTestSupport.onFx { dev.launcherfork.ui.components.WallpaperLayer() }
        FxTestSupport.onFx {
            layer.show(dev.launcherfork.wallpaper.Wallpapers.all.first(), background = false)
            assertTrue(layer.isLoaded)
            layer.drift = true
            assertNull(layer.driftAnimation, "no drift with motion off")
            assertEquals(1.0, layer.imageView.opacity, "the picture is there at once with motion off")
        }
        animated {
            FxTestSupport.onFx {
                assertTrue(layer.driftAnimation != null, "drift starts when motion comes on")
                layer.drift = false
                assertNull(layer.driftAnimation); assertEquals(1.0, layer.imageView.scaleX)
                layer.drift = true
            }
        }
        FxTestSupport.onFx { assertNull(layer.driftAnimation, "drift stops when motion goes off"); assertEquals(1.0, layer.imageView.scaleX) }
    }
}

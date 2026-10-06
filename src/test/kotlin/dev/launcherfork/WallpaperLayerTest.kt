package dev.launcherfork

import dev.launcherfork.UiTestKit.sceneBounds
import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.components.WallpaperLayer
import dev.launcherfork.wallpaper.CoverFit
import dev.launcherfork.wallpaper.Wallpaper
import dev.launcherfork.wallpaper.WallpaperMemory
import dev.launcherfork.wallpaper.Wallpapers
import javafx.scene.layout.StackPane
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * The wallpaper behind the MAIN window (WallpaperLayer in window.fxml's root): loaded in the background, cover-fit on
 * every window size (min, default, maximized), the scrim on top, and the plain theme background when the picture
 * cannot be loaded. The picture is the launch's [Wallpapers.current]: the same one after login and after a re-login.
 */
class WallpaperLayerTest {
    @AfterEach
    fun reset() = Wallpapers.resetForTests()

    private fun navigator(): StageNavigator = UiTestKit.mainOnScreen(AppContext.mock(authDelayMs = 0))

    private fun layerOf(nav: StageNavigator): WallpaperLayer = FxTestSupport.onFx { requireNotNull(nav.wallpaperLayer) { "the main window has no wallpaper layer" } }

    private fun assertCover(nav: StageNavigator, what: String) = FxTestSupport.onFx {
        UiTestKit.relayout(nav)
        val layer = nav.wallpaperLayer!!
        val scene = nav.mainStage.scene
        val view = layer.imageView
        // the layer and the picture fill the whole window, behind everything else of the root
        assertEquals(0, (scene.root as StackPane).children.indexOf(layer), "$what: layer is not the bottom child")
        val b = layer.sceneBounds()
        assertEquals(0.0, b.minX, 0.5); assertEquals(0.0, b.minY, 0.5)
        assertEquals(scene.width, b.width, 0.5, "$what: layer width"); assertEquals(scene.height, b.height, 0.5, "$what: layer height")
        assertEquals(scene.width, view.fitWidth, 0.5, "$what: picture width")
        assertEquals(scene.height, view.fitHeight, 0.5, "$what: picture height")
        // aspect ratio preserved: the viewport (crop of the picture) has the window's aspect and lies inside the picture
        val vp = view.viewport!!
        assertEquals(scene.width / scene.height, vp.width / vp.height, 1e-3, "$what: stretched picture")
        val img = view.image
        assertTrue(vp.minX >= -1e-6 && vp.minY >= -1e-6 && vp.maxX <= img.width + 1e-6 && vp.maxY <= img.height + 1e-6, "$what: $vp outside the picture")
        val wp = layer.wallpaper!!
        val expected = CoverFit.crop(img.width, img.height, scene.width, scene.height, wp.focusX, wp.focusY)
        assertEquals(expected.x, vp.minX, 1e-6); assertEquals(expected.width, vp.width, 1e-6)
        // the scrim covers the picture
        assertEquals(scene.width, layer.scrim.width, 0.5); assertEquals(scene.height, layer.scrim.height, 0.5)
        assertTrue(layer.scrim.styleClass.contains("wallpaper-scrim"))
    }

    @Test
    fun mainWindowShowsTheLaunchWallpaperCoverFitOnEverySize() {
        Wallpapers.choose(null, Random(1), forced = 2) // the wide river panorama: the hardest crop
        val nav = navigator()
        val layer = layerOf(nav)
        assertTrue(FxTestSupport.waitFx { layer.isLoaded }, "wallpaper did not load")
        FxTestSupport.onFx {
            assertEquals(2, layer.wallpaper!!.number)
            assertTrue(nav.mainStage.scene.root.styleClass.contains("wallpaper-on"), "window root lacks .wallpaper-on")
            assertTrue(layer.isMouseTransparent, "the wallpaper must not take clicks")
        }
        for ((w, h) in listOf(Theme.WINDOW_MIN_WIDTH to Theme.WINDOW_MIN_HEIGHT, Theme.WINDOW_WIDTH to Theme.WINDOW_HEIGHT, 1366.0 to 800.0)) {
            UiTestKit.resize(nav, w, h)
            assertCover(nav, "${w}x$h")
        }
        // maximize (the screen size of the headless test platform) and back
        FxTestSupport.onFx { nav.toggleMaximize() }
        assertTrue(FxTestSupport.waitFx { nav.isMaximized })
        Thread.sleep(200)
        assertCover(nav, "maximized")
        FxTestSupport.onFx { nav.toggleMaximize() }
        Thread.sleep(200)
        assertCover(nav, "restored")
        FxTestSupport.onFx { nav.close() }
    }

    @Test
    fun theSamePictureAfterLoginAndAfterReLogin() {
        val memory = object : WallpaperMemory {
            val writes = mutableListOf<Int>()
            override fun last(): Int? = null
            override fun remember(number: Int) { writes += number }
        }
        val chosen = Wallpapers.choose(memory, Random(11), forced = null)!!
        val nav = navigator() // logged in: the main window
        val layer = layerOf(nav)
        FxTestSupport.onFx { assertSame(chosen, layer.wallpaper) }
        // logout -> auth window -> login again: the main window keeps the launch's picture, nothing is picked again
        FxTestSupport.onFx { nav.showAuth() }
        FxTestSupport.onFx { nav.showMain() }
        FxTestSupport.onFx {
            assertSame(layer, nav.wallpaperLayer, "the window frame (and its wallpaper) is not rebuilt by a re-login")
            assertSame(chosen, nav.wallpaperLayer!!.wallpaper)
        }
        // the login window (Front.end.master) will take its background from the same place
        val forLogin = FxTestSupport.onFx { WallpaperLayer.forCurrent(target = null, background = false) }
        FxTestSupport.onFx { assertSame(chosen, forLogin!!.wallpaper); assertTrue(forLogin.isLoaded) }
        assertSame(chosen, Wallpapers.choose(memory, Random(99), forced = null))
        assertEquals(listOf(chosen.number), memory.writes, "picked and saved once per launch")
        FxTestSupport.onFx { nav.close() }
    }

    @Test
    fun missingPictureFallsBackToThePlainThemeBackground() {
        val missing = Wallpaper(9, Wallpapers.ROOT + "09.jpg")
        FxTestSupport.onFx {
            val target = StackPane()
            val layer = WallpaperLayer()
            layer.attach(target)
            layer.show(missing, background = false)
            assertFalse(layer.isLoaded)
            assertNull(layer.imageView.image, "no picture")
            assertFalse(layer.scrim.isVisible, "no scrim without a picture")
            assertFalse(target.styleClass.contains("wallpaper-on"), "the plain theme background stays")
        }
        // a resource that exists but is not a picture: the same fallback
        val broken = Wallpaper(8, "/dev/launcherfork/ui/style/layout.css")
        FxTestSupport.onFx {
            val target = StackPane()
            val layer = WallpaperLayer().also { it.attach(target) }
            layer.show(broken, background = false)
            assertFalse(layer.isLoaded)
            assertFalse(target.styleClass.contains("wallpaper-on"))
        }
        // background loading reports the failure later: still the plain background
        val target = StackPane()
        val layer = FxTestSupport.onFx { WallpaperLayer().also { it.attach(target); it.show(broken, background = true) } }
        Thread.sleep(300)
        FxTestSupport.onFx { assertFalse(layer.isLoaded); assertFalse(target.styleClass.contains("wallpaper-on")) }
    }

    @Test
    fun withoutAChosenWallpaperTheMainWindowKeepsItsPlainBackground() {
        assertNull(Wallpapers.current)
        val nav = navigator()
        FxTestSupport.onFx {
            assertNull(nav.wallpaperLayer)
            assertFalse(nav.mainStage.scene.root.styleClass.contains("wallpaper-on"))
            assertNull(WallpaperLayer.forCurrent(target = null))
            nav.close()
        }
    }

    @Test
    fun loadingIsAsynchronousAndSwitchesTheScrimOnWhenDone() {
        val wp = Wallpapers.all.single { it.number == 3 }
        val target = StackPane()
        val layer = FxTestSupport.onFx {
            WallpaperLayer().also {
                it.attach(target)
                it.show(wp) // background = true: returns before the picture is decoded
            }
        }
        assertTrue(FxTestSupport.waitFx { layer.isLoaded })
        FxTestSupport.onFx {
            assertTrue(layer.scrim.isVisible)
            assertTrue(target.styleClass.contains("wallpaper-on"))
            assertEquals(1920.0, layer.imageView.image.width, 0.0, "the bundled 1920 px picture")
            assertFalse(target.styleClass.contains("wallpaper-bright"), "03 is a dark picture")
            // a bright picture (manifest tone) switches the stronger scrim on, a dark one off again
            layer.show(Wallpapers.all.single { it.number == 1 }, background = false)
            assertTrue(target.styleClass.contains("wallpaper-bright") && layer.scrim.styleClass.contains("wallpaper-bright"))
            layer.show(wp, background = false)
            assertFalse(target.styleClass.contains("wallpaper-bright") || layer.scrim.styleClass.contains("wallpaper-bright"))
        }
    }
}

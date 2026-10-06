package dev.launcherfork

import dev.launcherfork.wallpaper.CoverFit
import dev.launcherfork.wallpaper.Wallpapers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** Cover-fit of the wallpaper: fills the window, keeps the picture's aspect ratio, crops around the focus point. */
class CoverFitTest {
    private val eps = 1e-6
    private val sizes = listOf(900.0 to 580.0, 1024.0 to 640.0, 1366.0 to 800.0, 1920.0 to 1080.0, 1920.0 to 1200.0, 2560.0 to 1080.0, 600.0 to 1000.0)

    @Test
    fun cropKeepsTheWindowAspectAndStaysInsideThePicture() {
        for ((iw, ih) in listOf(1920.0 to 1080.0, 1920.0 to 804.0, 1600.0 to 900.0, 800.0 to 1200.0))
            for ((w, h) in sizes) for (fx in listOf(0.0, 0.3, 0.5, 1.0)) for (fy in listOf(0.0, 0.5, 1.0)) {
                val c = CoverFit.crop(iw, ih, w, h, fx, fy)
                assertEquals(w / h, c.width / c.height, 1e-9, "aspect for ${iw}x$ih in ${w}x$h")
                assertTrue(c.x >= -eps && c.y >= -eps && c.x + c.width <= iw + eps && c.y + c.height <= ih + eps, "$c outside ${iw}x$ih")
                // cover, not contain: one side of the crop is the whole picture side
                assertTrue(abs(c.width - iw) < eps || abs(c.height - ih) < eps, "$c is not a cover crop of ${iw}x$ih")
            }
    }

    @Test
    fun centredByDefault() {
        // 16:9 picture in a 16:10 window: full height, sides cropped equally
        val c = CoverFit.crop(1920.0, 1080.0, 1024.0, 640.0)
        assertEquals(1080.0, c.height, eps)
        assertEquals(1728.0, c.width, eps)
        assertEquals(96.0, c.x, eps)
        assertEquals(0.0, c.y, eps)
        // wider window than the picture: full width, top and bottom cropped equally
        val wide = CoverFit.crop(1920.0, 1080.0, 2560.0, 1080.0)
        assertEquals(1920.0, wide.width, eps)
        assertEquals(810.0, wide.height, eps)
        assertEquals(135.0, wide.y, eps)
    }

    @Test
    fun focusMovesTheCropButNeverPastTheEdge() {
        val left = CoverFit.crop(1920.0, 1080.0, 900.0, 580.0, focusX = 0.0)
        assertEquals(0.0, left.x, eps)
        val right = CoverFit.crop(1920.0, 1080.0, 900.0, 580.0, focusX = 1.0)
        assertEquals(1920.0, right.x + right.width, eps)
        val f = CoverFit.crop(1920.0, 1080.0, 900.0, 580.0, focusX = 0.45)
        assertEquals(0.45 * 1920, f.x + f.width / 2, eps)
    }

    @Test
    fun degenerateSizesShowTheWholePicture() {
        for ((w, h) in listOf(0.0 to 0.0, 0.0 to 600.0, -5.0 to 10.0)) {
            val c = CoverFit.crop(1920.0, 1080.0, w, h)
            assertEquals(CoverFit.Crop(0.0, 0.0, 1920.0, 1080.0), c)
        }
    }

    /**
     * The wide river panorama (02, 2.39:1) loses a third of its width in the 900x580 window: its focus point keeps the
     * sunset gap over the river (x 50-58 % of the picture) in the content area, right of the 253 px sidebar.
     */
    @Test
    fun riverSunsetGapStaysInTheContentArea() {
        val river = Wallpapers.all.single { it.number == 2 }
        val (iw, ih) = 1920.0 to 804.0
        for ((w, h) in listOf(900.0 to 580.0, 1024.0 to 640.0, 1366.0 to 800.0, 1920.0 to 1080.0)) {
            val c = CoverFit.crop(iw, ih, w, h, river.focusX, river.focusY)
            for (gx in listOf(0.50, 0.58)) {
                val x = CoverFit.toBox(c, w, gx * iw)
                assertTrue(x > 253 + 40 && x < w - 40, "gap edge ${gx} at x=${"%.0f".format(x)} in ${w}x$h, outside the content area")
            }
        }
    }
}

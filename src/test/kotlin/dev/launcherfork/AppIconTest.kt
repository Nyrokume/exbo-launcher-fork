package dev.launcherfork

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.components.loadImage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** Window / taskbar icon: every size listed in Theme.APP_ICON_SIZES exists, loads and has its nominal size. */
class AppIconTest {
    @Test
    fun allIconSizesLoad() = FxTestSupport.onFx {
        assertEquals(listOf(16, 32, 48, 64, 128, 256), Theme.APP_ICON_SIZES)
        Theme.appIconResources().zip(Theme.APP_ICON_SIZES).forEach { (path, size) ->
            val img = loadImage(path)
            assertFalse(img.isError, "$path: ${img.exception}")
            assertEquals(size.toDouble(), img.width, path)
            assertEquals(size.toDouble(), img.height, path)
        }
        val legacy = loadImage(Theme.IMAGE_ROOT + "icon.png")
        assertFalse(legacy.isError)
        assertEquals(54.0, legacy.width)
        Theme.APP_ICON_SIZES.forEach { size ->
            val path = Theme.IMAGE_ROOT + "icon-frost-$size.png"
            val img = loadImage(path)
            assertFalse(img.isError, "$path: ${img.exception}")
            assertEquals(size.toDouble(), img.width, path)
            assertEquals(size.toDouble(), img.height, path)
        }
    }
}

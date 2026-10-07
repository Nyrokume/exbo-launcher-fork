package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.app.LauncherApp
import dev.launcherfork.app.Showcase
import dev.launcherfork.app.ShowcaseTour
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.app.TourActions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * Showcase mode (app/Showcase, docs/SHOWCASE.md): the step sequence, size parsing, and the hard rule that
 * nothing is written to the user's settings file (theme / appearance / wallpaper.last).
 */
class ShowcaseTest {
    @Test
    fun `size parsing accepts WxH and rejects junk`() {
        assertEquals(1366.0 to 800.0, Showcase.parseSize("1366x800"))
        assertEquals(1920.0 to 1080.0, Showcase.parseSize("1920X1080"))
        assertEquals(1600.0 to 900.0, Showcase.parseSize(" 1600*900 "))
        assertEquals(900.0 to 580.0, Showcase.parseSize("800x500")) // below minimum: raised
        assertNull(Showcase.parseSize(null))
        assertNull(Showcase.parseSize(""))
        assertNull(Showcase.parseSize("big"))
        assertNull(Showcase.parseSize("1366"))
    }

    @Test
    fun `fromProperties is off by default and reads the flags`() {
        assertNull(Showcase.fromProperties { null })
        assertNull(Showcase.fromProperties { if (it == Showcase.PROPERTY) "false" else null })
        val on = Showcase.fromProperties {
            when (it) {
                Showcase.PROPERTY -> "true"
                Showcase.SIZE_PROPERTY -> "1920x1080"
                Showcase.CAPTIONS_PROPERTY -> "false"
                Showcase.STILLS_PROPERTY -> "/tmp/stills"
                Showcase.LOOPS_PROPERTY -> "2"
                else -> null
            }
        }!!
        assertEquals(1920.0, on.width)
        assertEquals(1080.0, on.height)
        assertFalse(on.captions)
        assertEquals("/tmp/stills", on.stillsDir!!.path)
        assertEquals(2, on.loops)
    }

    @Test
    fun `settingsFile is always null in showcase even with launcher_settingsFile`() {
        val home = "/tmp/home-should-not-matter"
        assertNull(LauncherApp.settingsFile(showcase = true, screenshots = false, property = "/tmp/forced.properties", home = home))
        assertNull(LauncherApp.settingsFile(showcase = true, screenshots = true, property = null, home = home))
        assertEquals("/tmp/forced.properties", LauncherApp.settingsFile(false, false, "/tmp/forced.properties", home)!!.toString())
        assertNull(LauncherApp.settingsFile(false, screenshots = true, property = null, home = home))
        assertTrue(LauncherApp.settingsFile(false, false, null, home)!!.toString().endsWith(".launcher-fork/settings.properties"))
    }

    @Test
    fun `step sequence order durations and captions`() {
        val tour = ShowcaseTour(nav = navigator(), config = Showcase(captions = false), lang = "ru")
        assertEquals(
            listOf(
                "main", "wallpapers", "theme_light", "theme_exbo", "theme_shadcn", "theme_zone",
                "banner", "settings", "profile_menu", "community_menu", "download", "paused_ready",
                "auth", "age_gate", "register", ShowcaseTour.SIGN_IN,
            ),
            tour.steps.map { it.id },
        )
        tour.steps.filterNot { it.awaitsMain }.forEach { step ->
            assertTrue(step.seconds in 3.0..4.0, "${step.id} is ${step.seconds}s, expected 3–4")
        }
        assertTrue(tour.steps.last().awaitsMain)
        assertEquals("Главное окно", tour.caption(tour.steps.first()))
        assertEquals("Вход", tour.caption(tour.steps.last()))
        assertEquals(16, tour.steps.size)
        assertEquals(0, tour.loopsDone)
    }

    @Test
    fun `settings file bytes stay untouched after showcase settingsFile resolution`() {
        // AppearanceFileStore would rewrite the file on theme / animations / tray changes; WallpaperFileMemory
        // would write wallpaper.last. Showcase mode never opens either: settingsFile is forced to null.
        val sentinel = Files.createTempFile("showcase-settings-", ".properties")
        val before = byteArrayOf(0x53, 0x48, 0x4f, 0x57) // "SHOW"
        Files.write(sentinel, before)
        val wallpaperLast = Files.createTempFile("showcase-wallpaper-", ".properties")
        Files.write(wallpaperLast, "wallpaper.last=3\n".toByteArray())

        assertNull(LauncherApp.settingsFile(showcase = true, screenshots = false, property = sentinel.toString(), home = "/tmp"))
        assertTrue(Files.readAllBytes(sentinel).contentEquals(before), "settings file must stay byte-identical")
        assertEquals("wallpaper.last=3\n", Files.readString(wallpaperLast), "wallpaper.last must stay untouched")

        Files.deleteIfExists(sentinel)
        Files.deleteIfExists(wallpaperLast)
    }

    @Test
    fun `TourActions mock login is the showcase nickname`() {
        assertEquals("player", TourActions.MOCK_LOGIN)
        assertEquals("secret", TourActions.MOCK_PASSWORD)
    }

    companion object {
        @JvmStatic @BeforeAll fun init() = FxTestSupport.start()

        private fun navigator(): StageNavigator = FxTestSupport.onFx {
            StageNavigator(javafx.stage.Stage(), AppContext.mock(speed = 50.0, authDelayMs = 0), "ru").also { it.exit = {} }
        }
    }
}

package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.app.Showcase
import dev.launcherfork.app.ShowcaseTour
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.app.TourActions
import dev.launcherfork.data.mock.InMemorySettingsStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * Showcase mode (app/Showcase, docs/SHOWCASE.md): the step sequence, size parsing, and the hard rule that
 * nothing is written to disk (in-memory settings; no WallpaperFileMemory / wallpaper.last).
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
        assertEquals(java.io.File("/tmp/stills"), on.stillsDir)
        assertEquals(2, on.loops)
    }

    @Test
    fun `AppContext mock always uses in-memory settings`() {
        // Showcase / tests never pass a settings file: AppContext.mock always installs InMemorySettingsStore.
        val app = AppContext.mock(speed = 50.0, authDelayMs = 0)
        assertTrue(app.services.settings is InMemorySettingsStore)
        val loaded = app.services.settings.load()
        app.services.settings.save(loaded.copy(animations = false, language = "en"))
        assertFalse(app.services.settings.load().animations)
        assertEquals("en", app.services.settings.load().language)
    }

    @Test
    fun `step sequence order durations and captions`() {
        val tour = ShowcaseTour(nav = navigator(), config = Showcase(captions = false), lang = "ru")
        assertEquals(
            listOf(
                "age_gate", "register", "auth", ShowcaseTour.SIGN_IN,
                "main", "wallpapers", "theme_exbo", "theme_shadcn", "theme_zone",
                "banner", "settings", "profile_menu", "community_menu", "game_actions",
                "download", "paused_ready",
            ),
            tour.steps.map { it.id },
        )
        tour.steps.filterNot { it.awaitsMain }.forEach { step ->
            assertTrue(step.seconds in 3.0..4.0, "${step.id} is ${step.seconds}s, expected 3–4")
        }
        assertTrue(tour.steps[tour.steps.indexOfFirst { it.id == ShowcaseTour.SIGN_IN }].awaitsMain)
        assertEquals("Возраст", tour.caption(tour.steps.first()))
        assertEquals("Готово", tour.caption(tour.steps.last()))
        assertEquals(16, tour.steps.size)
        assertEquals(0, tour.loopsDone)
    }

    @Test
    fun `disk settings and wallpaper last stay untouched under in-memory showcase`() {
        // Showcase uses AppContext.mock (InMemorySettingsStore) and does not attach WallpaperFileMemory,
        // so neither animations nor wallpaper.last hit the disk.
        val sentinel = Files.createTempFile("showcase-settings-", ".properties")
        val before = byteArrayOf(0x53, 0x48, 0x4f, 0x57) // "SHOW"
        Files.write(sentinel, before)
        val wallpaperLast = Files.createTempFile("showcase-wallpaper-", ".properties")
        Files.write(wallpaperLast, "wallpaper.last=3\n".toByteArray())

        val app = AppContext.mock(speed = 50.0, authDelayMs = 0)
        assertTrue(app.services.settings is InMemorySettingsStore)
        app.services.settings.save(app.services.settings.load().copy(animations = false))
        assertTrue(Files.readAllBytes(sentinel).contentEquals(before), "settings file must stay byte-identical")
        assertEquals("wallpaper.last=3\n", Files.readString(wallpaperLast), "wallpaper.last must stay untouched")

        Files.deleteIfExists(sentinel)
        Files.deleteIfExists(wallpaperLast)
    }

    @Test
    fun `loop limit counts full passes and ends after the last launcher step`() {
        val tour = ShowcaseTour(navigator(), Showcase(captions = false, loops = 1))
        val last = tour.steps.lastIndex
        // mid-pass: each step advances
        for (i in 0 until last) assertEquals(i + 1, tour.advance(i))
        assertEquals(0, tour.loopsDone)
        // leaving the last step closes the pass and hits loops=1
        assertNull(tour.advance(last))
        assertEquals(1, tour.loopsDone)

        val endless = ShowcaseTour(navigator(), Showcase(captions = false, loops = 0))
        repeat(2) { for (i in endless.steps.indices) assertEquals((i + 1) % endless.steps.size, endless.advance(i)) }
        assertEquals(2, endless.loopsDone) // loops=0 never stops
    }

    @Test
    fun `TourActions mock login is the showcase nickname`() {
        assertEquals("DementiyRezak", TourActions.MOCK_LOGIN)
        assertEquals("secret", TourActions.MOCK_PASSWORD)
    }

    companion object {
        @JvmStatic @BeforeAll fun init() = FxTestSupport.start()

        private fun navigator(): StageNavigator = FxTestSupport.onFx {
            StageNavigator(javafx.stage.Stage(), AppContext.mock(speed = 50.0, authDelayMs = 0), "ru").also { it.exit = {} }
        }
    }
}

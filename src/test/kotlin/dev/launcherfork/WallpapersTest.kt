package dev.launcherfork

import dev.launcherfork.data.local.WallpaperFileMemory
import dev.launcherfork.wallpaper.Wallpaper
import dev.launcherfork.wallpaper.WallpaperMemory
import dev.launcherfork.wallpaper.Wallpapers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import javax.imageio.ImageIO
import kotlin.random.Random

/**
 * Main-window wallpapers: the bundled manifest, the per-launch pick (random, never the previous launch's picture),
 * `wallpaper.last` in settings.properties, the fixed choice -Dlauncher.wallpaper=N and the once-per-launch holder
 * [Wallpapers.current].
 */
class WallpapersTest {
    @AfterEach
    fun reset() = Wallpapers.resetForTests()

    private class FakeMemory(var stored: Int?) : WallpaperMemory {
        val writes = mutableListOf<Int>()
        override fun last(): Int? = stored
        override fun remember(number: Int) { writes += number; stored = number }
    }

    // ---- manifest / bundled files ----

    @Test
    fun manifestListsSixBundledJpegsUpTo1920Wide() {
        val all = Wallpapers.all
        assertEquals((1..6).toList(), all.map { it.number })
        all.forEach { wp ->
            assertEquals("%02d.jpg".format(wp.number), wp.resource.substringAfterLast('/'), "file name of #${wp.number}")
            val url = requireNotNull(Wallpapers.url(wp)) { "missing resource ${wp.resource}" }
            val img = url.openStream().use { ImageIO.read(it) }
            assertTrue(img.width <= 1920, "${wp.resource}: ${img.width} px wide > 1920")
            val bytes = url.openStream().use { it.readBytes().size }
            assertTrue(bytes < 600_000, "${wp.resource}: $bytes bytes, re-encode it (1920 px, quality ~82)")
            assertTrue(wp.focusX in 0.0..1.0 && wp.focusY in 0.0..1.0, "focus of #${wp.number}")
        }
    }

    @Test
    fun manifestParserReadsFocusAndSkipsComments() {
        val list = Wallpapers.parseManifest("""
            # number file focusX focusY title
            01.jpg 0.5 0.4 bright church
            
            02.jpg 0.46 0.5 river at dusk
            03.jpg
        """.trimIndent())
        assertEquals(listOf(1, 2, 3), list.map { it.number })
        assertEquals(0.4, list[0].focusY)
        assertTrue(list[0].bright && !list[1].bright && !list[2].bright, "tone")
        assertEquals("church", list[0].title)
        assertEquals(0.5 to 0.5, list[2].focusX to list[2].focusY, "default focus: the centre")
        assertEquals(0.46, list[1].focusX)
        assertEquals("river at dusk", list[1].title)
        assertTrue(list[1].resource.endsWith("/wallpapers/02.jpg"))
    }

    // ---- picker ----

    @Test
    fun pickNeverRepeatsThePreviousLaunch() {
        val random = Random(42)
        for (previous in 1..6) repeat(2_000) {
            val n = Wallpapers.pick(6, previous, random)
            assertTrue(n in 1..6, "out of range: $n")
            assertNotEquals(previous, n, "same wallpaper as the previous launch")
        }
    }

    @Test
    fun consecutiveLaunchesCoverAllSixAndNeverRepeat() {
        val random = Random(7)
        var previous: Int? = null
        val seen = mutableMapOf<Int, Int>()
        repeat(600) {
            val n = Wallpapers.pick(6, previous, random)
            assertNotEquals(previous, n)
            seen.merge(n, 1, Int::plus)
            previous = n
        }
        assertEquals((1..6).toSet(), seen.keys, "every wallpaper shows up")
        assertTrue(seen.values.all { it > 60 }, "roughly uniform: $seen")
    }

    @Test
    fun missingOrCorruptPreviousMeansAnyWallpaper() {
        for (previous in listOf(null, 0, 7, -3, Int.MAX_VALUE)) {
            val seen = (1..300).map { Wallpapers.pick(6, previous, Random(it)) }.toSet()
            assertEquals((1..6).toSet(), seen, "previous=$previous")
        }
        assertEquals(1, Wallpapers.pick(1, 1, Random(1)), "a single wallpaper is allowed to repeat")
    }

    @Test
    fun forcedNumberComesFromTheSystemPropertyValue() {
        assertEquals(3, Wallpapers.forcedNumber("3"))
        assertEquals(6, Wallpapers.forcedNumber(" 6 "))
        for (bad in listOf(null, "", "0", "7", "x", "-1", "2.5")) assertNull(Wallpapers.forcedNumber(bad), "'$bad'")
    }

    // ---- once per launch ----

    @Test
    fun chooseRemembersThePickAndAvoidsTheStoredOne() {
        repeat(50) { seed ->
            Wallpapers.resetForTests()
            val memory = FakeMemory(4)
            val wp = Wallpapers.choose(memory, Random(seed), forced = null)!!
            assertNotEquals(4, wp.number)
            assertEquals(listOf(wp.number), memory.writes)
        }
    }

    @Test
    fun chosenOncePerLaunchAndSharedByEveryWindow() {
        val memory = FakeMemory(null)
        val first = Wallpapers.choose(memory, Random(1), forced = null)!!
        assertSame(first, Wallpapers.current)
        // later callers (main window after login, the login window, a rebuilt main screen) get the same picture
        repeat(20) { assertSame(first, Wallpapers.choose(FakeMemory(first.number), Random(it + 100), forced = null)) }
        assertSame(first, Wallpapers.choose(null, Random(5), forced = (first.number % 6) + 1), "a later forced value changes nothing")
        assertEquals(listOf(first.number), memory.writes, "written once per launch")
    }

    @Test
    fun forcedChoiceWinsOverRandomAndMemory() {
        val memory = FakeMemory(2)
        assertEquals(2, Wallpapers.choose(memory, Random(1), forced = 2)!!.number, "-Dlauncher.wallpaper=2 even if it was last")
    }

    @Test
    fun forcedChoiceIsNotSaved(@TempDir dir: Path) {
        val file = dir.resolve("settings.properties")
        val memory = WallpaperFileMemory(file)
        memory.remember(4)
        assertEquals(5, Wallpapers.choose(memory, Random(1), forced = 5)!!.number)
        assertEquals(4, memory.last(), "-Dlauncher.wallpaper=N keeps the user's real wallpaper.last")
        val fake = FakeMemory(4)
        Wallpapers.resetForTests()
        Wallpapers.choose(fake, Random(1), forced = 5)
        assertEquals(emptyList<Int>(), fake.writes, "forced choice: remember() is never called")
    }

    @Test
    fun randomChoiceIsSaved(@TempDir dir: Path) {
        val memory = WallpaperFileMemory(dir.resolve("settings.properties"))
        memory.remember(4)
        val chosen = Wallpapers.choose(memory, Random(1), forced = null)!!.number
        assertEquals(chosen, memory.last(), "without the property wallpaper.last updates")
    }

    @Test
    fun noManifestMeansNoWallpaper() {
        assertNull(Wallpapers.choose(FakeMemory(null), Random(1), forced = null, list = emptyList<Wallpaper>()))
        assertNull(Wallpapers.current)
    }

    // ---- settings.properties ----

    @Test
    fun lastWallpaperSurvivesInSettingsFile(@TempDir dir: Path) {
        val file = dir.resolve("settings.properties")
        val memory = WallpaperFileMemory(file)
        assertNull(memory.last())
        memory.remember(5)
        assertEquals(5, WallpaperFileMemory(file).last())
        memory.remember(3)
        assertEquals(3, WallpaperFileMemory(file).last())
        val props = Properties().apply { Files.newBufferedReader(file).use { load(it) } }
        assertEquals("3", props.getProperty("wallpaper.last"))
        assertEquals(listOf("settings.properties"), Files.list(dir).use { s -> s.map { it.fileName.toString() }.toList() }, "leftover temp files")
    }

    @Test
    fun corruptOrMissingLastWallpaperIsIgnored(@TempDir dir: Path) {
        val file = dir.resolve("settings.properties")
        assertNull(WallpaperFileMemory(file).last(), "no file")
        for (bad in listOf("abc", "", "  ", "1.5", "99999999999")) {
            Files.writeString(file, "wallpaper.last=$bad\n")
            assertNull(WallpaperFileMemory(file).last(), "'$bad'")
        }
        Files.writeString(file, "\u0000\u0001garbage=\\u12")
        assertNull(WallpaperFileMemory(file).last(), "unreadable file")
        Files.createDirectories(dir.resolve("blocked"))
        WallpaperFileMemory(dir.resolve("blocked")).remember(3) // a directory in the way: logged, never thrown
        // the chooser still works without a usable memory
        assertNotNull(Wallpapers.choose(WallpaperFileMemory(dir.resolve("blocked")), Random(1), forced = null))
    }
}

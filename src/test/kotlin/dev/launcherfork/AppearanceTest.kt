package dev.launcherfork

import dev.launcherfork.UiTestKit.mainOnScreen
import dev.launcherfork.app.AppContext
import dev.launcherfork.data.local.AppearanceFileStore
import dev.launcherfork.data.mock.InMemorySettingsStore
import dev.launcherfork.domain.Appearance
import dev.launcherfork.domain.LauncherSettings
import dev.launcherfork.theme.Theme
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Settings «Тема: Тёмная / Белая»: the choice is kept on disk (survives a restart, AppearanceFileStore), switching
 * re-styles BOTH windows live with theme-zone-light.css (zone theme), and the exbo / shadcn dev themes stay dark.
 */
class AppearanceTest {
    private val lightSheet = Theme.stylesheet("theme-zone-light.css")

    @Test
    fun choiceSurvivesARestart(@TempDir dir: Path) {
        val file = dir.resolve("sub/settings.properties")
        val first = AppearanceFileStore(InMemorySettingsStore(), file)
        assertEquals(Appearance.DARK, first.load().appearance, "dark by default")
        first.save(first.load().copy(exitMode = dev.launcherfork.domain.ExitMode.ON_GAME_START))
        assertFalse(Files.exists(file), "nothing written while the theme did not change")
        first.save(first.load().copy(appearance = Appearance.LIGHT))
        assertTrue(Files.readString(file).contains("appearance=light"), Files.readString(file))
        // "restart": a fresh in-memory store, the same file
        val second = AppearanceFileStore(InMemorySettingsStore(LauncherSettings(language = "en")), file)
        assertEquals(Appearance.LIGHT, second.load().appearance)
        assertEquals("en", second.load().language, "other fields come from the session store")
        second.save(second.load().copy(appearance = Appearance.DARK))
        assertEquals(Appearance.DARK, AppearanceFileStore(InMemorySettingsStore(), file).load().appearance)
    }

    @Test
    fun overwriteIsAtomicAndLeavesNoTempFile(@TempDir dir: Path) {
        val file = dir.resolve("settings.properties")
        Files.writeString(file, "appearance=dark\n# an old, longer comment line that must not survive the overwrite\n")
        AppearanceFileStore.write(file, Appearance.LIGHT)
        AppearanceFileStore.write(file, Appearance.DARK)
        AppearanceFileStore.write(file, Appearance.LIGHT)
        assertEquals(listOf("settings.properties"), Files.list(dir).use { s -> s.map { it.fileName.toString() }.toList() }, "leftover files")
        val text = Files.readString(file)
        assertTrue(text.contains("appearance=light") && !text.contains("appearance=dark") && !text.contains("longer comment"), text)
        assertEquals(Appearance.LIGHT, AppearanceFileStore.read(file))
        // an unwritable target (a directory in its place) is logged, not thrown, and leaves no temp file behind
        val blocked = dir.resolve("blocked").also { Files.createDirectories(it.resolve("child")) }
        AppearanceFileStore.write(blocked, Appearance.LIGHT)
        assertTrue(Files.isDirectory(blocked))
        assertEquals(setOf("blocked", "settings.properties"), Files.list(dir).use { s -> s.map { it.fileName.toString() }.toList() }.toSet(), "temp file left after a failed write")
    }

    @Test
    fun brokenOrUnknownFileMeansDark(@TempDir dir: Path) {
        val file = dir.resolve("settings.properties")
        Files.writeString(file, "appearance=sepia\n")
        assertEquals(Appearance.DARK, AppearanceFileStore(InMemorySettingsStore(), file).load().appearance)
        Files.writeString(file, "\u0000\u0001garbage")
        assertEquals(Appearance.DARK, AppearanceFileStore(InMemorySettingsStore(), file).load().appearance)
        assertEquals(Appearance.DARK, AppearanceFileStore(InMemorySettingsStore(), dir).load().appearance, "a directory is not a file")
    }

    private fun <T> withTheme(name: String, block: () -> T): T {
        val (oldName, oldAppearance) = Theme.themeName to Theme.appearance
        Theme.themeName = name
        Theme.appearance = Appearance.DARK
        try { return block() } finally { Theme.themeName = oldName; Theme.appearance = oldAppearance }
    }

    private fun background(r: Region): Color = r.background.fills.first().fill as Color

    @Test
    fun switchingInSettingsRestylesBothWindowsLiveAndIsSaved(@TempDir dir: Path) = withTheme("zone") {
        val file = dir.resolve("settings.properties")
        val app = AppContext.mock(authDelayMs = 0, settingsFile = file)
        val nav = mainOnScreen(app)
        try {
            FxTestSupport.onFx {
                val main = nav.mainController!!
                main.setSettingsVisible(true)
                val s = main.settingsController
                assertTrue(s.themeDark.isSelected && !s.themeLight.isDisable, "zone: dark selected, the white option enabled")
                assertFalse(lightSheet in nav.mainStage.scene.stylesheets)

                s.themeLight.fire()
                for (scene in listOf(nav.mainStage.scene, nav.authStage.scene)) {
                    assertTrue(lightSheet in scene.stylesheets, "white sheet missing: ${scene.stylesheets}")
                    assertTrue(scene.stylesheets.indexOf(lightSheet) > scene.stylesheets.indexOf(Theme.stylesheet("theme-zone.css")),
                        "theme-zone-light.css must come after theme-zone.css")
                }
                assertEquals(Appearance.LIGHT, Theme.appearance)
                assertEquals(Appearance.LIGHT, app.services.settings.load().appearance)
                val content = nav.mainStage.scene.lookup("#mainContent") as Region
                nav.mainStage.scene.root.applyCss()
                assertEquals(Color.WHITE, background(content), "main content not white")
                assertEquals(Color.web("#0a0a0a"), (s.gameHeading.textFill as Color), "headings not dark")

                s.themeDark.fire()
                assertFalse(lightSheet in nav.mainStage.scene.stylesheets || lightSheet in nav.authStage.scene.stylesheets)
                nav.mainStage.scene.root.applyCss()
                assertEquals(Color.web("#0a0a0a"), background(content), "back to the dark zone background")
                s.themeLight.fire()
            }
            assertTrue(Files.readString(file).contains("appearance=light"), "choice not saved")
            // a new main screen (e.g. after a language switch) shows the current choice
            FxTestSupport.onFx {
                nav.showMain()
                nav.mainController!!.setSettingsVisible(true)
                assertTrue(nav.mainController!!.settingsController.themeLight.isSelected)
            }
        } finally {
            FxTestSupport.onFx { nav.close() }
            app.shutdown()
        }
    }

    @Test
    fun devThemesStayDark() = listOf("exbo", "shadcn").forEach { theme ->
        withTheme(theme) {
            val app = AppContext.mock(authDelayMs = 0)
            val nav = mainOnScreen(app)
            try {
                FxTestSupport.onFx {
                    val s = nav.mainController!!.settingsController
                    assertTrue(s.themeLight.isDisable && s.themeDark.isDisable, "$theme: «Тема» must be disabled")
                    nav.switchAppearance(Appearance.LIGHT)
                    assertEquals(Appearance.DARK, Theme.appearance, "$theme: switched anyway")
                    assertFalse(lightSheet in nav.mainStage.scene.stylesheets, "$theme: white sheet applied")
                    assertFalse(lightSheet in Theme.stylesheets(theme, Appearance.LIGHT, "ru"))
                }
            } finally {
                FxTestSupport.onFx { nav.close() }
                app.shutdown()
            }
        }
    }

    @Test
    fun zoneLightStackIsZonePlusTheLightTokens() {
        val sheets = Theme.stylesheets("zone", Appearance.LIGHT, "en")
        assertEquals(listOf("theme.css", "theme-zone.css", "theme-zone-light.css", "lang_en.css"), sheets.map { it.substringAfterLast('/') })
        assertEquals(listOf("theme.css", "theme-zone.css", "lang_ru.css"), Theme.stylesheets("zone", Appearance.DARK, "ru").map { it.substringAfterLast('/') })
    }
}

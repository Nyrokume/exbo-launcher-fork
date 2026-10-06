package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.data.mock.MockData
import dev.launcherfork.theme.Theme
import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.stage.Stage
import javafx.stage.StageStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Main window, zone theme: plain content background, Forum / Support next to the profile, the STALZONE entry. */
class MainZoneLayoutTest {
    private fun Node.sceneBounds(): Bounds = localToScene(layoutBounds)

    private fun withZoneMain(check: (StageNavigator) -> Unit) {
        val app = AppContext.mock(authDelayMs = 0)
        val nav = FxTestSupport.onFx {
            StageNavigator(Stage(StageStyle.UNDECORATED), app, "ru").also { it.exit = {}; it.showMain(); it.mainStage.show() }
        }
        try {
            FxTestSupport.onFx {
                val scene = nav.mainStage.scene
                Theme.zoneFonts
                val zoneSheet = Theme.stylesheet("theme-zone.css")
                if (zoneSheet !in scene.stylesheets) scene.stylesheets.add(1, zoneSheet)
                // second pass: the links row height is bound to the profile button height measured in the first
                scene.root.applyCss(); scene.root.layout(); scene.root.layout()
                check(nav)
            }
        } finally {
            FxTestSupport.onFx { nav.close() }
            app.shutdown()
        }
    }

    @Test
    fun zoneHasNoGameArtBehindTheContent() = withZoneMain { nav ->
        val main = nav.mainController!!
        assertFalse(main.background.isVisible, "game art must be hidden in the zone theme")
        assertFalse(main.backgroundShade.isVisible)
        // news cards keep their own images
        assertTrue(main.advancedNewsPaneController.cards.isNotEmpty())
    }

    @Test
    fun headerLinksSitInTheProfileRow() = withZoneMain { nav ->
        val main = nav.mainController!!
        val forum = main.headerController.forum.sceneBounds()
        val support = main.headerController.support.sceneBounds()
        val profile = main.profileBarController.loginButton.sceneBounds()
        // order left to right: ФОРУМ, ПОДДЕРЖКА, profile; one row, centred on the profile button
        assertTrue(forum.maxX < support.minX, "forum $forum / support $support")
        val gap = profile.minX - support.maxX
        assertTrue(gap in 16.0..24.0, "gap between ПОДДЕРЖКА and the profile: $gap")
        listOf(forum, support).forEach { assertEquals(profile.centerY, it.centerY, 1.0, "not centred on the profile: $it vs $profile") }
        // opening the profile dropdown must not move the links
        main.profileBarController.showMenu()
        nav.mainStage.scene.root.run { applyCss(); layout(); layout() }
        assertEquals(forum.centerY, main.headerController.forum.sceneBounds().centerY, 0.5,
            "links moved with the open dropdown: ${main.headerController.headerPane.sceneBounds()}")
        main.profileBarController.hideMenu()
        // the links stay a home-screen thing, the profile stays everywhere
        main.setSettingsVisible(true)
        assertFalse(main.headerController.headerPane.isVisible)
        assertTrue(main.profileBarController.loginButton.isVisible)
        main.setSettingsVisible(false)
        assertTrue(main.headerController.headerPane.isVisible)
    }

    @Test
    fun firstGameIsStalzoneWithTheSiteFavicon() {
        val game = MockData.servers.first()
        assertEquals("STALZONE", game.caption)
        assertTrue(game.description.first().startsWith("STALZONE — "))
        assertTrue(game.iconUri.toString().endsWith("/servers/game-stalzone.png"))
        assertTrue(MockData.servers.none { s -> "STALCRAFT" in s.caption || s.description.any { "STALCRAFT" in it } })
    }
}

package dev.launcherfork

import dev.launcherfork.UiTestKit.THEMES
import dev.launcherfork.UiTestKit.applyTheme
import dev.launcherfork.UiTestKit.mainOnScreen
import dev.launcherfork.UiTestKit.preview
import dev.launcherfork.UiTestKit.relayout
import dev.launcherfork.UiTestKit.resize
import dev.launcherfork.UiTestKit.sceneBounds
import dev.launcherfork.app.AppContext
import dev.launcherfork.theme.Theme
import javafx.geometry.BoundingBox
import javafx.scene.text.Text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The sidebar «НАСТРОЙКИ» item sits on the bottom status row's line: as tall as the status pill / «⋯» / ИГРАТЬ (44 px),
 * same vertical centre and bottom edge (±1 px), gear and caption centred on that line, fully visible inside the window
 * and the sidebar. Every theme, both languages, minimum / default / a larger window; opening the settings page does
 * not move it.
 */
class SidebarSettingsAlignTest {
    private val sizes = listOf(
        Theme.WINDOW_MIN_WIDTH to Theme.WINDOW_MIN_HEIGHT,
        Theme.WINDOW_WIDTH to Theme.WINDOW_HEIGHT,
        1366.0 to 800.0,
    )

    @Test
    fun settingsItemSitsOnTheStatusRowLine() {
        listOf("ru", "en").forEach { lang ->
            val app = AppContext.mock(language = lang, authDelayMs = 0)
            val nav = mainOnScreen(app, lang)
            try {
                THEMES.forEach { theme ->
                    sizes.forEach { (w, h) ->
                        FxTestSupport.onFx {
                            applyTheme(nav.mainStage.scene, theme, lang)
                            nav.mainController!!.setSettingsVisible(false)
                        }
                        resize(nav, w, h)
                        FxTestSupport.onFx {
                            relayout(nav)
                            val where = "$theme/$lang ${w.toInt()}x${h.toInt()}"
                            val main = nav.mainController!!
                            val scene = nav.mainStage.scene
                            val s = main.defaultStatusPaneController
                            val pill = s.statusPill.sceneBounds()
                            val play = s.downloadButton.sceneBounds()
                            val more = s.gameActionsButton.sceneBounds()
                            val button = main.serversListController.settingsButton
                            val b = button.sceneBounds()

                            assertTrue(button.isVisible && button.scene != null, "$where: НАСТРОЙКИ not shown")
                            assertEquals(44.0, pill.height, 1.0, "$where: status pill height")
                            assertEquals(pill.height, b.height, 1.0, "$where: НАСТРОЙКИ height ${b.height} vs pill ${pill.height}")
                            assertEquals(pill.centerY, b.centerY, 1.0, "$where: НАСТРОЙКИ centre ${b.centerY} vs pill ${pill.centerY}")
                            assertEquals(pill.maxY, b.maxY, 1.0, "$where: НАСТРОЙКИ bottom ${b.maxY} vs pill ${pill.maxY}")
                            listOf(play, more).forEach { assertEquals(it.centerY, b.centerY, 1.0, "$where: not on the ИГРАТЬ / «⋯» line") }

                            // gear and caption are centred on that line
                            val gear = button.graphic.sceneBounds()
                            assertEquals(pill.centerY, gear.centerY, 1.0, "$where: gear centre ${gear.centerY}")
                            val caption = button.lookupAll(".text").filterIsInstance<Text>().single { it.text.isNotEmpty() }
                            assertEquals(button.text, caption.text, "$where: caption truncated")
                            assertEquals(pill.centerY, caption.sceneBounds().centerY, 2.0, "$where: caption not centred")

                            // always fully visible: inside the window and inside the sidebar, above nothing else
                            val window = BoundingBox(0.0, 0.0, scene.width, scene.height)
                            val sidebar = main.serversListController.sidebar.sceneBounds()
                            listOf(b, gear, caption.sceneBounds()).forEach { n ->
                                assertTrue(n.minY >= window.minY && n.maxY <= window.maxY, "$where: $n outside the window $window")
                                assertTrue(n.minX >= sidebar.minX && n.maxX <= sidebar.maxX && n.maxY <= sidebar.maxY, "$where: $n outside the sidebar $sidebar")
                            }
                            assertTrue(main.serversListController.serversPane.sceneBounds().maxY <= b.minY + 0.5, "$where: games list overlaps НАСТРОЙКИ")

                            // the settings page does not move it
                            main.setSettingsVisible(true)
                            relayout(nav)
                            assertEquals(b, button.sceneBounds(), "$where: НАСТРОЙКИ moved when the settings page opened")
                            main.setSettingsVisible(false)
                            relayout(nav)
                            if (lang == "ru" && w == Theme.WINDOW_WIDTH) preview(scene, "main-navalign-$theme")
                        }
                    }
                }
            } finally {
                FxTestSupport.onFx { nav.close() }
                app.shutdown()
            }
        }
    }
}

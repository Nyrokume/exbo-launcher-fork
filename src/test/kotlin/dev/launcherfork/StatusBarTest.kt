package dev.launcherfork

import dev.launcherfork.UiTestKit.THEMES
import dev.launcherfork.UiTestKit.applyTheme
import dev.launcherfork.UiTestKit.mainOnScreen
import dev.launcherfork.UiTestKit.preview
import dev.launcherfork.UiTestKit.relayout
import dev.launcherfork.UiTestKit.resize
import dev.launcherfork.UiTestKit.sceneBounds
import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.domain.ClientOperation
import dev.launcherfork.domain.Progress
import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.screens.main.MainController
import dev.launcherfork.ui.state.ClientState
import javafx.geometry.Bounds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Bottom bar of the main screen: the status pill (status text + progress fill) is one control on the level of the
 * «⋯» and ИГРАТЬ buttons: same height, same vertical centre (within 1 px), text and fill inside the pill; the
 * percentage shows while downloading / paused. Every theme, both window sizes, ready / downloading / paused / verifying / reinstalling / deleting / running.
 */
class StatusBarTest {
    private fun withMain(check: (StageNavigator, MainController) -> Unit) {
        val app = AppContext.mock(speed = 50.0, authDelayMs = 0)
        val nav = mainOnScreen(app, "ru")
        try {
            FxTestSupport.onFx { relayout(nav) }
            check(nav, nav.mainController!!)
        } finally {
            FxTestSupport.onFx { nav.close() }
            app.shutdown()
        }
    }

    private fun Bounds.inside(o: Bounds) = minX >= o.minX - 0.5 && maxX <= o.maxX + 0.5 && minY >= o.minY - 0.5 && maxY <= o.maxY + 0.5

    private val gb = 1024L * 1024 * 1024
    private val states = listOf(
        "ready" to ClientState.Ready,
        "downloading" to ClientState.Downloading(Progress(0.42, (4.2 * gb).toLong(), 10 * gb, 24L * 1024 * 1024)),
        "paused" to ClientState.Paused(0.42, 10 * gb),
        "verifying" to ClientState.Busy(ClientOperation.VERIFY, 0.42),
        "reinstalling" to ClientState.Busy(ClientOperation.REINSTALL, 0.42),
        "deleting" to ClientState.Busy(ClientOperation.DELETE, 0.42),
        "running" to ClientState.Running,
    )

    @Test
    fun statusPillSitsOnThePlayButtonsLevelWithTheSameHeight() = withMain { nav, main ->
        val server = main.selected!!
        THEMES.forEach { theme ->
            listOf(Theme.WINDOW_MIN_WIDTH to Theme.WINDOW_MIN_HEIGHT, Theme.WINDOW_WIDTH to Theme.WINDOW_HEIGHT).forEach { (w, h) ->
                FxTestSupport.onFx { applyTheme(nav.mainStage.scene, theme, "ru") }
                resize(nav, w, h)
                states.forEach { (name, state) ->
                    FxTestSupport.onFx {
                        main.store.state(server).set(state)
                        relayout(nav)
                        val where = "$theme ${w.toInt()}x${h.toInt()} $name"
                        val s = main.defaultStatusPaneController
                        val pill = s.statusPill.sceneBounds()
                        val play = s.downloadButton.sceneBounds()
                        val more = s.gameActionsButton.sceneBounds()
                        assertEquals(play.height, pill.height, 1.0, "$where: pill height ${pill.height} vs ИГРАТЬ ${play.height}")
                        assertEquals(play.centerY, pill.centerY, 1.0, "$where: pill not centred on ИГРАТЬ")
                        assertEquals(play.centerY, more.centerY, 1.0, "$where: «⋯» not centred on ИГРАТЬ")
                        assertTrue(pill.maxX <= more.minX, "$where: pill runs under «⋯»")
                        assertTrue(pill.width > 200, "$where: pill too narrow ${pill.width}")
                        // text and fill inside the pill, the text vertically centred in it
                        val text = s.downloadingStatus.sceneBounds()
                        assertTrue(text.inside(pill), "$where: status text $text outside the pill $pill")
                        assertEquals(pill.centerY, text.centerY, 2.0, "$where: status text not centred")
                        assertTrue(s.progressBar.sceneBounds().inside(pill), "$where: progress outside the pill")
                        assertTrue(s.downloadingStatus.text.isNotBlank(), "$where: no status text")
                        val percent = s.progressPercent
                        when (state) {
                            is ClientState.Downloading, is ClientState.Paused, is ClientState.Busy -> {
                                assertEquals("42%", percent.text, "$where: percentage")
                                val pb = percent.sceneBounds()
                                assertTrue(pb.inside(pill) && pb.minX > text.maxX, "$where: percentage not inside, right of the text")
                                assertEquals(0.42, s.progressBar.progress, 1e-9)
                            }
                            else -> assertEquals("", percent.text, "$where: no percentage when idle")
                        }
                        // the number shows once: in the percentage slot, not repeated in the status text
                        assertFalse(s.downloadingStatus.text.contains("%"), "$where: percentage repeated in '${s.downloadingStatus.text}'")
                        if (theme == "zone" && w == Theme.WINDOW_WIDTH) preview(nav.mainStage.scene, "main-$name-zone")
                    }
                }
                FxTestSupport.onFx { main.store.state(server).set(ClientState.Ready); relayout(nav) }
            }
        }
        resize(nav, Theme.WINDOW_WIDTH, Theme.WINDOW_HEIGHT)
    }
}

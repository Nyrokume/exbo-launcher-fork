package dev.launcherfork

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.screens.window.AuthWindowController
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Every panel of the pre-main window, ru and en, at the height that window gives it
 * ([AuthWindowController.fittedWindowHeight]): nothing sticks out
 * (16 px air right and bottom) and the last control sits near the bottom (at most 44 px strip).
 */
class PanelFitTest {
    companion object {
        @JvmStatic
        fun panels(): List<Arguments> =
            listOf("auth_panel.fxml", "mail_auth_panel.fxml", "totp_auth_panel.fxml", "register_panel.fxml", "age_gate_panel.fxml", "update_panel.fxml")
                .flatMap { p -> listOf("ru", "en").map { Arguments.of(p, it) } }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("panels")
    fun panelFillsItsWindowExactly(panel: String, lang: String) = FxTestSupport.onFx {
        val loaded = Fxml.load<Any?>(recordingContext(RecordingNavigator(), lang), panel)
        val root = loaded.root as Region
        val scene = Scene(root, Theme.AUTH_PANEL_WIDTH, Theme.AUTH_PANEL_HEIGHT, Color.BLACK)
        Theme.apply(scene, lang)
        Theme.zoneFonts
        val zone = Theme.stylesheet("theme-zone.css")
        if (zone !in scene.stylesheets) scene.stylesheets.add(1, zone)
        root.applyCss(); root.layout()
        val windowHeight = AuthWindowController.fittedWindowHeight(root)
        // login, e-mail, authenticator and launcher-update panels are content-sized (compact windows, user 2026-10-27);
        // registration and age keep the full window
        val compact = panel == "auth_panel.fxml" || panel == "mail_auth_panel.fxml" || panel == "totp_auth_panel.fxml" || panel == "update_panel.fxml"
        if (compact) assertTrue(windowHeight < Theme.AUTH_WINDOW_HEIGHT, "$panel $lang should be compact: $windowHeight")
        else assertTrue(windowHeight == Theme.AUTH_WINDOW_HEIGHT, "$panel $lang should keep the full window: $windowHeight")
        val h = windowHeight - Theme.AUTH_TITLEBAR_HEIGHT
        root.resize(Theme.AUTH_PANEL_WIDTH, h); root.layout()
        if (panel == "update_panel.fxml") {
            val desc = root.lookup(".zone-description")
            val status = root.lookup("#zoneStatus")
            val bar = root.lookup("#zoneProgress")
            val descBox = desc.localToScene(desc.layoutBounds)
            val statusBox = status.localToScene(status.layoutBounds)
            val barBox = bar.localToScene(bar.layoutBounds)
            val gap = statusBox.minY - descBox.maxY
            assertTrue(gap in 12.0..72.0, "$panel $lang: gap between description and status is $gap px")
            assertTrue(barBox.minY + 0.5 >= statusBox.maxY, "$panel $lang: progress overlaps the status (${barBox.minY} < ${statusBox.maxY})")
            assertTrue(statusBox.minY + 0.5 >= descBox.maxY, "$panel $lang: status overlaps the description")
            assertEquals(316.0, windowHeight, 0.5, "$panel $lang window")
            val err = root.lookup("#zoneErrorFirst") as javafx.scene.control.Label
            err.text = if (lang == "ru") "Не удалось скачать обновление" else "Could not download the update"
            root.applyCss(); root.layout()
            val grown = AuthWindowController.fittedWindowHeight(root)
            assertTrue(grown > windowHeight && grown < Theme.AUTH_WINDOW_HEIGHT, "$panel $lang: error should grow the window ($windowHeight -> $grown)")
            val gh = grown - Theme.AUTH_TITLEBAR_HEIGHT
            root.resize(Theme.AUTH_PANEL_WIDTH, gh); root.layout()
            val errBox = err.localToScene(err.layoutBounds)
            val barAfter = root.lookup("#zoneProgress").localToScene(root.lookup("#zoneProgress").layoutBounds)
            assertTrue(errBox.minY + 0.5 >= barAfter.maxY, "$panel $lang: error overlaps the progress bar")
            assertTrue(errBox.maxX <= Theme.AUTH_PANEL_WIDTH - 16 + 0.5, "$panel $lang: error sticks out")
            assertTrue(errBox.maxY <= gh - 16 + 0.5, "$panel $lang: error sticks out at the bottom")
            err.text = ""
            root.applyCss(); root.layout()
            root.resize(Theme.AUTH_PANEL_WIDTH, h); root.layout()
        }

        var lowest = 0.0
        fun walk(n: Node) {
            if (!n.isVisible || !n.isManaged) return
            if (n is javafx.scene.control.Control || n is javafx.scene.text.Text) {
                val b = n.localToScene(n.layoutBounds)
                assertTrue(b.maxX <= Theme.AUTH_PANEL_WIDTH - 16 + 0.5, "$panel $lang: ${n.styleClass} sticks out to the right (${b.maxX})")
                assertTrue(b.maxY <= h - 16 + 0.5, "$panel $lang: ${n.styleClass} sticks out at the bottom (${b.maxY} of $h)")
                // the hidden spinner is a ProgressIndicator too, but it is unmanaged; the update bar is the last control
                if (n !is javafx.scene.control.ProgressIndicator || n is javafx.scene.control.ProgressBar) lowest = maxOf(lowest, b.maxY)
            }
            if (n is Parent && n !is javafx.scene.control.Control) n.childrenUnmodifiable.forEach(::walk)
        }
        walk(root)
        assertTrue(lowest >= h - 44, "$panel $lang: empty strip under the last control (${h - lowest} px)")
    }
}

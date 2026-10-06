package dev.launcherfork

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Fxml
import dev.launcherfork.ui.screens.window.AuthWindowController
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Every panel of the pre-main window, ru and en, at the window height it gets ([AuthWindowController.fittedWindowHeight]):
 * nothing sticks out (16 px of air right and bottom) and there is no empty strip under the last control (at most 44 px).
 * Compact panels (login, e-mail code, TOTP) are lower than the full 620 window.
 */
class PanelFitTest {
    companion object {
        private val compact = setOf("auth_panel.fxml", "mail_auth_panel.fxml", "totp_auth_panel.fxml")

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
        if (panel in compact) assertTrue(windowHeight < Theme.AUTH_WINDOW_HEIGHT, "$panel $lang is not compact: $windowHeight")
        else assertTrue(windowHeight == Theme.AUTH_WINDOW_HEIGHT, "$panel $lang should keep the full window: $windowHeight")
        val h = windowHeight - Theme.AUTH_TITLEBAR_HEIGHT
        root.resize(Theme.AUTH_PANEL_WIDTH, h); root.layout()

        var lowest = 0.0
        fun walk(n: Node) {
            if (!n.isVisible || !n.isManaged) return
            if (n is javafx.scene.control.Control || n is javafx.scene.text.Text) {
                val b = n.localToScene(n.layoutBounds)
                assertTrue(b.maxX <= Theme.AUTH_PANEL_WIDTH - 16 + 0.5, "$panel $lang: ${n.styleClass} sticks out to the right (${b.maxX})")
                assertTrue(b.maxY <= h - 16 + 0.5, "$panel $lang: ${n.styleClass} sticks out at the bottom (${b.maxY} of $h)")
                if (n !is javafx.scene.control.ProgressIndicator) lowest = maxOf(lowest, b.maxY)
            }
            if (n is Parent && n !is javafx.scene.control.Control) n.childrenUnmodifiable.forEach(::walk)
        }
        walk(root)
        assertTrue(lowest >= h - 44, "$panel $lang: empty strip under the last control (${h - lowest} px)")
    }
}

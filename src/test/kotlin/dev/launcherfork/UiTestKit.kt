package dev.launcherfork

import dev.launcherfork.app.AppContext
import dev.launcherfork.app.StageNavigator
import dev.launcherfork.theme.Theme
import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.stage.Stage
import javafx.stage.StageStyle
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Shared helpers of the settings / game-actions UI tests: live main window, theme switch, events, previews. */
object UiTestKit {
    /** Dark themes available via -Dlauncher.theme. */
    /** Stable themes for contrast / layout matrices. Prototype `frost` is covered by ThemeTokensTest only. */
    val THEMES = listOf("exbo", "shadcn", "zone")

    fun Node.sceneBounds(): Bounds = localToScene(layoutBounds)

    fun mainOnScreen(app: AppContext, lang: String = "ru"): StageNavigator = FxTestSupport.onFx {
        StageNavigator(Stage(StageStyle.UNDECORATED), app, lang).also { it.exit = {}; it.showMain(); it.mainStage.show() }
    }

    /** Same stylesheet stack as Theme.apply for [theme] / [lang] (Theme.themeName is fixed per JVM). */
    fun applyTheme(scene: Scene, theme: String, lang: String) {
        scene.stylesheets.setAll(Theme.stylesheets(theme, lang))
    }

    fun relayout(nav: StageNavigator) = nav.mainStage.scene.root.run { applyCss(); layout(); applyCss(); layout() }

    /** Resizes the main window and waits (off the FX thread) until the scene follows; call it outside onFx. */
    fun resize(nav: StageNavigator, w: Double, h: Double) {
        FxTestSupport.onFx { nav.mainStage.width = w; nav.mainStage.height = h }
        check(FxTestSupport.waitFx { nav.mainStage.scene.width == w && nav.mainStage.scene.height == h }) {
            "scene did not follow the window: ${nav.mainStage.scene.width}x${nav.mainStage.scene.height} for ${w}x$h"
        }
        FxTestSupport.onFx { relayout(nav) }
    }

    fun key(target: Node, code: KeyCode) =
        javafx.event.Event.fireEvent(target, KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))

    fun doubleClick(target: Node) {
        javafx.event.Event.fireEvent(target, MouseEvent(
            MouseEvent.MOUSE_CLICKED, 1.0, 1.0, 1.0, 1.0, MouseButton.PRIMARY, 2,
            false, false, false, false, true, false, false, true, false, false, null,
        ))
    }

    fun mouse(target: Node, type: javafx.event.EventType<MouseEvent>, sceneX: Double, sceneY: Double) {
        // x / y of a source-less event are scene coordinates; fireEvent converts them to the target's local ones
        javafx.event.Event.fireEvent(target, MouseEvent(
            type, sceneX, sceneY, sceneX, sceneY, MouseButton.PRIMARY, 1,
            false, false, false, false, true, false, false, true, false, false, null,
        ))
    }

    /** Scene snapshot as PNG (build/ui-preview by default, -Dui.preview=<dir>). */
    fun preview(scene: Scene, name: String) {
        val dir = File(System.getProperty("ui.preview", "build/ui-preview")).apply { mkdirs() }
        val img = scene.snapshot(null)
        val out = BufferedImage(img.width.toInt(), img.height.toInt(), BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until out.height) for (x in 0 until out.width) out.setRGB(x, y, img.pixelReader.getArgb(x, y))
        ImageIO.write(out, "png", File(dir, "$name.png"))
    }
}

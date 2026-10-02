package dev.launcherfork.theme

import javafx.scene.Scene
import javafx.scene.effect.ColorAdjust
import javafx.scene.effect.Effect
import javafx.scene.effect.GaussianBlur
import javafx.scene.text.Font
import java.net.URL

/**
 * Single place for everything visual that JavaFX CSS cannot express:
 * resource paths, window geometry, and effects (ColorAdjust/GaussianBlur are not CSS-able).
 * Colors and fonts live in ui/style/theme.css (looked-up color tokens).
 */
object Theme {
    const val UI_ROOT = "/dev/launcherfork/ui/"
    const val STYLE_ROOT = UI_ROOT + "style/"
    const val IMAGE_ROOT = STYLE_ROOT + "image/"

    // ---- window geometry (from the original launcher) ----
    const val AUTH_WIDTH = 379.0
    const val AUTH_HEIGHT = 490.0
    const val MAIN_WIDTH = 886.0
    const val MAIN_HEIGHT = 550.0

    /** Draggable header of the 379x490 auth/update windows (scene coordinates). */
    val AUTH_DRAG_POLYGON: List<Pair<Double, Double>> = listOf(
        5.0 to 5.0, 369.0 to 5.0, 369.0 to 36.0, 314.0 to 36.0,
        303.0 to 25.0, 72.0 to 25.0, 61.0 to 36.0, 5.0 to 36.0,
    )

    /** Draggable header of the main window, depends on the window width. */
    fun mainDragPolygon(w: Double): List<Pair<Double, Double>> = listOf(
        0.0 to 0.0, w to 0.0, w to 31.0, w - 232.0 to 31.0,
        w - 237.0 to 22.0, 237.0 to 22.0, 232.0 to 31.0, 0.0 to 31.0,
    )

    // ---- effects ----
    const val HOVER_BRIGHTNESS = 0.2
    const val SERVER_ENTRY_HOVER_BRIGHTNESS = 0.1
    const val BACKGROUND_BLUR_RADIUS = 3.0
    const val BACKGROUND_BRIGHTNESS = -0.2
    /** Download bar is desaturated at 0% and fully colored at 100%: saturation = (p - 1) * factor. */
    const val PROGRESS_SATURATION_FACTOR = 1.3
    /** Width of the right cap image of the download bar. */
    const val PROGRESS_CAP_WIDTH = 82.0

    fun hoverEffect(amount: Double = HOVER_BRIGHTNESS): Effect = ColorAdjust(0.0, 0.0, amount, 0.0)

    fun serverBackgroundEffect(): Effect = GaussianBlur(BACKGROUND_BLUR_RADIUS).apply {
        input = ColorAdjust(0.0, 0.0, BACKGROUND_BRIGHTNESS, 0.0)
    }

    fun progressEffect(progress: Double): Effect =
        ColorAdjust(0.0, (progress - 1.0) * PROGRESS_SATURATION_FACTOR, 0.0, 0.0)

    // ---- fonts / stylesheets ----
    /** Body font: Manrope (SIL OFL 1.1, Cyrillic) replaces the commercial Circe. Europe is still the display font. */
    private val FONT_FILES = listOf("Manrope-Light.ttf", "Manrope-Bold.ttf", "europe.ttf")

    /** Visual theme: "exbo" (original colors, default) or "shadcn" (shadcn/ui dark neutral). -Dlauncher.theme=shadcn */
    val themeName: String = System.getProperty("launcher.theme", "exbo").lowercase()

    /** Registers bundled fonts so CSS can refer to them by family name. Returns loaded family names. */
    fun loadFonts(): List<String> = FONT_FILES.mapNotNull { file ->
        Theme::class.java.getResourceAsStream(STYLE_ROOT + "font/" + file)?.use { Font.loadFont(it, 12.0)?.family }
    }

    fun resource(path: String): URL =
        requireNotNull(Theme::class.java.getResource(UI_ROOT + path)) { "Missing UI resource: $path" }

    fun stylesheet(name: String): String = resource("style/$name").toExternalForm()

    /** theme.css must be on the Scene so that .root defines the color tokens for every subtree. */
    fun apply(scene: Scene, lang: String) {
        val sheets = mutableListOf(stylesheet("theme.css"))
        if (themeName == "shadcn") sheets += stylesheet("theme-shadcn.css")
        sheets += stylesheet(if (lang == "en") "lang_en.css" else "lang_ru.css")
        scene.stylesheets.setAll(sheets)
    }
}

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

    // ---- window geometry: the main window is undecorated and resizable ----
    const val WINDOW_WIDTH = 1024.0
    const val WINDOW_HEIGHT = 640.0
    const val WINDOW_MIN_WIDTH = 900.0
    const val WINDOW_MIN_HEIGHT = 580.0
    /** Width of the invisible border zone that resizes the undecorated window. */
    const val RESIZE_MARGIN = 6.0

    /**
     * Separate pre-main window (update / auth / mail code, docs/design/WINDOWS.md): undecorated (transparent
     * corners), not resizable, rounded like the stalzone.com/login card. 460x620 = 32 px title strip + 460x588
     * panel host; the zone login panel stretches to the host (its min is 420x560), legacy 379x490 panels are centered.
     */
    const val AUTH_WINDOW_WIDTH = 460.0
    /** Tallest pre-main window (fixed-size legacy panels and update panel); compact panels fit their content. */
    const val AUTH_WINDOW_HEIGHT = 620.0
    /** Shortest pre-main window: a compact (content-sized) panel never makes the window lower than this. */
    const val AUTH_WINDOW_MIN_HEIGHT = 360.0
    const val AUTH_TITLEBAR_HEIGHT = 32.0
    const val AUTH_PANEL_WIDTH = AUTH_WINDOW_WIDTH
    const val AUTH_PANEL_HEIGHT = AUTH_WINDOW_HEIGHT - AUTH_TITLEBAR_HEIGHT
    /** Corner radius of the pre-main window clip; equals .zone-radius-md (CSS classes cannot clip). */
    const val AUTH_WINDOW_RADIUS = 10.0

    /**
     * Window / taskbar icon sizes: ui/style/image/icon-<size>.png (docs/design/icon/). All of them go into
     * Stage.icons so the OS picks a crisp one (16 title bar, 32/48 taskbar, 256 Alt-Tab / HiDPI).
     */
    val APP_ICON_SIZES = listOf(16, 32, 48, 64, 128, 256)

    fun appIconResources(): List<String> =
        APP_ICON_SIZES.map { size -> IMAGE_ROOT + "icon-$size.png" }

    // ---- overlay (in-window dialogs over the main screen) ----
    const val OVERLAY_BLUR_RADIUS = 16.0
    const val OVERLAY_BRIGHTNESS = -0.3

    /** Applied to the main content while an overlay dialog is shown. */
    fun overlayBackdropEffect(): Effect = GaussianBlur(OVERLAY_BLUR_RADIUS).apply {
        input = ColorAdjust(0.0, -0.3, OVERLAY_BRIGHTNESS, 0.0)
    }

    /** Game art behind the main/settings content: desaturated and slightly darkened. */
    fun gameArtEffect(): Effect = ColorAdjust(0.0, -1.0, -0.15, 0.05)

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
    /**
     * Body font: Manrope (SIL OFL 1.1, Cyrillic) replaces the commercial Circe. Europe is still the display font.
     * Roboto Regular is the small meta text of every theme (.font-meta: online count in the games list).
     */
    private val FONT_FILES = listOf("Manrope-Light.ttf", "Manrope-Bold.ttf", "europe.ttf", "Roboto-Regular.ttf")

    /** Theme without -Dlauncher.theme: the red STALZONE look (exbo / shadcn stay available through the property). */
    const val DEFAULT_THEME = "zone"

    private val requestedTheme: String = System.getProperty("launcher.theme", DEFAULT_THEME).lowercase()

    /**
     * Dark theme: "zone" (STALZONE, default), "exbo" (original EXBO colors) or "shadcn" (shadcn/ui dark neutral).
     * Legacy -Dlauncher.theme=zone-light is treated as "zone".
     */
    var themeName: String = if (requestedTheme == "zone-light") "zone" else requestedTheme
        internal set

    /** Registers bundled fonts so CSS can refer to them by family name. Returns loaded family names. */
    fun loadFonts(): List<String> = FONT_FILES.mapNotNull { file ->
        Theme::class.java.getResourceAsStream(STYLE_ROOT + "font/" + file)?.use { Font.loadFont(it, 12.0)?.family }
    }

    fun resource(path: String): URL =
        requireNotNull(Theme::class.java.getResource(UI_ROOT + path)) { "Missing UI resource: $path" }

    fun stylesheet(name: String): String = resource("style/$name").toExternalForm()

    /**
     * "zone" theme fonts, as on stalzone.com (site CSS: h1-h5 and the ВОЙТИ / ИГРАТЬ buttons use --beb "Bebas Neue",
     * body and caps menu items use --font-body "Roboto"): Bebas Neue Regular (Dharma Type, Cyrillic, SIL OFL 1.1)
     * for headings and big buttons, Roboto (SIL OFL 1.1) for text. Registered once, on first use.
     */
    private val ZONE_FONT_FILES = listOf(
        "BebasNeue-Regular.ttf",
        "Roboto-Light.ttf", "Roboto-Regular.ttf", "Roboto-Medium.ttf",
    )
    val zoneFonts: List<String> by lazy {
        ZONE_FONT_FILES.mapNotNull { file ->
            Theme::class.java.getResourceAsStream(STYLE_ROOT + "font/" + file)?.use { Font.loadFont(it, 12.0)?.family }
        }
    }

    /**
     * theme.css must be on the Scene so that .root defines the color tokens for every subtree.
     * Optional theme overrides (-Dlauncher.theme=shadcn|zone) come right after it.
     */
    fun stylesheets(theme: String, lang: String): List<String> {
        val sheets = mutableListOf(stylesheet("theme.css"))
        if (theme == "shadcn") sheets += stylesheet("theme-shadcn.css")
        if (theme == "zone") {
            zoneFonts // fonts must be registered before the stylesheet names them
            sheets += stylesheet("theme-zone.css")
        }
        sheets += stylesheet(if (lang == "en") "lang_en.css" else "lang_ru.css")
        return sheets
    }

    fun apply(scene: Scene, lang: String) {
        scene.stylesheets.setAll(stylesheets(themeName, lang))
    }
}

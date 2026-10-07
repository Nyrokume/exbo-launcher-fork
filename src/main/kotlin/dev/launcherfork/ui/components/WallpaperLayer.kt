package dev.launcherfork.ui.components

import dev.launcherfork.wallpaper.CoverFit
import dev.launcherfork.wallpaper.Wallpaper
import dev.launcherfork.wallpaper.Wallpapers
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyBooleanWrapper
import javafx.geometry.Rectangle2D
import javafx.scene.Node
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.Region

/**
 * Window background: a wallpaper ([Wallpaper]) cover-fit to the layer (fills it, keeps the picture's aspect ratio,
 * crops around the wallpaper's focus point; [CoverFit]) on every size, with the scrim (a Region with
 * [scrimStyleClass], coloured by the theme) on top. The layer has no size of its own (never enlarges the window), takes
 * no clicks and goes at the BOTTOM of a StackPane root.
 *
 * The picture loads in the background ([show]); until it is there, or if it cannot be loaded (missing resource, broken
 * file), the layer is empty, the scrim is hidden and the attached node ([attach]) does not get [STATE_CLASS], so its
 * plain theme background stays. When it is loaded the attached node gets [STATE_CLASS] (`wallpaper-on`), and the
 * stylesheet makes its panels translucent (layout.css, «wallpaper»). Colours: theme tokens only.
 *
 * Reuse on another window: `WallpaperLayer.forCurrent(target = root, scrimStyleClass = "my-scrim")?.let { root.children.add(0, it) }`
 * shows this launch's wallpaper ([Wallpapers.current]). The login window does not use it.
 */
class WallpaperLayer(scrimStyleClass: String = "wallpaper-scrim") : Region() {
    val imageView = ImageView().apply { isSmooth = true; isPreserveRatio = false; isManaged = false }
    val scrim = Region().apply { styleClass += scrimStyleClass; isVisible = false; isManaged = false }

    /** The wallpaper asked for by the last [show] (also while it loads or when it failed). */
    var wallpaper: Wallpaper? = null
        private set

    private val loaded = ReadOnlyBooleanWrapper(this, "loaded", false)
    /** True once the picture is decoded and shown (scrim on); false while loading and after a failure. */
    val loadedProperty: ReadOnlyBooleanProperty get() = loaded.readOnlyProperty
    val isLoaded: Boolean get() = loaded.get()

    init {
        id = "wallpaper"
        styleClass += "wallpaper-layer"
        isMouseTransparent = true
        isFocusTraversable = false
        children.addAll(imageView, scrim)
        loaded.addListener { _, _, on -> scrim.isVisible = on; imageView.isVisible = on; restyle() }
    }

    /**
     * [node] gets [STATE_CLASS] exactly while the picture is shown, plus [BRIGHT_CLASS] while that picture is a bright
     * one (the stylesheet picks the stronger scrim for it).
     */
    fun attach(node: Node) {
        attached += node
        restyle()
    }

    private val attached = mutableListOf<Node>()

    private fun restyle() {
        attached.forEach { node ->
            node.toggleClass(STATE_CLASS, isLoaded)
            node.toggleClass(BRIGHT_CLASS, isLoaded && wallpaper?.bright == true)
        }
        scrim.toggleClass(BRIGHT_CLASS, isLoaded && wallpaper?.bright == true)
    }

    /**
     * Loads [wallpaper]; [background] = true (the app) decodes it off the FX thread and shows it when done, false
     * (tests, screenshots) loads it right here. Failures are logged and leave the plain background.
     */
    fun show(wallpaper: Wallpaper, background: Boolean = true) {
        this.wallpaper = wallpaper
        val url = Wallpapers.url(wallpaper)
        if (url == null) {
            fail(wallpaper, "missing resource ${wallpaper.resource}")
            return
        }
        val image = try {
            Image(url.toExternalForm(), background)
        } catch (e: RuntimeException) {
            fail(wallpaper, e.toString())
            return
        }
        fun settle() {
            if (this.wallpaper !== wallpaper) return // a newer show() won
            if (image.isError || image.width <= 0) fail(wallpaper, image.exception?.toString() ?: "not a picture")
            else {
                imageView.image = image
                loaded.set(true)
                restyle() // a new picture of another tone while already loaded
                requestLayout()
            }
        }
        if (!background || image.progress >= 1.0 || image.isError) settle()
        else {
            image.progressProperty().addListener { _, _, p -> if (p.toDouble() >= 1.0) settle() }
            image.errorProperty().addListener { _, _, err -> if (err) settle() }
        }
    }

    private fun fail(wallpaper: Wallpaper, why: String) {
        System.err.println("[launcher-fork] WARNING: wallpaper %02d not shown, plain background: %s".format(wallpaper.number, why))
        imageView.image = null
        loaded.set(false)
    }

    override fun layoutChildren() {
        val w = width
        val h = height
        scrim.resizeRelocate(0.0, 0.0, w, h)
        val image = imageView.image ?: return
        val wp = wallpaper ?: return
        val crop = CoverFit.crop(image.width, image.height, w, h, wp.focusX, wp.focusY)
        imageView.viewport = Rectangle2D(crop.x, crop.y, crop.width, crop.height)
        imageView.fitWidth = w
        imageView.fitHeight = h
        imageView.relocate(0.0, 0.0)
    }

    // no size of its own: the window decides
    override fun computeMinWidth(height: Double) = 0.0
    override fun computeMinHeight(width: Double) = 0.0
    override fun computePrefWidth(height: Double) = 0.0
    override fun computePrefHeight(width: Double) = 0.0

    companion object {
        /** Style class of the node given to [attach] while a wallpaper is shown. */
        const val STATE_CLASS = "wallpaper-on"
        /** Style class of the attached node and of the scrim while the shown picture is a bright one. */
        const val BRIGHT_CLASS = "wallpaper-bright"

        /**
         * Background node for this launch's wallpaper ([Wallpapers.current]), already loading; null when none was chosen
         * (tests) or nothing is bundled. [target] (usually the window root) gets [STATE_CLASS] once the picture shows.
         */
        fun forCurrent(target: Node?, scrimStyleClass: String = "wallpaper-scrim", background: Boolean = true): WallpaperLayer? {
            val wallpaper = Wallpapers.current ?: return null
            return WallpaperLayer(scrimStyleClass).apply {
                target?.let(::attach)
                show(wallpaper, background)
            }
        }
    }
}

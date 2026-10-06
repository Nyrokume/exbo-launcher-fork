package dev.launcherfork.ui.components

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Navigator
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyBooleanWrapper
import javafx.geometry.Bounds
import javafx.geometry.Rectangle2D
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.stage.Screen
import javafx.stage.Stage

/** Undecorated-window helpers shared by both windows. */
object WindowChrome {
    /** Legacy auth/update panels still carry their own close/minimize buttons. */
    fun wireButtons(navigator: Navigator, close: Button?, hide: Button?) {
        close?.setOnAction { navigator.close() }
        hide?.setOnAction { navigator.minimize() }
    }
}

/** Border zone of an undecorated window that resizes it. */
enum class ResizeEdge(val cursor: Cursor, val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) {
    N(Cursor.N_RESIZE, false, true, false, false),
    S(Cursor.S_RESIZE, false, false, false, true),
    E(Cursor.E_RESIZE, false, false, true, false),
    W(Cursor.W_RESIZE, true, false, false, false),
    NE(Cursor.NE_RESIZE, false, true, true, false),
    NW(Cursor.NW_RESIZE, true, true, false, false),
    SE(Cursor.SE_RESIZE, false, false, true, true),
    SW(Cursor.SW_RESIZE, true, false, false, true),
    ;

    companion object {
        /**
         * Which edge (if any) the scene point (x, y) is on, for a w x h scene and a [margin] px border.
         * Points inside any of [exclusions] (scene bounds of the title-bar buttons) are never an edge, so the
         * resize zone does not steal clicks on minimize / maximize / close in the corner.
         */
        fun at(
            x: Double, y: Double, w: Double, h: Double, margin: Double = Theme.RESIZE_MARGIN,
            exclusions: List<Bounds> = emptyList(),
        ): ResizeEdge? {
            if (exclusions.any { it.contains(x, y) }) return null
            val l = x < margin
            val r = x > w - margin
            val t = y < margin
            val b = y > h - margin
            return when {
                t && l -> NW
                t && r -> NE
                b && l -> SW
                b && r -> SE
                t -> N
                b -> S
                l -> W
                r -> E
                else -> null
            }
        }
    }

    /** New window bounds after dragging this edge by (dx, dy) from [start]; never smaller than minW x minH. */
    fun resize(start: Rectangle2D, dx: Double, dy: Double, minW: Double, minH: Double): Rectangle2D {
        var x = start.minX
        var y = start.minY
        var w = start.width
        var h = start.height
        if (right) w = maxOf(minW, start.width + dx)
        if (bottom) h = maxOf(minH, start.height + dy)
        if (left) {
            w = maxOf(minW, start.width - dx)
            x = start.maxX - w
        }
        if (top) {
            h = maxOf(minH, start.height - dy)
            y = start.maxY - h
        }
        return Rectangle2D(x, y, w, h)
    }
}

/**
 * Frame behaviour of the single undecorated launcher window: drag by the title bar, double-click to maximize,
 * resize by the edges. Maximize is done by hand (screen visual bounds) so it also works without a window manager.
 */
class WindowFrame(private val stage: Stage) {
    companion object {
        /** Visual bounds of the screen that holds most of [r] (primary screen if none). */
        fun screenBoundsOf(r: Rectangle2D): Rectangle2D {
            val screens = Screen.getScreensForRectangle(r.minX, r.minY, r.width.coerceAtLeast(1.0), r.height.coerceAtLeast(1.0))
            return (screens.firstOrNull() ?: Screen.getPrimary()).visualBounds
        }

        /** Top-left corner that centers a w x h window in [screen]. */
        fun centeredIn(screen: Rectangle2D, w: Double, h: Double): Pair<Double, Double> =
            screen.minX + (screen.width - w) / 2 to screen.minY + (screen.height - h) / 2
    }

    /** Current bounds of the window. */
    val bounds: Rectangle2D get() = Rectangle2D(stage.x, stage.y, stage.width.coerceAtLeast(0.0), stage.height.coerceAtLeast(0.0))

    /** Centers the window (at w x h) on the screen that holds [reference], e.g. where the other window was. */
    fun centerOnScreenOf(reference: Rectangle2D, w: Double, h: Double) {
        val (x, y) = centeredIn(screenBoundsOf(reference), w, h)
        stage.x = x
        stage.y = y
    }

    private var restoreBounds: Rectangle2D? = null
    private val maximized = ReadOnlyBooleanWrapper(false)
    val maximizedProperty: ReadOnlyBooleanProperty get() = maximized.readOnlyProperty
    val isMaximized: Boolean get() = maximized.get()

    fun toggleMaximize() = if (isMaximized) restore() else maximize()

    fun maximize() {
        if (isMaximized) return
        restoreBounds = Rectangle2D(stage.x, stage.y, stage.width, stage.height)
        setBounds(screenBoundsOf(bounds))
        maximized.set(true)
    }

    fun restore() {
        val r = restoreBounds ?: return
        restoreBounds = null
        setBounds(r)
        maximized.set(false)
    }

    private fun setBounds(r: Rectangle2D) {
        stage.x = r.minX
        stage.y = r.minY
        stage.width = r.width
        stage.height = r.height
    }

    /** [area] (the free part of the title bar) moves the window; a double click toggles maximize if [maximizable]. */
    fun installDrag(area: Node, maximizable: Boolean = true) {
        var dx = 0.0
        var dy = 0.0
        area.addEventHandler(MouseEvent.MOUSE_PRESSED) { e ->
            dx = stage.x - e.screenX
            dy = stage.y - e.screenY
        }
        area.addEventHandler(MouseEvent.MOUSE_DRAGGED) { e ->
            if (e.button != MouseButton.PRIMARY) return@addEventHandler
            if (maximizable && isMaximized) {
                // un-maximize under the cursor, keeping the grab point proportional
                val ratio = (e.screenX - stage.x) / stage.width
                restore()
                dx = -stage.width * ratio
                dy = -e.sceneY
            }
            stage.x = e.screenX + dx
            stage.y = e.screenY + dy
        }
        area.addEventHandler(MouseEvent.MOUSE_CLICKED) { e ->
            if (maximizable && e.button == MouseButton.PRIMARY && e.clickCount == 2) toggleMaximize()
        }
    }

    /**
     * Edge resizing via scene-level filters (they run before any control sees the press). The [excluded] nodes
     * (window buttons) keep their clicks: their scene bounds are cut out of the edge zone.
     */
    fun installResize(scene: Scene, margin: Double = Theme.RESIZE_MARGIN, excluded: () -> List<Node> = { emptyList() }) {
        var active: ResizeEdge? = null
        var start = Rectangle2D.EMPTY
        var sx = 0.0
        var sy = 0.0
        fun edgeAt(e: MouseEvent) = if (isMaximized) null else ResizeEdge.at(
            e.sceneX, e.sceneY, scene.width, scene.height, margin,
            excluded().filter { it.isVisible && it.scene === scene }.map { it.localToScene(it.layoutBounds) },
        )
        scene.addEventFilter(MouseEvent.MOUSE_MOVED) { e -> scene.cursor = edgeAt(e)?.cursor }
        scene.addEventFilter(MouseEvent.MOUSE_EXITED_TARGET) { if (active == null) scene.cursor = null }
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED) { e ->
            active = edgeAt(e)?.takeIf { e.button == MouseButton.PRIMARY }
            if (active != null) {
                start = Rectangle2D(stage.x, stage.y, stage.width, stage.height)
                sx = e.screenX
                sy = e.screenY
                e.consume()
            }
        }
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED) { e ->
            val edge = active ?: return@addEventFilter
            setBounds(edge.resize(start, e.screenX - sx, e.screenY - sy, stage.minWidth, stage.minHeight))
            e.consume()
        }
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED) { e ->
            if (active != null) {
                active = null
                e.consume()
            }
        }
    }
}

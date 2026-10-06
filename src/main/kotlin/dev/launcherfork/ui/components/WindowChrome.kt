package dev.launcherfork.ui.components

import dev.launcherfork.theme.Theme
import dev.launcherfork.ui.Navigator
import javafx.beans.property.ReadOnlyBooleanProperty
import javafx.beans.property.ReadOnlyBooleanWrapper
import javafx.geometry.Rectangle2D
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.stage.Screen
import javafx.stage.Stage

/** Undecorated-window helpers shared by every screen. */
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
        /** Which edge (if any) the scene point (x, y) is on, for a w x h scene and a [margin] px border. */
        fun at(x: Double, y: Double, w: Double, h: Double, margin: Double = Theme.RESIZE_MARGIN): ResizeEdge? {
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
    private var restoreBounds: Rectangle2D? = null
    private val maximized = ReadOnlyBooleanWrapper(false)
    val maximizedProperty: ReadOnlyBooleanProperty get() = maximized.readOnlyProperty
    val isMaximized: Boolean get() = maximized.get()

    fun toggleMaximize() = if (isMaximized) restore() else maximize()

    fun maximize() {
        if (isMaximized) return
        restoreBounds = Rectangle2D(stage.x, stage.y, stage.width, stage.height)
        val screens = Screen.getScreensForRectangle(stage.x, stage.y, stage.width.coerceAtLeast(1.0), stage.height.coerceAtLeast(1.0))
        val vb = (screens.firstOrNull() ?: Screen.getPrimary()).visualBounds
        setBounds(vb)
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

    /** [area] (the free part of the title bar) moves the window; a double click toggles maximize. */
    fun installDrag(area: Node) {
        var dx = 0.0
        var dy = 0.0
        area.addEventHandler(MouseEvent.MOUSE_PRESSED) { e ->
            dx = stage.x - e.screenX
            dy = stage.y - e.screenY
        }
        area.addEventHandler(MouseEvent.MOUSE_DRAGGED) { e ->
            if (e.button != MouseButton.PRIMARY) return@addEventHandler
            if (isMaximized) {
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
            if (e.button == MouseButton.PRIMARY && e.clickCount == 2) toggleMaximize()
        }
    }

    /** Edge resizing via scene-level filters (they run before any control sees the press). */
    fun installResize(scene: Scene, margin: Double = Theme.RESIZE_MARGIN) {
        var active: ResizeEdge? = null
        var start = Rectangle2D.EMPTY
        var sx = 0.0
        var sy = 0.0
        fun edgeAt(e: MouseEvent) = if (isMaximized) null else ResizeEdge.at(e.sceneX, e.sceneY, scene.width, scene.height, margin)
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

package dev.launcherfork.ui.components

import dev.launcherfork.ui.Navigator
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.input.MouseEvent

/** Undecorated-window helpers: custom close/minimize buttons and dragging by a header polygon. */
object WindowChrome {
    fun wireButtons(navigator: Navigator, close: Button?, hide: Button?) {
        close?.setOnAction { navigator.close() }
        hide?.setOnAction { navigator.minimize() }
    }

    fun installDrag(scene: Scene, polygon: (Double) -> List<Pair<Double, Double>>) {
        var dx = 0.0
        var dy = 0.0
        var dragging = false
        scene.addEventHandler(MouseEvent.MOUSE_PRESSED) { e ->
            val window = scene.window ?: return@addEventHandler
            dragging = isPointInPolygon(e.sceneX, e.sceneY, polygon(scene.width))
            dx = window.x - e.screenX
            dy = window.y - e.screenY
        }
        scene.addEventHandler(MouseEvent.MOUSE_DRAGGED) { e ->
            val window = scene.window ?: return@addEventHandler
            if (dragging) {
                window.x = e.screenX + dx
                window.y = e.screenY + dy
            }
        }
        scene.addEventHandler(MouseEvent.MOUSE_RELEASED) { dragging = false }
    }

    /** Ray casting point-in-polygon. */
    fun isPointInPolygon(x: Double, y: Double, v: List<Pair<Double, Double>>): Boolean {
        var inside = false
        var j = v.size - 1
        for (i in v.indices) {
            val (xi, yi) = v[i]
            val (xj, yj) = v[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}

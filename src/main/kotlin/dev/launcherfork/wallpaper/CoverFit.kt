package dev.launcherfork.wallpaper

import kotlin.math.max

/**
 * Cover-fit (CSS `background-size: cover`) with a focus point: the part of the picture that fills a box completely
 * while keeping the picture's aspect ratio. The crop has the box's aspect ratio, is as large as the picture allows
 * (one side is the whole picture side) and is centred on the focus point, moved back inside the picture at the edges.
 */
object CoverFit {
    /** Rectangle of the picture in picture pixels (an ImageView viewport). */
    data class Crop(val x: Double, val y: Double, val width: Double, val height: Double)

    fun crop(imageW: Double, imageH: Double, boxW: Double, boxH: Double, focusX: Double = 0.5, focusY: Double = 0.5): Crop {
        if (boxW <= 0 || boxH <= 0 || imageW <= 0 || imageH <= 0) return Crop(0.0, 0.0, max(imageW, 0.0), max(imageH, 0.0))
        val scale = max(boxW / imageW, boxH / imageH)
        val w = (boxW / scale).coerceAtMost(imageW)
        val h = (boxH / scale).coerceAtMost(imageH)
        val x = (focusX.coerceIn(0.0, 1.0) * imageW - w / 2).coerceIn(0.0, imageW - w)
        val y = (focusY.coerceIn(0.0, 1.0) * imageH - h / 2).coerceIn(0.0, imageH - h)
        return Crop(x, y, w, h)
    }

    /** Box x of the picture column [imageX] under [crop] scaled into a box [boxW] wide. */
    fun toBox(crop: Crop, boxW: Double, imageX: Double): Double = (imageX - crop.x) / crop.width * boxW
}

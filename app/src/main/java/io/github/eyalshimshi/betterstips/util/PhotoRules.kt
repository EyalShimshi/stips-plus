package io.github.eyalshimshi.betterstips.util

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The profile photo (#46): which square of the picked photo the crop screen shows in its circle, and how Stips is told
 * about it. Pure.
 *
 * Stips keeps its own copy of an upload [LARGE_WIDTH] px wide (its "l" size) and reads the avatar's square
 * (`profile.set_thumbnail`) on that copy, not on the file sent. 0.22 sent the square of its 720 px photo, so the
 * avatar would have been a small photo in the corner of a black square (measured on the owner's account in #46).
 */
object PhotoRules {
    /** How wide Stips' copy of an upload is: the avatar's square is measured on it. */
    const val LARGE_WIDTH = 300

    /** The longest side of the photo the crop screen works on. */
    const val DECODE_MAX_SIDE = 1600

    /** The side of the square sent to Stips: its own copies are 300 px and smaller. */
    const val UPLOAD_SIDE = 600

    /** How far the crop screen zooms in, past just covering the circle. */
    const val MAX_ZOOM = 6f

    /** A square in an image's pixels. */
    data class Square(val x: Int, val y: Int, val side: Int)

    /**
     * `profile.set_thumbnail`'s x0, x1, y0, y1 for [square] of an upload [uploadWidth] px wide, on Stips' copy of it:
     * scaled down to [LARGE_WIDTH], never up, as the site does.
     */
    fun thumbnailBox(uploadWidth: Int, square: Square): IntArray {
        val ratio = min(1.0, LARGE_WIDTH.toDouble() / uploadWidth.coerceAtLeast(1))
        val x0 = (square.x * ratio).roundToInt()
        val y0 = (square.y * ratio).roundToInt()
        val side = (square.side * ratio).roundToInt().coerceAtLeast(1)
        return intArrayOf(x0, x0 + side, y0, y0 + side)
    }

    /** The scale at which an image [width] × [height] px just covers a circle [diameter] px across. */
    fun coverScale(width: Int, height: Int, diameter: Float): Float =
        max(diameter / width.coerceAtLeast(1), diameter / height.coerceAtLeast(1))

    /**
     * [pan] (from the circle's center to the image's, in screen px) kept small enough that the image, [imageSide] px
     * along that axis and drawn at [scale], still covers the circle.
     */
    fun clampPan(pan: Float, imageSide: Int, scale: Float, diameter: Float): Float {
        val room = ((imageSide * scale - diameter) / 2f).coerceAtLeast(0f)
        return pan.coerceIn(-room, room)
    }

    /**
     * The circle's square in the image's pixels: the image ([width] × [height]) drawn at [scale], with its center
     * [panX], [panY] screen px from the circle's center, and the circle [diameter] px across.
     */
    fun cropSquare(width: Int, height: Int, scale: Float, panX: Float, panY: Float, diameter: Float): Square {
        val side = (diameter / scale).roundToInt().coerceIn(1, max(1, min(width, height)))
        // From the image's left (top) edge to the circle's, in screen px, then in the image's.
        val left = (width * scale / 2f - panX - diameter / 2f) / scale
        val top = (height * scale / 2f - panY - diameter / 2f) / scale
        val x = left.roundToInt().coerceIn(0, (width - side).coerceAtLeast(0))
        val y = top.roundToInt().coerceIn(0, (height - side).coerceAtLeast(0))
        return Square(x, y, side)
    }
}

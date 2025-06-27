package ir.simurgh.photolib.utils.extensions

import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

fun RectF.perimeter(): Float = (width() + height()) * 2f

fun Rect.perimeter(): Int = (width() + height()) * 2


/**
 * Calculates the maximum bounding rectangle from an array of points.
 * This method finds the extents of a set of transformed points.
 *
 * @param array The array of points (x1,y1,x2,y2,...).
 */
fun RectF.setMaximumRect(array: FloatArray) {
    val minX =
        min(min(array[0], array[2]), min(array[4], array[6]))

    val minY =
        min(min(array[1], array[3]), min(array[5], array[7]))

    val maxX =
        max(max(array[0], array[2]), max(array[4], array[6]))

    val maxY =
        max(max(array[1], array[3]), max(array[5], array[7]))

    set(minX, minY, maxX, maxY)
}
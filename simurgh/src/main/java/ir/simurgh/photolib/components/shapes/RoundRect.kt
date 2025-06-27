package ir.simurgh.photolib.components.shapes

import android.graphics.Path
import android.graphics.RectF

/**
 * Draws a round rect with given corner radius.
 * @param radius an array of 4 pairs[X,Y] for corner radius. Radius is applied from top-left to top-right to bottom - right to bottom - left.
 */
class RoundRect(var radius: FloatArray) : BaseShape() {

    constructor(allRadius: Float) : this(
        floatArrayOf(
            allRadius,
            allRadius,
            allRadius,
            allRadius,
            allRadius,
            allRadius,
            allRadius,
            allRadius,
        )
    )

    private val allocatedRectF = RectF()
    override fun resize(width: Float, height: Float) {
        super.resize(width, height)

        allocatedRectF.set(0f, 0f, desiredWidth, desiredHeight)

        fPath.rewind()
        fPath.addRoundRect(allocatedRectF, radius, Path.Direction.CW)
    }

    override fun clone(): RoundRect {
        val rR = RoundRect(radius)
        rR.resize(desiredWidth, desiredHeight)
        return rR
    }
}
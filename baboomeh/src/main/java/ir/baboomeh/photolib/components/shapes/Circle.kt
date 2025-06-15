package ir.baboomeh.photolib.components.shapes

import android.graphics.Path
import kotlin.math.abs
import kotlin.math.min

class Circle : BaseShape() {

    override fun resize(width: Float, height: Float) {
        super.resize(width, height)

        val finalSize = min(desiredWidth, desiredHeight) / 2f
        fPath.rewind()
        fPath.addCircle(finalSize, finalSize, abs(finalSize), Path.Direction.CW)
    }

    override fun clone(): Circle {
        val c = Circle()
        c.resize(desiredWidth, desiredHeight)
        return c
    }
}
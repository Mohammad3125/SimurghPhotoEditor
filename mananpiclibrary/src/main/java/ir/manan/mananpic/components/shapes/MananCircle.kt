package ir.manan.mananpic.components.shapes

import android.graphics.Path
import kotlin.math.min

class MananCircle : MananBaseShape() {

    override fun resize(width: Float, height: Float) {
        super.resize(width, height)

        val finalSize = min(desiredWidth, desiredHeight) / 2f
        fPath.rewind()
        fPath.addCircle(finalSize, finalSize, finalSize, Path.Direction.CW)
    }

    override fun clone(): MananCircle {
        val c = MananCircle()
        c.resize(desiredWidth, desiredHeight)
        return c
    }
}
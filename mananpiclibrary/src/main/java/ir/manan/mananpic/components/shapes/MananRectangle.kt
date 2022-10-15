package ir.manan.mananpic.components.shapes

import android.graphics.Path

class MananRectangle : MananBaseShape() {
    override fun resize(width: Float, height: Float) {
        super.resize(width, height)
        fPath.rewind()
        fPath.addRect(0f, 0f, desiredWidth, desiredHeight, Path.Direction.CW)
    }

    override fun clone(): MananRectangle {
        val r = MananRectangle()
        r.resize(desiredWidth, desiredHeight)
        return r
    }
}
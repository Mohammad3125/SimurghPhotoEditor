package ir.manan.mananpic.components.shapes

import android.graphics.Path

class MananRectangle : MananBaseShape() {
    override fun resize(width: Float, height: Float) {
        super.resize(width, height)
        fPath.rewind()
        if (desiredWidth < 0f && desiredHeight < 0f) {
            fPath.addRect(desiredWidth, desiredHeight, 0f, 0f, Path.Direction.CW)
        } else if (desiredWidth < 0f && desiredHeight > 0f) {
            fPath.addRect(desiredWidth, 0f, 0f, desiredHeight, Path.Direction.CW)
        } else if (desiredWidth > 0f && desiredHeight < 0f) {
            fPath.addRect(0f, desiredHeight, desiredWidth, 0f, Path.Direction.CW)
        } else {
            fPath.addRect(0f, 0f, desiredWidth, desiredHeight, Path.Direction.CW)
        }
    }

    override fun clone(): MananRectangle {
        val r = MananRectangle()
        r.resize(desiredWidth, desiredHeight)
        return r
    }
}
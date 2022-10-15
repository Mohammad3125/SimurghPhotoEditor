package ir.manan.mananpic.components.shapes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

open class MananBaseShape : MananShape() {

    protected val fPath = Path()

    protected var desiredWidth = 0f
    protected var desiredHeight = 0f

    protected var isPathResized = false

    override fun resize(width: Float, height: Float) {
        desiredWidth = width
        desiredHeight = height

        isPathResized = true
    }

    override fun draw(canvas: Canvas, paint: Paint) {
        canvas.drawPath(fPath, paint)
    }

    override fun getPath(): Path {
        if (!isPathResized) throw IllegalStateException("Shape should be resized before calling converting to path")

        return fPath
    }

    override fun clone(): MananShape {
        return super.clone() as MananShape
    }
}
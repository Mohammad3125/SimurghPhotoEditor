package ir.manan.mananpic.components.shapes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

abstract class MananShape {
    /**
     * makes a boundary for shape.
     */
    abstract fun resize(width: Float, height: Float)

    /**
     * Draws the shape on a canvas.
     * @param canvas Canvas to draw shape on.
     * @param paint Paint used to draw the shape on canvas.
     */
    abstract fun draw(canvas: Canvas, paint: Paint)

    /**
     * Returns a path representing the shape.
     */
    abstract fun getPath(): Path

    open fun clone(): MananShape {
        throw IllegalStateException("Cannot clone an abstract class [${javaClass.name}]")
    }
}
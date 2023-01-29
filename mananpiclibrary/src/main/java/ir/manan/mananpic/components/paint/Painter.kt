package ir.manan.mananpic.components.paint

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import ir.manan.mananpic.components.selection.selectors.BrushSelector
import ir.manan.mananpic.components.selection.selectors.Selector
import ir.manan.mananpic.utils.MananMatrix

abstract class Painter {
    private var invalidateListener: Selector.OnDispatchToInvalidate? = null

    fun setOnInvalidateListener(listener: Selector.OnDispatchToInvalidate) {
        invalidateListener = listener
    }

    fun invalidate() {
        invalidateListener?.invalidateDrawings()
    }

    abstract fun initialize(
        matrix: MananMatrix,
        bounds: RectF,
    )

    /**
     * Called when user starts to move his/her finger on screen.
     * A selector might use it to draw something.
     * @param initialX Coordinate of current x.
     * @param initialY Coordinate of current y.
     */
    abstract fun onMoveBegin(initialX: Float, initialY: Float)

    /**
     * Called when user is currently moving his/her finger on screen.
     * A selector might use it to draw something.
     * @param dx Delta x.
     * @param dy Delta y.
     * @param ex Exact location of current x.
     * @param ey Exact location of current y.
     */
    abstract fun onMove(dx: Float, dy: Float, ex: Float, ey: Float)

    /**
     * Called when user raises his/her finger on screen.
     * A selector might use it to draw something.
     * @param lastX Exact location of last x user touched.
     * @param lastY Exact location of last y user touched.
     */
    abstract fun onMoveEnded(lastX: Float, lastY: Float)

    /**
     * Draws any content that a selector might draw; for example [BrushSelector] draws circle indicating
     * interested areas in image.
     * @param canvas Canvas that selector draws content on.
     */
    abstract fun draw(canvas: Canvas)


    abstract fun resetPaint()


    abstract fun onSizeChanged(newBounds: RectF, changeMatrix: Matrix)


    abstract fun onLayerChanged(layer: PaintLayer?)

}
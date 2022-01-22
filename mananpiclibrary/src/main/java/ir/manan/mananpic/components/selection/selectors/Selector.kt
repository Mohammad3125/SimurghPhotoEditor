package ir.manan.mananpic.components.selection.selectors

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Base class for selection tools.
 */
abstract class Selector {

    var invalidateListener: OnDispatchToInvalidate? = null

    /**
     * Initializes the selector.
     * @param bounds Bounds of visible image.
     * @param matrix Transformation Matrix of target canvas (avoid changing it. It's only for reading matrix values.)
     * @param context Context of view. A selector might use it to use methods such as [ir.manan.mananpic.utils.dp] and such.
     */
    abstract fun initialize(context: Context, matrix: Matrix?, bounds: RectF)

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
     * Selects(Crops/Clips) the selected area by user.
     * @param drawable That is going to be clipped/cropped.
     */
    abstract fun select(drawable: Drawable): Bitmap?

    /**
     * Draws any content that a selector might draw; for example [BrushSelector] draws circle indicating
     * interested areas in image.
     * @param canvas Canvas that selector draws content on.
     */
    abstract fun draw(canvas: Canvas?)

    /**
     * Resets current selection if there is any.
     */
    abstract fun resetSelection()

    /**
     * Determines if the selection is closed or not.
     * A closed selection is ready to be cropper/clipped.
     */
    abstract fun isClosed(): Boolean

    interface OnDispatchToInvalidate {
        fun invalidateDrawings()
    }
}
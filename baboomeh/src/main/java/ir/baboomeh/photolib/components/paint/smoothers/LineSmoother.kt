package ir.baboomeh.photolib.components.paint.smoothers

import ir.baboomeh.photolib.components.paint.painters.brushpaint.brushes.Brush
import ir.baboomeh.photolib.utils.gesture.TouchData

abstract class LineSmoother {
    var onDrawPoint: OnDrawPoint? = null

    abstract fun setFirstPoint(touchData: TouchData, brush: Brush)
    abstract fun addPoints(touchData: TouchData, brush: Brush)
    abstract fun setLastPoint(touchData: TouchData, brush: Brush)

    interface OnDrawPoint {
        fun onDrawPoint(
            ex: Float,
            ey: Float,
            angleDirection: Float,
            totalDrawCount: Int,
            isLastPoint: Boolean
        )
    }

}
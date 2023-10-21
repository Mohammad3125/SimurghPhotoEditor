package ir.manan.mananpic.components.paint.smoothers

import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush

abstract class LineSmoother {
    var onDrawPoint: OnDrawPoint? = null

    abstract fun setFirstPoint(ex: Float, ey: Float, brush: Brush)
    abstract fun addPoints(ex: Float, ey: Float, brush: Brush)
    abstract fun setLastPoint(ex: Float, ey: Float, brush: Brush)

    interface OnDrawPoint {
        fun onDrawPoint(ex: Float, ey: Float, angleDirection: Float, isLastPoint: Boolean)
    }

}
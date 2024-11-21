package ir.manan.mananpic.components.paint.smoothers

import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.components.paint.paintview.MananPaintView

abstract class LineSmoother {
    var onDrawPoint: OnDrawPoint? = null

    abstract fun setFirstPoint(touchData: MananPaintView.TouchData, brush: Brush)
    abstract fun addPoints(touchData: MananPaintView.TouchData, brush: Brush)
    abstract fun setLastPoint(touchData: MananPaintView.TouchData, brush: Brush)

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
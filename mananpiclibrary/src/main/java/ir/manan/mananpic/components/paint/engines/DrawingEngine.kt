package ir.manan.mananpic.components.paint.engines

import android.graphics.Canvas
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush

interface DrawingEngine {
    fun draw(ex: Float, ey: Float, directionalAngle: Float, canvas: Canvas, brush: Brush)
    fun onMoveBegin(ex: Float, ey: Float, brush: Brush)

    fun onMove(ex: Float, ey: Float, dx: Float, dy: Float, brush: Brush)

    fun onMoveEnded(ex: Float, ey: Float, brush: Brush)
}
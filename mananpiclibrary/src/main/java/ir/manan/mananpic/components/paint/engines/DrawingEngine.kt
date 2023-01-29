package ir.manan.mananpic.components.paint.engines

import android.graphics.Canvas
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush

interface DrawingEngine {
    fun draw(ex: Float, ey: Float, canvas: Canvas, brush: Brush)
}
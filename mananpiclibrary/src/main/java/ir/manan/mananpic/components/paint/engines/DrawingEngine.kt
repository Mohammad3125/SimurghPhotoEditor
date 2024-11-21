package ir.manan.mananpic.components.paint.engines

import android.graphics.Canvas
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.components.paint.paintview.MananPaintView

interface DrawingEngine {
    fun draw(
        ex: Float,
        ey: Float,
        directionalAngle: Float,
        canvas: Canvas,
        brush: Brush,
        drawCount: Int
    )

    fun onMoveBegin(touchData: MananPaintView.TouchData, brush: Brush)

    fun onMove(touchData: MananPaintView.TouchData, brush: Brush)

    fun onMoveEnded(touchData: MananPaintView.TouchData, brush: Brush)
}
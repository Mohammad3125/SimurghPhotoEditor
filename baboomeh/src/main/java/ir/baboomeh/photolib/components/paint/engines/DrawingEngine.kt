package ir.baboomeh.photolib.components.paint.engines

import android.graphics.Canvas
import ir.baboomeh.photolib.components.paint.painters.brushpaint.brushes.Brush
import ir.baboomeh.photolib.utils.gesture.TouchData

interface DrawingEngine {
    fun draw(
        ex: Float,
        ey: Float,
        directionalAngle: Float,
        canvas: Canvas,
        brush: Brush,
        drawCount: Int
    )

    fun onMoveBegin(touchData: TouchData, brush: Brush)

    fun onMove(touchData: TouchData, brush: Brush)

    fun onMoveEnded(touchData: TouchData, brush: Brush)

    fun setEraserMode(isEnabled: Boolean)

    fun isEraserModeEnabled(): Boolean
}
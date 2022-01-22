package ir.manan.mananpic.components.selection.selectors

import android.content.Context
import android.graphics.*

class BrushSelector : PathBasedSelector() {

    // Paint for circles in path.
    private val brushPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    /**
     * Brush size. Default is 32px.
     * View will get invalidated after setting this value.
     */
    var brushSize = 32f
        set(value) {
            brushPaint.strokeWidth = value
            field = value
            invalidateListener?.invalidateDrawings()
        }

    /**
     * Brush color. Default is [Color.BLACK]
     */
    var brushColor = Color.BLACK
        set(value) {
            brushPaint.color = value
            brushPaint.alpha = brushAlpha
            field = value
            invalidateListener?.invalidateDrawings()
        }

    /**
     * Brush alpha. Default values is 128
     * Value should be between 0 and 255. If value exceeds
     * the range then nearest allowable value is replaced.
     */
    var brushAlpha = 128
        set(value) {
            val finalVal = if (value > 255) 255 else if (value < 0) 0 else value
            brushPaint.alpha = finalVal
            field = finalVal
            invalidateListener?.invalidateDrawings()
        }

    override fun initialize(context: Context, bounds: RectF) {
        super.initialize(context, bounds)
        brushPaint.alpha = brushAlpha
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {
        drawCircles(initialX, initialY)
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float) {
        drawCircles(ex, ey)
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
    }

    private fun drawCircles(touchX: Float, touchY: Float) {
        path.addCircle(touchX, touchY, brushSize, Path.Direction.CW)
        invalidateListener?.invalidateDrawings()
        isPathClose = true
    }

    override fun draw(canvas: Canvas?) {
        canvas?.run {
            // Draw circles.
            drawPath(path, brushPaint)
        }
    }

    override fun resetSelection() {
        path.rewind()
        isPathClose = false
        invalidateListener?.invalidateDrawings()
    }
}
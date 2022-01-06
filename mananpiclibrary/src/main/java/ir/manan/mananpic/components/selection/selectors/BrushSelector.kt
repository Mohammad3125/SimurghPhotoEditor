package ir.manan.mananpic.components.selection.selectors

import android.graphics.*
import android.view.View

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
            view.invalidate()
        }

    /**
     * Brush color. Default is [Color.BLACK]
     * View will get invalidated after setting this value.
     */
    var brushColor = Color.BLACK
        set(value) {
            brushPaint.color = value
            brushPaint.alpha = brushAlpha
            field = value
            view.invalidate()
        }

    /**
     * Brush alpha. Default values is 128
     * View will get invalidated after setting this value.
     */
    var brushAlpha = 128
        set(value) {
            brushPaint.alpha = value
            field = value
            view.invalidate()
        }

    // Reference to view to invalidate it.
    private lateinit var view: View

    override fun initialize(view: View, bitmap: Bitmap?, bounds: RectF) {
        super.initialize(view, bitmap, bounds)

        this.view = view
        view.run {
            brushPaint.alpha = brushAlpha
        }
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
        view.invalidate()
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
        view.invalidate()
    }
}
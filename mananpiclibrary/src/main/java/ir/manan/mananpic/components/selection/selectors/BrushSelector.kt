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
            invalidate()
        }

    /**
     * Brush color. Default is [Color.BLACK]
     */
    var brushColor = Color.BLACK
        set(value) {
            brushPaint.color = value
            brushPaint.alpha = brushAlpha
            field = value
            invalidate()
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
            invalidate()
        }

    // Used to buffer last path that has been drawn to then be added to stack of paths.
    private val pathBuffer = Path()

    override fun initialize(context: Context, matrix: Matrix, bounds: RectF) {
        super.initialize(context, matrix, bounds)
        brushPaint.alpha = brushAlpha
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {

    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float) {
        drawCircles(ex, ey)
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        drawCircles(lastX, lastY)

        // If buffer is not empty add the current path to stack.
        if (!pathBuffer.isEmpty) {
            paths.add(Path(pathBuffer))
        }
        // Add current path to buffer to store it as last path and then add it to stack if
        // another path has been added. we do this to not have duplicate path at top of stack
        // which would mess up the undo operation.
        pathBuffer.set(Path(path))
    }

    private fun drawCircles(touchX: Float, touchY: Float) {
        path.addCircle(touchX, touchY, brushSize, Path.Direction.CW)
        invalidate()
        isPathClose = true
    }

    override fun draw(canvas: Canvas?) {
        canvas?.run {
            // Create a copy of path to later transform the transformed path to it.
            pathCopy.set(path)

            // Apply matrix to path.
            path.transform(canvasMatrix)

            // Draw the transformed path.
            drawPath(path, brushPaint)

            // Revert it back.
            path.set(pathCopy)

            // Reset the pathCopy to release memory.
            pathCopy.rewind()
        }
    }

    override fun undo() {
        paths.run {
            if (!isEmpty()) {
                path.set(pop())
                invalidate()
            } else {
                path.rewind()
                pathBuffer.rewind()
                isPathClose = false
                invalidate()
            }
        }
    }

    override fun resetSelection() {
        path.rewind()
        pathBuffer.rewind()
        paths.clear()
        isPathClose = false
        invalidate()
    }
}
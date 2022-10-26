package ir.manan.mananpic.components.selection.selectors

import android.content.Context
import android.graphics.*
import ir.manan.mananpic.utils.MananMatrix

/**
 * Selector that let's user brush an area to later select it.
 */
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

    override fun shouldParentTransformDrawings(): Boolean {
        return true
    }

    override fun initialize(context: Context, matrix: MananMatrix, bounds: RectF) {
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

        paths.add(Path(path))
    }

    private fun drawCircles(touchX: Float, touchY: Float) {
        path.addCircle(touchX, touchY, brushSize, Path.Direction.CCW)
        invalidate()
        isPathClose = true
    }

    override fun draw(canvas: Canvas?) {
        canvas?.run {
            // Draw the transformed path.
            if (isSelectionInverse) {
                path.fillType = Path.FillType.INVERSE_WINDING
                clipPath(path)
                pathCopy.rewind()
                pathCopy.addRect(leftEdge, topEdge, rightEdge, bottomEdge, Path.Direction.CCW)
                drawPath(pathCopy, brushPaint)
            } else {
                path.fillType = Path.FillType.WINDING
                drawPath(path, brushPaint)
            }
        }
    }

    override fun undo() {
        if (isSelectionInverse) {
            isSelectionInverse = false
            if (paths.isEmpty()) {
                invalidate()
            }
        }
        paths.run {
            if (isNotEmpty()) {
                pop()
                if (isNotEmpty()) {
                    path.set(peek())
                    invalidate()
                    return@run
                }
            }
            path.rewind()
            isPathClose = false
            invalidate()
        }
    }

    override fun getClipPath(): Path? {
        return if (isPathClose) {
            path
        } else {
            null
        }
    }

    override fun onSizeChanged(newBounds: RectF, changeMatrix: Matrix) {
        if (isClosed()) {
            path.transform(changeMatrix)
            invalidate()
        }

        leftEdge = newBounds.left
        topEdge = newBounds.top
        rightEdge = newBounds.right
        bottomEdge = newBounds.bottom
    }

    override fun resetSelection() {
        path.rewind()
        paths.clear()
        isPathClose = false
        invalidate()
    }
}
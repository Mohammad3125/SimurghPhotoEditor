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
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeMiter = 20f
            strokeWidth = 32f
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

    private var perv1x = 0f
    private var perv1y = 0f

    private var mid1x = 0f
    private var mid1y = 0f

    private var mid2x = 0f
    private var mid2y = 0f

    private var perv2x = 0f
    private var perv2y = 0f

    private var curX = 0f
    private var curY = 0f

    private var isFirstThreeCreated = false

    private var counter = 0

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
        if (!isFirstThreeCreated) {

            when (counter) {
                0 -> {
                    perv2x = ex
                    perv2y = ey
                }
                1 -> {
                    perv1x = ex
                    perv1y = ey
                }
                else -> {
                    curX = ex
                    curY = ey

                    calculateAndDrawQuad()

                    counter = 0

                    isFirstThreeCreated = true

                    return
                }
            }

            counter++
        } else {
            perv2x = perv1x
            perv2y = perv1y

            perv1x = curX
            perv1y = curY

            curX = ex
            curY = ey

            calculateAndDrawQuad()
        }
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        paths.add(Path(path))
        isFirstThreeCreated = false
    }

    private fun calculateAndDrawQuad() {
        mid1x = (perv1x + perv2x) * 0.5f
        mid1y = (perv1y + perv2y) * 0.5f

        mid2x = (curX + perv1x) * 0.5f
        mid2y = (curY + perv1y) * 0.5f

        path.moveTo(mid1x, mid1y)

        path.quadTo(perv1x, perv1y, mid2x, mid2y)

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
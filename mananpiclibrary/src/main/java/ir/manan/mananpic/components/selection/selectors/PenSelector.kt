package ir.manan.mananpic.components.selection.selectors

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.animation.LinearInterpolator
import ir.manan.mananpic.utils.dp

class PenSelector : PathBasedSelector() {

    // These two variables determine the location of first touch to later
    // use to close a path.
    private var firstX = 0f
    private var firstY = 0f


    // These two variables store the end point of quad bezier.
    private var bx = 0f
    private var by = 0f

    /**
     * This range will later determine the range of acceptance for current touch
     * location to close the path. Default value is 10dp (after selector is initialized).
     */
    var touchRange = 0f

    /**
     * If true selector draws quad bezier instead of straight line.
     */
    var isQuadBezier = false

    private val bezierPath by lazy {
        Path().apply {
            fillType = Path.FillType.WINDING
        }
    }

    private lateinit var context: Context

    // Counts total number of points on screen.
    // This variable will later be used to only select bitmap if our points are more than 2 otherwise
    // we cannot make a side or shape with it to be able to select.
    private var pointCounter = 0

    // Path effect for corner of path.
    private lateinit var cornerPathEffect: CornerPathEffect

    // Animator for when a path is closed. This animator basically shifts the
    // phase of path effect to create a cool animation.
    private val pathEffectAnimator = ValueAnimator().apply {
        duration = 500
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        setFloatValues(0f, 20f)
        addUpdateListener {
            pointsPaint.pathEffect =
                ComposePathEffect(
                    DashPathEffect(floatArrayOf(10f, 10f), it.animatedValue as Float),
                    cornerPathEffect
                )

            invalidateListener?.invalidateDrawings()
        }
    }

    private val pointsPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }
    }

    private val firstPointCirclePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#69a2ff")
            style = Paint.Style.FILL
        }
    }

    private var firstPointCircleRadius = 0f

    private var pointsPaintStrokeWidth: Float = 0.0f
        set(value) {
            field = value
            pointsPaint.strokeWidth = field
        }


    override fun initialize(context: Context, matrix: Matrix, bounds: RectF) {
        super.initialize(context, matrix, bounds)
        this.context = context

        context.run {
            cornerPathEffect = CornerPathEffect(dp(2))

            touchRange = dp(10)

            pointsPaintStrokeWidth = dp(3)

            firstPointCircleRadius = dp(4)
        }
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {
        // If use is in quad bezier mode and path is not empty,
        // save to location of current touch to later use it to draw
        // quad bezier to that point. We check if path is empty because
        // 'quadTo' method draws the first point from 0,0 if there aren't
        // any line or path available, so if use is in quad bezier mode and
        // it's the first line that user wants to draw, then we first move
        // the path to that current location then we draw a bezier.
        if (!path.isEmpty && isQuadBezier) {
            bx = initialX
            by = initialY
        }
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float) {
        // If path is closed then offset (move around) path if user moves his/her finger.
        if (isPathClose) {
            path.offset(dx, dy)
            invalidateListener?.invalidateDrawings()
        } else if (isQuadBezier && !path.isEmpty) {
            bezierPath.run {
                // Reset the bezier path to original path.
                set(path)
                // Draw a quad to points that user first touched(bx,by)
                // and put 'ex''ey' as handle bar points.
                quadTo(ex, ey, bx, by)

                invalidateListener?.invalidateDrawings()
            }
        }
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        // If path is closed.
        if (!isPathClose) {
            // If path is empty store the first touch location.
            if (pointCounter == 0) {
                firstX = lastX
                firstY = lastY
                path.moveTo(lastX, lastY)
            } else {
                if (isQuadBezier && pointCounter > 0) {
                    path.set(bezierPath)
                } else {
                    path.lineTo(lastX, lastY)
                }

                // If line is close to first point that user touched and we have at least 3 lines, then
                // close the path.
                if ((isNearFirstLine(lastX, lastY) || isNearFirstLine(
                        bx,
                        by
                    )) && pointCounter > 2
                ) {
                    closePath()
                }
            }
            pointCounter++
        }

        // Invalidate to hide the circle.
        invalidateListener?.invalidateDrawings()

    }

    private fun closePath() {
        path.close()
        bezierPath.reset()
        pathEffectAnimator.start()
        isPathClose = true
    }

    private fun isNearFirstLine(initialX: Float, initialY: Float): Boolean {
        // Calculate the touch range if user is zoomed in image.
        canvasMatrix.getValues(matrixValueHolder)
        val finalTouchRange = touchRange * (1f / matrixValueHolder[Matrix.MSCALE_X])

        return (initialX in (firstX - finalTouchRange)..(firstX + finalTouchRange) && initialY in (firstY - finalTouchRange)..(firstY + finalTouchRange))
    }

    override fun resetSelection() {
        path.rewind()
        bezierPath.reset()
        isPathClose = false
        pointCounter = 0

        if (pathEffectAnimator.isRunning || pathEffectAnimator.isStarted) {
            pointsPaint.pathEffect = null
            pathEffectAnimator.cancel()
        }

        invalidateListener?.invalidateDrawings()
    }


    override fun draw(canvas: Canvas?) {
        canvas?.run {

            // Create a copy of path to later transform the transformed path to it.
            val pathCopy = Path(path)

            // Apply matrix to path.
            path.transform(canvasMatrix)

            // Draw the transformed path.
            drawPath(path, pointsPaint)

            // Revert it back.
            path.set(pathCopy)

            // Reset path copy to release memory.
            pathCopy.reset()

            // Only draw bezier path if we're in quad bezier mode.
            if (isQuadBezier) {
                val bezierCopy = Path(bezierPath)

                bezierPath.transform(canvasMatrix)

                drawPath(bezierPath, pointsPaint)

                bezierPath.set(bezierCopy)

                bezierCopy.reset()
            }

            // Draw circle if it's first point that user touches so it will be visible that user
            // has touch the first point.
            if (pointCounter == 1) {

                // Get scale and divide 1 by it to get factor to resize the circle radius.
                canvasMatrix.getValues(matrixValueHolder)
                val scale = 1f / matrixValueHolder[Matrix.MSCALE_X]

                // Set matrix to 'canvasMatrix' to transform the circle.
                setMatrix(canvasMatrix)
                // Draw first point circle.
                drawCircle(firstX, firstY, firstPointCircleRadius * scale, firstPointCirclePaint)

                // Finally set canvas matrix to null to prevent affecting other drawings.
                setMatrix(null)
            }
        }
    }
}
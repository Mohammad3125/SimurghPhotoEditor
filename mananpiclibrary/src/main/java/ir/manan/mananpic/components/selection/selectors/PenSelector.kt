package ir.manan.mananpic.components.selection.selectors

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.animation.LinearInterpolator
import ir.manan.mananpic.utils.dp
import kotlin.math.abs

class PenSelector : PathBasedSelector() {

    // These two variables determine the location of first touch to later
    // use to close a path.
    private var firstX = 0f
    private var firstY = 0f

    // These two variables store the end point of quad bezier.
    private var bx = 0f
    private var by = 0f

    // Handle position for quad bezier.
    private var handleX = 0f
    private var handleY = 0f

    // These variables are used to store last location that user touched.
    private var lbx = 0f
    private var lby = 0f

    // Determines if initial bezier is drawn.
    private var isBezierDrawn = false

    // Variable that holds information about which handle is user currenly touching in bezier mode.
    private var currentHandleSelected: Handle = Handle.NONE


    /**
     * This range will later determine the range of acceptance for current touch
     * location to close the path in pixels. Default value is 10dp (later will be transformed to pixel after selector is initialized.)
     */
    var touchRange = 0f

    /**
     * Acceptable range for handle bars in bezier mode to be accepted that
     * user has touched the handle bar (range is in pixels).
     * Default is 24dp (later will be transformed to pixel after selector is initialized.)
     */
    var handleTouchRange = 0f

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

            if (touchRange == 0f)
                touchRange = dp(10)

            if (handleTouchRange == 0f)
                handleTouchRange = dp(24)

            pointsPaintStrokeWidth = dp(3)

            firstPointCircleRadius = dp(4)
        }
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {
        // If we're currently in bezier mode then figure out
        // which handle currently user is touching and then move it
        // as user moves his/her finger across screen in 'onMove' method.
        if (isBezierDrawn) {
            currentHandleSelected =
                when {
                    isNearFirstLine(
                        initialX,
                        initialY,
                        handleX,
                        handleY,
                        handleTouchRange,
                        true
                    ) -> Handle.BEZIER_HANDLE
                    isNearFirstLine(
                        initialX,
                        initialY,
                        bx,
                        by,
                        handleTouchRange,
                        true
                    ) -> Handle.END_HANDLE
                    else -> Handle.NONE
                }

        }
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float) {
        // If path is closed then offset (move around) path if user moves his/her finger.
        if (isPathClose) {
            path.offset(dx, dy)
            invalidateListener?.invalidateDrawings()
        } else if (isQuadBezier && isBezierDrawn && !path.isEmpty) {
            bezierPath.run {
                when (currentHandleSelected) {
                    Handle.BEZIER_HANDLE -> {
                        // Reset the bezier path to original path.
                        set(path)

                        // Change location of handle that is shown on screen.
                        handleX += dx
                        handleY += dy

                        // Draw quad bezier with new handle points.
                        quadTo(handleX, handleY, bx, by)

                        invalidateListener?.invalidateDrawings()
                    }
                    Handle.END_HANDLE -> {
                        // Reset the bezier to current path to discard last drawn quad bezier.
                        set(path)

                        // Draw quad bezier with new end point.
                        quadTo(handleX, handleY, ex, ey)

                        // Change the end point of current path.
                        bx = ex
                        by = ey

                        invalidateListener?.invalidateDrawings()
                    }
                    Handle.NONE -> {

                    }
                }
                return@run
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

                // If bezier is drawn and user touched somewhere else instead of bezier handles, then
                // replace the current path by bezier path and set 'isBezierDrawn' to false to create a
                // new bezier.
                if (isBezierDrawn && currentHandleSelected == Handle.NONE) {
                    path.set(bezierPath)
                    bezierPath.reset()
                    isBezierDrawn = false
                }

                if (isQuadBezier) {
                    if (!isBezierDrawn && pointCounter > 0) {

                        // Determine width and height of current line to later
                        // get center of that line to use as handle for bezier.
                        val halfX = abs(lastX - lbx) * 0.5f
                        val halfY = abs(lastY - lby) * 0.5f

                        // Determine if we have to append or subtract from last
                        // points touched to find center of line to use as handle
                        // location.
                        handleX = if (lastX > lbx) lbx + halfX else lbx - halfX
                        handleY = if (lastY > lby) lby + halfY else lby - halfY

                        // Reset bezier path to original path to draw a bezier
                        // from last point in 'path'.
                        bezierPath.set(path)
                        bezierPath.quadTo(handleX, handleY, lastX, lastY)

                        isBezierDrawn = true

                        // Store last point of current bezier to variables.
                        bx = lastX
                        by = lastY
                    }
                } else {
                    path.lineTo(lastX, lastY)
                }

                // If line is close to first point that user touched and we have at least 3 lines, then
                // close the path.
                if ((isNearFirstLine(lastX, lastY, firstX, firstY, touchRange) || isNearFirstLine(
                        bx,
                        by,
                        firstX,
                        firstY,
                        touchRange
                    )) && pointCounter > 2
                ) {
                    closePath()
                }
            }
            // Store the last location that user has touched.
            lbx = lastX
            lby = lastY
            pointCounter++
        }

        // Invalidate to hide the circle.
        invalidateListener?.invalidateDrawings()

    }

    private fun closePath() {
        path.close()
        bezierPath.reset()
        isBezierDrawn = false
        pathEffectAnimator.start()
        isPathClose = true
    }

    private fun isNearFirstLine(
        x: Float,
        y: Float,
        targetX: Float,
        targetY: Float,
        range: Float,
        scaleDown: Boolean = true
    ): Boolean {
        var finalTouchRange = touchRange
        if (scaleDown) {
            // Calculate the touch range if user is zoomed in image.
            canvasMatrix.getValues(matrixValueHolder)
            finalTouchRange = range * (1f / matrixValueHolder[Matrix.MSCALE_X])
        }

        return (x in (targetX - finalTouchRange)..(targetX + finalTouchRange) && y in (targetY - finalTouchRange)..(targetY + finalTouchRange))
    }

    override fun resetSelection() {
        path.rewind()
        bezierPath.reset()

        isBezierDrawn = false
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
            pathCopy.set(path)

            // Apply matrix to path.
            path.transform(canvasMatrix)

            // Draw the transformed path.
            drawPath(path, pointsPaint)

            // Revert it back.
            path.set(pathCopy)

            // Reset path copy to release memory.
            pathCopy.rewind()

            // Only draw bezier if we have currently drawn it in path.
            if (isBezierDrawn) {
                pathCopy.set(bezierPath)

                bezierPath.transform(canvasMatrix)

                drawPath(bezierPath, pointsPaint)

                bezierPath.set(pathCopy)

                pathCopy.rewind()
            }

            save()
            // Concat the canvas matrix to 'canvasMatrix' to transform the circle.
            concat(canvasMatrix)

            // Get scale and divide 1 by it to get factor to resize the circle radius.
            canvasMatrix.getValues(matrixValueHolder)
            val scale = 1f / matrixValueHolder[Matrix.MSCALE_X]

            // Draw circle if it's first point that user touches so it will be visible that user
            // has touch the first point.
            if (pointCounter == 1) {
                // Draw first point circle.
                drawCircle(firstX, firstY, firstPointCircleRadius * scale, firstPointCirclePaint)
            }

            if (isBezierDrawn && isQuadBezier) {

                // Handle for quad bezier.
                drawCircle(handleX, handleY, firstPointCircleRadius * scale, firstPointCirclePaint)

                // End point of bezier.
                drawCircle(bx, by, firstPointCircleRadius * scale, firstPointCirclePaint)
            }

            // Restore the state of canvas.
            restore()
        }
    }

    private enum class Handle {
        NONE,
        END_HANDLE,
        BEZIER_HANDLE
    }
}
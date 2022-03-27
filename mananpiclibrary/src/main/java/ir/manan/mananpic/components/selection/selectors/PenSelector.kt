package ir.manan.mananpic.components.selection.selectors

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.animation.LinearInterpolator
import ir.manan.mananpic.components.selection.selectors.PenSelector.Handle.*
import ir.manan.mananpic.components.selection.selectors.PenSelector.LineType.*
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.GestureUtils
import kotlin.math.abs

/**
 * Pen tool for selecting an area of interest with straight, quad bezier and cubic bezier.
 */
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

    // Handle position for second handle if line type is CUBIC_BEZIER
    private var secondHandleX = 0f
    private var secondHandleY = 0f

    // These variables are used to store last location that user touched.
    private var lbx = 0f
    private var lby = 0f

    // Determines if initial bezier is drawn.
    private var isTempLineDrawn = false

    // Variable that holds information about which handle is user currently touching in bezier mode.
    private var currentHandleSelected: Handle = NONE


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
     * Type of line that is going to be drawn in the next touch down.
     * @see LineType
     */
    var lineType = NORMAL
        set(value) {
            field = value
            // Set original path to temp path if user changes
            // the type of line while a temporary line has been
            // drawn.
            if (isTempLineDrawn) {
                setBezierToPath()
                invalidate()
            }
        }

    private val tempPath by lazy {
        Path().apply {
            fillType = Path.FillType.WINDING
        }
    }

    private val helperLinesPath by lazy {
        Path()
    }

    private lateinit var context: Context

    /**
     *Counts total number of points on screen.
     * This variable will later be used to only select bitmap if our points are more than 2 otherwise
     * we cannot make a side or shape with it to be able to select.
     */
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

            invalidate()
        }
    }

    private val pointsPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }
    }

    private val circlesPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#69a2ff")
            style = Paint.Style.FILL
        }
    }

    private val helperLinesPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }
    }

    private var circlesRadius = 0f

    private var pointsPaintStrokeWidth: Float = 0.0f
        set(value) {
            field = value
            pointsPaint.strokeWidth = field
        }

    override fun initialize(context: Context, matrix: MananMatrix, bounds: RectF) {
        super.initialize(context, matrix, bounds)
        this.context = context

        context.run {
            cornerPathEffect = CornerPathEffect(dp(2))

            if (touchRange == 0f)
                touchRange = dp(10)

            if (handleTouchRange == 0f)
                handleTouchRange = dp(24)

            pointsPaintStrokeWidth = dp(3)

            val intervals = dp(2)

            helperLinesPaint.strokeWidth = dp(2)

            helperLinesPaint.pathEffect = DashPathEffect(floatArrayOf(intervals, intervals), 0f)

            circlesRadius = dp(4)
        }
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {
        val finalRange = handleTouchRange * canvasMatrix.getOppositeScale()
        // Figure out which handle in a line user has selected.
        // Some handles are specific to one or two type of line and
        // others might be for each type of them.
        var nearest = finalRange

        currentHandleSelected = NONE

        if (GestureUtils.isNearTargetPoint(
                initialX,
                initialY,
                bx,
                by,
                finalRange
            )
        ) {
            (abs(bx - initialX) + abs(by - initialY)).let {
                if (it < nearest) {
                    nearest = it
                    currentHandleSelected = END_HANDLE
                }
            }
        }

        if (GestureUtils.isNearTargetPoint(
                initialX,
                initialY,
                handleX,
                handleY,
                finalRange
            )
        ) {
            (abs(handleX - initialX) + abs(handleY - initialY)).let {
                if (it < nearest) {
                    nearest = it
                    currentHandleSelected = FIRST_BEZIER_HANDLE
                }
            }
        }

        if (lineType == CUBIC_BEZIER && (GestureUtils.isNearTargetPoint(
                initialX,
                initialY,
                secondHandleX,
                secondHandleY,
                finalRange
            ))
        ) {
            (abs(secondHandleX - initialX) + abs(secondHandleY - initialY)).let {
                if (it < nearest) {
                    nearest = it
                    currentHandleSelected = SECOND_BEZIER_HANDLE
                }
            }
        }
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float) {
        // If path is closed then offset (move around) path if user moves his/her finger.
        if (isPathClose) {
            path.offset(dx, dy)
            invalidate()
        } else if (isTempLineDrawn) {
            tempPath.run {
                when (currentHandleSelected) {
                    FIRST_BEZIER_HANDLE -> {
                        // Reset the bezier path to original path.
                        set(path)

                        // Change location of handle that is shown on screen.
                        handleX += dx
                        handleY += dy

                        if (lineType == QUAD_BEZIER) {
                            // Draw quad bezier with new handle points.
                            quadTo(handleX, handleY, bx, by)
                        } else {
                            cubicTo(handleX, handleY, secondHandleX, secondHandleY, bx, by)
                        }


                        invalidate()
                    }
                    // Second handle is only for CUBIC_BEZIER.
                    SECOND_BEZIER_HANDLE -> {
                        set(path)

                        secondHandleX += dx
                        secondHandleY += dy

                        cubicTo(handleX, handleY, secondHandleX, secondHandleY, bx, by)

                        invalidate()
                    }
                    END_HANDLE -> {
                        // Reset the bezier to current path to discard last drawn quad bezier.
                        set(path)

                        // Since END_HANDLE is common on every line then we should check
                        // type of line and then decide what to do with it.
                        when (lineType) {
                            QUAD_BEZIER -> {
                                // Draw quad bezier with new end point.
                                quadTo(handleX, handleY, ex, ey)
                            }
                            CUBIC_BEZIER -> {
                                cubicTo(handleX, handleY, secondHandleX, secondHandleY, ex, ey)
                            }
                            else -> {
                                lineTo(ex, ey)
                            }
                        }

                        // Change the end point of current path.
                        bx = ex
                        by = ey

                        invalidate()
                    }
                    NONE -> {

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
                pushToStack()
                pointCounter++
            } else {

                // If bezier is drawn and user touched somewhere else instead of bezier handles, then
                // replace the current path by bezier path and set 'isBezierDrawn' to false to create a
                // new bezier.
                if (isTempLineDrawn && currentHandleSelected == NONE) {
                    setBezierToPath()
                }

                if (!isTempLineDrawn && pointCounter > 0) {

                    tempPath.set(path)

                    if (lineType != NORMAL) {

                        // Determine width and height of current line to later
                        // get center of that line to use as handle for bezier.
                        val lineWidth = (lastX - lbx)
                        val lineHeight = (lastY - lby)

                        if (lineType == QUAD_BEZIER) {
                            handleX = lbx + (lineWidth * 0.5f)
                            handleY = lby + (lineHeight * 0.5f)

                            tempPath.quadTo(handleX, handleY, lastX, lastY)
                        } else {
                            // First handle of CUBIC_BEZIER is at 33% of
                            // width and height of line.
                            handleX = lbx + (lineWidth * 0.33f)
                            handleY = lby + (lineHeight * 0.33f)

                            // Second handle of CUBIC_BEZIER is at 66% of
                            // width and height of line.
                            secondHandleX = lbx + (lineWidth * 0.66f)
                            secondHandleY = lby + (lineHeight * 0.66f)

                            // Finally draw a cubic with given handle and end point.
                            tempPath.cubicTo(
                                handleX,
                                handleY,
                                secondHandleX,
                                secondHandleY,
                                lastX,
                                lastY
                            )
                        }
                    } else {
                        // Temporarily draw a line.
                        // This is temporary because later user might change the end point of that line
                        // and after that we set current temp path to original path.
                        tempPath.lineTo(lastX, lastY)
                    }

                    isTempLineDrawn = true

                    // Store last point of current bezier to variables.
                    bx = lastX
                    by = lastY

                    pointCounter++
                }


                // If line is close to first point that user touched and we have at least 3 lines, then
                // close the path.
                if (shouldClosePath(lastX, lastY)) {
                    if (!tempPath.isEmpty) {
                        setBezierToPath()
                    }

                    closePath()
                }
            }
            // Store the last location that user has touched (if it's not a handle.)
            if (currentHandleSelected == NONE) {
                lbx = lastX
                lby = lastY
            }
        }

        invalidate()

    }

    private fun pushToStack() {
        paths.push(Path(path))
    }

    private fun setBezierToPath() {
        path.set(tempPath)

        // Push original path to stack to take a snapshot of current state.
        pushToStack()

        tempPath.reset()
        isTempLineDrawn = false
    }

    private fun closePath() {
        path.close()
        tempPath.reset()
        isTempLineDrawn = false
        pathEffectAnimator.start()
        isPathClose = true
    }

    private fun shouldClosePath(lastX: Float, lastY: Float): Boolean {
        val finalRange = touchRange * canvasMatrix.getOppositeScale()
        return (GestureUtils.isNearTargetPoint(
            lastX,
            lastY,
            firstX,
            firstY,
            finalRange
        ) || GestureUtils.isNearTargetPoint(
            bx,
            by,
            firstX,
            firstY,
            finalRange
        )) && pointCounter > 2
    }

    override fun resetSelection() {
        path.rewind()
        tempPath.reset()

        isTempLineDrawn = false
        isPathClose = false

        pointCounter = 0

        cancelAnimation()

        paths.clear()

        invalidate()
    }

    private fun cancelAnimation() {
        if (pathEffectAnimator.isRunning || pathEffectAnimator.isStarted) {
            pointsPaint.pathEffect = null
            pathEffectAnimator.cancel()
        }
    }


    override fun draw(canvas: Canvas?) {
        canvas?.run {

            if (!isTempLineDrawn) {
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
            }
            // Only draw temp path if we have drawn a temporary line.
            else {
                pathCopy.set(tempPath)

                tempPath.transform(canvasMatrix)

                drawPath(tempPath, pointsPaint)

                tempPath.set(pathCopy)

                pathCopy.rewind()
            }

            // Get opposite of current scale to resize the radius.
            val scale = canvasMatrix.getOppositeScale()
            val downSizedRadius = circlesRadius * scale

            if (isTempLineDrawn && lineType != NORMAL) {

                helperLinesPath.run {
                    // Set the helper points to current path to start drawings from last point of it.
                    set(path)

                    // Always draw first handle because both beziers have at least one handle.
                    lineTo(handleX, handleY)

                    if (lineType == QUAD_BEZIER) {
                        // If it's QUAD_BEZIER then draw a line to last point of bezier.
                        // This way we draw a line from start point to handle and finally to last
                        // point of bezier.
                        lineTo(bx, by)
                    } else {
                        // Else if we're in CUBIC_BEZIER mode the draw a line
                        // from first handle to second handle and then a line
                        // from second handle to end point of bezier.
                        lineTo(secondHandleX, secondHandleY)
                        lineTo(bx, by)
                    }

                    transform(canvasMatrix)
                }

                // Finally draw helper lines path.
                drawPath(helperLinesPath, helperLinesPaint)

                save()

                concat(canvasMatrix)

                // Handle for QUAD_BEZIER (also acts as first handle for CUBIC_BEZIER).
                drawCircle(handleX, handleY, downSizedRadius, circlesPaint)

                if (lineType == CUBIC_BEZIER) {
                    // Draw second handle only if we're in CUBIC_BEZIER type.
                    drawCircle(secondHandleX, secondHandleY, downSizedRadius, circlesPaint)
                }

                restore()
            }

            save()

            concat(canvasMatrix)

            if (isTempLineDrawn) {
                // End point of line (either bezier or straight).
                drawCircle(bx, by, downSizedRadius, circlesPaint)
            }

            // Draw circle if it's first point that user touches so it will be visible that user
            // has touch the first point.
            if (pointCounter == 1) {
                // Draw first point circle.
                drawCircle(firstX, firstY, downSizedRadius, circlesPaint)
            }
        }
    }

    override fun undo() {
        paths.run {
            if (!isEmpty()) {
                // Rewind any bezier path to prevent it from re-drawing.
                tempPath.rewind()
                isTempLineDrawn = false

                // If path is currently closed then restore
                // state to open path (cancel animation and etc...)
                if (isPathClose) {
                    isPathClose = false
                    cancelAnimation()
                }

                // Pop the last path from stack.
                path.set(pop())
                // Decrement amount of lines counter.
                --pointCounter
            }
            // Else if stack is empty and counter is greater than 0 then clear the path
            // And put it in initial state (state that user have to provide the initial point).
            else if (isEmpty() && pointCounter > 0) {
                path.rewind()
                --pointCounter
            }
            invalidate()
        }
    }

    private enum class Handle {
        NONE,
        END_HANDLE,
        FIRST_BEZIER_HANDLE,
        SECOND_BEZIER_HANDLE
    }

    /**
     * A class that represents the type of line that is going to be drawn in [PenSelector].
     */
    enum class LineType {
        NORMAL,
        QUAD_BEZIER,
        CUBIC_BEZIER
    }
}
package ir.manan.mananpic.components.selection.selectors

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import ir.manan.mananpic.components.selection.selectors.PenSelector.Handle.*
import ir.manan.mananpic.components.selection.selectors.PenSelector.LineType.NORMAL
import ir.manan.mananpic.components.selection.selectors.PenSelector.LineType.QUAD_BEZIER
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.GestureUtils
import java.util.*
import kotlin.math.abs

/**
 * Pen tool for selecting an area of interest with straight, quad bezier and cubic bezier lines.
 */
class PenSelector : PathBasedSelector() {

    // These two variables determine the location of first touch to later
    // use to close a path.
    private var firstX = 0f
    private var firstY = 0f

    // These two variables store the end point of quad bezier.
    private var bx = 0f
    private var by = 0f

    // Represent end point of previous line.
    private var vx = 0f
    private var vy = 0f

    private var lvx = 0f
    private var lvy = 0f

    // Handle position for quad bezier.
    private var handleX = 0f
    private var handleY = 0f

    // Handle position for second handle if line type is CUBIC_BEZIER
    private var secondHandleX = 0f
    private var secondHandleY = 0f

    /** Determines if a new line is drawn. */
    private var isNewLineDrawn = false

    /** Variable that holds information about which handle is user currently touching in bezier mode. */
    private var currentHandleSelected: Handle = NONE

    /**
     *  This variables prevents end point of a line to be shifted if user selects it. */
    private var isOtherLinesSelected = false


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
            // Set original path to temp path if user changes
            // the type of line while a temporary line has been
            // drawn.
            if (isNewLineDrawn) {
                invalidate()
            }
            field = value
        }

    /**
     * Color of circles which represent anchor points.
     * Default color is #69a2ff.
     *
     * Value should be a [ColorInt].
     */
    @ColorInt
    var circlesColor = Color.parseColor("#69a2ff")
        set(value) {
            field = value
            circlesPaint.color = value
        }

    /**
     * Color of unselected anchor points. Default color is #7888a1.
     * Values should be a [ColorInt].
     */
    @ColorInt
    var unselectedCirclesColor = Color.parseColor("#7888a1")

    private val helperLinesPath by lazy {
        Path()
    }

    private lateinit var context: Context

    /**
     * Counts total number of lines drawn.
     */
    private var pointCounter = 0

    /** Path effect for corner of path. */
    private lateinit var cornerPathEffect: CornerPathEffect

    /** Animator for when a path is closed. This animator basically shifts the
    phase of path effect to create a cool animation. */
    private val pathEffectAnimator = ValueAnimator().apply {
        duration = 500
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        setFloatValues(0f, 20f)
        addUpdateListener {
            linesPaint.pathEffect =
                ComposePathEffect(
                    DashPathEffect(floatArrayOf(10f, 10f), it.animatedValue as Float),
                    cornerPathEffect
                )

            invalidate()
        }
    }

    /**
     * Paint that draws lines.
     */
    private val linesPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }
    }

    /**
     * Paint of circles which is handle points etc...
     */
    private val circlesPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#69a2ff")
            style = Paint.Style.FILL
        }
    }

    /**
     * Paint that draws helper points.
     */
    private val helperLinesPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }
    }

    /**
     * Radius of circles which will be initialized after current selector has been initialized.
     */
    private var circlesRadius = 0f

    /**
     * Stroke width of lines drawn.
     *
     * Default values is 3dp (initialized after 'initialize' method has been called.)
     *
     * Values are interpreted as pixels.
     */
    var linesStrokeWidth: Float = 0.0f
        set(value) {
            field = value
            linesPaint.strokeWidth = field
            invalidate()
        }

    /**
     * This stack is used to store all lines that is drawn on screen.
     * This can act as a history to implement undo mechanism.
     */
    private val lines = Stack<Line>()

    /**
     * Reference to current selected line.
     */
    private var selectedLine: Line? = null

    private val mappingMatrix by lazy {
        Matrix()
    }

    override fun shouldParentTransformDrawings(): Boolean {
        return false
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

            if (linesStrokeWidth == 0f) {
                linesStrokeWidth = dp(3)
            }

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

        // Reset selected handle.
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

        if (GestureUtils.isNearTargetPoint(
                initialX,
                initialY,
                firstX,
                firstY,
                finalRange
            ) && (pointCounter == 1 || lines.indexOf(selectedLine) == 0)
        ) {
            (abs(firstX - initialX) + abs(firstY - initialY)).let {
                if (it < nearest) {
                    nearest = it
                    currentHandleSelected = FIRST_POINT_HANDLE
                }
            }
        }


        if (selectedLine is CubicBezier && (GestureUtils.isNearTargetPoint(
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

        // If there isn't any handle of current selected line selected,
        // then look at other lines' end point and see if user has selected them.
        // If user has selected another line, then restore state of that line, which may
        // contain handle points and etc..
        if (currentHandleSelected == NONE) {
            lines.find {
                GestureUtils.isNearTargetPoint(
                    initialX,
                    initialY,
                    it.epx,
                    it.epy,
                    finalRange
                )
            }?.let { line ->
                // Restore state of current line.
                setLineRelatedVariables(line)

                // Select the line.
                selectedLine = line

                // Since all end point of handles are visible,
                // then select the end handle.
                // State of selector will reset to the selected line.
                currentHandleSelected = END_HANDLE

                isOtherLinesSelected = true
            }
        }
    }

    private fun setLineRelatedVariables(line: Line) {
        bx = line.epx
        by = line.epy

        handleX = 0f
        handleY = 0f

        secondHandleX = 0f
        secondHandleY = 0f

        if (line is QuadBezier) {
            handleX = line.handleX
            handleY = line.handleY
        }
        if (line is CubicBezier) {

            handleX = line.firstHandleX
            handleY = line.firstHandleY

            secondHandleX = line.secondHandleX
            secondHandleY = line.secondHandleY
        }

        val index = lines.indexOf(line) - 1
        if (index < 0) {
            vx = firstX
            vy = firstY
        } else {
            lines[index].run {
                vx = epx
                vy = epy
            }
        }

    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float) {
        // If path is closed then offset (move around) path if user moves his/her finger.
        if (isPathClose && currentHandleSelected == NONE) {
            mappingMatrix.setTranslate(dx, dy)

            firstX += dx
            firstY += dy

            selectedLine = null

            lines.forEach { line ->
                line.transform(mappingMatrix)
            }

            invalidate()
        } else if (!isOtherLinesSelected) {
            when (currentHandleSelected) {
                FIRST_BEZIER_HANDLE -> {
                    // Reset the bezier path to original path.
                    (selectedLine as? QuadBezier)?.run {
                        handleX += dx
                        handleY += dy

                        this@PenSelector.handleX = handleX
                        this@PenSelector.handleY = handleY
                    }

                    (selectedLine as? CubicBezier)?.run {
                        firstHandleX += dx
                        firstHandleY += dy

                        this@PenSelector.handleX = firstHandleX
                        this@PenSelector.handleY = firstHandleY
                    }

                    invalidate()
                }
                // Second handle is only for CUBIC_BEZIER.
                SECOND_BEZIER_HANDLE -> {
                    (selectedLine as? CubicBezier)?.run {
                        secondHandleX += dx
                        secondHandleY += dy

                        this@PenSelector.secondHandleX = secondHandleX
                        this@PenSelector.secondHandleY = secondHandleY
                    }
                    invalidate()
                }
                END_HANDLE -> {
                    selectedLine?.run {
                        epx = ex
                        epy = ey

                        if (selectedLine === lines.lastElement() && isPathClose) {
                            firstX = epx
                            firstY = epy
                        }
                    }

                    // Change the end point of current path.
                    bx = ex
                    by = ey

                    if (lines.indexOf(selectedLine) == lines.size - 1) {
                        lvx = ex
                        lvy = ey
                    }

                    invalidate()
                }
                FIRST_POINT_HANDLE -> {
                    firstX = ex
                    firstY = ey

                    if (isPathClose) {
                        lines.lastElement()?.let { lastLine ->
                            lastLine.epx = firstX
                            lastLine.epy = firstY
                        }
                    }

                    vx = ex
                    vy = ey

                    invalidate()
                }
                NONE -> {

                }
            }
        }
    }

    override fun select(drawable: Drawable): Bitmap? {
        makePathReadyForSelection()

        return super.select(drawable)
    }

    private fun makePathReadyForSelection() {
        path.rewind()

        path.moveTo(firstX, firstY)

        lines.forEach {
            it.putIntoPath(path)
        }

        path.fillType =
            if (isSelectionInverse) Path.FillType.INVERSE_WINDING else Path.FillType.WINDING
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        // If path is closed.
        if (!isPathClose) {
            // If path is empty store the first touch location.
            if (pointCounter == 0) {
                firstX = lastX
                firstY = lastY
                vx = lastX
                vy = lastY
                pointCounter++
            } else {
                // If a new line is drawn and user touched somewhere else instead of lines' handles, then
                // store the last point of current line as start point of new line (because lines are drawn relative to another).
                if (isNewLineDrawn && currentHandleSelected == NONE) {
                    finalizeLine()
                }

                // If we have drawn the first point (pointCounter > 0) and current handle is not first handle then draw a new line.
                if (!isNewLineDrawn && pointCounter > 0 && currentHandleSelected != FIRST_POINT_HANDLE) {

                    if (lineType != NORMAL) {

                        // Determine width and height of current line to later
                        // get center of that line to use as handle for bezier.
                        val lineWidth = (lastX - vx)
                        val lineHeight = (lastY - vy)

                        if (lineType == QUAD_BEZIER) {
                            handleX = vx + (lineWidth * 0.5f)
                            handleY = vy + (lineHeight * 0.5f)

                            // Create new QuadBezier and select it.
                            selectedLine = QuadBezier(lastX, lastY, handleX, handleY)
                        } else {
                            // First handle of CUBIC_BEZIER is at 33% of
                            // width and height of line.
                            handleX = vx + (lineWidth * 0.33f)
                            handleY = vy + (lineHeight * 0.33f)

                            // Second handle of CUBIC_BEZIER is at 66% of
                            // width and height of line.
                            secondHandleX = vx + (lineWidth * 0.66f)
                            secondHandleY = vy + (lineHeight * 0.66f)

                            // Create new CubicBezier and select it.
                            selectedLine = CubicBezier(
                                lastX,
                                lastY,
                                handleX,
                                handleY,
                                secondHandleX,
                                secondHandleY,
                            )

                        }
                    } else {
                        // Create new simple line and select it.
                        selectedLine = Line(lastX, lastY)
                    }

                    isNewLineDrawn = true

                    // Store last point of current bezier to variables.
                    bx = lastX
                    by = lastY

                    lvx = lastX
                    lvy = lastY

                    pointCounter++

                    // Push the newly create line to lines holder.
                    lines.push(selectedLine)
                }

                // If line is close to first point that user touched,
                // close the path.
                if (shouldClosePath(lastX, lastY) && currentHandleSelected != FIRST_POINT_HANDLE) {
                    finalizeLine()
                    closePath()
                }
            }
        }
        isOtherLinesSelected = false
        invalidate()
    }

    private fun finalizeLine() {
        if (lines.indexOf(selectedLine) == lines.size - 1) {
            vx = bx
            vy = by
        } else {
            vx = lvx
            vy = lvy
        }
        isNewLineDrawn = false
    }

    private fun closePath() {
        isNewLineDrawn = false
        lines.lastElement().run {
            epx = firstX
            epy = firstY
        }
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
        selectedLine = null
        lines.clear()
        path.reset()

        isNewLineDrawn = false
        isPathClose = false

        pointCounter = 0

        cancelAnimation()

        invalidate()
    }

    private fun cancelAnimation() {
        if (pathEffectAnimator.isRunning || pathEffectAnimator.isStarted) {
            linesPaint.pathEffect = null
            pathEffectAnimator.cancel()
        }
    }


    override fun draw(canvas: Canvas?) {
        canvas?.run {

            // Move the allocation path to first point.
            pathCopy.moveTo(firstX, firstY)

            // Draw lines in stack of lines one by one relative to each other.
            lines.forEach {
                it.putIntoPath(pathCopy)
            }

            if (isSelectionInverse && isClosed()) {
                pathCopy.addRect(leftEdge, topEdge, rightEdge, bottomEdge, Path.Direction.CW)
            }

            // Transform the drawings base on the transformation matrix of parent.
            pathCopy.transform(canvasMatrix)

            drawPath(pathCopy, linesPaint)

            // Reset path copy to release memory.
            pathCopy.rewind()

            // Get opposite of current scale to resize the radius.
            val scale = canvasMatrix.getOppositeScale()
            val downSizedRadius = circlesRadius * scale

            if ((selectedLine is QuadBezier || selectedLine is CubicBezier)) {

                helperLinesPath.run {
                    // Set the helper points to current path to start drawings from last point of it.
                    reset()
                    moveTo(vx, vy)

                    // Always draw first handle because both beziers have at least one handle.
                    lineTo(handleX, handleY)

                    if (selectedLine is QuadBezier) {
                        // If it's QUAD_BEZIER then draw a line to last point of bezier.
                        // This way we draw a line from start point to handle and finally to last
                        // point of bezier.
                        lineTo(bx, by)
                    } else if (selectedLine is CubicBezier) {
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

                if (selectedLine is CubicBezier) {
                    // Draw second handle only if we're in CUBIC_BEZIER type.
                    drawCircle(secondHandleX, secondHandleY, downSizedRadius, circlesPaint)
                }

                restore()
            }

            save()

            concat(canvasMatrix)

            selectedLine?.let { line ->
                // End point of line (either bezier or straight).
                drawCircle(line.epx, line.epy, downSizedRadius, circlesPaint)
            }

            // Draw circle if it's first point that user touches so it will be visible that user
            // has touch the first point.
            if (pointCounter == 1 || lines.indexOf(selectedLine) == 0) {
                // Draw first point circle.
                drawCircle(firstX, firstY, downSizedRadius, circlesPaint)
            }

            circlesPaint.color = unselectedCirclesColor

            lines.minus(selectedLine).forEach {
                if (it != null) {
                    drawCircle(
                        it.epx,
                        it.epy,
                        downSizedRadius,
                        circlesPaint
                    )
                }
            }
            circlesPaint.color = circlesColor
        }
    }

    override fun undo() {
        if (isSelectionInverse) {
            isSelectionInverse = false
            if (lines.isEmpty()) {
                invalidate()
            }
        }
        lines.run {
            if (isNotEmpty()) {
                // Remove last line in stack.
                pop()

                // If it's not empty...
                if (isNotEmpty()) {
                    // Then get the previous line and select it and restore its state.
                    val currentLine = peek()

                    selectedLine = currentLine

                    setLineRelatedVariables(currentLine)

                    isNewLineDrawn = true
                } else {
                    isNewLineDrawn = false
                }

                // If path is close and user undoes the operation,
                // then open the path and reset its offset and cancel path animation.
                if (isPathClose) {
                    isPathClose = false
                    cancelAnimation()
                }
            }

            // Decrement the counter.
            if (pointCounter > 0) {
                --pointCounter
            }

            invalidate()
        }
    }

    override fun getClipPath(): Path? {
        return if (isPathClose) {
            makePathReadyForSelection()
            path
        } else null
    }

    override fun onSizeChanged(newBounds: RectF, changeMatrix: Matrix) {
        val fl = floatArrayOf(firstX, firstY)
        changeMatrix.mapPoints(fl)

        firstX = fl[0]
        firstY = fl[1]

        lines.forEach {
            it.transform(changeMatrix)
        }

        selectedLine?.let { selected ->
            setLineRelatedVariables(selected)
        }

        leftEdge = newBounds.left
        topEdge = newBounds.top
        rightEdge = newBounds.right
        bottomEdge = newBounds.bottom

        invalidate()
    }

    private enum class Handle {
        NONE,
        END_HANDLE,
        FIRST_BEZIER_HANDLE,
        SECOND_BEZIER_HANDLE,
        FIRST_POINT_HANDLE
    }

    /**
     * A class that represents the type of line that is going to be drawn in [PenSelector].
     */
    enum class LineType {
        NORMAL,
        QUAD_BEZIER,
        CUBIC_BEZIER
    }

    open class Line(
        var epx: Float,
        var epy: Float,
    ) {

        protected var floatArrayPointsHolder = FloatArray(6) {
            0f
        }

        /**
         * Draws content of current line on a given path.
         * @param path Path that content of current line is going to be drawn on.
         */
        open fun putIntoPath(path: Path) {
            path.lineTo(epx, epy)
        }

        open fun transform(matrix: Matrix) {
            floatArrayPointsHolder[0] = epx
            floatArrayPointsHolder[1] = epy

            matrix.mapPoints(floatArrayPointsHolder)

            epx = floatArrayPointsHolder[0]
            epy = floatArrayPointsHolder[1]

        }
    }

    class QuadBezier(
        epx: Float,
        epy: Float,
        var handleX: Float,
        var handleY: Float
    ) : Line(epx, epy) {
        override fun putIntoPath(path: Path) {
            path.quadTo(handleX, handleY, epx, epy)
        }

        override fun transform(matrix: Matrix) {
            super.transform(matrix)
            floatArrayPointsHolder[2] = handleX
            floatArrayPointsHolder[3] = handleY
            matrix.mapPoints(floatArrayPointsHolder)

            handleX = floatArrayPointsHolder[2]
            handleY = floatArrayPointsHolder[3]
        }
    }

    class CubicBezier(
        epx: Float,
        epy: Float,
        var firstHandleX: Float,
        var firstHandleY: Float,
        var secondHandleX: Float,
        var secondHandleY: Float
    ) : Line(epx, epy) {
        override fun putIntoPath(path: Path) {
            path.cubicTo(firstHandleX, firstHandleY, secondHandleX, secondHandleY, epx, epy)
        }

        override fun transform(matrix: Matrix) {
            super.transform(matrix)

            floatArrayPointsHolder[2] = firstHandleX
            floatArrayPointsHolder[3] = firstHandleY
            floatArrayPointsHolder[4] = secondHandleX
            floatArrayPointsHolder[5] = secondHandleY

            matrix.mapPoints(floatArrayPointsHolder)

            firstHandleX = floatArrayPointsHolder[2]
            firstHandleY = floatArrayPointsHolder[3]
            secondHandleX = floatArrayPointsHolder[4]
            secondHandleY = floatArrayPointsHolder[5]

        }
    }
}
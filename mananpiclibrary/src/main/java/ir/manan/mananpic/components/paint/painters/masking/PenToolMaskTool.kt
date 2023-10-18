package ir.manan.mananpic.components.paint.painters.masking

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposePathEffect
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.components.selection.selectors.PenSelector
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.GestureUtils
import java.util.Stack
import kotlin.math.abs

class PenToolMaskTool : Painter() {

    private val canvasApply by lazy {
        Canvas()
    }

    // A path used by other paths in drawings operation to maintain
    // the previous state of a path.
    private val path by lazy {
        Path()
    }

    /**
     * Determines if path is closed or not.
     *
     *
     * Selector cannot clip the content if this value is not 'true'.
     */
    protected var isPathClose = false

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
    private var currentHandleSelected: Handle = Handle.NONE

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
    var lineType = LineType.NORMAL
        set(value) {
            // Set original path to temp path if user changes
            // the type of line while a temporary line has been
            // drawn.
            if (isNewLineDrawn) {
                sendMessage(PainterMessage.INVALIDATE)
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

            sendMessage(PainterMessage.INVALIDATE)
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
            sendMessage(PainterMessage.INVALIDATE)
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

    private lateinit var canvasMatrix: MananMatrix

    private var selectedLayer: PaintLayer? = null

    private var vectorHolder = FloatArray(2)

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        bounds: RectF
    ) {

        canvasMatrix = transformationMatrix

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
        val finalRange = canvasMatrix.mapRadius(handleTouchRange)
        // Figure out which handle in a line user has selected.
        // Some handles are specific to one or two type of line and
        // others might be for each type of them.
        var nearest = finalRange

        // Reset selected handle.
        currentHandleSelected = Handle.NONE

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
                    currentHandleSelected = Handle.END_HANDLE
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
                    currentHandleSelected = Handle.FIRST_BEZIER_HANDLE
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
                    currentHandleSelected = Handle.FIRST_POINT_HANDLE
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
                    currentHandleSelected = Handle.SECOND_BEZIER_HANDLE
                }
            }
        }

        // If there isn't any handle of current selected line selected,
        // then look at other lines' end point and see if user has selected them.
        // If user has selected another line, then restore state of that line, which may
        // contain handle points and etc..
        if (currentHandleSelected == Handle.NONE) {
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
                currentHandleSelected = Handle.END_HANDLE

                isOtherLinesSelected = true
            }
        }

        if (currentHandleSelected == Handle.NONE && isPathClose) {
            selectedLine = null
            sendMessage(PainterMessage.INVALIDATE)
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


    override fun onMove(ex: Float, ey: Float, dx: Float, dy: Float) {
        // If path is closed then offset (move around) path if user moves his/her finger.
        if (isPathClose && currentHandleSelected == Handle.NONE) {

            vectorHolder[0] = dx
            vectorHolder[1] = dy

            canvasMatrix.invert(mappingMatrix)
            mappingMatrix.mapVectors(vectorHolder)

            val tdx = vectorHolder[0]
            val tdy = vectorHolder[1]

            mappingMatrix.setTranslate(tdx, tdy)

            firstX += tdx
            firstY += tdy

            selectedLine = null

            lines.forEach { line ->
                line.transform(mappingMatrix)
            }

            sendMessage(PainterMessage.INVALIDATE)
        } else if (!isOtherLinesSelected) {
            when (currentHandleSelected) {
                Handle.FIRST_BEZIER_HANDLE -> {
                    // Reset the bezier path to original path.
                    (selectedLine as? QuadBezier)?.run {
                        handleX = ex
                        handleY = ey

                        this@PenToolMaskTool.handleX = handleX
                        this@PenToolMaskTool.handleY = handleY
                    }

                    (selectedLine as? CubicBezier)?.run {
                        firstHandleX = ex
                        firstHandleY = ey

                        this@PenToolMaskTool.handleX = firstHandleX
                        this@PenToolMaskTool.handleY = firstHandleY
                    }

                    sendMessage(PainterMessage.INVALIDATE)
                }
                // Second handle is only for CUBIC_BEZIER.
                Handle.SECOND_BEZIER_HANDLE -> {
                    (selectedLine as? CubicBezier)?.run {
                        secondHandleX = ex
                        secondHandleY = ey

                        this@PenToolMaskTool.secondHandleX = secondHandleX
                        this@PenToolMaskTool.secondHandleY = secondHandleY
                    }
                    sendMessage(PainterMessage.INVALIDATE)
                }

                Handle.END_HANDLE -> {
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

                    sendMessage(PainterMessage.INVALIDATE)
                }
                Handle.FIRST_POINT_HANDLE -> {
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

                    sendMessage(PainterMessage.INVALIDATE)
                }

                Handle.NONE -> {

                }
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
                vx = lastX
                vy = lastY
                pointCounter++
            } else {
                // If a new line is drawn and user touched somewhere else instead of lines' handles, then
                // store the last point of current line as start point of new line (because lines are drawn relative to another).
                if (isNewLineDrawn && currentHandleSelected == Handle.NONE) {
                    finalizeLine()
                }

                // If we have drawn the first point (pointCounter > 0) and current handle is not first handle then draw a new line.
                if (!isNewLineDrawn && pointCounter > 0 && currentHandleSelected != Handle.FIRST_POINT_HANDLE) {

                    if (lineType != LineType.NORMAL) {

                        // Determine width and height of current line to later
                        // get center of that line to use as handle for bezier.
                        val lineWidth = (lastX - vx)
                        val lineHeight = (lastY - vy)

                        if (lineType == LineType.QUAD_BEZIER) {
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
                if (shouldClosePath(
                        lastX,
                        lastY
                    ) && currentHandleSelected != Handle.FIRST_POINT_HANDLE
                ) {
                    finalizeLine()
                    closePath()
                }
            }
        }
        isOtherLinesSelected = false
        sendMessage(PainterMessage.INVALIDATE)
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
        val finalRange = canvasMatrix.mapRadius(touchRange)
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

    private fun cancelAnimation() {
        if (pathEffectAnimator.isRunning || pathEffectAnimator.isStarted) {
            linesPaint.pathEffect = null
            pathEffectAnimator.cancel()
        }
    }

    private fun drawLinesIntoPath(targetPath: Path) {
        // Move the allocation path to first point.
        targetPath.moveTo(firstX, firstY)

        // Draw lines in stack of lines one by one relative to each other.
        lines.forEach {
            it.putIntoPath(targetPath)
        }
    }

    fun getPathCopy(): Path {
        val cPath = Path()
        drawLinesIntoPath(cPath)
        return cPath
    }

    fun applyOnMaskLayer() {
        drawLinesIntoPath(path)
        selectedLayer?.let { maskLayer ->
            canvasApply.setBitmap(maskLayer.bitmap)
            linesPaint.style = Paint.Style.FILL
            canvasApply.drawPath(path, linesPaint)
            linesPaint.style = Paint.Style.STROKE
        }
    }

    fun cutFromMaskLayer() {
        linesPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        applyOnMaskLayer()
        linesPaint.xfermode = null
    }

    fun removeLastLine() {
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
                    selectedLine = null
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

            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    override fun resetPaint() {
        selectedLine = null
        lines.clear()
        path.reset()

        isNewLineDrawn = false
        isPathClose = false

        pointCounter = 0

        cancelAnimation()

        sendMessage(PainterMessage.INVALIDATE)
    }


    override fun draw(canvas: Canvas) {
        canvas.run {

            drawLinesIntoPath(path)

            drawPath(path, linesPaint)

            // Reset path copy to release memory.
            path.rewind()

            if (selectedLine is QuadBezier || selectedLine is CubicBezier) {

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

                }

                // Finally draw helper lines path.
                drawPath(helperLinesPath, helperLinesPaint)

                // Handle for QUAD_BEZIER (also acts as first handle for CUBIC_BEZIER).
                drawCircle(handleX, handleY, circlesRadius, circlesPaint)

                if (selectedLine is CubicBezier) {
                    // Draw second handle only if we're in CUBIC_BEZIER type.
                    drawCircle(secondHandleX, secondHandleY, circlesRadius, circlesPaint)
                }

            }

            selectedLine?.let { line ->
                // End point of line (either bezier or straight).
                drawCircle(line.epx, line.epy, circlesRadius, circlesPaint)
            }

            // Draw circle if it's first point that user touches so it will be visible that user
            // has touch the first point.
            if (pointCounter == 1 || lines.indexOf(selectedLine) == 0) {
                // Draw first point circle.
                drawCircle(firstX, firstY, circlesRadius, circlesPaint)
            }

            circlesPaint.color = unselectedCirclesColor

            lines.minus(selectedLine).forEach {
                if (it != null) {
                    drawCircle(
                        it.epx,
                        it.epy,
                        circlesRadius,
                        circlesPaint
                    )
                }
            }
            circlesPaint.color = circlesColor
        }
    }

    override fun onLayerChanged(layer: PaintLayer?) {
        selectedLayer = layer
    }

    override fun doesNeedTouchSlope(): Boolean {
        return false
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

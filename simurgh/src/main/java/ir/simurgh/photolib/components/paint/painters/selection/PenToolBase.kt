package ir.simurgh.photolib.components.paint.painters.selection

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
import android.graphics.Rect
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import androidx.core.graphics.toColorInt
import ir.simurgh.photolib.components.paint.painters.painter.Painter
import ir.simurgh.photolib.components.paint.painters.painter.PainterMessage
import ir.simurgh.photolib.components.paint.view.PaintLayer
import ir.simurgh.photolib.utils.extensions.dp
import ir.simurgh.photolib.utils.gesture.GestureUtils
import ir.simurgh.photolib.utils.gesture.TouchData
import ir.simurgh.photolib.utils.matrix.SimurghMatrix
import java.util.Stack
import kotlin.math.abs

/**
 * Advanced pen tool base class for creating precise vector-based selections and paths.
 *
 * This comprehensive tool provides professional-grade path creation capabilities similar to
 * those found in vector graphics and advanced image editing software. It supports multiple
 * line types and sophisticated editing features for creating precise selections.
 *
 * **Core Path Types:**
 * - **Normal Lines**: Straight line segments between anchor points.
 * - **Quadratic Bezier**: Curved segments with single control handle.
 * - **Cubic Bezier**: Curved segments with dual control handles for maximum flexibility.
 * - All types can be mixed within a single path for complex shapes.
 *
 * **Interactive Editing Features:**
 * - **Anchor Point Manipulation**: Click and drag any point to reposition.
 * - **Bezier Handle Control**: Precise curve adjustment with visual handles.
 * - **Real-time Preview**: Live feedback during path construction and editing.
 * - **Path Transformation**: Move entire paths with multi-touch gestures.
 *
 * **Professional Visual Feedback:**
 * - **Marching Ants**: Animated selection outline when path is closed.
 * - **Anchor Points**: Visual indicators for all editable points.
 * - **Control Handles**: Bezier curve handles with connecting guide lines.
 * - **Selection States**: Different colors for selected vs unselected elements.
 *
 * **Advanced Path Management:**
 * - **Automatic Closure**: Smart path closure when returning to start point.
 * - **Undo System**: Full history support for all path operations.
 * - **Precision Control**: Configurable touch ranges for different UI elements.
 * - **Zoom Awareness**: Scale-appropriate rendering at all zoom levels.
 *
 * **Technical Architecture:**
 * - Abstract base class designed for extension by specific selection tools.
 * - Thread-safe path operations with proper state management.
 * - Memory-efficient storage of path data with history support.
 * - Matrix-based transformations for zoom and pan operations.
 *
 * **Common Use Cases:**
 * - Creating precise selections around complex objects.
 * - Vector-based masking and clipping operations.
 * - Custom shape creation for design applications.
 * - Professional photo editing selection workflows.
 *
 * **Usage Pattern:**
 * ```kotlin
 * class MyPenTool : PenToolBase(context) {
 *     override fun performSelection() {
 *         val path = getPathCopy()
 *         // Use path for selection operations
 *     }
 * }
 * ```
 */
abstract class PenToolBase(context: Context) : Painter() {

    // A path used by other paths in drawings operation to maintain
    // the previous state of a path.
    protected val path by lazy {
        Path()
    }

    /**
     * Determines if path is closed or not.
     *
     * Selector cannot clip the content if this value is not 'true'.
     */
    protected var isPathClose = false

    // These two variables determine the location of first touch to later
    // use to close a path.
    protected var firstX = 0f
    protected var firstY = 0f

    // These two variables store the end point of quad bezier.
    protected var bx = 0f
    protected var by = 0f

    // Represent end point of previous line.
    protected var vx = 0f
    protected var vy = 0f

    /** Last valid end point coordinates for line continuation. */
    protected var lvx = 0f
    protected var lvy = 0f

    // Handle position for quad bezier.
    protected var handleX = 0f
    protected var handleY = 0f

    // Handle position for second handle if line type is CUBIC_BEZIER
    protected var secondHandleX = 0f
    protected var secondHandleY = 0f

    /** Determines if a new line is drawn. */
    protected var isNewLineDrawn = false

    /** Variable that holds information about which handle is user currently touching in bezier mode. */
    protected var currentHandleSelected: Handle = Handle.NONE

    /**
     *  This variables prevents end point of a line to be shifted if user selects it.
     */
    protected var isOtherLinesSelected = false

    /**
     * This range will later determine the range of acceptance for current touch
     * location to close the path in pixels. Default value is 10dp (later will be transformed to pixel after selector is initialized.)
     */
    var touchRange = context.dp(10)

    /**
     * Acceptable range for handle bars in bezier mode to be accepted that
     * user has touched the handle bar (range is in pixels).
     * Default is 24dp (later will be transformed to pixel after selector is initialized.)
     */
    var handleTouchRange = context.dp(24)

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
    var circlesColor = "#69a2ff".toColorInt()
        set(value) {
            field = value
            circlesPaint.color = value
        }

    /**
     * Color of unselected anchor points. Default color is #7888a1.
     * Values should be a [ColorInt].
     */
    @ColorInt
    var unselectedCirclesColor = "#7888a1".toColorInt()

    /** Path for drawing helper/guide lines that connect bezier handles. */
    protected val helperLinesPath by lazy {
        Path()
    }

    /**
     * Counts total number of lines drawn.
     */
    protected var pointCounter = 0

    /** Path effect for corner of path. */
    protected val cornerPathEffect by lazy {
        CornerPathEffect(context.dp(2))
    }

    /** Animator for when a path is closed. This animator basically shifts the
    phase of path effect to create a cool animation. */
    protected val pathEffectAnimator = ValueAnimator().apply {
        duration = 500
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        setFloatValues(0f, 20f)
        addUpdateListener {
            // Create marching ants effect with dashed line animation.
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
    protected val linesPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = context.dp(3)
        }
    }

    /**
     * Paint of circles which is handle points etc...
     */
    protected val circlesPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#69a2ff")
            style = Paint.Style.FILL
        }
    }

    /**
     * Paint that draws helper points.
     */
    protected val helperLinesPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = context.dp(3)
            val intervals = context.dp(2)
            // Create dashed line effect for helper lines.
            pathEffect = DashPathEffect(floatArrayOf(intervals, intervals), 0f)
        }
    }

    /**
     * Radius of circles which will be initialized after current selector has been initialized.
     */
    var circlesRadius = context.dp(4)

    /**
     * Stroke width of lines drawn.
     *
     * Default values is 3dp (initialized after 'initialize' method has been called.)
     *
     * Values are interpreted as pixels.
     */
    var linesStrokeWidth: Float = linesPaint.strokeWidth
        set(value) {
            field = value
            linesPaint.strokeWidth = field
            helperLinesPaint.strokeWidth = field
            sendMessage(PainterMessage.INVALIDATE)
        }

    /**
     * This stack is used to store all lines that is drawn on screen.
     * This can act as a history to implement undo mechanism.
     */
    protected val lines = Stack<Line>()

    /**
     * Reference to current selected line.
     */
    protected var selectedLine: Line? = null

    /** Matrix for coordinate transformations between screen and canvas space. */
    protected val mappingMatrix by lazy {
        Matrix()
    }

    /** Canvas transformation matrix for zoom and pan operations. */
    protected lateinit var canvasMatrix: SimurghMatrix

    /** Currently selected layer for path operations. */
    protected var selectedLayer: PaintLayer? = null

    /** Reusable array for coordinate transformations to avoid allocations. */
    protected var vectorHolder = FloatArray(2)

    /**
     * Initializes the pen tool with transformation matrices and layer bounds.
     * Sets up the coordinate system for precise path creation.
     */
    override fun initialize(
        context: Context,
        transformationMatrix: SimurghMatrix,
        fitInsideMatrix: SimurghMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)
        // Store canvas matrix for coordinate transformations.
        canvasMatrix = transformationMatrix
    }

    /**
     * Called when user starts touching the screen.
     * Determines which path element (if any) is being selected for editing.
     */
    override fun onMoveBegin(touchData: TouchData) {
        findLines(touchData.ex, touchData.ey)
    }

    /**
     * Sets up tool state variables based on the selected line.
     * Restores the editing context for the specified line segment.
     */
    protected fun setLineRelatedVariables(line: Line) {
        // Set current end point to the line's end point.
        bx = line.epx
        by = line.epy

        // Reset handle positions.
        handleX = 0f
        handleY = 0f
        secondHandleX = 0f
        secondHandleY = 0f

        // Restore handle positions if line has bezier curves.
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

        // Find the starting point for this line (previous line's end or path start).
        val index = lines.indexOf(line) - 1
        if (index < 0) {
            // This is the first line, start from path beginning.
            vx = firstX
            vy = firstY
        } else {
            // Start from previous line's end point.
            lines[index].run {
                vx = epx
                vy = epy
            }
        }
    }

    /**
     * Handles continuous touch movement for path editing and transformation.
     * Supports different editing modes based on which element is selected.
     */
    override fun onMove(touchData: TouchData) {
        // If path is closed then offset (move around) path if user moves his/her finger.
        if (isPathClose && currentHandleSelected == Handle.NONE) {
            // Transform entire path when no specific handle is selected.

            // Convert screen movement to canvas coordinates.
            vectorHolder[0] = touchData.dx
            vectorHolder[1] = touchData.dy

            canvasMatrix.invert(mappingMatrix)
            mappingMatrix.mapVectors(vectorHolder)

            val tdx = vectorHolder[0]
            val tdy = vectorHolder[1]

            // Apply translation to path coordinates.
            mappingMatrix.setTranslate(tdx, tdy)

            // Move the starting point.
            firstX += tdx
            firstY += tdy

            // Clear line selection since we're moving the entire path.
            selectedLine = null

            // Transform all lines in the path.
            lines.forEach { line ->
                line.transform(mappingMatrix)
            }

            sendMessage(PainterMessage.INVALIDATE)
        } else if (!isOtherLinesSelected) {
            // Handle individual element editing based on selection.
            when (currentHandleSelected) {
                Handle.FIRST_BEZIER_HANDLE -> {
                    // Adjust first bezier handle position.
                    (selectedLine as? QuadBezier)?.run {
                        handleX = touchData.ex
                        handleY = touchData.ey

                        this@PenToolBase.handleX = handleX
                        this@PenToolBase.handleY = handleY
                    }

                    (selectedLine as? CubicBezier)?.run {
                        firstHandleX = touchData.ex
                        firstHandleY = touchData.ey

                        this@PenToolBase.handleX = firstHandleX
                        this@PenToolBase.handleY = firstHandleY
                    }

                    sendMessage(PainterMessage.INVALIDATE)
                }
                // Second handle is only for CUBIC_BEZIER.
                Handle.SECOND_BEZIER_HANDLE -> {
                    (selectedLine as? CubicBezier)?.run {
                        secondHandleX = touchData.ex
                        secondHandleY = touchData.ey

                        this@PenToolBase.secondHandleX = secondHandleX
                        this@PenToolBase.secondHandleY = secondHandleY
                    }
                    sendMessage(PainterMessage.INVALIDATE)
                }

                Handle.END_HANDLE -> {
                    // Move the end point of the selected line.
                    selectedLine?.run {
                        epx = touchData.ex
                        epy = touchData.ey

                        // If this is the last line and path is closed, update start point too.
                        if (selectedLine === lines.lastElement() && isPathClose) {
                            firstX = epx
                            firstY = epy
                        }
                    }

                    // Update current drawing position.
                    bx = touchData.ex
                    by = touchData.ey

                    // Update line continuation point if this is the last line.
                    if (lines.indexOf(selectedLine) == lines.size - 1) {
                        lvx = touchData.ex
                        lvy = touchData.ey
                    }

                    sendMessage(PainterMessage.INVALIDATE)
                }

                Handle.FIRST_POINT_HANDLE -> {
                    // Move the starting point of the entire path.
                    firstX = touchData.ex
                    firstY = touchData.ey

                    // If path is closed, update the last line's end point to match.
                    if (isPathClose) {
                        lines.lastElement()?.let { lastLine ->
                            lastLine.epx = firstX
                            lastLine.epy = firstY
                        }
                    }

                    // Update line start position.
                    vx = touchData.ex
                    vy = touchData.ey

                    sendMessage(PainterMessage.INVALIDATE)
                }

                Handle.NONE -> {
                    // No specific handle selected, no action needed.
                }
            }
        }
    }

    /**
     * Handles the completion of touch gestures for path creation and editing.
     * Manages path construction logic and automatic closure detection.
     */
    override fun onMoveEnded(touchData: TouchData) {
        touchData.run {
            // Find which elements are near the touch end position.
            findLines(ex, ey)

            // If path is closed.
            if (!isPathClose) {
                if (pointCounter == 0) {
                    // First point of the path - establish starting position.
                    firstX = ex
                    firstY = ey
                    vx = ex
                    vy = ey
                    pointCounter++
                } else {
                    // Subsequent points - handle line creation and path closure.

                    // If a new line is drawn and user touched somewhere else instead of lines' handles, then
                    // store the last point of current line as start point of new line (because lines are drawn relative to another).
                    if (isNewLineDrawn && currentHandleSelected == Handle.NONE) {
                        finalizeLine()
                    }

                    // If we have drawn the first point (pointCounter > 0) and current handle is not first handle then draw a new line.
                    if (!isNewLineDrawn && pointCounter > 0 && currentHandleSelected != Handle.FIRST_POINT_HANDLE) {

                        if (lineType != LineType.NORMAL) {
                            // Create bezier curves with automatic handle positioning.

                            // Determine width and height of current line to later
                            // get center of that line to use as handle for bezier.
                            val lineWidth = (ex - vx)
                            val lineHeight = (ey - vy)

                            if (lineType == LineType.QUAD_BEZIER) {
                                // Position handle at midpoint of line for natural curve.
                                handleX = vx + (lineWidth * 0.5f)
                                handleY = vy + (lineHeight * 0.5f)

                                // Create new QuadBezier and select it.
                                selectedLine = QuadBezier(ex, ey, handleX, handleY)
                            } else {
                                // Create cubic bezier with handles at 1/3 and 2/3 positions.

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
                                    ex,
                                    ey,
                                    handleX,
                                    handleY,
                                    secondHandleX,
                                    secondHandleY,
                                )
                            }
                        } else {
                            // Create new simple line and select it.
                            selectedLine = Line(ex, ey)
                        }

                        isNewLineDrawn = true

                        // Store last point of current bezier to variables.
                        bx = ex
                        by = ey

                        lvx = ex
                        lvy = ey

                        pointCounter++

                        // Push the newly create line to lines holder.
                        lines.push(selectedLine)
                    }

                    // If line is close to first point that user touched,
                    // close the path.
                    if (shouldClosePath(
                            ex,
                            ey
                        ) && currentHandleSelected != Handle.FIRST_POINT_HANDLE
                    ) {
                        finalizeLine()
                        closePath()
                    }
                }
            }
            // Reset selection state flags.
            isOtherLinesSelected = false
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Determines which path elements are near the specified coordinates.
     * Handles selection logic for different types of handles and anchor points.
     */
    protected fun findLines(lastX: Float, lastY: Float) {
        // Calculate touch range adjusted for current zoom level.
        canvasMatrix.invert(mappingMatrix)
        val finalRange = mappingMatrix.mapRadius(handleTouchRange)

        // Figure out which handle in a line user has selected.
        // Some handles are specific to one or two type of line and
        // others might be for each type of them.
        var nearest = finalRange

        // Reset selected handle.
        currentHandleSelected = Handle.NONE

        // Check if end handle of current line is touched.
        if (GestureUtils.isNearTargetPoint(
                lastX,
                lastY,
                bx,
                by,
                handleTouchRange
            )
        ) {
            (abs(bx - lastX) + abs(by - lastY)).let {
                if (it < nearest) {
                    nearest = it
                    currentHandleSelected = Handle.END_HANDLE
                }
            }
        }

        // Check if first bezier handle is touched.
        if (GestureUtils.isNearTargetPoint(
                lastX,
                lastY,
                handleX,
                handleY,
                finalRange
            )
        ) {
            (abs(handleX - lastX) + abs(handleY - lastY)).let {
                if (it < nearest) {
                    nearest = it
                    currentHandleSelected = Handle.FIRST_BEZIER_HANDLE
                }
            }
        }

        // Check if first point of path is touched.
        if (GestureUtils.isNearTargetPoint(
                lastX,
                lastY,
                firstX,
                firstY,
                finalRange
            ) && (pointCounter == 1 || lines.indexOf(selectedLine) == 0)
        ) {
            (abs(firstX - lastX) + abs(firstY - lastY)).let {
                if (it < nearest) {
                    nearest = it
                    currentHandleSelected = Handle.FIRST_POINT_HANDLE
                }
            }
        }

        // Check if second bezier handle is touched (cubic bezier only).
        if (selectedLine is CubicBezier && (GestureUtils.isNearTargetPoint(
                lastX,
                lastY,
                secondHandleX,
                secondHandleY,
                finalRange
            ))
        ) {
            (abs(secondHandleX - lastX) + abs(secondHandleY - lastY)).let {
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
                    lastX,
                    lastY,
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

        // Clear selection if path is closed and no handle selected.
        if (currentHandleSelected == Handle.NONE && isPathClose) {
            selectedLine = null
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Finalizes the current line being drawn and prepares for the next line.
     * Updates the drawing state to continue path construction.
     */
    protected fun finalizeLine() {
        if (lines.indexOf(selectedLine) == lines.size - 1) {
            // If this is the last line, use its end point as next start.
            vx = bx
            vy = by
        } else {
            // Otherwise, use the stored continuation point.
            vx = lvx
            vy = lvy
        }
        isNewLineDrawn = false
    }

    /**
     * Closes the current path and activates the marching ants animation.
     * Completes the path construction process.
     */
    protected fun closePath() {
        isNewLineDrawn = false
        // Connect the last line to the starting point.
        lines.lastElement().run {
            epx = firstX
            epy = firstY
        }
        // Start marching ants animation to indicate closed path.
        pathEffectAnimator.start()
        isPathClose = true
    }

    /**
     * Determines if the current touch position should trigger path closure.
     * Checks proximity to starting point and ensures minimum path complexity.
     */
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
        )) && pointCounter > 2 // Require at least 3 points for closure.
    }

    /**
     * Creates a copy of the current path for external use.
     * @return A new Path object containing the current path data.
     */
    fun getPathCopy(): Path {
        val cPath = Path()
        drawLinesIntoPath(cPath)
        return cPath
    }

    /**
     * Stops the marching ants animation and cleans up animation resources.
     * Called when animation is no longer needed.
     */
    protected fun cancelAnimation() {
        if (pathEffectAnimator.isRunning || pathEffectAnimator.isStarted) {
            linesPaint.pathEffect = null
            pathEffectAnimator.cancel()
        }
    }

    /**
     * Renders all path lines into the specified Path object.
     * Builds the complete path from individual line segments.
     */
    protected fun drawLinesIntoPath(targetPath: Path) {
        // Move the allocation path to first point.
        targetPath.moveTo(firstX, firstY)

        // Draw lines in stack of lines one by one relative to each other.
        lines.forEach {
            it.putIntoPath(targetPath)
        }
    }

    /**
     * Resets the pen tool to its initial state.
     * Clears all path data and prepares for new path creation.
     */
    override fun resetPaint() {
        selectedLine = null
        lines.clear()
        path.reset()

        isNewLineDrawn = false
        isPathClose = false

        pointCounter = 0

        // Stop animations and clean up resources.
        cancelAnimation()

        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Renders the complete path with all visual elements.
     * Draws the path, anchor points, handles, and guide lines.
     */
    override fun draw(canvas: Canvas) {
        canvas.run {
            // Build and draw the main path.
            drawLinesIntoPath(path)
            drawPath(path, linesPaint)

            // Reset path copy to release memory.
            path.rewind()

            // Draw bezier curve helper elements if applicable.
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

            // Draw end point of currently selected line.
            selectedLine?.let { line ->
                drawCircle(line.epx, line.epy, circlesRadius, circlesPaint)
            }

            // Draw circle if it's first point that user touches so it will be visible that user
            // has touch the first point.
            if (pointCounter == 1 || lines.indexOf(selectedLine) == 0) {
                // Draw first point circle.
                drawCircle(firstX, firstY, circlesRadius, circlesPaint)
            }

            // Draw unselected anchor points in different color.
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
            // Restore original color for selected elements.
            circlesPaint.color = circlesColor
        }
    }

    /**
     * Called when the active layer changes.
     * Updates the tool's reference to the current working layer.
     */
    override fun onLayerChanged(layer: PaintLayer?) {
        selectedLayer = layer
    }

    /**
     * Indicates that this tool doesn't require touch slope calculation.
     * Path creation works with discrete point placement rather than continuous drawing.
     */
    override fun doesNeedTouchSlope(): Boolean {
        return false
    }

    /**
     * Enumeration of different handle types that can be selected and manipulated.
     * Each handle type provides different editing capabilities.
     */
    protected enum class Handle {
        /** No handle is currently selected. */
        NONE,

        /** End point of a line segment. */
        END_HANDLE,

        /** First (or only) bezier control handle. */
        FIRST_BEZIER_HANDLE,

        /** Second bezier control handle (cubic bezier only). */
        SECOND_BEZIER_HANDLE,

        /** Starting point of the entire path. */
        FIRST_POINT_HANDLE
    }

    /**
     * A class that represents the type of line that is going to be drawn in [PenSelector].
     */
    enum class LineType {
        /** Straight line segment. */
        NORMAL,

        /** Quadratic bezier curve with single control point. */
        QUAD_BEZIER,

        /** Cubic bezier curve with two control points. */
        CUBIC_BEZIER
    }

    /**
     * Base class representing a line segment in the path.
     * Provides basic functionality for line storage and transformation.
     */
    open class Line(
        var epx: Float,
        var epy: Float,
    ) {

        /** Reusable array for coordinate transformations to avoid allocations. */
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

        /**
         * Applies matrix transformation to the line coordinates.
         * @param matrix Transformation matrix to apply.
         */
        open fun transform(matrix: Matrix) {
            floatArrayPointsHolder[0] = epx
            floatArrayPointsHolder[1] = epy

            matrix.mapPoints(floatArrayPointsHolder)

            epx = floatArrayPointsHolder[0]
            epy = floatArrayPointsHolder[1]
        }
    }

    /**
     * Quadratic bezier curve implementation with single control handle.
     * Provides smooth curves with simple control interface.
     */
    class QuadBezier(
        epx: Float,
        epy: Float,
        var handleX: Float,
        var handleY: Float
    ) : Line(epx, epy) {

        /**
         * Draws quadratic bezier curve into the path.
         * @param path Target path for the curve.
         */
        override fun putIntoPath(path: Path) {
            path.quadTo(handleX, handleY, epx, epy)
        }

        /**
         * Applies matrix transformation to both end point and control handle.
         * @param matrix Transformation matrix to apply.
         */
        override fun transform(matrix: Matrix) {
            super.transform(matrix)
            floatArrayPointsHolder[2] = handleX
            floatArrayPointsHolder[3] = handleY
            matrix.mapPoints(floatArrayPointsHolder)

            handleX = floatArrayPointsHolder[2]
            handleY = floatArrayPointsHolder[3]
        }
    }

    /**
     * Cubic bezier curve implementation with dual control handles.
     * Provides maximum flexibility for complex curve shapes.
     */
    class CubicBezier(
        epx: Float,
        epy: Float,
        var firstHandleX: Float,
        var firstHandleY: Float,
        var secondHandleX: Float,
        var secondHandleY: Float
    ) : Line(epx, epy) {

        /**
         * Draws cubic bezier curve into the path.
         * @param path Target path for the curve.
         */
        override fun putIntoPath(path: Path) {
            path.cubicTo(firstHandleX, firstHandleY, secondHandleX, secondHandleY, epx, epy)
        }

        /**
         * Applies matrix transformation to end point and both control handles.
         * @param matrix Transformation matrix to apply.
         */
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

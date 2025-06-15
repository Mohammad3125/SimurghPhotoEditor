package ir.baboomeh.photolib.components.paint.painters.transform

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toRectF
import androidx.core.graphics.withSave
import ir.baboomeh.photolib.R
import ir.baboomeh.photolib.components.paint.Painter
import ir.baboomeh.photolib.components.paint.painters.transform.TransformTool.TransformableAlignment.BOTTOM
import ir.baboomeh.photolib.components.paint.painters.transform.TransformTool.TransformableAlignment.CENTER
import ir.baboomeh.photolib.components.paint.painters.transform.TransformTool.TransformableAlignment.HORIZONTAL
import ir.baboomeh.photolib.components.paint.painters.transform.TransformTool.TransformableAlignment.LEFT
import ir.baboomeh.photolib.components.paint.painters.transform.TransformTool.TransformableAlignment.RIGHT
import ir.baboomeh.photolib.components.paint.painters.transform.TransformTool.TransformableAlignment.TOP
import ir.baboomeh.photolib.components.paint.painters.transform.TransformTool.TransformableAlignment.VERTICAL
import ir.baboomeh.photolib.components.paint.paintview.PaintLayer
import ir.baboomeh.photolib.utils.dp
import ir.baboomeh.photolib.utils.gesture.GestureUtils
import ir.baboomeh.photolib.utils.gesture.TouchData
import ir.baboomeh.photolib.utils.history.HistoryState
import ir.baboomeh.photolib.utils.history.handlers.StackHistoryHandler
import ir.baboomeh.photolib.utils.matrix.MananMatrix
import java.util.LinkedList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * A comprehensive transformation tool that provides interactive manipulation capabilities for transformable objects.
 * This tool handles scaling, rotation, translation, and provides smart guidelines for precise object positioning.
 *
 * The TransformTool is the core component for object manipulation in the paint system, offering:
 * - Multi-touch gesture support for transformations (scaling, rotation, translation)
 * - Smart guidelines for automatic alignment assistance between objects
 * - Rotation snapping to specific degrees for precise positioning
 * - Complete history management with undo/redo functionality
 * - Layer ordering operations (bring to front, send to back, etc.)
 * - Free transform mode for advanced corner-by-corner manipulation
 * - Visual feedback with transformation bounds and handles
 * - Support for multiple transformable objects simultaneously
 *
 * The tool integrates with the paint layer system and provides callbacks for selection events,
 * making it suitable for complex editing applications that require precise object manipulation.
 *
 * @param context The Android context used for resource access, measurements, and drawable loading.
 */
class TransformTool(context: Context) : Painter(), Transformable.OnInvalidate {

    /** The currently selected paint layer for rendering operations. */
    private var selectedLayer: PaintLayer? = null

    /** Paint object used for drawing transformation bounds around selected objects. */
    private val boundPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = context.dp(2)
        }

    /**
     * The stroke width for transformation bounds in pixels.
     * Setting this value automatically updates the paint and triggers invalidation for immediate visual feedback.
     */
    var boundStrokeWidth = boundPaint.strokeWidth
        set(value) {
            field = value
            boundPaint.strokeWidth = field
            onInvalidate()
        }

    /**
     * The color for transformation bounds.
     * Setting this value automatically updates the paint and triggers invalidation for immediate visual feedback.
     */
    var boundColor = boundPaint.color
        set(value) {
            field = value
            boundPaint.color = field
            onInvalidate()
        }


    /** The drawable used for transformation handles (corner and edge controls) that users can drag. */
    var handleDrawable: Drawable = ResourcesCompat.getDrawable(
        context.resources,
        R.drawable.defualt_transform_tool_handles,
        null
    )!!

    /** The touch range in pixels for detecting handle interactions and determining touch sensitivity. */
    var touchRange = context.dp(24)

    /** Matrix used for coordinate mappings and transformations between different coordinate systems. */
    private val mappingMatrix by lazy {
        MananMatrix()
    }

    /** Array holding the base corner points of a transformable object (8 values: x1,y1,x2,y2,x3,y3,x4,y4). */
    private val basePoints by lazy {
        FloatArray(8)
    }

    /** Array holding mapped mesh points for drawing after transformation calculations. */
    private val mappedMeshPoints by lazy {
        FloatArray(8)
    }

    /** Array holding mapped points for size change handles after transformation calculations. */
    private val mappedBaseSizeChangePoints by lazy {
        FloatArray(8)
    }

    /** Temporary array for coordinate calculations to avoid object allocation during operations. */
    private val cc by lazy {
        FloatArray(8)
    }

    /** Temporary array for holding single point coordinates during transformations. */
    private val pointHolder by lazy {
        FloatArray(2)
    }

    /** Rectangle representing the bounds of the target component being transformed. */
    private val targetComponentBounds by lazy {
        RectF()
    }

    /** Temporary rectangle for various calculations to avoid object allocation. */
    private val tempRect by lazy {
        RectF()
    }

    /** Canvas used for final rendering operations when applying transformations to layers. */
    private val finalCanvas by lazy {
        Canvas()
    }

    /**
     * Enables free transform mode, allowing individual corner manipulation for advanced transformations.
     * When disabled, transformations maintain aspect ratio and shape consistency.
     * When enabled, each corner can be moved independently for perspective-like effects.
     */
    var isFreeTransform = false
        set(value) {
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    /** Index of the first selected corner for transformation (-1 if none selected). */
    private var firstSelectedIndex = -1

    /** Index of the second selected corner for transformation (-1 if none selected). */
    private var secondSelectedIndex = -1

    /** Index of the third selected corner for transformation (-1 if none selected). */
    private var thirdSelectedIndex = -1

    /** Index of the fourth selected corner for transformation (-1 if none selected). */
    private var forthSelectedIndex = -1

    /** Index of the first size change handle (-1 if none selected). */
    private var firstSizeChangeIndex = -1

    /** Index of the second size change handle (-1 if none selected). */
    private var secondSizeChangeIndex = -1

    /** Flag indicating if movement should be restricted to X-axis only during transformations. */
    private var isOnlyMoveX = false

    /** Last recorded X coordinate for transformation calculations and delta computations. */
    private var lastX = 0f

    /** Last recorded Y coordinate for transformation calculations and delta computations. */
    private var lastY = 0f

    /** The bounds rectangle for clipping operations and coordinate system boundaries. */
    private lateinit var bounds: Rect

    /** The main transformation matrix for coordinate system transformations. */
    private lateinit var matrix: MananMatrix

    /** Matrix for fitting content inside bounds with proper scaling and positioning. */
    private lateinit var fitMatrix: MananMatrix

    /** Internal list of child transformable objects managed by this tool. */
    private val _children = LinkedList<Child>()

    /**
     * Public read-only access to the list of transformable children.
     * This provides external access to all objects currently managed by the transform tool.
     *
     * @return Immutable list of transformable objects managed by this tool.
     */
    val children: List<Transformable>
        get() {
            return _children.map { it.transformable }
        }

    /** When true, prevents any transformation operations on objects, effectively locking them in place. */
    var isTransformationLocked = false

    /**
     * Controls visibility of transformation bounds.
     * When false, bounds are not drawn around selected objects.
     */
    var isBoundsEnabled = true
        set(value) {
            field = value
            onInvalidate()
        }

    /** Stores the initial state of a child for history operations and undo functionality. */
    private var initialChildState: Child? = null

    /**
     * The currently selected child object for transformations.
     * Setting this automatically creates a backup for history operations to enable undo functionality.
     */
    private var _selectedChild: Child? = null
        set(value) {
            field = value
            initialChildState = value?.clone(true)
        }

    /**
     * Public access to the currently selected transformable object.
     * This provides external access to the object currently being manipulated.
     *
     * @return The selected transformable object or null if none is selected.
     */
    val selectedChild: Transformable?
        get() {
            return _selectedChild?.transformable
        }

    /** Paint object used for drawing smart guidelines that assist with object alignment. */
    private val smartGuidePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = context.dp(2)
            color = Color.MAGENTA
        }
    }

    var smartGuidelineStrokeWidth = smartGuidePaint.strokeWidth
        set(value) {
            field = value
            smartGuidePaint.strokeWidth = field
            onInvalidate()
        }

    var smartGuidelineColor = smartGuidePaint.color
        set(value) {
            field = value
            smartGuidePaint.color = field
            onInvalidate()
        }

    /**
     * Holds lines for smart guidelines.
     */
    private val smartGuidelineHolder = arrayListOf<Float>()

    /** Container for determining which guidelines should be drawn as dashed lines. */
    private val smartGuidelineDashedLine = arrayListOf<Boolean>()

    /** Bitwise flags controlling which smart guidelines are enabled for detection and rendering. */
    private var smartGuidelineFlags: Int = 0

    /** Array of target degrees for rotation smart guidelines (null if disabled). */
    private var smartRotationDegreeHolder: FloatArray? = null

    /** Original rotation degree holder for external access (excludes internal 360-degree addition). */
    private var originalRotationHolder: FloatArray? = null

    /** Array holding coordinates for the smart rotation guideline (x1, y1, x2, y2). */
    private var smartRotationLineHolder = FloatArray(4)

    /** The acceptable distance in pixels for triggering smart guideline detection. */
    var acceptableDistanceForSmartGuideline = context.dp(1)

    /**
     * The degree range for smart rotation guideline detection.
     * Objects will snap to target rotations when within this range.
     *
     * @throws IllegalStateException if value is less than 0 or greater than 360.
     */
    var rangeForSmartRotationGuideline = 2f
        set(value) {
            if (value < 0f || value > 360) throw IllegalStateException("this value should not be less than 0 or greater than 360")
            field = value
        }

    /** Path effect for rendering dashed smart guidelines. */
    private val smartGuideLineDashedPathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)

    /** Callback invoked when a child object is selected, providing access to the selected object and initialization state. */
    var onChildSelected: ((Transformable, isInitialization: Boolean) -> Unit)? = null

    /** Callback invoked when a child object is deselected. */
    var onChildDeselected: (() -> Unit)? = null

    init {
        // Initialize with stack-based history handler for undo/redo functionality.
        historyHandler = StackHistoryHandler()
    }

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)

        matrix = transformationMatrix
        fitMatrix = fitInsideMatrix
        bounds = clipBounds

        _children.forEach { child ->
            initializeChild(child, shouldCalculateBounds = true)
        }
    }

    private fun initializeChild(
        child: Child,
        isSelectChild: Boolean = false,
        shouldCalculateBounds: Boolean,
        shouldStickToTargetRect: Boolean = true
    ) {
        child.apply {
            transformable.onInvalidateListener = this@TransformTool

            if (isSelectChild && shouldCalculateBounds) {
                transformable.getBounds(targetComponentBounds)
            }

            if (!isSelectChild) {
                transformable.getBounds(targetComponentBounds)

                transformationMatrix.reset()
                polyMatrix.reset()

                if (shouldStickToTargetRect && targetRect != null) {
                    changeMatrixToMatchRect(child, targetRect)
                } else {
                    transformationMatrix.setRectToRect(
                        targetComponentBounds,
                        bounds.toRectF(),
                        Matrix.ScaleToFit.CENTER
                    )
                }
            }

            val w = targetComponentBounds.width()
            val h = targetComponentBounds.height()

            val wh = w * 0.5f
            val hh = h * 0.5f

            basePoints[0] = 0f
            basePoints[1] = 0f
            basePoints[2] = w
            basePoints[3] = 0f
            basePoints[4] = 0f
            basePoints[5] = h
            basePoints[6] = w
            basePoints[7] = h

            baseSizeChangeArray[0] = wh
            baseSizeChangeArray[1] = 0f
            baseSizeChangeArray[2] = w
            baseSizeChangeArray[3] = hh
            baseSizeChangeArray[4] = wh
            baseSizeChangeArray[5] = h
            baseSizeChangeArray[6] = 0f
            baseSizeChangeArray[7] = hh

            if (!isSelectChild) {
                basePoints.copyInto(meshPoints)
            }

            child.mergeMatrices()
        }
    }

    private fun changeMatrixToMatchRect(child: Child, rect: RectF) {
        child.apply {
            transformable.getBounds(targetComponentBounds)

            transformationMatrix.setRectToRect(
                targetComponentBounds,
                rect,
                Matrix.ScaleToFit.CENTER
            )

            child.mergeMatrices()

            onInvalidate()
        }
    }

    override fun onMoveBegin(touchData: TouchData) {
        _selectedChild?.let {
            selectIndexes(it, touchData.ex, touchData.ey)
        }
    }

    private fun selectIndexes(child: Child, ex: Float, ey: Float) {

        val range = touchRange / matrix.getRealScaleX()

        child.baseSizeChangeArray.copyInto(cc)
        child.mapMeshPoints(cc)

        var nearest = range

        firstSelectedIndex = -1
        secondSelectedIndex = -1
        thirdSelectedIndex = -1
        forthSelectedIndex = -1
        firstSizeChangeIndex = -1
        secondSizeChangeIndex = -1

        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[0],
                cc[1],
                range
            )
        ) {
            (abs(ex - cc[0]) + abs(ey - cc[1])).let {
                if (it < nearest) {
                    nearest = it
                    firstSelectedIndex = 0
                    secondSelectedIndex = 1
                    thirdSelectedIndex = 2
                    forthSelectedIndex = 3
                    firstSizeChangeIndex = 0
                    secondSizeChangeIndex = 1
                    isOnlyMoveX = false
                }
            }

        }
        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[2],
                cc[3],
                range
            )
        ) {
            (abs(ex - cc[2]) + abs(ey - cc[3])).let {
                if (it < nearest) {
                    nearest = it
                    firstSelectedIndex = 2
                    secondSelectedIndex = 3
                    thirdSelectedIndex = 6
                    forthSelectedIndex = 7
                    firstSizeChangeIndex = 2
                    secondSizeChangeIndex = 3
                    isOnlyMoveX = true
                }
            }

        }
        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[4],
                cc[5],
                range
            )
        ) {
            (abs(ex - cc[4]) + abs(ey - cc[5])).let {
                if (it < nearest) {
                    nearest = it
                    firstSelectedIndex = 6
                    secondSelectedIndex = 7
                    thirdSelectedIndex = 4
                    forthSelectedIndex = 5
                    firstSizeChangeIndex = 4
                    secondSizeChangeIndex = 5
                    isOnlyMoveX = false
                }
            }

        }
        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[6],
                cc[7],
                range
            )
        ) {
            (abs(ex - cc[6]) + abs(ey - cc[7])).let {
                if (it < nearest) {
                    nearest = it
                    firstSelectedIndex = 4
                    secondSelectedIndex = 5
                    thirdSelectedIndex = 0
                    forthSelectedIndex = 1
                    firstSizeChangeIndex = 6
                    secondSizeChangeIndex = 7
                    isOnlyMoveX = true
                }
            }
        }

        if (firstSizeChangeIndex > -1 && secondSizeChangeIndex > -1) {
            child.map(cc)
            lastX = cc[firstSizeChangeIndex]
            lastY = cc[secondSizeChangeIndex]
        }

        if (!isFreeTransform) {
            return
        }

        basePoints.copyInto(cc)
        child.mapMeshPoints(cc)

        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[0],
                cc[1],
                range
            )
        ) {
            (abs(ex - cc[0]) + abs(ey - cc[1])).let {
                if (it < nearest) {
                    firstSelectedIndex = 0
                    secondSelectedIndex = 1
                }
            }
        }
        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[2],
                cc[3],
                range
            )
        ) {
            (abs(ex - cc[2]) + abs(ey - cc[3])).let {
                if (it < nearest) {
                    firstSelectedIndex = 2
                    secondSelectedIndex = 3
                }
            }
        }
        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[4],
                cc[5],
                range
            )
        ) {
            (abs(ex - cc[4]) + abs(ey - cc[5])).let {
                if (it < nearest) {
                    firstSelectedIndex = 4
                    secondSelectedIndex = 5
                }
            }
        }
        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[6],
                cc[7],
                range
            )
        ) {
            (abs(ex - cc[6]) + abs(ey - cc[7])).let {
                if (it < nearest) {
                    firstSelectedIndex = 6
                    secondSelectedIndex = 7
                }
            }
        }
    }

    override fun onMove(touchData: TouchData) {
        if (isTransformationLocked) {
            return
        }
        _selectedChild?.apply {
            mapMeshPoints(touchData.ex, touchData.ey)

            if (firstSelectedIndex > -1 && secondSelectedIndex > -1) {
                if (thirdSelectedIndex > -1 && forthSelectedIndex > -1) {
                    if (isFreeTransform) {
                        var diff = pointHolder[0] - lastX
                        meshPoints[firstSelectedIndex] += diff
                        meshPoints[thirdSelectedIndex] += diff
                        diff = pointHolder[1] - lastY
                        meshPoints[secondSelectedIndex] += diff
                        meshPoints[forthSelectedIndex] += diff
                    } else if (isOnlyMoveX) {
                        val diff = pointHolder[0] - lastX
                        meshPoints[firstSelectedIndex] += diff
                        meshPoints[thirdSelectedIndex] += diff
                    } else {
                        val diff = pointHolder[1] - lastY
                        meshPoints[secondSelectedIndex] += diff
                        meshPoints[forthSelectedIndex] += diff
                    }

                    lastX = pointHolder[0]
                    lastY = pointHolder[1]

                } else {
                    meshPoints[firstSelectedIndex] = pointHolder[0]
                    meshPoints[secondSelectedIndex] = pointHolder[1]
                }
            } else if (_selectedChild?.isPointInChildRect(touchData) == true) {
                mappingMatrix.setTranslate(touchData.dx, touchData.dy)
                onTransformed(mappingMatrix)
            }

            makePolyToPoly()
            onInvalidate()
        }
    }

    private fun Child.mapFinalPointsForDraw() {
        mappingMatrix.set(transformationMatrix)

        meshPoints.copyInto(mappedMeshPoints)
        mappingMatrix.mapPoints(mappedMeshPoints)

        mappingMatrix.preConcat(polyMatrix)
        baseSizeChangeArray.copyInto(mappedBaseSizeChangePoints)
        mappingMatrix.mapPoints(mappedBaseSizeChangePoints)
    }

    override fun onMoveEnded(touchData: TouchData) {
        if (firstSelectedIndex == -1 && secondSelectedIndex == -1 && firstSizeChangeIndex == -1 && secondSizeChangeIndex == -1) {

            val lastSelected = _selectedChild
            _selectedChild = null

            _children.forEach { child ->
                if (child.isPointInChildRect(touchData)) {
                    _selectedChild = child
                }
            }

            if (lastSelected !== _selectedChild) {
                _selectedChild?.let {
                    it.select(true)
                    onChildSelected?.invoke(it.transformable, false)
                }
            }

            if (_selectedChild == null) {
                onChildDeselected?.invoke()
            }

            onInvalidate()

        } else {
            saveState(_selectedChild.createState())
        }

        clearRotationSmartGuidelines()

        firstSelectedIndex = -1
        secondSelectedIndex = -1
        thirdSelectedIndex = -1
        forthSelectedIndex = -1
        firstSizeChangeIndex = -1
        secondSizeChangeIndex = -1

    }

    private fun Child.isPointInChildRect(touchData: TouchData): Boolean {
        mapMeshPoints(touchData.ex, touchData.ey)

        val x = pointHolder[0]
        val y = pointHolder[1]

        tempRect.calculateMaximumRect(meshPoints)

        return (x.coerceIn(
            tempRect.left,
            tempRect.right
        ) == x && y.coerceIn(
            tempRect.top,
            tempRect.bottom
        ) == y)
    }

    private fun makePolyToPoly() {
        _selectedChild!!.apply {
            polyMatrix.setPolyToPoly(basePoints, 0, meshPoints, 0, 4)
        }
    }

    private fun Child.mapMeshPoints(ex: Float, ey: Float) {
        pointHolder[0] = ex
        pointHolder[1] = ey
        map(pointHolder)
    }

    private fun Child.map(array: FloatArray) {
        transformationMatrix.invert(mappingMatrix)
        mappingMatrix.mapPoints(array)
    }

    private fun Child.mapMeshPoints(array: FloatArray) {
        polyMatrix.mapPoints(array)
        transformationMatrix.mapPoints(array)
    }

    /**
     * Maps touch vectors for coordinate system transformation.
     * This method converts touch deltas to the appropriate coordinate space.
     *
     * @param touchData The touch data containing delta values.
     */
    private fun mapVectors(touchData: TouchData) {
        pointHolder[0] = touchData.dx
        pointHolder[1] = touchData.dy
        matrix.invert(mappingMatrix)
        mappingMatrix.mapVectors(pointHolder)
    }

    /**
     * Renders all child objects and their transformation UI elements to the canvas.
     * This method handles the complete rendering pipeline including bounds, handles, and guidelines.
     *
     * @param canvas The canvas to draw on.
     */
    override fun draw(canvas: Canvas) {
        _children.forEach { child ->

            child.mergeMatrices(false)

            drawChild(canvas, child)

            val mergedCanvasMatrices = mergeCanvasMatrices()
            val sx = mergedCanvasMatrices.getRealScaleX()
            val currentBoundWidth = boundPaint.strokeWidth
            val currentGuideWidth = smartGuidePaint.strokeWidth
            val finalBoundWidth = currentBoundWidth * sx
            val finalGuidelineWidth = currentGuideWidth * sx

            boundPaint.strokeWidth = finalBoundWidth
            smartGuidePaint.strokeWidth = finalGuidelineWidth

            if (child === _selectedChild && isBoundsEnabled) {

                child.select()

                canvas.apply {
                    drawLine(
                        mappedMeshPoints[0],
                        mappedMeshPoints[1],
                        mappedMeshPoints[2],
                        mappedMeshPoints[3],
                        boundPaint
                    )

                    drawLine(
                        mappedMeshPoints[4],
                        mappedMeshPoints[5],
                        mappedMeshPoints[6],
                        mappedMeshPoints[7],
                        boundPaint
                    )

                    drawLine(
                        mappedMeshPoints[0],
                        mappedMeshPoints[1],
                        mappedMeshPoints[4],
                        mappedMeshPoints[5],
                        boundPaint
                    )

                    drawLine(
                        mappedMeshPoints[2],
                        mappedMeshPoints[3],
                        mappedMeshPoints[6],
                        mappedMeshPoints[7],
                        boundPaint
                    )
                }

                if (isFreeTransform) {
                    resizeAndDrawDrawable(
                        mappedMeshPoints[0].toInt(),
                        mappedMeshPoints[1].toInt(),
                        canvas
                    )
                    resizeAndDrawDrawable(
                        mappedMeshPoints[2].toInt(),
                        mappedMeshPoints[3].toInt(),
                        canvas
                    )
                    resizeAndDrawDrawable(
                        mappedMeshPoints[4].toInt(),
                        mappedMeshPoints[5].toInt(),
                        canvas
                    )
                    resizeAndDrawDrawable(
                        mappedMeshPoints[6].toInt(),
                        mappedMeshPoints[7].toInt(),
                        canvas
                    )
                }


                resizeAndDrawDrawable(
                    mappedBaseSizeChangePoints[0].toInt(),
                    mappedBaseSizeChangePoints[1].toInt(),
                    canvas
                )
                resizeAndDrawDrawable(
                    mappedBaseSizeChangePoints[2].toInt(),
                    mappedBaseSizeChangePoints[3].toInt(),
                    canvas
                )
                resizeAndDrawDrawable(
                    mappedBaseSizeChangePoints[4].toInt(),
                    mappedBaseSizeChangePoints[5].toInt(),
                    canvas
                )
                resizeAndDrawDrawable(
                    mappedBaseSizeChangePoints[6].toInt(),
                    mappedBaseSizeChangePoints[7].toInt(),
                    canvas
                )


                canvas.drawLines(smartRotationLineHolder, smartGuidePaint)

                for (i in smartGuidelineHolder.indices step 4) {

                    if (smartGuidelineDashedLine[i]) {
                        smartGuidePaint.pathEffect = smartGuideLineDashedPathEffect
                    }

                    canvas.drawLine(
                        smartGuidelineHolder[i],
                        smartGuidelineHolder[i + 1],
                        smartGuidelineHolder[i + 2],
                        smartGuidelineHolder[i + 3],
                        smartGuidePaint
                    )

                    smartGuidePaint.pathEffect = null
                }
            }
            boundPaint.strokeWidth = currentBoundWidth
            smartGuidePaint.strokeWidth = currentGuideWidth
        }
    }

    /**
     * Positions and draws a transformation handle drawable at the specified coordinates.
     * This method centers the drawable on the given point for intuitive handle interaction.
     *
     * @param x The x-coordinate for the handle center.
     * @param y The y-coordinate for the handle center.
     * @param canvas The canvas to draw the handle on.
     */
    private fun resizeAndDrawDrawable(x: Int, y: Int, canvas: Canvas) {
        val hw = handleDrawable.intrinsicWidth / 2
        val hh = handleDrawable.intrinsicHeight / 2
        handleDrawable.setBounds(
            x - hw,
            y - hh,
            hw + x,
            hh + y
        )

        handleDrawable.draw(canvas)
    }

    /**
     * Draws a single child object with its transformation matrix applied.
     * This method handles the matrix concatenation for proper object positioning.
     *
     * @param canvas The canvas to draw on.
     * @param child The child object to draw.
     */
    private fun drawChild(canvas: Canvas, child: Child) {
        canvas.apply {
            withSave {

                concat(mappingMatrix)

                child.transformable.draw(this)

            }

        }
    }

    /**
     * Resets the paint tool by clearing history and all child objects.
     * This method returns the tool to its initial state.
     */
    override fun resetPaint() {
        historyHandler!!.reset()
        initialChildState = null
        _selectedChild = null
        _children.clear()
        onInvalidate()
    }

    /**
     * Called when the active paint layer changes.
     * This method updates the internal layer reference for rendering operations.
     *
     * @param layer The new active paint layer, or null if none is active.
     */
    override fun onLayerChanged(layer: PaintLayer?) {
        selectedLayer = layer
    }

    /**
     * Indicates that this tool handles its own history management.
     * This prevents external history systems from interfering with transformation undo/redo.
     *
     * @return Always returns true.
     */
    override fun doesHandleHistory(): Boolean {
        return true
    }

    /**
     * Determines if this tool should receive gesture events.
     * Gestures are only processed when an object is selected and transformations are not locked.
     *
     * @return True if gestures should be processed, false otherwise.
     */
    override fun doesTakeGestures(): Boolean {
        return _selectedChild != null && !isTransformationLocked
    }

    /**
     * Called when a transformation matrix is applied to the selected object.
     * This method updates the object's transformation and triggers smart guideline detection.
     *
     * @param transformMatrix The transformation matrix to apply.
     */
    override fun onTransformed(transformMatrix: Matrix) {
        if (isTransformationLocked) {
            return
        }
        _selectedChild?.apply {
            transformationMatrix.postConcat(transformMatrix)
            findAllGuidelines()
            mergeMatrices()
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Called when a transformation operation ends.
     * This method saves the current state for undo functionality.
     */
    override fun onTransformEnded() {
        saveState(_selectedChild.createState())
    }

    /**
     * Finds possible guide lines on selected component and other components and populates the line holder if there is
     * any line that could help user.
     * This method detects guide lines on sides of selected component.
     * Sides that are calculated for guide lines include:
     * - Left-Left
     * - Left,Right
     * - Right,Left
     * - Right,Right
     * - Top,Top
     * - Top,Bottom
     * - Bottom,Top
     * - Bottom,Bottom
     * - CenterX
     * - CenterY
     */
    /**
     * Finds possible guide lines on selected component and other components and populates the line holder.
     * This method detects guide lines on sides of selected component including:
     * - Left-Left, Left-Right, Right-Left, Right-Right alignment
     * - Top-Top, Top-Bottom, Bottom-Top, Bottom-Bottom alignment
     * - Center X and Center Y alignment
     */
    private fun findSmartGuideLines() {

        smartGuidelineDashedLine.clear()

        smartGuidelineHolder.clear()

        if (smartGuidelineFlags == 0) {
            return
        }

        _selectedChild?.let { child ->
            // Get flags to determine if we should use corresponding guideline or not.
            val isLeftLeftEnabled = smartGuidelineFlags.and(Guidelines.LEFT_LEFT) != 0
            val isLeftRightEnabled = smartGuidelineFlags.and(Guidelines.LEFT_RIGHT) != 0
            val isRightLeftEnabled = smartGuidelineFlags.and(Guidelines.RIGHT_LEFT) != 0
            val isRightRightEnabled =
                smartGuidelineFlags.and(Guidelines.RIGHT_RIGHT) != 0
            val isTopTopEnabled = smartGuidelineFlags.and(Guidelines.TOP_TOP) != 0
            val isTopBottomEnabled = smartGuidelineFlags.and(Guidelines.TOP_BOTTOM) != 0
            val isBottomTopEnabled = smartGuidelineFlags.and(Guidelines.BOTTOM_TOP) != 0
            val isBottomBottomEnabled =
                smartGuidelineFlags.and(Guidelines.BOTTOM_BOTTOM) != 0
            val isCenterXEnabled = smartGuidelineFlags.and(Guidelines.CENTER_X) != 0
            val isCenterYEnabled = smartGuidelineFlags.and(Guidelines.CENTER_Y) != 0

            child.mergeMatrices(true)
            tempRect.calculateMaximumRect(mappedMeshPoints)

            val floutBounds = bounds.toRectF()
            // Remove selected component from list of children (because we don't need to find smart guideline for
            // selected component which is a undefined behaviour) and then map each bounds of children to get exact
            // location of points and then add page's bounds to get smart guidelines for page too.
            _children.minus(child).map { c ->
                RectF().apply {
                    c.mergeMatrices(true)
                    calculateMaximumRect(mappedMeshPoints)
                }
            }.plus(floutBounds).forEach { childBounds ->

                // Stores total value that selected component should shift in each axis
                var totalToShiftX = 0f
                var totalToShiftY = 0f

                // Calculate distance between two centers in x axis.
                val centerXDiff = childBounds.centerX() - tempRect.centerX()
                val centerXDiffAbs = abs(centerXDiff)

                // Calculate distance between two centers in y axis.
                val centerYDiff = childBounds.centerY() - tempRect.centerY()
                val centerYDiffAbs = abs(centerYDiff)

                // If absolute value of difference two center x was in range of acceptable distance,
                // then store total difference to later shift the component.
                if (centerXDiffAbs <= acceptableDistanceForSmartGuideline && isCenterXEnabled) {
                    totalToShiftX = centerXDiff
                }
                if (centerYDiffAbs <= acceptableDistanceForSmartGuideline && isCenterYEnabled) {
                    totalToShiftY = centerYDiff
                }

                // Calculate distance between two lefts.
                val leftToLeft = childBounds.left - tempRect.left
                val leftToLeftAbs = abs(leftToLeft)

                // Calculate distance between two other component left and selected component right.
                val leftToRight = childBounds.left - tempRect.right
                val leftToRightAbs = abs(leftToRight)

                // Calculate distance between two rights.
                val rightToRight = childBounds.right - tempRect.right
                val rightToRightAbs = abs(rightToRight)

                // Calculate distance between other component right and selected component left.
                val rightToLeft = childBounds.right - tempRect.left
                val rightToLeftAbs = abs(rightToLeft)

                // If left to left of two components was less than left two right and
                // if the lesser value was in acceptable range then set total shift amount
                // in x axis to that value.
                // If we are currently centering in x direction then any of these
                // side should not be calculated or be smart guided.
                if (totalToShiftX != centerXDiff) {
                    if (leftToLeftAbs < leftToRightAbs) {
                        if (leftToLeftAbs <= acceptableDistanceForSmartGuideline && isLeftLeftEnabled) {
                            totalToShiftX = leftToLeft
                        }
                    } else if (leftToRightAbs < leftToLeftAbs) {
                        if (leftToRightAbs <= acceptableDistanceForSmartGuideline && isLeftRightEnabled) {
                            totalToShiftX = leftToRight
                        }
                    }
                    // If right to right of two components was less than right to left of them,
                    // Then check if we haven't set the total shift amount so far, if either we didn't
                    // set any value to shift so far or current difference is less than current
                    // total shift amount, then set total shift amount to the right to right difference.
                    if (rightToRightAbs < rightToLeftAbs) {
                        if (rightToRightAbs <= acceptableDistanceForSmartGuideline && isRightRightEnabled) {
                            if (totalToShiftX == 0f) {
                                totalToShiftX = rightToRight
                            } else if (rightToRightAbs < abs(totalToShiftX)) {
                                totalToShiftX = rightToRight
                            }
                        }
                    } else if (rightToLeftAbs < rightToRightAbs) {
                        if (rightToLeftAbs <= acceptableDistanceForSmartGuideline && isRightLeftEnabled) {
                            if (totalToShiftX == 0f) {
                                totalToShiftX = rightToLeft
                            } else if (rightToLeftAbs < abs(totalToShiftX)) {
                                totalToShiftX = rightToLeft
                            }
                        }
                    }
                }

                val topToTop = childBounds.top - tempRect.top
                val topToTopAbs = abs(topToTop)
                val topToBottom = childBounds.top - tempRect.bottom
                val topToBottomAbs = abs(topToBottom)

                val bottomToBottom = childBounds.bottom - tempRect.bottom
                val bottomToBottomAbs = abs(bottomToBottom)
                val bottomToTop = childBounds.bottom - tempRect.top
                val bottomToTopAbs = abs(bottomToTop)

                if (totalToShiftY != centerYDiff) {
                    if (topToTopAbs < topToBottomAbs) {
                        if (topToTopAbs <= acceptableDistanceForSmartGuideline && isTopTopEnabled) {
                            totalToShiftY = topToTop
                        }
                    } else if (topToBottomAbs < topToTopAbs && isTopBottomEnabled) {
                        if (topToBottomAbs <= acceptableDistanceForSmartGuideline) {
                            totalToShiftY = topToBottom
                        }
                    }

                    if (bottomToBottomAbs < bottomToTopAbs) {
                        if (bottomToBottomAbs <= acceptableDistanceForSmartGuideline && isBottomBottomEnabled) {
                            if (totalToShiftY == 0f) {
                                totalToShiftY = bottomToBottom
                            } else if (bottomToBottomAbs < abs(totalToShiftY)) {
                                totalToShiftY = bottomToBottom
                            }
                        }
                    } else if (bottomToTopAbs < bottomToBottomAbs) {
                        if (bottomToTopAbs <= acceptableDistanceForSmartGuideline && isBottomTopEnabled) {
                            if (totalToShiftY == 0f) {
                                totalToShiftY = bottomToTop
                            } else if (bottomToTopAbs < abs(totalToShiftY)) {
                                totalToShiftY = bottomToTop
                            }
                        }
                    }
                }

                child.transformationMatrix.postTranslate(totalToShiftX, totalToShiftY)
                child.mergeMatrices()
                tempRect.calculateMaximumRect(mappedMeshPoints)

                // Calculate the minimum and maximum amount of two axes
                // because we want to draw a line from leftmost to rightmost
                // and topmost to bottommost component.
                val minTop = min(tempRect.top, childBounds.top)
                val maxBottom = max(tempRect.bottom, childBounds.bottom)

                val minLeft = min(tempRect.left, childBounds.left)
                val maxRight = max(tempRect.right, childBounds.right)

                smartGuidelineHolder.run {

                    val isNotPage = childBounds !== floutBounds

                    // Draw a line on left side of selected component if two lefts are the same
                    // or right of other component is same to left of selected component
                    if (totalToShiftX == leftToLeft || totalToShiftX == rightToLeft) {
                        add(tempRect.left)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(minTop)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(tempRect.left)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(maxBottom)
                        smartGuidelineDashedLine.add(isNotPage)
                    }
                    // Draw a line on right side of selected component if left side of other
                    // component is right side of selected component or two rights are the same.
                    if (totalToShiftX == leftToRight || totalToShiftX == rightToRight) {
                        add(tempRect.right)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(minTop)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(tempRect.right)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(maxBottom)
                        smartGuidelineDashedLine.add(isNotPage)
                    }

                    // Draw a line on other component top if it's top is same as
                    // selected component top or bottom of selected component is same as
                    // top of other component.
                    if (totalToShiftY == topToTop || totalToShiftY == topToBottom) {
                        add(minLeft)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(childBounds.top)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(maxRight)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(childBounds.top)
                        smartGuidelineDashedLine.add(isNotPage)
                    }
                    // Draw a line on other component bottom if bottom of it is same as
                    // selected component's top or two bottoms are the same.
                    if (totalToShiftY == bottomToTop || totalToShiftY == bottomToBottom) {
                        add(minLeft)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(childBounds.bottom)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(maxRight)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(childBounds.bottom)
                        smartGuidelineDashedLine.add(isNotPage)
                    }

                    // Finally draw a line from center of each component to another.
                    if (totalToShiftX == centerXDiff || totalToShiftY == centerYDiff) {
                        if (isNotPage) {
                            add(tempRect.centerX())
                            smartGuidelineDashedLine.add(isNotPage)
                            add(tempRect.centerY())
                            smartGuidelineDashedLine.add(isNotPage)
                            add(childBounds.centerX())
                            smartGuidelineDashedLine.add(isNotPage)
                            add(childBounds.centerY())
                            smartGuidelineDashedLine.add(isNotPage)
                        } else {
                            if (totalToShiftX == centerXDiff) {
                                add(tempRect.centerX())
                                smartGuidelineDashedLine.add(isNotPage)
                                add(floutBounds.top)
                                smartGuidelineDashedLine.add(isNotPage)
                                add(tempRect.centerX())
                                smartGuidelineDashedLine.add(isNotPage)
                                add(floutBounds.bottom)
                                smartGuidelineDashedLine.add(isNotPage)
                            }

                            if (totalToShiftY == centerYDiff) {
                                add(floutBounds.left)
                                smartGuidelineDashedLine.add(isNotPage)
                                add(tempRect.centerY())
                                smartGuidelineDashedLine.add(isNotPage)
                                add(floutBounds.right)
                                smartGuidelineDashedLine.add(isNotPage)
                                add(tempRect.centerY())
                                smartGuidelineDashedLine.add(isNotPage)
                            }
                        }
                    }

                }
            }
        }
    }

    /**
     * Merges canvas transformation matrices for proper coordinate mapping.
     * This method combines fit and transformation matrices for accurate rendering.
     *
     * @return The merged transformation matrix.
     */
    private fun mergeCanvasMatrices(): MananMatrix {
        return mappingMatrix.apply {
            setConcat(fitMatrix, matrix)
            invert(mappingMatrix)
        }
    }

    /**
     * Calculates the maximum bounding rectangle from an array of points.
     * This method finds the extents of a set of transformed points.
     *
     * @param array The array of points (x1,y1,x2,y2,...).
     */
    private fun RectF.calculateMaximumRect(array: FloatArray) {
        val minX =
            min(min(array[0], array[2]), min(array[4], array[6]))

        val minY =
            min(min(array[1], array[3]), min(array[5], array[7]))

        val maxX =
            max(max(array[0], array[2]), max(array[4], array[6]))

        val maxY =
            max(max(array[1], array[3]), max(array[5], array[7]))

        set(minX, minY, maxX, maxY)
    }

    /**
     * Merges all transformation matrices for a child object.
     * This method combines the transformation and poly matrices for final rendering.
     *
     * @param shouldMap Whether to map final points for drawing.
     */
    private fun Child.mergeMatrices(shouldMap: Boolean = true) {
        if (shouldMap) {
            mapFinalPointsForDraw()
        }
        mappingMatrix.set(transformationMatrix)
        mappingMatrix.preConcat(polyMatrix)
    }

    /**
     * Applies all child transformations to the current layer's bitmap.
     * This method permanently renders all transformations to the layer.
     */
    fun applyComponentOnLayer() {

        selectedLayer?.let { layer ->
            finalCanvas.setBitmap(layer.bitmap)

            _children.forEach { child ->
                child.select()
                drawChild(finalCanvas, child)
            }

            sendMessage(PainterMessage.SAVE_HISTORY)
            onInvalidate()

        }
    }

    /**
     * Called when the bounds of the selected child change.
     * This method adjusts the transformation to maintain visual consistency.
     */
    override fun onBoundsChange() {
        _selectedChild?.let { child ->
            tempRect.set(targetComponentBounds)

            child.select(true)

            val tw = targetComponentBounds.width()
            val th = targetComponentBounds.height()

            val lw = tempRect.width()
            val lh = tempRect.height()

            child.apply {

                tempRect.calculateMaximumRect(meshPoints)

                mappingMatrix.apply {
                    setScale(tw / lw, th / lh, tempRect.centerX(), tempRect.centerY())
                    mapPoints(meshPoints)
                }

            }
            makePolyToPoly()
            child.mergeMatrices()
            onInvalidate()
        }
    }

    /**
     * Adds a new transformable child object to the tool.
     * This method creates a new child with default positioning.
     *
     * @param transformable The transformable object to add.
     */
    fun addChild(transformable: Transformable) {
        addChild(transformable, null)
    }

    /**
     * Adds a new transformable child object with a specific target rectangle.
     * This method creates a new child and positions it according to the target rectangle.
     *
     * @param transformable The transformable object to add.
     * @param targetRect The target rectangle for positioning, or null for default positioning.
     */
    fun addChild(transformable: Transformable, targetRect: RectF?) {
        clearSmartGuidelineList()
        clearSmartRotationArray()

        _selectedChild = Child(
            transformable, MananMatrix(), MananMatrix(), FloatArray(8),
            FloatArray(8), targetRect
        )

        val initialChildren = LinkedList(_children)

        _children.add(_selectedChild!!)

        onChildSelected?.invoke(_selectedChild!!.transformable, true)

        if (isInitialized) {
            initializeChild(_selectedChild!!, shouldCalculateBounds = true)
            onInvalidate()
        }

        saveState(_selectedChild.createState(initialChildren))
    }

    /**
     * Moves the selected child object up one layer in the rendering order.
     * This method increases the z-order of the selected object.
     */
    fun bringSelectedChildUp() {
        getSelectedChildIndexAndCompare(_children.lastIndex) { child, selectedChildIndex ->
            swap(selectedChildIndex + 1, selectedChildIndex, child)
        }
    }

    /**
     * Brings the selected child object to the front of the rendering order.
     * This method moves the selected object to the highest z-order.
     */
    fun bringSelectedChildToFront() {
        getSelectedChildIndexAndCompare(_children.lastIndex) { _, selectedChildIndex ->
            bringFromIndexToIndex(selectedChildIndex, _children.lastIndex)
        }
    }

    /**
     * Moves the selected child object down one layer in the rendering order.
     * This method decreases the z-order of the selected object.
     */
    fun bringSelectedChildDown() {
        getSelectedChildIndexAndCompare(0) { child, selectedChildIndex ->
            swap(selectedChildIndex - 1, selectedChildIndex, child)
        }
    }

    /**
     * Sends the selected child object to the back of the rendering order.
     * This method moves the selected object to the lowest z-order.
     */
    fun bringSelectedChildToBack() {
        getSelectedChildIndexAndCompare(0) { _, selectedChildIndex ->
            bringFromIndexToIndex(selectedChildIndex, 0)
        }
    }

    /**
     * Helper method for layer ordering operations that checks bounds and executes operations.
     * This method prevents unnecessary operations when objects are already at the target position.
     *
     * @param compareIndex The index to compare against.
     * @param operation The operation to perform if the comparison allows it.
     */
    private fun getSelectedChildIndexAndCompare(
        compareIndex: Int,
        operation: (child: Child, selectedChildIndex: Int) -> Unit
    ) {
        _selectedChild?.let { child ->
            val selectedChildIndex = _children.indexOf(child)

            if (selectedChildIndex != compareIndex) {
                operation(child, selectedChildIndex)
                onInvalidate()
            }
        }
    }

    /**
     * Swaps two children in the rendering order.
     * This method exchanges the positions of two objects in the z-order.
     *
     * @param firstIndex The index of the first object.
     * @param secondIndex The index of the second object.
     * @param child The child object being moved.
     */
    private fun swap(firstIndex: Int, secondIndex: Int, child: Child) {
        val initialChildren = LinkedList(_children)
        val temp = _children[firstIndex]
        _children[firstIndex] = child
        _children[secondIndex] = temp
        saveState(_selectedChild.createState(initialChildren))
    }

    /**
     * Moves an object from one index to another in the rendering order.
     * This method handles moving objects to the front or back of the z-order.
     *
     * @param fromIndex The current index of the object.
     * @param toIndex The target index for the object.
     */
    private fun bringFromIndexToIndex(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) {
            return
        }

        val initialChildren = LinkedList(_children)

        val temp = _children[fromIndex]
        _children.removeAt(fromIndex)

        if (fromIndex < toIndex) {
            _children.addLast(temp)
        } else {
            _children.addFirst(temp)
        }

        saveState(_selectedChild.createState(initialChildren))
    }

    /**
     * Removes the currently selected child object from the tool.
     * This method deletes the selected object and clears the selection.
     */
    fun removeSelectedChild() {
        _selectedChild?.apply {
            val initialChildren = LinkedList(_children)
            _children.remove(this)
            saveState(createState(initialChildren))
            _selectedChild = null
            onInvalidate()
        }
    }

    /**
     * Removes a child object at the specified index.
     * This method deletes the object at the given position in the children list.
     *
     * @param index The index of the child to remove.
     */
    fun removeChildAt(index: Int) {
        val initialChildren = LinkedList(_children)
        _children.removeAt(index)
        saveState(_selectedChild.createState(initialChildren))
        onInvalidate()
    }

    /**
     * Removes all child objects from the tool.
     * This method clears all managed objects and resets the selection.
     */
    fun removeAllChildren() {
        val initialChildren = LinkedList(_children)
        _children.clear()
        _selectedChild = null
        saveState(_selectedChild.createState(initialChildren))
        onInvalidate()
    }

    /**
     * Selects a child object and initializes it for manipulation.
     * This method prepares the child for transformation operations.
     *
     * @param shouldCalculateBounds Whether to recalculate the object's bounds.
     */
    private fun Child.select(shouldCalculateBounds: Boolean = false) {
        initializeChild(this, true, shouldCalculateBounds)
    }

    /**
     * Sets flags of smart guideline to customize needed smart guidelines,
     * for example if user sets [Guidelines.CENTER_X] and [Guidelines.BOTTOM_BOTTOM], only these
     * guidelines would be detected.
     * If [Guidelines.ALL] is set then all flags would bet set to 1 indicating they are all enabled.
     * ### NOTE: Flags should OR each other to create desired output:
     *      setFlags(LEFT_LEFT.or(RIGHT_LEFT).or(CENTER_X)))
     *      setFlags(LEFT_LEFT | RIGHT_LEFT | CENTER_X)
     * @see Guidelines
     */
    /**
     * Sets flags for smart guideline detection and rendering.
     * This method controls which types of alignment guidelines are active.
     *
     * Guidelines can be combined using bitwise OR operations:
     * ```
     * setSmartGuidelineFlags(Guidelines.LEFT_LEFT or Guidelines.CENTER_X)
     * ```
     *
     * @param flags Bitwise combination of guideline flags from Guidelines class.
     * @see Guidelines
     */
    fun setSmartGuidelineFlags(flags: Int) {
        // If flag has the ALL in it then store the maximum int value in flag holder to indicate
        // that all of flags has been set, otherwise set it to provided flags.
        smartGuidelineFlags = if (flags.and(Guidelines.ALL) != 0) Int.MAX_VALUE else flags
    }

    /**
     * Clears all currently visible smart guidelines.
     * This method removes guideline visuals without affecting the detection settings.
     */
    fun clearSmartGuidelines() {
        clearSmartGuidelineList()
        onInvalidate()
    }

    /**
     * Clears the internal smart guideline coordinate lists.
     * This method removes stored guideline data for rendering.
     */
    private fun clearSmartGuidelineList() {
        smartGuidelineDashedLine.clear()
        smartGuidelineHolder.clear()
    }

    /**
     * Resets smart guideline detection flags to disable all guidelines.
     * This method turns off automatic guideline detection completely.
     */
    fun resetSmartGuidelineFlag() {
        smartGuidelineFlags = 0
    }

    /**
     * Clears rotation smart guidelines from the display.
     * This method removes rotation snap indicators without affecting snap settings.
     */
    fun clearRotationSmartGuidelines() {
        clearSmartRotationArray()
        onInvalidate()
    }

    /**
     * Clears the internal rotation guideline coordinate array.
     * This method removes stored rotation guideline data for rendering.
     */
    private fun clearSmartRotationArray() {
        smartRotationLineHolder[0] = 0f
        smartRotationLineHolder[1] = 0f
        smartRotationLineHolder[2] = 0f
        smartRotationLineHolder[3] = 0f
    }

    /**
     * Returns smart guidelines flags.
     * @see setSmartGuidelineFlags
     * @see resetSmartGuidelineFlag
     */
    /**
     * Gets the current smart guideline detection flags.
     * This method returns the bitwise combination of active guideline types.
     *
     * @return The current guideline flags.
     * @see setSmartGuidelineFlags
     * @see resetSmartGuidelineFlag
     */
    fun getSmartGuidelineFlags(): Int = smartGuidelineFlags


    /**
     * Finds smart guidelines for rotation if [smartRotationDegreeHolder] does have target rotations.
     * @return True if it found smart guideline, false otherwise.
     */
    /**
     * Finds smart guidelines for rotation based on target rotation degrees.
     * This method detects when the selected object's rotation is near target angles and snaps to them.
     *
     * @return True if a rotation guideline was found and applied, false otherwise.
     */
    private fun findRotationSmartGuidelines(): Boolean {
        _selectedChild?.let { child ->

            clearSmartRotationArray()

            smartRotationDegreeHolder?.forEach { snapDegree ->

                child.mapFinalPointsForDraw()

                val imageRotation =
                    child.transformationMatrix.run {
                        GestureUtils.mapTo360(
                            -atan2(
                                getSkewX(true),
                                (getScaleX())
                            ) * (180f / PI)
                        ).toFloat()
                    }

                if (imageRotation in (snapDegree - rangeForSmartRotationGuideline)..(snapDegree + rangeForSmartRotationGuideline)
                ) {
                    tempRect.calculateMaximumRect(mappedMeshPoints)

                    child.transformationMatrix.postRotate(
                        snapDegree - imageRotation,
                        tempRect.centerX(),
                        tempRect.centerY()
                    )

                    val centerXBound = tempRect.centerX()

                    smartRotationLineHolder[0] = (centerXBound)
                    smartRotationLineHolder[1] = (-10000f)
                    smartRotationLineHolder[2] = (centerXBound)
                    smartRotationLineHolder[3] = (10000f)

                    mappingMatrix.setRotate(snapDegree, tempRect.centerX(), tempRect.centerY())
                    mappingMatrix.mapPoints(smartRotationLineHolder)

                    return true
                }
            }
        }
        return false
    }

    /**
     * Add degrees that user wants to snap to it if rotation reaches it.
     * These values should be between 0 and 359 (360 is same as 0 degree so use 0 instead of 360).
     * @param degrees Array of specific degrees that rotation snaps to.
     * @throws IllegalStateException if provided array is empty or any element in array is not between 0-360 degrees.
     */
    /**
     * Sets target degrees for rotation smart guidelines.
     * Objects will automatically snap to these rotation angles when rotated nearby.
     *
     * @param degrees Array of target rotation degrees (0-359). 360 is treated as 0.
     * @throws IllegalStateException if array is empty or contains invalid degree values.
     */
    fun setRotationSmartGuideline(degrees: FloatArray) {
        if (degrees.any { degree -> (degree < 0 || degree > 359) }) throw IllegalStateException(
            "array elements should be between 0-359 degrees"
        )
        if (degrees.isEmpty()) throw IllegalStateException("array should contain at least 1 element")

        originalRotationHolder = degrees

        smartRotationDegreeHolder = if (degrees.any { it == 0f } && !degrees.any { it == 360f }) {
            FloatArray(degrees.size + 1).also { array ->
                degrees.copyInto(array)
                array[array.lastIndex] = 360f
            }
        } else {
            degrees
        }
    }

    /**
     * Clears any degrees that smart guideline detector detects.
     * This way smart guideline wouldn't snap to any specific degree.
     */
    /**
     * Clears rotation smart guideline target degrees.
     * This method disables automatic rotation snapping completely.
     */
    fun clearRotationSmartGuideline() {
        smartRotationDegreeHolder = null
        originalRotationHolder = null
        onInvalidate()
    }

    /**
     * Returns the rotation degree holder. Smart guideline detector snaps to these
     * degrees if there is any.
     */
    /**
     * Gets the current rotation smart guideline target degrees.
     * This method returns the original degree array (without internal 360-degree additions).
     *
     * @return Array of target rotation degrees, or null if rotation guidelines are disabled.
     */
    fun getRotationSmartGuidelineDegreeHolder() = originalRotationHolder

    /**
     * Applies a transformation matrix to the selected child object.
     * This method is a convenience wrapper for the onTransformed method.
     *
     * @param matrix The transformation matrix to apply.
     */
    fun applyMatrix(matrix: Matrix) {
        onTransformed(matrix)
    }

    /**
     * Rotates the selected child object by the specified number of degrees.
     * This method applies rotation around the object's center point.
     *
     * @param degree The rotation angle in degrees (positive for clockwise).
     */
    fun rotateSelectedChildBy(degree: Float) {
        _selectedChild?.apply {
            transformable.getBounds(tempRect)
            mappingMatrix.setRotate(
                degree,
                tempRect.centerX(),
                tempRect.centerY()
            )
            transformationMatrix.preConcat(mappingMatrix)
            mergeMatrices()
            findAllGuidelines()
            saveState(createState())
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Resets the rotation of the selected child object to zero degrees.
     * This method removes all rotation while preserving other transformations.
     */
    fun resetSelectedChildRotation() {
        getChildMatrix()?.let {
            rotateSelectedChildBy(it.getMatrixRotation())
        }
    }

    /**
     * Flips the selected child object vertically (around horizontal axis).
     * This method creates a vertical mirror effect.
     */
    fun flipSelectedChildVertically() {
        _selectedChild?.apply {
            transformable.getBounds(tempRect)
            mappingMatrix.setScale(1f, -1f, tempRect.centerX(), tempRect.centerY())
            preConcatTransformationMatrix(mappingMatrix)
        }
    }

    /**
     * Flips the selected child object horizontally (around vertical axis).
     * This method creates a horizontal mirror effect.
     */
    fun flipSelectedChildHorizontally() {
        _selectedChild?.apply {
            transformable.getBounds(tempRect)
            mappingMatrix.setScale(-1f, 1f, tempRect.centerX(), tempRect.centerY())
            preConcatTransformationMatrix(mappingMatrix)
        }
    }

    /**
     * Applies a matrix transformation with pre-concatenation and guideline detection.
     * This method is used internally for flip and other transformation operations.
     *
     * @param matrix The transformation matrix to pre-concatenate.
     */
    private fun Child.preConcatTransformationMatrix(matrix: Matrix) {
        transformationMatrix.preConcat(matrix)
        mergeMatrices()
        findAllGuidelines()
        saveState(createState())
        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Resets the transformation matrix of the selected child object.
     * This method can either reset to original bounds or to the current canvas bounds.
     *
     * @param resetToBounds If true, resets to original object bounds; if false, resets to canvas bounds.
     */
    fun resetSelectedChildMatrix(resetToBounds: Boolean) {
        _selectedChild?.apply {
            initializeChild(this, false, false, resetToBounds)
            findAllGuidelines()
            saveState(createState())
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Sets the transformation matrix of the selected child object.
     * This method replaces the current transformation with the specified matrix.
     *
     * @param matrix The new transformation matrix to apply.
     */
    fun setMatrix(matrix: Matrix) {
        _selectedChild?.apply {
            transformationMatrix.set(matrix)
            findAllGuidelines()
            mergeMatrices()
            saveState(createState())
            onInvalidate()
        }
    }

    /**
     * Gets the transformation matrix of the selected child object.
     * This method provides access to the current transformation state.
     *
     * @return The transformation matrix of the selected child, or null if none is selected.
     */
    fun getChildMatrix(): MananMatrix? =
        _selectedChild?.transformationMatrix


    /**
     * Gets the bounds of the selected child object after transformations.
     * This method calculates the final screen-space bounds of the selected object.
     *
     * @param rect The rectangle to store the calculated bounds.
     * @return True if bounds were calculated (child is selected), false otherwise.
     */
    fun getSelectedChildBounds(rect: RectF): Boolean {
        _selectedChild?.let { child ->
            child.mapFinalPointsForDraw()
            rect.calculateMaximumRect(mappedMeshPoints)
            return true
        }
        return false
    }

    /**
     * Aligns the selected child object according to the specified alignment mode.
     * This method positions the object relative to the canvas bounds.
     *
     * @param alignment The alignment mode to apply.
     */
    fun setSelectedChildAlignment(alignment: TransformableAlignment) {
        _selectedChild?.let {
            getSelectedChildBounds(tempRect)

            when (alignment) {
                TOP -> mappingMatrix.setTranslate(0f, bounds.top - tempRect.top)
                BOTTOM -> mappingMatrix.setTranslate(0f, bounds.bottom - tempRect.bottom)
                LEFT -> mappingMatrix.setTranslate(bounds.left - tempRect.left, 0f)
                RIGHT -> mappingMatrix.setTranslate(bounds.right - tempRect.right, 0F)
                VERTICAL -> mappingMatrix.setTranslate(0f, bounds.centerY() - tempRect.centerY())
                HORIZONTAL -> mappingMatrix.setTranslate(bounds.centerX() - tempRect.centerX(), 0f)
                CENTER -> mappingMatrix.setTranslate(
                    bounds.centerX() - tempRect.centerX(),
                    bounds.centerY() - tempRect.centerY()
                )
            }

            applyMatrix(mappingMatrix)

            saveState(it.createState())
        }
    }

    /**
     * Finds and applies all types of smart guidelines.
     * This method combines alignment and rotation guideline detection.
     */
    private fun findAllGuidelines() {
        findSmartGuideLines()
        findRotationSmartGuidelines()
    }

    /**
     * Called when a child object requests invalidation.
     * This method triggers a redraw of the entire transform tool.
     */
    override fun onInvalidate() {
        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Releases resources when the tool is no longer needed.
     * This method is currently empty as no special cleanup is required.
     */
    override fun release() {
        // Do not reinitialize child
    }

    /**
     * Saves a transformation state to the history stack.
     * This method enables undo/redo functionality for transformations.
     *
     * @param state The state to save.
     */
    private fun saveState(state: State) {
        if (isTransformationLocked) {
            return
        }
        historyHandler!!.addState(state)
        initialChildState = _selectedChild?.clone(true)
    }

    /**
     * Saves the current state of the selected child for undo functionality.
     * This method is used to create checkpoints during transformation operations.
     */
    fun saveSelectedChildState() {
        _selectedChild?.apply {
            saveState(createState())
            onInvalidate()
        }
    }

    /**
     * Undoes the last transformation operation.
     * This method restores the previous state from the history stack.
     */
    override fun undo() {
        historyHandler!!.undo()
    }

    /**
     * Redoes the next transformation operation.
     * This method applies the next state from the history stack.
     */
    override fun redo() {
        historyHandler!!.redo()
    }

    /**
     * Creates a history state for the specified child.
     * This method captures the current state for undo/redo operations.
     *
     * @param initialChildren The initial children list for this state.
     * @param reference The reference child for this state.
     * @return A new State object representing the current transformation state.
     */
    private fun Child?.createState(
        initialChildren: LinkedList<Child>? = null,
        reference: Child? = this,
    ): State = State(initialChildState, initialChildren, reference)

    /**
     * Inner class representing a saved transformation state for history operations.
     * This class manages undo/redo functionality by storing object states and child lists.
     *
     * @param initialChildState The initial state of the child when the operation began.
     * @param initialChildren The initial list of children when the operation began.
     * @param reference The reference child object for this state.
     */
    private inner class State(
        val initialChildState: Child?,
        val initialChildren: LinkedList<Child>? = null,
        val reference: Child?,
    ) : HistoryState {
        private val clonedChildren = LinkedList(_children)
        private val clonedChild = reference?.clone(true)

        override fun undo() {
            restoreState(initialChildState, initialChildren)
        }

        override fun redo() {
            restoreState(clonedChild, clonedChildren)
        }

        private fun restoreState(targetChild: Child?, targetChildren: MutableList<Child>?) {
            targetChild?.clone(true)?.let {
                reference?.set(it)
            }

            targetChildren?.let {
                _children.clear()
                _children.addAll(LinkedList(it))
            }

            _selectedChild = reference

            _selectedChild?.apply {
                select(true)
                onChildSelected?.invoke(transformable, false)
            }

            findAllGuidelines()

            onInvalidate()
        }
    }

    /**
     * Data class representing a transformable child object with its transformation state.
     * This class encapsulates all information needed to manage and transform an object.
     *
     * @param transformable The transformable object being managed.
     * @param transformationMatrix The transformation matrix applied to the object.
     * @param polyMatrix The poly-to-poly transformation matrix for free transform mode.
     * @param baseSizeChangeArray The base positions for size change handles.
     * @param meshPoints The current mesh points for transformation calculations.
     * @param targetRect The target rectangle for positioning, or null for default positioning.
     */
    private data class Child(
        var transformable: Transformable,
        val transformationMatrix: MananMatrix,
        val polyMatrix: MananMatrix,
        var baseSizeChangeArray: FloatArray,
        var meshPoints: FloatArray,
        val targetRect: RectF?
    ) {
        fun clone(cloneTransformable: Boolean = false): Child {
            return Child(
                if (cloneTransformable) transformable.clone() else transformable,
                MananMatrix().apply {
                    set(transformationMatrix)
                },
                MananMatrix().apply {
                    set(polyMatrix)
                },
                baseSizeChangeArray.clone(),
                meshPoints.clone(),
                targetRect
            )
        }

        fun set(otherChild: Child) {
            transformable = otherChild.transformable
            transformationMatrix.set(otherChild.transformationMatrix)
            polyMatrix.set(otherChild.polyMatrix)
            baseSizeChangeArray = otherChild.baseSizeChangeArray.clone()
            meshPoints = otherChild.meshPoints

            otherChild.targetRect?.let {
                targetRect?.set(it)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Child

            if (transformable != other.transformable) return false
            if (transformationMatrix != other.transformationMatrix) return false
            if (polyMatrix != other.polyMatrix) return false
            if (!baseSizeChangeArray.contentEquals(other.baseSizeChangeArray)) return false
            if (!meshPoints.contentEquals(other.meshPoints)) return false
            if (targetRect != other.targetRect) return false

            return true
        }

        override fun hashCode(): Int {
            var result = transformable.hashCode()
            result = 31 * result + transformationMatrix.hashCode()
            result = 31 * result + polyMatrix.hashCode()
            result = 31 * result + baseSizeChangeArray.contentHashCode()
            result = 31 * result + meshPoints.contentHashCode()
            result = 31 * result + (targetRect?.hashCode() ?: 0)
            return result
        }
    }

    /**
     * A class holding static flags for smart guideline. User should
     * set the desired flags in [setSmartGuidelineFlags] method.
     */
    /**
     * Class containing static constants for smart guideline types.
     * These flags can be combined using bitwise OR operations to enable specific guidelines.
     */
    class Guidelines {
        companion object {
            const val ALL = 1
            const val LEFT_LEFT = 2
            const val LEFT_RIGHT = 4
            const val RIGHT_LEFT = 8
            const val RIGHT_RIGHT = 16
            const val TOP_TOP = 32
            const val TOP_BOTTOM = 64
            const val BOTTOM_TOP = 128
            const val BOTTOM_BOTTOM = 256
            const val CENTER_X = 512
            const val CENTER_Y = 1024
        }
    }

    /**
     * Enumeration defining alignment options for transformable objects.
     * These values are used with setSelectedChildAlignment to position objects.
     */
    enum class TransformableAlignment {
        TOP, LEFT, RIGHT, BOTTOM, VERTICAL, HORIZONTAL, CENTER
    }
}
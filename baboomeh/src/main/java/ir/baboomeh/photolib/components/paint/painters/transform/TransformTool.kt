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
import ir.baboomeh.photolib.components.paint.painters.transform.managers.ChildManager
import ir.baboomeh.photolib.components.paint.painters.transform.managers.LinkedListChildManager
import ir.baboomeh.photolib.components.paint.painters.transform.smartguideline.AlignmentGuidelineResult
import ir.baboomeh.photolib.components.paint.painters.transform.smartguideline.DefaultAlignmentSmartGuidelineDetector
import ir.baboomeh.photolib.components.paint.painters.transform.smartguideline.DefaultRotationSmartGuidelineDetector
import ir.baboomeh.photolib.components.paint.painters.transform.smartguideline.RotationGuidelineResult
import ir.baboomeh.photolib.components.paint.painters.transform.smartguideline.SmartAlignmentGuidelineDetector
import ir.baboomeh.photolib.components.paint.painters.transform.smartguideline.SmartRotationGuidelineDetector
import ir.baboomeh.photolib.components.paint.painters.transform.transformables.Transformable
import ir.baboomeh.photolib.components.paint.paintview.PaintLayer
import ir.baboomeh.photolib.utils.extensions.dp
import ir.baboomeh.photolib.utils.extensions.isNearPoint
import ir.baboomeh.photolib.utils.extensions.setMaximumRect
import ir.baboomeh.photolib.utils.gesture.TouchData
import ir.baboomeh.photolib.utils.history.HistoryHandler
import ir.baboomeh.photolib.utils.history.HistoryState
import ir.baboomeh.photolib.utils.history.handlers.StackHistoryHandler
import ir.baboomeh.photolib.utils.matrix.MananMatrix
import kotlin.math.abs

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
open class TransformTool(
    context: Context,
    open var rotationGuidelineDetector: SmartRotationGuidelineDetector = DefaultRotationSmartGuidelineDetector(),
    open var alignmentGuidelineDetector: SmartAlignmentGuidelineDetector = DefaultAlignmentSmartGuidelineDetector(),
) : Painter(), Transformable.OnInvalidate {

    /** The currently selected paint layer for rendering operations. */
    protected var selectedLayer: PaintLayer? = null

    /** Paint object used for drawing transformation bounds around selected objects. */
    protected val boundPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = context.dp(2)
        }

    /**
     * The stroke width for transformation bounds in pixels.
     * Setting this value automatically updates the paint and triggers invalidation for immediate visual feedback.
     */
    open var boundStrokeWidth = boundPaint.strokeWidth
        set(value) {
            field = value
            boundPaint.strokeWidth = field
            onInvalidate()
        }

    /**
     * The color for transformation bounds.
     * Setting this value automatically updates the paint and triggers invalidation for immediate visual feedback.
     */
    open var boundColor = boundPaint.color
        set(value) {
            field = value
            boundPaint.color = field
            onInvalidate()
        }


    /** The drawable used for transformation handles (corner and edge controls) that users can drag. */
    open var handleDrawable: Drawable = ResourcesCompat.getDrawable(
        context.resources,
        R.drawable.defualt_transform_tool_handles,
        null
    )!!

    /** The touch range in pixels for detecting handle interactions and determining touch sensitivity. */
    open var touchRange = context.dp(24)

    /** Matrix used for coordinate mappings and transformations between different coordinate systems. */
    protected val mappingMatrix by lazy {
        MananMatrix()
    }

    /** Array holding the base corner points of a transformable object (8 values: x1,y1,x2,y2,x3,y3,x4,y4). */
    protected val basePoints by lazy {
        FloatArray(8)
    }

    /** Array holding mapped mesh points for drawing after transformation calculations. */
    protected val mappedMeshPoints by lazy {
        FloatArray(8)
    }

    /** Array holding mapped points for size change handles after transformation calculations. */
    protected val mappedBaseSizeChangePoints by lazy {
        FloatArray(8)
    }

    /** Temporary array for coordinate calculations to avoid object allocation during operations. */
    protected val cc by lazy {
        FloatArray(8)
    }

    /** Temporary array for holding single point coordinates during transformations. */
    protected val pointHolder by lazy {
        FloatArray(2)
    }

    /** Rectangle representing the bounds of the target component being transformed. */
    protected val targetComponentBounds by lazy {
        RectF()
    }

    /** Temporary rectangle for various calculations to avoid object allocation. */
    protected val tempRect by lazy {
        RectF()
    }

    /** Canvas used for final rendering operations when applying transformations to layers. */
    protected val finalCanvas by lazy {
        Canvas()
    }

    /**
     * Enables free transform mode, allowing individual corner manipulation for advanced transformations.
     * When disabled, transformations maintain aspect ratio and shape consistency.
     * When enabled, each corner can be moved independently for perspective-like effects.
     */
    open var isFreeTransform = false
        set(value) {
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    /** Index of the first selected corner for transformation (-1 if none selected). */
    protected var firstSelectedIndex = -1

    /** Index of the second selected corner for transformation (-1 if none selected). */
    protected var secondSelectedIndex = -1

    /** Index of the third selected corner for transformation (-1 if none selected). */
    protected var thirdSelectedIndex = -1

    /** Index of the fourth selected corner for transformation (-1 if none selected). */
    protected var forthSelectedIndex = -1

    /** Index of the first size change handle (-1 if none selected). */
    protected var firstSizeChangeIndex = -1

    /** Index of the second size change handle (-1 if none selected). */
    protected var secondSizeChangeIndex = -1

    /** Flag indicating if movement should be restricted to X-axis only during transformations. */
    protected var isOnlyMoveX = false

    /** Last recorded X coordinate for transformation calculations and delta computations. */
    protected var lastX = 0f

    /** Last recorded Y coordinate for transformation calculations and delta computations. */
    protected var lastY = 0f

    /** The bounds rectangle for clipping operations and coordinate system boundaries. */
    protected lateinit var bounds: Rect

    /** The main transformation matrix for coordinate system transformations. */
    protected lateinit var matrix: MananMatrix

    /** Matrix for fitting content inside bounds with proper scaling and positioning. */
    protected lateinit var fitMatrix: MananMatrix

    protected open var childManager: ChildManager = LinkedListChildManager()

    /**
     * Public read-only access to the list of transformable children.
     * This provides external access to all objects currently managed by the transform tool.
     *
     * @return Immutable list of transformable objects managed by this tool.
     */
    open val children: List<Transformable>
        get() {
            return childManager.map { it.transformable }
        }

    /** When true, prevents any transformation operations on objects, effectively locking them in place. */
    open var isTransformationLocked = false

    /**
     * Controls visibility of transformation bounds.
     * When false, bounds are not drawn around selected objects.
     */
    open var isBoundsEnabled = true
        set(value) {
            field = value
            onInvalidate()
        }

    /** Stores the initial state of a child for history operations and undo functionality. */
    protected var initialChildState: Child? = null

    /**
     * The currently selected child object for transformations.
     * Setting this automatically creates a backup for history operations to enable undo functionality.
     */
    protected open var currentSelectedChild: Child? = null
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
    open val selectedChild: Transformable?
        get() {
            return currentSelectedChild?.transformable
        }

    /** Paint object used for drawing smart guidelines that assist with object alignment. */
    protected val smartGuidePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = context.dp(2)
            color = Color.MAGENTA
        }
    }

    open var smartGuidelineStrokeWidth = smartGuidePaint.strokeWidth
        set(value) {
            field = value
            smartGuidePaint.strokeWidth = field
            onInvalidate()
        }

    open var smartGuidelineColor = smartGuidePaint.color
        set(value) {
            field = value
            smartGuidePaint.color = field
            onInvalidate()
        }

    /** Path effect for rendering dashed smart guidelines. */
    protected val smartGuideLineDashedPathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)

    /** Callback invoked when a child object is selected, providing access to the selected object and initialization state. */
    open var onChildSelected: ((Transformable, isInitialization: Boolean) -> Unit)? = null

    /** Callback invoked when a child object is deselected. */
    open var onChildDeselected: (() -> Unit)? = null

    override var historyHandler: HistoryHandler? = StackHistoryHandler()

    protected var rotationGuideline: RotationGuidelineResult? = null

    protected var alignmentGuideline: AlignmentGuidelineResult? = null

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

        childManager.forEach { child ->
            initializeChild(child, shouldCalculateBounds = true)
        }
    }

    protected open fun initializeChild(
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

            mergeMatrices(child)
        }
    }

    protected open fun changeMatrixToMatchRect(child: Child, rect: RectF) {
        child.apply {
            transformable.getBounds(targetComponentBounds)

            transformationMatrix.setRectToRect(
                targetComponentBounds,
                rect,
                Matrix.ScaleToFit.CENTER
            )

            mergeMatrices(child)

            onInvalidate()
        }
    }

    override fun onMoveBegin(touchData: TouchData) {
        currentSelectedChild?.let {
            selectIndexes(it, touchData)
        }
    }

    protected open fun selectIndexes(child: Child, touchData: TouchData) {

        val range = touchRange / matrix.getRealScaleX()

        child.baseSizeChangeArray.copyInto(cc)
        mapMeshPoints(child, cc)

        var nearest = range

        firstSelectedIndex = -1
        secondSelectedIndex = -1
        thirdSelectedIndex = -1
        forthSelectedIndex = -1
        firstSizeChangeIndex = -1
        secondSizeChangeIndex = -1

        if (touchData.isNearPoint(cc[0], cc[1], range)) {
            (abs(touchData.ex - cc[0]) + abs(touchData.ey - cc[1])).let {
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

        if (touchData.isNearPoint(cc[2], cc[3], range)) {
            (abs(touchData.ex - cc[2]) + abs(touchData.ey - cc[3])).let {
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
        if (touchData.isNearPoint(cc[4], cc[5], range)) {
            (abs(touchData.ex - cc[4]) + abs(touchData.ey - cc[5])).let {
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
        if (touchData.isNearPoint(cc[6], cc[7], range)) {
            (abs(touchData.ex - cc[6]) + abs(touchData.ey - cc[7])).let {
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
            map(child, cc)
            lastX = cc[firstSizeChangeIndex]
            lastY = cc[secondSizeChangeIndex]
        }

        if (!isFreeTransform) {
            return
        }

        basePoints.copyInto(cc)
        mapMeshPoints(child, cc)

        if (touchData.isNearPoint(cc[0], cc[1], range)) {
            (abs(touchData.ex - cc[0]) + abs(touchData.ey - cc[1])).let {
                if (it < nearest) {
                    firstSelectedIndex = 0
                    secondSelectedIndex = 1
                }
            }
        }
        if (touchData.isNearPoint(cc[2], cc[3], range)) {
            (abs(touchData.ex - cc[2]) + abs(touchData.ey - cc[3])).let {
                if (it < nearest) {
                    firstSelectedIndex = 2
                    secondSelectedIndex = 3
                }
            }
        }
        if (touchData.isNearPoint(cc[4], cc[5], range)) {
            (abs(touchData.ex - cc[4]) + abs(touchData.ey - cc[5])).let {
                if (it < nearest) {
                    firstSelectedIndex = 4
                    secondSelectedIndex = 5
                }
            }
        }
        if (touchData.isNearPoint(cc[6], cc[7], range)) {
            (abs(touchData.ex - cc[6]) + abs(touchData.ey - cc[7])).let {
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
        currentSelectedChild?.apply {
            mapMeshPoints(this, touchData.ex, touchData.ey)

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
            } else if (currentSelectedChild?.isPointInChildRect(touchData) == true) {
                mappingMatrix.setTranslate(touchData.dx, touchData.dy)
                onTransformed(mappingMatrix)
            }

            makePolyToPoly()
            onInvalidate()
        }
    }

    override fun onMoveEnded(touchData: TouchData) {
        if (firstSelectedIndex == -1 && secondSelectedIndex == -1 && firstSizeChangeIndex == -1 && secondSizeChangeIndex == -1) {

            val lastSelected = currentSelectedChild
            currentSelectedChild = null

            childManager.forEach { child ->
                if (child.isPointInChildRect(touchData)) {
                    currentSelectedChild = child
                }
            }

            if (lastSelected !== currentSelectedChild) {
                currentSelectedChild?.let {
                    select(it, true)
                    onChildSelected?.invoke(it.transformable, false)
                }
            }

            if (currentSelectedChild == null) {
                onChildDeselected?.invoke()
            }

            onInvalidate()

        } else {
            saveState(createState(currentSelectedChild))
        }

        eraseRotationSmartGuidelines()

        firstSelectedIndex = -1
        secondSelectedIndex = -1
        thirdSelectedIndex = -1
        forthSelectedIndex = -1
        firstSizeChangeIndex = -1
        secondSizeChangeIndex = -1

    }

    protected fun Child.isPointInChildRect(touchData: TouchData): Boolean {
        mapMeshPoints(this, touchData.ex, touchData.ey)

        val x = pointHolder[0]
        val y = pointHolder[1]

        tempRect.setMaximumRect(meshPoints)

        return (x.coerceIn(
            tempRect.left,
            tempRect.right
        ) == x && y.coerceIn(
            tempRect.top,
            tempRect.bottom
        ) == y)
    }

    protected open fun makePolyToPoly() {
        currentSelectedChild!!.apply {
            polyMatrix.setPolyToPoly(basePoints, 0, meshPoints, 0, 4)
        }
    }


    protected open fun mapMeshPoints(child: Child, ex: Float, ey: Float) {
        pointHolder[0] = ex
        pointHolder[1] = ey
        map(child, pointHolder)
    }

    protected open fun map(child: Child, array: FloatArray) {
        child.transformationMatrix.invert(mappingMatrix)
        mappingMatrix.mapPoints(array)
    }

    protected open fun mapMeshPoints(child: Child, array: FloatArray) {
        child.polyMatrix.mapPoints(array)
        child.transformationMatrix.mapPoints(array)
    }

    /**
     * Maps touch vectors for coordinate system transformation.
     * This method converts touch deltas to the appropriate coordinate space.
     *
     * @param touchData The touch data containing delta values.
     */
    protected open fun mapVectors(touchData: TouchData) {
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
        childManager.forEach { child ->

            mergeMatrices(child, false)

            drawChild(canvas, child)

            val mergedCanvasMatrices = mergeCanvasMatrices()
            val sx = mergedCanvasMatrices.getRealScaleX()
            val currentBoundWidth = boundPaint.strokeWidth
            val currentGuideWidth = smartGuidePaint.strokeWidth
            val finalBoundWidth = currentBoundWidth * sx
            val finalGuidelineWidth = currentGuideWidth * sx

            boundPaint.strokeWidth = finalBoundWidth
            smartGuidePaint.strokeWidth = finalGuidelineWidth

            if (child === currentSelectedChild && isBoundsEnabled) {

                select(child)

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

                rotationGuideline?.let { rotationGuideline ->
                    canvas.drawLines(rotationGuideline.guideline.lineArray, smartGuidePaint)
                }

                alignmentGuideline?.lines?.forEach { smartGuideline ->
                    if (smartGuideline.isDashed) {
                        smartGuidePaint.pathEffect = smartGuideLineDashedPathEffect
                    }

                    canvas.drawLines(smartGuideline.lineArray, smartGuidePaint)

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
    protected open fun resizeAndDrawDrawable(x: Int, y: Int, canvas: Canvas) {
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
    protected open fun drawChild(canvas: Canvas, child: Child) {
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
        currentSelectedChild = null
        childManager.removeAllChildren()
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
        return currentSelectedChild != null && !isTransformationLocked
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
        currentSelectedChild?.apply {
            transformationMatrix.postConcat(transformMatrix)
            findAllGuidelines()
            mergeMatrices(this)
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Called when a transformation operation ends.
     * This method saves the current state for undo functionality.
     */
    override fun onTransformEnded() {
        saveState(createState(currentSelectedChild))
    }

    /**
     * Finds possible guide lines on selected component and other components and populates the line holder.
     * This method detects guide lines on sides of selected component including:
     * - Left-Left, Left-Right, Right-Left, Right-Right alignment
     * - Top-Top, Top-Bottom, Bottom-Top, Bottom-Bottom alignment
     * - Center X and Center Y alignment
     */
    protected open fun findAlignmentSmartGuidelines() {
        alignmentGuideline = currentSelectedChild?.let { child ->
            alignmentGuidelineDetector.detectAlignmentGuidelines(
                child,
                childManager - child,
                bounds.toRectF()
            )?.apply {
                child.transformationMatrix.postConcat(transformation)
            }
        }
    }

    /**
     * Merges canvas transformation matrices for proper coordinate mapping.
     * This method combines fit and transformation matrices for accurate rendering.
     *
     * @return The merged transformation matrix.
     */
    protected open fun mergeCanvasMatrices(): MananMatrix {
        return mappingMatrix.apply {
            setConcat(fitMatrix, matrix)
            invert(mappingMatrix)
        }
    }

    /**
     * Merges all transformation matrices for a child object.
     * This method combines the transformation and poly matrices for final rendering.
     *
     * @param shouldMap Whether to map final points for drawing.
     */

    private fun mergeMatrices(child: Child, shouldMap: Boolean = true) {
        if (shouldMap) {
            child.mapMeshPointsByMatrices(mappingMatrix, mappedMeshPoints)
            child.mapSizeChangePointsByMatrices(mappingMatrix, mappedBaseSizeChangePoints)
        }
        mappingMatrix.set(child.transformationMatrix)
        mappingMatrix.preConcat(child.polyMatrix)
    }

    /**
     * Applies all child transformations to the current layer's bitmap.
     * This method permanently renders all transformations to the layer.
     */
    open fun applyComponentOnLayer() {

        selectedLayer?.let { layer ->
            finalCanvas.setBitmap(layer.bitmap)

            childManager.forEach { child ->
                select(child)
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
        currentSelectedChild?.let { child ->
            tempRect.set(targetComponentBounds)

            select(child, true)

            val tw = targetComponentBounds.width()
            val th = targetComponentBounds.height()

            val lw = tempRect.width()
            val lh = tempRect.height()

            child.apply {

                tempRect.setMaximumRect(meshPoints)

                mappingMatrix.apply {
                    setScale(tw / lw, th / lh, tempRect.centerX(), tempRect.centerY())
                    mapPoints(meshPoints)
                }

            }
            makePolyToPoly()
            mergeMatrices(child)
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
    open fun addChild(transformable: Transformable, targetRect: RectF?) {
        eraseAlignmentSmartGuidelines()
        eraseRotationSmartGuidelines()

        currentSelectedChild = Child(
            transformable, MananMatrix(), MananMatrix(), FloatArray(8),
            FloatArray(8), targetRect
        )

        val initialChildren = childManager.getAllChildren()

        childManager.addChild(currentSelectedChild!!)

        onChildSelected?.invoke(currentSelectedChild!!.transformable, true)

        if (isInitialized) {
            initializeChild(currentSelectedChild!!, shouldCalculateBounds = true)
            onInvalidate()
        }

        saveState(createState(currentSelectedChild, initialChildren))
    }

    /**
     * Moves the selected child object up one layer in the rendering order.
     * This method increases the z-order of the selected object.
     */
    open fun bringSelectedChildUp() {
        getSelectedChildIndexAndCompare(childManager.getAllChildren().lastIndex) { child, selectedChildIndex ->
            swap(selectedChildIndex + 1, selectedChildIndex)
        }
    }

    /**
     * Brings the selected child object to the front of the rendering order.
     * This method moves the selected object to the highest z-order.
     */
    open fun bringSelectedChildToFront() {
        getSelectedChildIndexAndCompare(childManager.getAllChildren().lastIndex) { _, selectedChildIndex ->
            bringFromIndexToIndex(selectedChildIndex, childManager.getAllChildren().lastIndex)
        }
    }

    /**
     * Moves the selected child object down one layer in the rendering order.
     * This method decreases the z-order of the selected object.
     */
    open fun bringSelectedChildDown() {
        getSelectedChildIndexAndCompare(0) { child, selectedChildIndex ->
            swap(selectedChildIndex - 1, selectedChildIndex)
        }
    }

    /**
     * Sends the selected child object to the back of the rendering order.
     * This method moves the selected object to the lowest z-order.
     */
    open fun bringSelectedChildToBack() {
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
    protected fun getSelectedChildIndexAndCompare(
        compareIndex: Int,
        operation: (child: Child, selectedChildIndex: Int) -> Unit
    ) {
        currentSelectedChild?.let { child ->
            val selectedChildIndex = childManager.indexOf(child)

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
    protected open fun swap(firstIndex: Int, secondIndex: Int) {
        val initialChildren = childManager.toList()
        childManager.swap(firstIndex, secondIndex)
        saveState(createState(currentSelectedChild, initialChildren))
    }

    /**
     * Moves an object from one index to another in the rendering order.
     * This method handles moving objects to the front or back of the z-order.
     *
     * @param fromIndex The current index of the object.
     * @param toIndex The target index for the object.
     */
    protected open fun bringFromIndexToIndex(fromIndex: Int, toIndex: Int) {
        val initialChildren = childManager.getAllChildren()

        childManager.reorder(fromIndex, toIndex)

        saveState(createState(currentSelectedChild, initialChildren))
    }

    /**
     * Removes the currently selected child object from the tool.
     * This method deletes the selected object and clears the selection.
     */
    open fun removeSelectedChild() {
        currentSelectedChild?.apply {
            val initialChildren = childManager.getAllChildren()
            childManager.removeChild(this)
            saveState(createState(this, initialChildren))
            currentSelectedChild = null
            onInvalidate()
        }
    }

    /**
     * Removes a child object at the specified index.
     * This method deletes the object at the given position in the children list.
     *
     * @param index The index of the child to remove.
     */
    open fun removeChildAt(index: Int) {
        val initialChildren = childManager.getAllChildren()
        childManager.removeChildAt(index)
        saveState(createState(currentSelectedChild, initialChildren))
        onInvalidate()
    }

    /**
     * Removes all child objects from the tool.
     * This method clears all managed objects and resets the selection.
     */
    open fun removeAllChildren() {
        val initialChildren = childManager.getAllChildren()
        childManager.removeAllChildren()
        currentSelectedChild = null
        saveState(createState(currentSelectedChild, initialChildren))
        onInvalidate()
    }

    /**
     * Selects a child object and initializes it for manipulation.
     * This method prepares the child for transformation operations.
     *
     * @param shouldCalculateBounds Whether to recalculate the object's bounds.
     */
    private fun select(child: Child, shouldCalculateBounds: Boolean = false) {
        initializeChild(child, true, shouldCalculateBounds)
    }


    /**
     * Clears all currently visible smart guidelines.
     * This method removes guideline visuals without affecting the detection settings.
     */
    open fun eraseAlignmentSmartGuidelines() {
        alignmentGuideline = null
        onInvalidate()
    }

    /**
     * Clears rotation smart guidelines from the display.
     * This method removes rotation snap indicators without affecting snap settings.
     */
    open fun eraseRotationSmartGuidelines() {
        rotationGuideline = null
        onInvalidate()
    }


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
    protected open fun findRotationSmartGuidelines(): Boolean {
        rotationGuideline = currentSelectedChild?.let { child ->
            rotationGuidelineDetector.detectRotationGuidelines(child)?.apply {
                child.transformationMatrix.postConcat(transformation)
            }
        }

        return rotationGuideline != null
    }

    /**
     * Applies a transformation matrix to the selected child object.
     * This method is a convenience wrapper for the onTransformed method.
     *
     * @param matrix The transformation matrix to apply.
     */
    open fun applyMatrix(matrix: Matrix) {
        onTransformed(matrix)
    }

    /**
     * Rotates the selected child object by the specified number of degrees.
     * This method applies rotation around the object's center point.
     *
     * @param degree The rotation angle in degrees (positive for clockwise).
     */
    open fun rotateSelectedChildBy(degree: Float) {
        currentSelectedChild?.apply {
            transformable.getBounds(tempRect)
            mappingMatrix.setRotate(
                degree,
                tempRect.centerX(),
                tempRect.centerY()
            )
            transformationMatrix.preConcat(mappingMatrix)
            mergeMatrices(this)
            findAllGuidelines()
            saveState(createState(this))
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Resets the rotation of the selected child object to zero degrees.
     * This method removes all rotation while preserving other transformations.
     */
    open fun resetSelectedChildRotation() {
        getChildMatrix()?.let {
            rotateSelectedChildBy(it.getMatrixRotation())
        }
    }

    /**
     * Flips the selected child object vertically (around horizontal axis).
     * This method creates a vertical mirror effect.
     */
    open fun flipSelectedChildVertically() {
        currentSelectedChild?.apply {
            transformable.getBounds(tempRect)
            mappingMatrix.setScale(1f, -1f, tempRect.centerX(), tempRect.centerY())
            preConcatTransformationMatrix(mappingMatrix)
        }
    }

    /**
     * Flips the selected child object horizontally (around vertical axis).
     * This method creates a horizontal mirror effect.
     */
    open fun flipSelectedChildHorizontally() {
        currentSelectedChild?.apply {
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
    protected fun Child.preConcatTransformationMatrix(matrix: Matrix) {
        transformationMatrix.preConcat(matrix)
        mergeMatrices(this)
        findAllGuidelines()
        saveState(createState(this))
        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Resets the transformation matrix of the selected child object.
     * This method can either reset to original bounds or to the current canvas bounds.
     *
     * @param resetToBounds If true, resets to original object bounds; if false, resets to canvas bounds.
     */
    open fun resetSelectedChildMatrix(resetToBounds: Boolean) {
        currentSelectedChild?.apply {
            initializeChild(this, false, false, resetToBounds)
            findAllGuidelines()
            saveState(createState(this))
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Sets the transformation matrix of the selected child object.
     * This method replaces the current transformation with the specified matrix.
     *
     * @param matrix The new transformation matrix to apply.
     */
    open fun setMatrix(matrix: Matrix) {
        currentSelectedChild?.apply {
            transformationMatrix.set(matrix)
            findAllGuidelines()
            mergeMatrices(this)
            saveState(createState(this))
            onInvalidate()
        }
    }

    /**
     * Gets the transformation matrix of the selected child object.
     * This method provides access to the current transformation state.
     *
     * @return The transformation matrix of the selected child, or null if none is selected.
     */
    open fun getChildMatrix(): MananMatrix? =
        currentSelectedChild?.transformationMatrix


    /**
     * Gets the bounds of the selected child object after transformations.
     * This method calculates the final screen-space bounds of the selected object.
     *
     * @param rect The rectangle to store the calculated bounds.
     * @return True if bounds were calculated (child is selected), false otherwise.
     */
    open fun getSelectedChildBounds(rect: RectF): Boolean {
        currentSelectedChild?.let { child ->
            child.mapMeshPointsByMatrices(mappingMatrix, mappedMeshPoints)
            rect.setMaximumRect(mappedMeshPoints)
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
    open fun setSelectedChildAlignment(alignment: TransformableAlignment) {
        currentSelectedChild?.let {
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

            saveState(createState(it))
        }
    }

    /**
     * Finds and applies all types of smart guidelines.
     * This method combines alignment and rotation guideline detection.
     */
    protected open fun findAllGuidelines() {
        findAlignmentSmartGuidelines()
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
    protected open fun saveState(state: State) {
        if (isTransformationLocked) {
            return
        }
        historyHandler!!.addState(state)
        initialChildState = currentSelectedChild?.clone(true)
    }

    /**
     * Saves the current state of the selected child for undo functionality.
     * This method is used to create checkpoints during transformation operations.
     */
    open fun saveSelectedChildState() {
        currentSelectedChild?.apply {
            saveState(createState(this))
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
    protected open fun createState(
        child: Child?,
        initialChildren: Collection<Child>? = null,
        reference: Child? = child,
    ): State = State(initialChildState, initialChildren, reference)

    /**
     * Inner class representing a saved transformation state for history operations.
     * This class manages undo/redo functionality by storing object states and child lists.
     *
     * @param initialChildState The initial state of the child when the operation began.
     * @param initialChildren The initial list of children when the operation began.
     * @param reference The reference child object for this state.
     */
    protected open inner class State(
        val initialChildState: Child?,
        val initialChildren: Collection<Child>? = null,
        val reference: Child?,
    ) : HistoryState {
        protected val clonedChildren = childManager.getAllChildren()
        protected val clonedChild = reference?.clone(true)

        override fun undo() {
            restoreState(initialChildState, initialChildren)
        }

        override fun redo() {
            restoreState(clonedChild, clonedChildren)
        }

        protected open fun restoreState(targetChild: Child?, targetChildren: Collection<Child>?) {
            targetChild?.clone(true)?.let {
                reference?.set(it)
            }

            targetChildren?.let {
                childManager.removeAllChildren()
                childManager.addAllChildren(targetChildren.toList())
            }

            currentSelectedChild = reference

            currentSelectedChild?.apply {
                select(this, true)
                onChildSelected?.invoke(transformable, false)
            }

            findAllGuidelines()

            onInvalidate()
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
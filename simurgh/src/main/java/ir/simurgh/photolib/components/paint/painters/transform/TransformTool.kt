package ir.simurgh.photolib.components.paint.painters.transform

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
import ir.simurgh.photolib.R
import ir.simurgh.photolib.components.paint.painters.painter.MessageChannel
import ir.simurgh.photolib.components.paint.painters.painter.Painter
import ir.simurgh.photolib.components.paint.painters.painter.PainterMessage
import ir.simurgh.photolib.components.paint.painters.transform.TransformTool.TransformableAlignment.BOTTOM
import ir.simurgh.photolib.components.paint.painters.transform.TransformTool.TransformableAlignment.CENTER
import ir.simurgh.photolib.components.paint.painters.transform.TransformTool.TransformableAlignment.HORIZONTAL
import ir.simurgh.photolib.components.paint.painters.transform.TransformTool.TransformableAlignment.LEFT
import ir.simurgh.photolib.components.paint.painters.transform.TransformTool.TransformableAlignment.RIGHT
import ir.simurgh.photolib.components.paint.painters.transform.TransformTool.TransformableAlignment.TOP
import ir.simurgh.photolib.components.paint.painters.transform.TransformTool.TransformableAlignment.VERTICAL
import ir.simurgh.photolib.components.paint.painters.transform.managers.child.ChildManager
import ir.simurgh.photolib.components.paint.painters.transform.managers.child.LinkedListChildManager
import ir.simurgh.photolib.components.paint.painters.transform.managers.handle.HandleTransformer
import ir.simurgh.photolib.components.paint.painters.transform.managers.handle.PerspectiveHandleTransformer
import ir.simurgh.photolib.components.paint.painters.transform.smartguideline.AlignmentGuidelineResult
import ir.simurgh.photolib.components.paint.painters.transform.smartguideline.DefaultAlignmentSmartGuidelineDetector
import ir.simurgh.photolib.components.paint.painters.transform.smartguideline.DefaultRotationSmartGuidelineDetector
import ir.simurgh.photolib.components.paint.painters.transform.smartguideline.RotationGuidelineResult
import ir.simurgh.photolib.components.paint.painters.transform.smartguideline.SmartAlignmentGuidelineDetector
import ir.simurgh.photolib.components.paint.painters.transform.smartguideline.SmartRotationGuidelineDetector
import ir.simurgh.photolib.components.paint.painters.transform.transformables.Transformable
import ir.simurgh.photolib.components.paint.paintview.PaintLayer
import ir.simurgh.photolib.utils.extensions.dp
import ir.simurgh.photolib.utils.extensions.setMaximumRect
import ir.simurgh.photolib.utils.gesture.TouchData
import ir.simurgh.photolib.utils.history.HistoryHandler
import ir.simurgh.photolib.utils.history.HistoryState
import ir.simurgh.photolib.utils.history.handlers.StackHistoryHandler
import ir.simurgh.photolib.utils.matrix.SimurghMatrix

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
open class TransformTool(context: Context) : Painter(), Transformable.OnInvalidate, MessageChannel {

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

    /** Matrix used for coordinate mappings and transformations between different coordinate systems. */
    protected val mappingMatrix by lazy {
        SimurghMatrix()
    }

    /** Array holding mapped mesh points for drawing after transformation calculations. */
    protected val mappedMeshPoints by lazy {
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

    /** The bounds rectangle for clipping operations and coordinate system boundaries. */
    protected lateinit var bounds: Rect

    /** The main transformation matrix for coordinate system transformations. */
    protected lateinit var matrix: SimurghMatrix

    /** Matrix for fitting content inside bounds with proper scaling and positioning. */
    protected lateinit var fitMatrix: SimurghMatrix

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

    open var rotationGuidelineDetector: SmartRotationGuidelineDetector =
        DefaultRotationSmartGuidelineDetector()

    open var alignmentGuidelineDetector: SmartAlignmentGuidelineDetector =
        DefaultAlignmentSmartGuidelineDetector()

    open var handleTransformer: HandleTransformer = PerspectiveHandleTransformer(context, this)

    protected open var childManager: ChildManager = LinkedListChildManager()
        set(value) {
            value.addAllChildren(field.getAllChildren())
            field = value
        }

    protected var rotationGuideline: RotationGuidelineResult? = null

    protected var alignmentGuideline: AlignmentGuidelineResult? = null

    override fun initialize(
        context: Context,
        transformationMatrix: SimurghMatrix,
        fitInsideMatrix: SimurghMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)

        matrix = transformationMatrix
        fitMatrix = fitInsideMatrix
        bounds = clipBounds

        childManager.forEach { child ->
            initializeChild(child)
        }
    }

    protected open fun initializeChild(
        child: Child,
        isSelectChild: Boolean = false,
        targetRect: RectF? = null
    ) {
        child.apply {
            transformable.onInvalidateListener = this@TransformTool
            initialize(targetRect ?: RectF(bounds))
            handleTransformer.createHandles(this)
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

            onInvalidate()
        }
    }

    override fun onMoveBegin(touchData: TouchData) {
        currentSelectedChild?.let {
            handleTransformer.findHandle(it, touchData)
        }
    }

    override fun onMove(touchData: TouchData) {
        if (isTransformationLocked) {
            return
        }
        currentSelectedChild?.apply {
            if (handleTransformer.selectedHandle != null) {
                handleTransformer.transform(this, touchData)
            } else if (isPointInChildRect(this, touchData)) {
                mappingMatrix.setTranslate(touchData.dx, touchData.dy)
                onTransformed(mappingMatrix)
            }
            onInvalidate()
        }
    }

    override fun onMoveEnded(touchData: TouchData) {
        if (handleTransformer.selectedHandle == null) {

            val lastSelected = currentSelectedChild
            currentSelectedChild = null

            currentSelectedChild =
                childManager.firstOrNull { child -> isPointInChildRect(child, touchData) }

            if (lastSelected !== currentSelectedChild) {
                currentSelectedChild?.let {
                    select(it)
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

        handleTransformer.selectedHandle = null
    }

    protected open fun isPointInChildRect(child: Child, touchData: TouchData): Boolean {
        mapMeshPoints(child, touchData.ex, touchData.ey)

        val x = pointHolder[0]
        val y = pointHolder[1]

        tempRect.setMaximumRect(child.meshPoints)

        return (x.coerceIn(
            tempRect.left,
            tempRect.right
        ) == x && y.coerceIn(
            tempRect.top,
            tempRect.bottom
        ) == y)
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

    /**
     * Renders all child objects and their transformation UI elements to the canvas.
     * This method handles the complete rendering pipeline including bounds, handles, and guidelines.
     *
     * @param canvas The canvas to draw on.
     */
    override fun draw(canvas: Canvas) {

        val mergedCanvasMatrices = mergeCanvasMatrices()
        val sx = mergedCanvasMatrices.getRealScaleX()
        val currentBoundWidth = boundPaint.strokeWidth
        val currentGuideWidth = smartGuidePaint.strokeWidth
        val finalBoundWidth = currentBoundWidth * sx
        val finalGuidelineWidth = currentGuideWidth * sx

        childManager.forEach { child ->

            child.draw(canvas)

            boundPaint.strokeWidth = finalBoundWidth
            smartGuidePaint.strokeWidth = finalGuidelineWidth

            if (child === currentSelectedChild && isBoundsEnabled) {
                drawBounds(canvas, child)

                drawHandles(canvas,child)

                drawGuidelines(canvas)
            }

            boundPaint.strokeWidth = currentBoundWidth
            smartGuidePaint.strokeWidth = currentGuideWidth
        }
    }

    protected open fun drawBounds(canvas: Canvas, child: Child) {
        child.mapMeshPointsByMatrices(mappingMatrix, mappedMeshPoints)

        canvas.apply {

            drawLine(
                mappedMeshPoints[0],
                mappedMeshPoints[1],
                mappedMeshPoints[2],
                mappedMeshPoints[3],
                boundPaint
            )

            drawLine(
                mappedMeshPoints[2],
                mappedMeshPoints[3],
                mappedMeshPoints[4],
                mappedMeshPoints[5],
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
                mappedMeshPoints[6],
                mappedMeshPoints[7],
                mappedMeshPoints[0],
                mappedMeshPoints[1],
                boundPaint
            )
        }
    }

    protected open fun drawGuidelines(canvas: Canvas) {
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

    protected open fun drawHandles(canvas: Canvas,child: Child) {
        handleTransformer.getAllHandles(child).forEach {
            resizeAndDrawDrawable(it.x, it.y, canvas)
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
    protected open fun resizeAndDrawDrawable(x: Float, y: Float, canvas: Canvas) {
        val hw = handleDrawable.intrinsicWidth / 2
        val hh = handleDrawable.intrinsicHeight / 2
        handleDrawable.setBounds(
            (x - hw).toInt(),
            (y - hh).toInt(),
            (hw + x).toInt(),
            (hh + y).toInt()
        )

        handleDrawable.draw(canvas)
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
                RectF(bounds)
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
    protected open fun mergeCanvasMatrices(): SimurghMatrix {
        return mappingMatrix.apply {
            setConcat(fitMatrix, matrix)
            invert(mappingMatrix)
        }
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
                child.draw(finalCanvas)
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

            child.getBounds(tempRect)
            child.updateBounds()
            child.getBounds(targetComponentBounds)

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

            handleTransformer.createHandles(child)

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
            transformable, SimurghMatrix(), SimurghMatrix(), FloatArray(8),
            FloatArray(8), FloatArray(8)
        )

        val initialChildren = childManager.getAllChildren()

        childManager.addChild(currentSelectedChild!!)

        onChildSelected?.invoke(currentSelectedChild!!.transformable, true)

        if (isInitialized) {
            initializeChild(currentSelectedChild!!)
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
            childManager.swap(selectedChildIndex + 1, selectedChildIndex)
        }
    }

    /**
     * Brings the selected child object to the front of the rendering order.
     * This method moves the selected object to the highest z-order.
     */
    open fun bringSelectedChildToFront() {
        getSelectedChildIndexAndCompare(childManager.getAllChildren().lastIndex) { _, selectedChildIndex ->
            childManager.reorder(selectedChildIndex, childManager.getAllChildren().lastIndex)
        }
    }

    /**
     * Moves the selected child object down one layer in the rendering order.
     * This method decreases the z-order of the selected object.
     */
    open fun bringSelectedChildDown() {
        getSelectedChildIndexAndCompare(0) { child, selectedChildIndex ->
            doAndSaveState {
                childManager.swap(selectedChildIndex - 1, selectedChildIndex)
            }
        }
    }

    /**
     * Sends the selected child object to the back of the rendering order.
     * This method moves the selected object to the lowest z-order.
     */
    open fun bringSelectedChildToBack() {
        getSelectedChildIndexAndCompare(0) { _, selectedChildIndex ->
            doAndSaveState {
                childManager.reorder(selectedChildIndex, 0)
            }
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

    protected fun doAndSaveState(operation: () -> Unit) {
        val initialChildren = childManager.getAllChildren()
        operation()
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
    protected open fun select(child: Child) {
        child.apply {
            transformable.onInvalidateListener = this@TransformTool
            transformable.getBounds(targetComponentBounds)
            handleTransformer.createHandles(child)
        }
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
        findAllGuidelines()
        saveState(createState(this))
        onInvalidate()
    }

    /**
     * Resets the transformation matrix of the selected child object.
     * This method can either reset to original bounds or to the current canvas bounds.
     *
     * @param resetToBounds If true, resets to original object bounds; if false, resets to canvas bounds.
     */
    open fun resetSelectedChildMatrix(resetToBoundsRect: RectF? = null) {
        currentSelectedChild?.apply {
            initializeChild(this, false, resetToBoundsRect)
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
    open fun getChildMatrix(): SimurghMatrix? =
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
            child.getTransformedBounds(mappingMatrix, rect)
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
                select(this)
                onChildSelected?.invoke(transformable, false)
            }

            findAllGuidelines()

            onInvalidate()
        }
    }

    override fun onSendMessage(message: PainterMessage) {
        if (message == PainterMessage.INVALIDATE) {
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
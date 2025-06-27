package ir.simurgh.photolib.components.paint.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.AttributeSet
import android.view.ScaleGestureDetector
import androidx.core.graphics.createBitmap
import ir.simurgh.photolib.components.paint.painters.painter.Painter
import ir.simurgh.photolib.components.paint.painters.painter.PainterMessage
import ir.simurgh.photolib.utils.gesture.TouchData
import ir.simurgh.photolib.utils.gesture.detectors.translation.TranslationDetector
import ir.simurgh.photolib.utils.history.HistoryHandler
import ir.simurgh.photolib.utils.history.HistoryState
import ir.simurgh.photolib.utils.history.handlers.StackHistoryHandler

/**
 * A sophisticated [PainterView] that supports multiple layers with independent blending modes,
 * opacity levels, and painting operations.
 *
 * This view extends [PainterView] to provide comprehensive layer management functionality
 * including:
 *
 * **Layer Management:**
 * - Create, duplicate, delete, and reorder layers.
 * - Individual layer opacity and blending mode control.
 * - Layer locking to prevent accidental modifications.
 * - Visual layer caching for improved performance.
 *
 * **History System:**
 * - Full undo/redo support for all layer operations.
 * - State preservation for complex multi-layer edits.
 * - Automatic history management with configurable handlers.
 *
 * **Performance Optimizations:**
 * - Intelligent layer caching system.
 * - Efficient memory management for large layer stacks.
 *
 * **Visual Features:**
 * - Checkerboard transparency background.
 * - Layer composition with proper blending.
 * - Real-time preview during transformations.
 *
 * The view automatically manages the complexity of multi-layer rendering while providing
 * a simple API for layer manipulation. It's designed for use in photo editing and
 * digital art applications where layer-based editing is essential
 */
open class LayeredPainterView(context: Context, attrSet: AttributeSet?) :
    PainterView(context, attrSet) {

    /**
     * Constructor for programmatic view creation without attributes.
     */
    constructor(context: Context) : this(context, null)

    protected val bitmapPaint by lazy {
        Paint().apply {
            isFilterBitmap = true
        }
    }

    /**
     * Controls whether the checkerboard transparency background is displayed.
     * When enabled, provides visual indication of transparent areas in layers.
     */
    override var isCheckerBoardEnabled = true
        set(value) {
            field = value
            cacheLayers()
            invalidate()
        }

    /**
     * History handler for managing undo/redo operations across all layers.
     */
    val historyHandler: HistoryHandler = StackHistoryHandler().apply {
        setOnHistoryChanged {
            callUndoRedoListener()
        }
    }

    /**
     * Internal storage for all layers in the paint view.
     */
    protected var layerHolder = mutableListOf<PaintLayer>()

    /**
     * Lambda-based callback for layer change notifications.
     */
    protected var onLayersChanged: ((layers: List<PaintLayer>, selectedLayerIndex: Int) -> Unit)? =
        null

    /**
     * Interface-based callback for layer change notifications.
     */
    protected var layerChangedCallback: OnLayersChanged? = null

    /**
     * Callback for state change notifications during history operations.
     */
    protected var stateChangedCallback: ((layers: List<PaintLayer>?, clip: Rect?) -> Unit)? = null

    /**
     * Callback for undo/redo availability changes.
     */
    protected var onUndoOrRedoStateChanged: ((isUndoEnabled: Boolean, isRedoEnabled: Boolean) -> Unit)? =
        null

    /**
     * Canvas used for layer compositing and merging operations.
     */
    protected val mergeCanvas by lazy {
        Canvas()
    }

    /**
     * Reference bitmap for painter operations and layer composition.
     */
    protected lateinit var bitmapReference: Bitmap

    /**
     * Cached bitmap containing layers below the selected layer.
     */
    protected lateinit var cachedLayer: Bitmap

    /**
     * Cached bitmap containing layers from bottom up to (but not including) selected layer.
     */
    protected lateinit var partiallyCachedLayer: Bitmap

    /**
     * Flag indicating whether all layers have been cached for optimal rendering.
     */
    protected var isAllLayersCached: Boolean = false

    /**
     * Flag to track first-time listener calls for proper initialization.
     */
    protected var isFirstTimeToCallListener = false

    /**
     * Backup of the initial paint layer state for history operations.
     */
    protected var initialPaintLayer: PaintLayer? = null

    /**
     * Reference to the currently selected layer for painting operations.
     * Setting this creates a backup for history management.
     */
    override var selectedLayer: PaintLayer? = null
        set(value) {
            field = value
            // Create backup for undo/redo operations.
            initialPaintLayer = value?.clone(true)
        }

    /**
     * Controls whether layer caching is enabled for performance optimization.
     * When enabled, layers are pre-composited to reduce rendering overhead.
     */
    open var isCachingEnabled = false
        set(value) {
            field = value
            if (value) {
                // Initialize caching system if view is ready.
                if (isViewInitialized || isCacheLayerInitialized() && (partiallyCachedLayer.isRecycled || cachedLayer.isRecycled)) {
                    createCacheLayers()
                    cacheLayers()
                }
                invalidate()
            } else {
                // Clean up cache bitmaps when disabled.
                if (isCacheLayerInitialized()) {
                    partiallyCachedLayer.recycle()
                    cachedLayer.recycle()
                }
            }
        }

    /**
     * Checks if cache layer bitmaps have been initialized and are ready for use.
     */
    protected fun isCacheLayerInitialized(): Boolean =
        this::partiallyCachedLayer.isInitialized && this::cachedLayer.isInitialized

    /**
     * Sets the active painter and configures history management.
     * Delegates history handling to painter if it supports it, otherwise uses internal handler.
     */
    override var painter: Painter? = null
        set(value) {
            cacheLayers()
            super.painter = value

            field = value

            if (value == null) {
                // Use internal history handler when no painter.
                historyHandler.apply {
                    setOnHistoryChanged {
                        callUndoRedoListener()
                    }
                    callUndoRedoListener()
                }
            } else {
                // Use painter's history handler if available.
                value.historyHandler?.apply {
                    setOnHistoryChanged {
                        callUndoRedoListener()
                    }
                    callUndoRedoListener()
                }
            }
        }

    /**
     * Called when the view layout is established and the image bounds are calculated.
     * Initializes the painter, creates cache layers, and sets up the reference bitmap.
     */
    override fun onImageLaidOut() {
        rectAlloc.set(boundsRectangle)

        if (!isViewInitialized) {
            initializedPainter(painter)

            createCacheLayers()

            bitmapReference = createLayerBitmap()

            cacheLayers()

            isViewInitialized = true
        }
    }

    /**
     * Creates the bitmap cache layers if caching is enabled and they don't exist.
     * This optimizes rendering by pre-compositing layer content.
     */
    protected open fun createCacheLayers() {
        if ((!isCacheLayerInitialized() || cachedLayer.isRecycled || partiallyCachedLayer.isRecycled) && isCachingEnabled) {
            cachedLayer = createLayerBitmap()
            partiallyCachedLayer = createLayerBitmap()
        }
    }

    /**
     * Initializes the painter with the current view state and layer information.
     * Sets up coordinate systems, bounds, and layer references.
     *
     * @param pp The painter to initialize, or null if no painter is set.
     */
    override fun initializedPainter(pp: Painter?) {
        pp?.apply {
            rectAlloc.set(layerBounds)

            if (!isInitialized) {
                initialize(
                    context,
                    canvasMatrix,
                    imageviewMatrix,
                    identityClip,
                    layerClipBounds
                )

                onPainterInitializedListener.invoke()
            }

            onLayerChanged(selectedLayer)

            if (this@LayeredPainterView::bitmapReference.isInitialized) {
                onReferenceLayerCreated(bitmapReference)
            }
        }
    }

    /**
     * Called when move gesture ends, triggers layer caching for performance.
     */
    override fun onMoveEnded(detector: TranslationDetector) {
        super.onMoveEnded(detector)
        cacheLayers()
    }

    /**
     * Called when move begins, invalidates cached layers for interactive updates.
     */
    override fun callPainterOnMoveBegin(touchData: TouchData) {
        super.callPainterOnMoveBegin(touchData)
        isAllLayersCached = false
    }

    /**
     * Called when move ends, saves current state for undo/redo functionality.
     */
    override fun canCallPainterMoveEnd(touchData: TouchData) {
        super.canCallPainterMoveEnd(touchData)
        saveState(createState(), false)
    }

    /**
     * Performs undo operation on the layer stack or delegates to painter if it handles history.
     * Prevents undo during clip animations to maintain visual consistency.
     */
    open fun undo() {
        if (clipAnimator.isRunning) {
            return
        }
        painter?.let { p ->
            if (p.doesHandleHistory()) {
                p.undo()
                return
            }
        }
        historyHandler.undo()?.let {
            updateAfterStateChange()
        }
    }

    /**
     * Performs redo operation on the layer stack or delegates to painter if it handles history.
     * Prevents redo during clip animations to maintain visual consistency.
     */
    open fun redo() {
        if (clipAnimator.isRunning) {
            return
        }
        painter?.let { p ->
            if (p.doesHandleHistory()) {
                p.redo()
                return
            }
        }
        historyHandler.redo()?.let {
            updateAfterStateChange()
        }
    }

    /**
     * Updates the view state after a history operation (undo/redo).
     * Refreshes caches, notifies listeners, and triggers repainting.
     */
    protected open fun updateAfterStateChange() {
        cacheLayers()

        callOnLayerChangedListeners()

        painter?.onLayerChanged(selectedLayer)

        invalidate()
    }

    /**
     * Notifies listeners about undo/redo availability changes.
     * Called automatically when the history stack changes.
     */
    protected open fun callUndoRedoListener() {
        if (painter?.doesHandleHistory() == true) {
            painter?.historyHandler?.let { handler ->
                onUndoOrRedoStateChanged?.invoke(
                    handler.getUndoSize() != 0,
                    handler.getRedoSize() != 0
                )
            }
        } else {
            onUndoOrRedoStateChanged?.invoke(
                historyHandler.getUndoSize() != 0,
                historyHandler.getRedoSize() != 0
            )
        }
    }

    /**
     * Called when rotation begins, updates layer caching state.
     */
    override fun onRotateBegin(initialDegree: Float, px: Float, py: Float): Boolean {
        isAllLayersCached = isFirstMove
        return true
    }

    /**
     * Called when scaling begins, updates layer caching state.
     */
    override fun onScaleBegin(p0: ScaleGestureDetector): Boolean {
        isAllLayersCached = isFirstMove
        return super.onScaleBegin(p0)
    }

    /**
     * Main drawing method that renders all layers with optimized caching.
     * Uses different rendering paths based on caching state and layer complexity.
     */
    override fun onDraw(canvas: Canvas) {
        canvas.run {
            // Concat the canvas to 'canvasMatrix'.
            concat(canvasMatrix)

            concat(imageviewMatrix)

            if (!layerClipBounds.isEmpty) {
                clipRect(layerClipBounds)
            }

            if (!isCachingEnabled) {
                // Direct rendering without caching.
                drawLayers(canvas)
                return
            }

            if (isAllLayersCached && !isAnyLayerBlending()) {
                // Optimized rendering with cached layers.
                drawBitmap(partiallyCachedLayer, 0f, 0f, bitmapPaint)

                drawPainterLayer(canvas)

                drawBitmap(cachedLayer, 0f, 0f, bitmapPaint)
            } else {
                // Mixed rendering for partial caching or blending layers.
                if (this@LayeredPainterView::partiallyCachedLayer.isInitialized) {
                    drawBitmap(partiallyCachedLayer, 0f, 0f, bitmapPaint)
                }

                drawPainterLayer(canvas)

                mergeLayersAtIndex(
                    canvas,
                    layerHolder.indexOf(selectedLayer) + 1,
                    layerHolder.lastIndex
                )
            }
        }
    }

    /**
     * Checks if any layer in the stack uses blending modes.
     * Used to determine optimal rendering strategy.
     */
    open fun isAnyLayerBlending(): Boolean =
        layerHolder.any { it.blendingModeObject != null }

    /**
     * Renders the painter layer with checkerboard background and active painting.
     * Enhanced version that handles layer positioning and transparency.
     */
    override fun drawPainterLayer(canvas: Canvas) {
        canvas.apply {
            doOnSelectedLayer {
                // Draw checkerboard for first layer if transparency is enabled.
                if (this === layerHolder.first() && isCheckerBoardEnabled) {
                    drawPaint(checkerPatternPaint)
                }

                drawPaintLayer(canvas, this)

                painter?.draw(canvas)
            }
        }
    }

    /**
     * Renders all layers without caching optimization.
     * Used when caching is disabled or unavailable.
     */
    protected open fun drawLayers(canvas: Canvas) {
        canvas.apply {
            layerHolder.forEach { layer ->
                // Draw checkerboard for first layer.
                if (layer === layerHolder.first() && isCheckerBoardEnabled) {
                    drawPaint(checkerPatternPaint)
                }

                drawPaintLayer(canvas, layer)

                // Draw active painter content on selected layer.
                if (layer === selectedLayer) {
                    painter?.draw(this)
                }
            }
        }
    }

    /**
     * Handles messages from the painter system including layer caching.
     */
    override fun onSendMessage(message: PainterMessage) {
        when (message) {
            PainterMessage.INVALIDATE -> {
                invalidate()
            }

            PainterMessage.SAVE_HISTORY -> {
                saveState(createState())
            }

            PainterMessage.CACHE_LAYERS -> {
                cacheLayers()
            }
        }
    }

    /**
     * Creates a new layer based on the current view dimensions and adds it to the layer stack.
     * This method requires that [addNewLayer(bitmap)] has been called previously to establish
     * the view dimensions.
     *
     * @throws IllegalStateException If the view hasn't been initialized with bitmap dimensions.
     */
    open fun addNewLayer() {
        if (!isViewInitialized) {
            throw IllegalStateException("Cannot make new layer based on previous layers. Did you call `addNewLayer(bitmap)` first?")
        }

        addNewLayerWithoutLayoutReset(PaintLayer(createLayerBitmap()))
    }

    /**
     * Adds a new layer with the specified bitmap and initializes the view if needed.
     * The bitmap must be mutable to allow painting operations.
     *
     * @param bitmap The bitmap to use for the new layer, or null to skip creation.
     * @throws IllegalStateException If the bitmap is not mutable.
     */
    override fun addNewLayer(bitmap: Bitmap?) {
        if (bitmap != null) {
            if (!bitmap.isMutable) {
                throw IllegalStateException("Bitmap should be mutable.")
            }

            initializeBitmap(bitmap)

            addNewLayerWithoutLayoutReset(PaintLayer(bitmap))
        }
    }

    /**
     * Adds a new layer using the provided PaintLayer object.
     *
     * @param layer The layer to add to the layer stack.
     */
    open fun addNewLayer(layer: PaintLayer) {
        initializeBitmap(layer.bitmap)

        addNewLayerWithoutLayoutReset(layer)
    }

    /**
     * Initializes the view with bitmap dimensions and resets the layer stack.
     *
     * @param bitmap The bitmap that defines the canvas dimensions.
     */
    protected open fun initializeBitmap(bitmap: Bitmap) {
        layerHolder.clear()
        isNewLayer = true
        bitmapWidth = bitmap.width
        bitmapHeight = bitmap.height
        layerClipBounds.set(0, 0, bitmapWidth, bitmapHeight)
        identityClip.set(layerClipBounds)
    }

    /**
     * Internal method to add a layer without triggering view layout operations.
     * Handles history state management and listener notifications.
     *
     * @param layer The layer to add.
     */
    protected open fun addNewLayerWithoutLayoutReset(layer: PaintLayer) {
        selectedLayer = layer

        val initialLayers = layerHolder.toMutableList()

        layerHolder.add(selectedLayer!!)

        saveState(createState(initialLayers))

        if (!isViewInitialized) {
            requestLayout()
        }

        updateAfterStateChange()
    }

    /**
     * Saves the current state for history management.
     * Can optionally ignore painter history handling for specific operations.
     */
    protected open fun saveState(state: State?, ignorePainterHandleHistoryFlag: Boolean = true) {
        if (painter?.doesHandleHistory() == true && !ignorePainterHandleHistoryFlag) {
            return
        }

        stateChangedCallback?.invoke(state?.clonedLayers, state?.clonedClip)

        if (state == null) {
            return
        }

        initialPaintLayer = selectedLayer?.clone(true)

        historyHandler.addState(state)
    }

    /**
     * Adds a new layer without saving to history.
     * Used for operations that manage their own history state.
     */
    open fun addNewLayerWithoutSavingHistory() {
        addNewLayerWithoutSavingHistory(createLayerBitmap())

        if (isViewInitialized) {
            cacheLayers()
        }

        invalidate()
    }

    /**
     * Internal helper for adding layer without history using bitmap.
     */
    protected open fun addNewLayerWithoutSavingHistory(bitmap: Bitmap) {
        addNewLayerWithoutSavingHistory(PaintLayer(bitmap, false, 1f))
    }

    /**
     * Internal helper for adding layer without history using PaintLayer.
     */
    open fun addNewLayerWithoutSavingHistory(layer: PaintLayer) {
        selectedLayer = layer

        layerHolder.add(selectedLayer!!)

        callOnLayerChangedListeners()
    }

    /**
     * Creates a new blank bitmap with current layer dimensions.
     */
    protected open fun createLayerBitmap(): Bitmap {
        return createBitmap(bitmapWidth, bitmapHeight)
    }

    /**
     * Updates the layer caches to optimize rendering performance.
     * Handles both full caching and reference bitmap creation.
     */
    protected open fun cacheLayers() {
        if (!this::bitmapReference.isInitialized) {
            return
        }
        mergeCanvas.apply {
            if (!isCachingEnabled) {
                // Update reference bitmap without caching.
                bitmapReference.eraseColor(Color.TRANSPARENT)
                if (isCheckerBoardEnabled) {
                    drawPaint(checkerPatternPaint)
                }
                setBitmap(bitmapReference)
                mergeLayersAtIndex(mergeCanvas, 0, layerHolder.lastIndex)
                painter?.onReferenceLayerCreated(bitmapReference)
                return
            }

            if (!isCacheLayerInitialized()) {
                return
            }
            if (layerHolder.isNotEmpty()) {
                doOnSelectedLayer {
                    // Create partially cached layer (layers below selected).
                    partiallyCachedLayer.eraseColor(Color.TRANSPARENT)

                    setBitmap(partiallyCachedLayer)

                    if (isCheckerBoardEnabled) {
                        drawPaint(checkerPatternPaint)
                    }

                    mergeLayersAtIndex(mergeCanvas, 0, getSelectedLayerIndex() - 1)

                    // Create cached layer (layers above selected).
                    cachedLayer.eraseColor(Color.TRANSPARENT)

                    setBitmap(cachedLayer)

                    mergeLayersAtIndex(
                        mergeCanvas,
                        getSelectedLayerIndex() + 1,
                        layerHolder.lastIndex
                    )

                    // Update reference bitmap with complete composition.
                    bitmapReference.eraseColor(Color.TRANSPARENT)

                    setBitmap(bitmapReference)

                    drawBitmap(partiallyCachedLayer, 0f, 0f, layersPaint)

                    drawBitmap(cachedLayer, 0f, 0f, layersPaint)

                    drawPainterLayer(mergeCanvas)

                    painter?.onReferenceLayerCreated(bitmapReference)

                    isAllLayersCached = true
                }
            }
        }
    }

    /**
     * Renders a range of layers to the specified canvas.
     * Used for both caching and direct rendering operations.
     */
    protected open fun mergeLayersAtIndex(canvas: Canvas, from: Int, to: Int) {
        layerHolder.slice(from..to).forEach { layer ->

            drawPaintLayer(canvas, layer)

            if (layer === selectedLayer) {
                painter?.draw(canvas)
            }
        }
    }

    /**
     * Returns the opacity value of the layer at the specified index.
     *
     * @param index The index of the layer to query.
     * @return The opacity value (0.0 to 1.0).
     * @throws ArrayIndexOutOfBoundsException If the index is invalid.
     */
    open fun getLayerOpacityAt(index: Int): Float {
        checkIndex(index)
        return layerHolder[index].opacity
    }

    /**
     * Changes the opacity of the layer at the specified index and saves the state.
     *
     * @param index The index of the layer to modify.
     * @param opacity The new opacity value (0.0 to 1.0).
     * @throws ArrayIndexOutOfBoundsException If the index is invalid.
     */
    open fun changeLayerOpacityAt(index: Int, opacity: Float) {
        checkIndex(index)

        layerHolder[index].apply {
            this.opacity = opacity
            saveState(createState())
        }

        cacheLayers()
        invalidate()
    }

    /**
     * Changes layer opacity without saving state to history.
     * Used for operations that manage their own history.
     */
    open fun changeLayerOpacityAtWithoutStateSave(index: Int, opacity: Float) {
        checkIndex(index)

        layerHolder[index].apply {
            this.opacity = opacity
        }

        cacheLayers()
        invalidate()
    }

    /**
     * Sets the blending mode for the currently selected layer and saves state.
     */
    override fun setSelectedLayerBlendingMode(blendingMode: PorterDuff.Mode) {
        doOnSelectedLayer {
            this.blendingMode = blendingMode
            saveState(createState())
        }

        cacheLayers()

        invalidate()
    }

    /**
     * Sets the blending mode for the layer at the specified index.
     *
     * @param index The index of the layer to modify.
     * @param blendingMode The Porter-Duff blending mode to apply.
     * @throws ArrayIndexOutOfBoundsException If the index is invalid.
     */
    open fun setLayerBlendingModeAt(index: Int, blendingMode: PorterDuff.Mode) {
        checkIndex(index)

        layerHolder[index].apply {
            this.blendingMode = blendingMode
            saveState(createState())
        }

        cacheLayers()

        invalidate()
    }

    /**
     * Moves a layer from one position to another in the layer stack.
     * Saves state for undo/redo functionality.
     */
    open fun moveLayer(from: Int, to: Int) {
        if (cantMoveLayers(from, to)) {
            return
        }

        val initialLayers = layerHolder.toMutableList()

        val layerFrom = layerHolder[from]
        layerHolder[from] = layerHolder[to]
        layerHolder[to] = layerFrom

        saveState(createState(initialLayers))

        cacheLayers()

        callOnLayerChangedListeners()

        invalidate()
    }

    /**
     * Validates whether layer movement is possible.
     */
    protected open fun cantMoveLayers(from: Int, to: Int): Boolean {
        return from == to || from < 0 || to < 0 || from > layerHolder.lastIndex || to > layerHolder.lastIndex
    }

    /**
     * Moves the currently selected layer down in the layer stack.
     */
    open fun moveSelectedLayerDown() {
        doOnSelectedLayer {
            val index = getSelectedLayerIndex()

            if (index > 0) {
                val initialLayers = layerHolder.toMutableList()
                val pervIndex = index - 1
                val previousLayer = layerHolder[pervIndex]
                layerHolder[pervIndex] = this
                layerHolder[index] = previousLayer

                cacheLayers()

                saveState(createState(initialLayers))

                callOnLayerChangedListeners()

                invalidate()
            }
        }
    }

    /**
     * Moves the currently selected layer up in the layer stack.
     */
    open fun moveSelectedLayerUp() {
        doOnSelectedLayer {
            val index = getSelectedLayerIndex()

            if (index < layerHolder.lastIndex) {
                val initialLayers = layerHolder.toMutableList()
                val nextIndex = index + 1
                val previousLayer = layerHolder[nextIndex]
                layerHolder[nextIndex] = this
                layerHolder[index] = previousLayer

                cacheLayers()

                saveState(createState(initialLayers))

                callOnLayerChangedListeners()

                invalidate()
            }
        }
    }

    /**
     * Locks the layer at the specified index to prevent modifications.
     */
    open fun lockLayer(index: Int) {
        changeLayerLockState(index, true)
    }

    /**
     * Unlocks the layer at the specified index to allow modifications.
     */
    open fun unlockLayer(index: Int) {
        changeLayerLockState(index, false)
    }

    /**
     * Changes the lock state of a layer and saves the state.
     */
    protected open fun changeLayerLockState(index: Int, shouldLock: Boolean) {
        checkIndex(index)

        layerHolder[index].apply {
            isLocked = shouldLock
            saveState(createState())
        }

        cacheLayers()

        callOnLayerChangedListeners()
    }

    /**
     * Checks if the layer at the specified index is locked.
     */
    open fun isLayerAtIndexLocked(index: Int): Boolean {
        checkIndex(index)
        return layerHolder[index].isLocked
    }

    /**
     * Removes the layer at the specified index from the layer stack.
     * Automatically selects an appropriate replacement layer if the selected layer is removed.
     *
     * @param index The index of the layer to remove.
     * @throws ArrayIndexOutOfBoundsException If the index is invalid.
     */
    open fun removeLayerAt(index: Int) {
        checkIndex(index)

        val isSelectedLayerIndex = index == getSelectedLayerIndex()

        layerHolder.removeAt(index)

        saveState(createState(layerHolder.toMutableList()))

        if (isSelectedLayerIndex) {
            // Select appropriate replacement layer
            selectedLayer = when {
                layerHolder.isEmpty() -> null
                index > 0 -> layerHolder[index - 1]
                layerHolder.size == 1 -> layerHolder.first()
                else -> null
            }
            painter?.onLayerChanged(selectedLayer)
        }

        cacheLayers()

        callOnLayerChangedListeners()

        invalidate()

    }

    /**
     * Removes multiple layers by their indices.
     * More efficient than removing layers one by one.
     */
    open fun removeLayers(layersIndex: IntArray) {
        val initialLayers = layerHolder.toMutableList()
        removeLayersWithoutStateSave(layersIndex)
        saveState(createState(initialLayers))
    }

    /**
     * Internal helper for removing multiple layers without state saving.
     */
    protected open fun removeLayersWithoutStateSave(layersIndex: IntArray) {
        layersIndex.forEach { checkIndex(it) }

        if (layersIndex.contains(getSelectedLayerIndex())) {
            selectedLayer = if (layerHolder.size > 1) {
                layerHolder[layersIndex.min() - 1]
            } else {
                null
            }

            painter?.onLayerChanged(selectedLayer)
        }

        layerHolder =
            layerHolder.filterIndexed { index, _ -> !layersIndex.contains(index) }
                .toMutableList()

        cacheLayers()

        callOnLayerChangedListeners()

        invalidate()
    }

    /**
     * Returns the total number of layers in the layer stack.
     */
    open fun getLayerCount(): Int {
        return layerHolder.size
    }

    /**
     * Selects the layer at the specified index as the active layer for painting.
     *
     * @param index The index of the layer to select.
     * @throws ArrayIndexOutOfBoundsException If the index is invalid.
     */
    open fun selectLayer(index: Int) {
        checkIndex(index)
        selectedLayer = layerHolder[index]
        painter?.onLayerChanged(selectedLayer)
        callOnLayerChangedListeners()
        cacheLayers()
        invalidate()
    }

    /**
     * Selects the specified PaintLayer as the active layer.
     * Only works if the layer is already in the layer stack.
     *
     * @param paintLayer The layer to select.
     */
    open fun selectLayer(paintLayer: PaintLayer) {
        if (layerHolder.contains(paintLayer) && selectedLayer !== paintLayer) {
            selectedLayer = paintLayer
            painter?.onLayerChanged(selectedLayer)
            callOnLayerChangedListeners()
            cacheLayers()
            invalidate()
        }
    }

    /**
     * Returns the index of the currently selected layer.
     *
     * @return The index of the selected layer, or -1 if no layer is selected.
     */
    open fun getSelectedLayerIndex(): Int {
        return layerHolder.indexOf(selectedLayer)
    }

    /**
     * Returns a list of all layer bitmaps.
     * Useful for external processing or export operations.
     */
    open fun getLayersBitmap(): List<Bitmap> {
        return layerHolder.map {
            it.bitmap
        }
    }

    /**
     * Merges multiple layers into the bottom-most layer of the selection.
     * The resulting layer replaces all merged layers in the stack.
     */
    open fun mergeLayers(layersIndex: IntArray) {
        if (layersIndex.size < 2) {
            throw IllegalStateException("You need at least two layers to merge.")
        }
        layersIndex.forEach { checkIndex(it) }

        if (layersIndex.distinct().size != layersIndex.size) {
            throw IllegalStateException("Cannot merge layers with duplicate index.")
        }

        val initialLayers = layerHolder.toMutableList()

        val lowerIndex = layersIndex.min()
        val bottomLayer = layerHolder[lowerIndex]

        val clonedBottomLayer = bottomLayer.clone(true)

        mergeCanvas.setBitmap(bottomLayer.bitmap)

        val sortedMinusBottomIndex = layersIndex.sorted().minus(lowerIndex)

        sortedMinusBottomIndex.map { layerHolder[it] }.forEach { layer ->

            drawPaintLayer(mergeCanvas, layer)

            if (layer === selectedLayer) {
                painter?.draw(mergeCanvas)
            }
        }

        removeLayersWithoutStateSave(sortedMinusBottomIndex.toIntArray())

        initialPaintLayer = clonedBottomLayer

        saveState(createState(initialLayers, bottomLayer))
    }

    /**
     * Duplicates the currently selected layer.
     */
    open fun duplicateSelectedLayer() {
        doOnSelectedLayer {
            duplicateLayer()
        }
    }

    /**
     * Duplicates the layer at the specified index.
     */
    open fun duplicateLayerAt(index: Int) {
        checkIndex(index)
        layerHolder.getOrNull(index)?.duplicateLayer()
    }

    /**
     * Extension function to duplicate a layer and add it to the stack.
     */
    protected fun PaintLayer.duplicateLayer() {
        val initialLayers = layerHolder.toMutableList()
        val cloned = clone(true)
        layerHolder.add(cloned)
        selectedLayer = cloned
        saveState(createState(initialLayers))
        updateAfterStateChange()
    }

    /**
     * Returns all [PaintLayer] in the [LayeredPainterView].
     * Do not change content of [PaintLayer] directly, this leads to unsaved states unless if that's what you want.
     * @return a list containing [PaintLayer].
     */
    open fun getPaintLayers(): List<PaintLayer> {
        return layerHolder.toMutableList()
    }

    /**
     * Validates that the provided index is within valid bounds.
     */
    protected open fun checkIndex(index: Int) {
        if (index < 0 || index >= layerHolder.size) {
            throw ArrayIndexOutOfBoundsException("Provided index is out of bounds.")
        }
    }

    /**
     * Sets the interface-based callback for layer change notifications.
     */
    open fun setOnLayersChangedListener(onLayersChanged: OnLayersChanged) {
        layerChangedCallback = onLayersChanged
        callListenerForFirstTime()
    }

    /**
     * Sets the lambda-based callback for layer change notifications.
     */
    open fun setOnLayersChangedListener(callback: ((layers: List<PaintLayer>, selectedLayerIndex: Int) -> Unit)) {
        onLayersChanged = callback
        callListenerForFirstTime()
    }

    /**
     * Sets the bitmap of the currently selected layer and saves state.
     */
    override fun setSelectedLayerBitmap(bitmap: Bitmap) {
        doOnSelectedLayer {
            this.bitmap = bitmap
            saveState(createState(), ignorePainterHandleHistoryFlag = true)
        }
        invalidate()
    }

    /**
     * Clears all layers and resets the history handler.
     */
    open fun clearLayers() {
        layerHolder.clear()
        historyHandler.reset()
        selectedLayer = null
    }

    /**
     * Converts all layers to a single flattened bitmap.
     * Returns the final composited result of all layers.
     */
    override fun convertToBitmap(): Bitmap? {
        if (layerHolder.isEmpty()) {
            return null
        }

        val finalBitmap = layerHolder.first().bitmap.let { layer ->
            createBitmap(layer.width, layer.height, layer.config ?: Bitmap.Config.ARGB_8888)
        }

        mergeCanvas.setBitmap(finalBitmap)

        layerHolder.forEach { layer ->
            drawPaintLayer(mergeCanvas, layer)
        }

        return Bitmap.createBitmap(
            finalBitmap,
            layerClipBounds.left,
            layerClipBounds.top,
            layerClipBounds.width(),
            layerClipBounds.height()
        )
    }

    /**
     * Sets the clipping rectangle with state saving for undo/redo.
     */
    open fun setClipRectWithStateSave(
        rect: Rect,
        initialClip: Rect,
        animate: Boolean = true,
        func: () -> Unit = {}
    ) {
        super.setClipRect(rect, animate, func)
        doOnSelectedLayer {
            saveState(createState(initialClip = Rect(initialClip), clonedClip = rect))
        }
    }

    /**
     * Calls listeners for the first time if conditions are met.
     */
    protected open fun callListenerForFirstTime() {
        if (isViewInitialized && isFirstTimeToCallListener) {
            callOnLayerChangedListeners()
            isFirstTimeToCallListener = false
        }
    }

    /**
     * Notifies all layer change listeners with current state.
     */
    protected open fun callOnLayerChangedListeners() {
        val layers = layerHolder.toList()
        val selectedLayerIndex = getSelectedLayerIndex()
        layerChangedCallback?.onLayersChanged(layers, selectedLayerIndex)
        onLayersChanged?.invoke(layers, selectedLayerIndex)
    }

    /**
     * Sets callback for state change notifications.
     */
    open fun setOnStateChanged(callback: (layers: List<PaintLayer>?, clip: Rect?) -> Unit) {
        stateChangedCallback = callback
    }

    /**
     * Sets callback for undo/redo availability changes.
     */
    open fun setOnUndoOrRedoListener(func: (isUndoEnabled: Boolean, isRedoEnabled: Boolean) -> Unit) {
        onUndoOrRedoStateChanged = func
    }

    /**
     * Internal state class for managing undo/redo operations.
     * Captures complete layer stack state and clipping information.
     */
    protected open inner class State(
        val initialLayer: PaintLayer?,
        val initialLayers: MutableList<PaintLayer>?,
        val reference: PaintLayer? = selectedLayer,
        val initialClip: Rect? = null,
        val clonedClip: Rect? = null
    ) : HistoryState {
        val clonedLayer = reference?.clone(true)
        val clonedLayers = layerHolder.toMutableList()

        /**
         * Restores state to the initial conditions.
         */
        override fun undo() {
            restoreState(initialLayer, initialLayers, initialClip)
        }

        /**
         * Restores state to the final conditions.
         */
        override fun redo() {
            restoreState(clonedLayer, clonedLayers, clonedClip)
        }

        /**
         * Internal method to restore a specific state configuration.
         */
        protected open fun restoreState(
            targetLayer: PaintLayer?,
            targetLayers: MutableList<PaintLayer>?,
            targetClip: Rect?,
        ) {
            targetLayer?.clone(true)?.let {
                reference?.set(it)
            }

            val isSelectLayerNeeded = layerHolder.isNotEmpty() && reference === layerHolder.last()

            targetLayers?.let {
                layerHolder = targetLayers.toMutableList()
            }

            targetClip?.let {
                if (it != layerClipBounds) {
                    setClipRect(it, true)
                }
            }

            selectedLayer = when {
                layerHolder.isEmpty() -> {
                    null
                }

                isSelectLayerNeeded -> {
                    layerHolder.last()
                }

                else -> {
                    reference
                }
            }
        }
    }

    /**
     * Creates a new state object for history management.
     */
    protected open fun createState(
        initialLayers: MutableList<PaintLayer>? = null,
        referenceLayer: PaintLayer? = selectedLayer,
        initialClip: Rect? = null,
        clonedClip: Rect? = null,
    ) =
        State(initialPaintLayer, initialLayers, referenceLayer, initialClip, clonedClip)

    /**
     * Utility function for safe operations on the selected layer.
     */
    protected inline fun doOnSelectedLayer(function: PaintLayer.() -> Unit) {
        selectedLayer?.let(function)
    }

    /** Interface for layer change event handling. */
    interface OnLayersChanged {
        fun onLayersChanged(layers: List<PaintLayer>, selectedLayerIndex: Int)
    }
}

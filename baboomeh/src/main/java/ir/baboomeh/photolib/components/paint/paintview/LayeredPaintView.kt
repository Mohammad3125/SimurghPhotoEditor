package ir.baboomeh.photolib.components.paint.paintview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.AttributeSet
import android.view.ScaleGestureDetector
import androidx.core.graphics.createBitmap
import ir.baboomeh.photolib.components.paint.Painter
import ir.baboomeh.photolib.utils.gesture.TouchData
import ir.baboomeh.photolib.utils.gesture.detectors.translation.TranslationDetector
import ir.baboomeh.photolib.utils.history.HistoryHandler
import ir.baboomeh.photolib.utils.history.HistoryState
import ir.baboomeh.photolib.utils.history.handlers.StackHistoryHandler

open class LayeredPaintView(context: Context, attrSet: AttributeSet?) :
    MananPaintView(context, attrSet) {

    constructor(context: Context) : this(context, null)

    override var isCheckerBoardEnabled = true
        set(value) {
            field = value
            cacheLayers()
            invalidate()
        }

    val historyHandler: HistoryHandler = StackHistoryHandler().apply {
        setOnHistoryChanged {
            callUndoRedoListener()
        }
    }

    protected var layerHolder = mutableListOf<PaintLayer>()

    protected var onLayersChanged: ((layers: List<PaintLayer>, selectedLayerIndex: Int) -> Unit)? =
        null
    protected var layerChangedCallback: OnLayersChanged? = null

    protected var stateChangedCallback: ((layers: List<PaintLayer>?, clip: Rect?) -> Unit)? = null

    protected var onUndoOrRedoStateChanged: ((isUndoEnabled: Boolean, isRedoEnabled: Boolean) -> Unit)? =
        null

    protected val mergeCanvas by lazy {
        Canvas()
    }

    protected lateinit var bitmapReference: Bitmap

    protected lateinit var cachedLayer: Bitmap

    protected lateinit var partiallyCachedLayer: Bitmap

    protected var isAllLayersCached: Boolean = false

    protected var isFirstTimeToCallListener = false

    protected var initialPaintLayer: PaintLayer? = null

    override var selectedLayer: PaintLayer? = null
        set(value) {
            field = value
            initialPaintLayer = value?.clone(true)
        }

    open var isCachingEnabled = false
        set(value) {
            field = value
            if (value) {
                if (isViewInitialized || isCacheLayerInitialized() && (partiallyCachedLayer.isRecycled || cachedLayer.isRecycled)) {
                    createCacheLayers()
                    cacheLayers()
                }
                invalidate()
            } else {
                if (isCacheLayerInitialized()) {
                    partiallyCachedLayer.recycle()
                    cachedLayer.recycle()
                }
            }
        }

    protected fun isCacheLayerInitialized(): Boolean =
        this::partiallyCachedLayer.isInitialized && this::cachedLayer.isInitialized


    override var painter: Painter? = null
        set(value) {
            cacheLayers()
            super.painter = value

            field = value

            if (value == null) {
                historyHandler.apply {
                    setOnHistoryChanged {
                        callUndoRedoListener()
                    }
                    callUndoRedoListener()
                }
            } else {
                value.historyHandler?.apply {
                    setOnHistoryChanged {
                        callUndoRedoListener()
                    }
                    callUndoRedoListener()
                }
            }
        }

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

    protected open fun createCacheLayers() {
        if ((!isCacheLayerInitialized() || cachedLayer.isRecycled || partiallyCachedLayer.isRecycled) && isCachingEnabled) {
            cachedLayer = createLayerBitmap()
            partiallyCachedLayer = createLayerBitmap()
        }
    }

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

            if (this@LayeredPaintView::bitmapReference.isInitialized) {
                onReferenceLayerCreated(bitmapReference)
            }
        }
    }

    override fun onMoveEnded(detector: TranslationDetector) {
        super.onMoveEnded(detector)
        cacheLayers()
    }

    override fun callPainterOnMoveBegin(touchData: TouchData) {
        super.callPainterOnMoveBegin(touchData)
        isAllLayersCached = false
    }


    override fun canCallPainterMoveEnd(touchData: TouchData) {
        super.canCallPainterMoveEnd(touchData)
        saveState(createState(), false)
    }

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

    protected open fun updateAfterStateChange() {
        cacheLayers()

        callOnLayerChangedListeners()

        painter?.onLayerChanged(selectedLayer)

        invalidate()
    }

    protected open fun callUndoRedoListener() {
        onUndoOrRedoStateChanged?.invoke(
            historyHandler.getUndoSize() != 0,
            historyHandler.getRedoSize() != 0
        )
    }

    override fun onRotateBegin(initialDegree: Float, px: Float, py: Float): Boolean {
        isAllLayersCached = isFirstMove
        return true
    }

    override fun onScaleBegin(p0: ScaleGestureDetector): Boolean {
        isAllLayersCached = isFirstMove
        return super.onScaleBegin(p0)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.run {

            // Concat the canvas to 'canvasMatrix'.
            concat(canvasMatrix)

            concat(imageviewMatrix)

            if (!layerClipBounds.isEmpty) {
                clipRect(layerClipBounds)
            }

            if (!isCachingEnabled) {
                drawLayers(canvas)
                return
            }

            if (isAllLayersCached && !isAnyLayerBlending()) {
                layersPaint.xfermode = null
                layersPaint.alpha = 255
                drawBitmap(partiallyCachedLayer, 0f, 0f, layersPaint)

                drawPainterLayer(canvas)

                layersPaint.xfermode = null
                layersPaint.alpha = 255

                drawBitmap(cachedLayer, 0f, 0f, layersPaint)
            } else {
                if (this@LayeredPaintView::partiallyCachedLayer.isInitialized) {
                    layersPaint.xfermode = null
                    layersPaint.alpha = 255
                    drawBitmap(partiallyCachedLayer, 0f, 0f, layersPaint)
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

    open fun isAnyLayerBlending(): Boolean =
        layerHolder.any { it.blendingModeObject != null }

    override fun drawPainterLayer(canvas: Canvas) {
        canvas.apply {
            doOnSelectedLayer {
                if (this === layerHolder.first() && isCheckerBoardEnabled) {
                    drawPaint(checkerPatternPaint)
                }

                drawPaintLayer(canvas, this)

                painter?.draw(canvas)
            }
        }
    }

    protected open fun drawLayers(canvas: Canvas) {
        canvas.apply {
            layerHolder.forEach { layer ->
                if (layer === layerHolder.first() && isCheckerBoardEnabled) {
                    drawPaint(checkerPatternPaint)
                }

                drawPaintLayer(canvas, layer)

                if (layer === selectedLayer) {
                    painter?.draw(this)
                }
            }
        }
    }

    override fun onSendMessage(message: Painter.PainterMessage) {
        when (message) {
            Painter.PainterMessage.INVALIDATE -> {
                invalidate()
            }

            Painter.PainterMessage.SAVE_HISTORY -> {
                saveState(createState())
            }

            Painter.PainterMessage.CACHE_LAYERS -> {
                cacheLayers()
            }
        }
    }

    /**
     * Creates a new PaintLayer based on previous layers and adds it to list of layers.
     * @throws IllegalStateException If previous call to [addNewLayer(bitmap)] hasn't been made yet.
     */
    open fun addNewLayer() {
        if (!isViewInitialized) {
            throw IllegalStateException("Cannot make new layer based on previous layers. Did you call `addNewLayer(bitmap)` first?")
        }

        addNewLayerWithoutLayoutReset(PaintLayer(createLayerBitmap()))
    }

    override fun addNewLayer(bitmap: Bitmap?) {
        if (bitmap != null) {
            if (!bitmap.isMutable) {
                throw IllegalStateException("Bitmap should be mutable")
            }

            initializeBitmap(bitmap)

            addNewLayerWithoutLayoutReset(PaintLayer(bitmap))
        }
    }

    open fun addNewLayer(layer: PaintLayer) {
        initializeBitmap(layer.bitmap)

        addNewLayerWithoutLayoutReset(layer)
    }

    protected open fun initializeBitmap(bitmap: Bitmap) {
        layerHolder.clear()
        isNewLayer = true
        bitmapWidth = bitmap.width
        bitmapHeight = bitmap.height
        layerClipBounds.set(0, 0, bitmapWidth, bitmapHeight)
        identityClip.set(layerClipBounds)
    }

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

    open fun addNewLayerWithoutSavingHistory() {
        addNewLayerWithoutSavingHistory(createLayerBitmap())

        if (isViewInitialized) {
            cacheLayers()
        }

        invalidate()
    }

    protected open fun addNewLayerWithoutSavingHistory(bitmap: Bitmap) {
        addNewLayerWithoutSavingHistory(PaintLayer(bitmap, false, 1f))
    }

    open fun addNewLayerWithoutSavingHistory(layer: PaintLayer) {
        selectedLayer = layer

        layerHolder.add(selectedLayer!!)

        callOnLayerChangedListeners()
    }

    protected open fun createLayerBitmap(): Bitmap {
        return createBitmap(bitmapWidth, bitmapHeight)
    }

    protected open fun cacheLayers() {
        if (!this::bitmapReference.isInitialized) {
            return
        }
        mergeCanvas.apply {
            if (!isCachingEnabled) {
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
                    partiallyCachedLayer.eraseColor(Color.TRANSPARENT)

                    setBitmap(partiallyCachedLayer)

                    if (isCheckerBoardEnabled) {
                        drawPaint(checkerPatternPaint)
                    }

                    mergeLayersAtIndex(mergeCanvas, 0, getSelectedLayerIndex() - 1)

                    cachedLayer.eraseColor(Color.TRANSPARENT)

                    setBitmap(cachedLayer)

                    mergeLayersAtIndex(
                        mergeCanvas,
                        getSelectedLayerIndex() + 1,
                        layerHolder.lastIndex
                    )

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

    protected open fun mergeLayersAtIndex(canvas: Canvas, from: Int, to: Int) {
        layerHolder.slice(from..to).forEach { layer ->

            drawPaintLayer(canvas, layer)

            if (layer === selectedLayer) {
                painter?.draw(canvas)
            }
        }
    }

    open fun getLayerOpacityAt(index: Int): Float {
        checkIndex(index)
        return layerHolder[index].opacity
    }

    open fun changeLayerOpacityAt(index: Int, opacity: Float) {
        checkIndex(index)

        layerHolder[index].apply {
            this.opacity = opacity
            saveState(createState())
        }

        cacheLayers()
        invalidate()
    }

    open fun changeLayerOpacityAtWithoutStateSave(index: Int, opacity: Float) {
        checkIndex(index)

        layerHolder[index].apply {
            this.opacity = opacity
        }

        cacheLayers()
        invalidate()
    }

    override fun setSelectedLayerBlendingMode(blendingMode: PorterDuff.Mode) {
        doOnSelectedLayer {
            this.blendingMode = blendingMode
            saveState(createState())
        }

        cacheLayers()

        invalidate()
    }

    open fun setLayerBlendingModeAt(index: Int, blendingMode: PorterDuff.Mode) {
        checkIndex(index)

        layerHolder[index].apply {
            this.blendingMode = blendingMode
            saveState(createState())
        }

        cacheLayers()

        invalidate()
    }


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

    protected open fun cantMoveLayers(from: Int, to: Int): Boolean {
        return from == to || from < 0 || to < 0 || from > layerHolder.lastIndex || to > layerHolder.lastIndex
    }

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

    open fun lockLayer(index: Int) {
        changeLayerLockState(index, true)
    }


    open fun unlockLayer(index: Int) {
        changeLayerLockState(index, false)
    }

    protected open fun changeLayerLockState(index: Int, shouldLock: Boolean) {
        checkIndex(index)

        layerHolder[index].apply {
            isLocked = shouldLock
            saveState(createState())
        }

        cacheLayers()

        callOnLayerChangedListeners()
    }

    open fun isLayerAtIndexLocked(index: Int): Boolean {
        checkIndex(index)
        return layerHolder[index].isLocked
    }

    open fun removeLayerAt(index: Int) {
        checkIndex(index)

        val initialLayers = layerHolder.toMutableList()

        val isSelectedLayerIndex = index == getSelectedLayerIndex()

        layerHolder.removeAt(index)

        saveState(createState(initialLayers))

        if (isSelectedLayerIndex) {
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

    open fun removeLayers(layersIndex: IntArray) {
        val initialLayers = layerHolder.toMutableList()
        removeLayersWithoutStateSave(layersIndex)
        saveState(createState(initialLayers))
    }

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

    open fun getLayerCount(): Int {
        return layerHolder.size
    }

    open fun selectLayer(index: Int) {
        checkIndex(index)
        selectedLayer = layerHolder[index]
        painter?.onLayerChanged(selectedLayer)
        callOnLayerChangedListeners()
        cacheLayers()
        invalidate()
    }

    open fun selectLayer(paintLayer: PaintLayer) {
        if (layerHolder.contains(paintLayer) && selectedLayer !== paintLayer) {
            selectedLayer = paintLayer
            painter?.onLayerChanged(selectedLayer)
            callOnLayerChangedListeners()
            cacheLayers()
            invalidate()
        }
    }

    open fun getSelectedLayerIndex(): Int {
        return layerHolder.indexOf(selectedLayer)
    }

    open fun getLayersBitmap(): List<Bitmap> {
        return layerHolder.map {
            it.bitmap
        }
    }

    open fun mergeLayers(layersIndex: IntArray) {
        if (layersIndex.size < 2) {
            throw IllegalStateException("you need at least two layers to merge")
        }
        layersIndex.forEach { checkIndex(it) }

        if (layersIndex.distinct().size != layersIndex.size) {
            throw IllegalStateException("cannot merge layers with duplicate index")
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

    open fun duplicateSelectedLayer() {
        doOnSelectedLayer {
            duplicateLayer()
        }
    }

    open fun duplicateLayerAt(index: Int) {
        checkIndex(index)
        layerHolder.getOrNull(index)?.duplicateLayer()
    }

    protected fun PaintLayer.duplicateLayer() {
        val initialLayers = layerHolder.toMutableList()
        val cloned = clone(true)
        layerHolder.add(cloned)
        selectedLayer = cloned
        saveState(createState(initialLayers))
        updateAfterStateChange()
    }

    /**
     * Returns all [PaintLayer] in the [LayeredPaintView].
     * Do not change content of [PaintLayer] directly, this leads to unsaved states unless if that's what you want.
     * @return a list containing [PaintLayer]
     */
    open fun getPaintLayers(): List<PaintLayer> {
        return layerHolder.toMutableList()
    }

    protected open fun checkIndex(index: Int) {
        if (index < 0 || index >= layerHolder.size) {
            throw ArrayIndexOutOfBoundsException("provided index is out of bounds")
        }
    }

    open fun setOnLayersChangedListener(onLayersChanged: OnLayersChanged) {
        layerChangedCallback = onLayersChanged
        callListenerForFirstTime()
    }

    open fun setOnLayersChangedListener(callback: ((layers: List<PaintLayer>, selectedLayerIndex: Int) -> Unit)) {
        onLayersChanged = callback
        callListenerForFirstTime()
    }

    override fun setSelectedLayerBitmap(bitmap: Bitmap) {
        doOnSelectedLayer {
            this.bitmap = bitmap
            saveState(createState(), ignorePainterHandleHistoryFlag = true)
        }
        invalidate()
    }

    open fun clearLayers() {
        layerHolder.clear()
        historyHandler.reset()
        selectedLayer = null
    }

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


    protected open fun callListenerForFirstTime() {
        if (isViewInitialized && isFirstTimeToCallListener) {
            callOnLayerChangedListeners()
            isFirstTimeToCallListener = false
        }
    }

    protected open fun callOnLayerChangedListeners() {
        val layers = layerHolder.toList()
        val selectedLayerIndex = getSelectedLayerIndex()
        layerChangedCallback?.onLayersChanged(layers, selectedLayerIndex)
        onLayersChanged?.invoke(layers, selectedLayerIndex)
    }

    open fun setOnStateChanged(callback: (layers: List<PaintLayer>?, clip: Rect?) -> Unit) {
        stateChangedCallback = callback
    }

    open fun setOnUndoOrRedoListener(func: (isUndoEnabled: Boolean, isRedoEnabled: Boolean) -> Unit) {
        onUndoOrRedoStateChanged = func
    }

    protected open inner class State(
        val initialLayer: PaintLayer?,
        val initialLayers: MutableList<PaintLayer>?,
        val reference: PaintLayer? = selectedLayer,
        val initialClip: Rect? = null,
        val clonedClip: Rect? = null
    ) : HistoryState {
        val clonedLayer = reference?.clone(true)
        val clonedLayers = layerHolder.toMutableList()

        override fun undo() {
            restoreState(initialLayer, initialLayers, initialClip)
        }

        override fun redo() {
            restoreState(clonedLayer, clonedLayers, clonedClip)
        }

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

    protected open fun createState(
        initialLayers: MutableList<PaintLayer>? = null,
        referenceLayer: PaintLayer? = selectedLayer,
        initialClip: Rect? = null,
        clonedClip: Rect? = null,
    ) =
        State(initialPaintLayer, initialLayers, referenceLayer, initialClip, clonedClip)

    protected inline fun doOnSelectedLayer(function: PaintLayer.() -> Unit) {
        selectedLayer?.let(function)
    }

    interface OnLayersChanged {
        fun onLayersChanged(layers: List<PaintLayer>, selectedLayerIndex: Int)
    }

}
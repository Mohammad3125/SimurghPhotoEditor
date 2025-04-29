package ir.baboomeh.photolib.components.paint.paintview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.AttributeSet
import android.view.ScaleGestureDetector
import ir.baboomeh.photolib.components.paint.Painter
import ir.baboomeh.photolib.utils.gesture.TouchData
import ir.baboomeh.photolib.utils.gesture.detectors.translation.TranslationDetector
import java.util.Stack

open class LayeredPaintView(context: Context, attrSet: AttributeSet?) :
    MananPaintView(context, attrSet) {

    constructor(context: Context) : this(context, null)

    override var isCheckerBoardEnabled = true
        set(value) {
            field = value
            cacheLayers()
            invalidate()
        }

    private val undoStack = Stack<State>()
    private val redoStack = Stack<State>()

    private var layerHolder = mutableListOf<PaintLayer>()


    private var onLayersChanged: ((layers: List<PaintLayer>, selectedLayerIndex: Int) -> Unit)? =
        null
    private var onLayersChangedListener: OnLayersChanged? = null

    private var onStateChanged: (() -> Unit)? = null

    private val mergeCanvas by lazy {
        Canvas()
    }

    private lateinit var bitmapReference: Bitmap

    private lateinit var cachedLayer: Bitmap

    private lateinit var partiallyCachedLayer: Bitmap

    private var isAllLayersCached: Boolean = false

    private var isFirstTimeToCallListener = false

    protected var initialPaintLayer: PaintLayer? = null

    override var selectedLayer: PaintLayer? = null
        set(value) {
            field = value
            initialPaintLayer = value?.clone(true)
        }

    var isCachingEnabled = false
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

    private fun isCacheLayerInitialized(): Boolean =
        this::partiallyCachedLayer.isInitialized && this::cachedLayer.isInitialized


    var maximumHistorySize = 15
        set(value) {
            field = value
            while (isHistorySizeExceeded()) {
                removeFirstState()
            }
        }

    override var painter: Painter? = null
        set(value) {
            cacheLayers()
            super.painter = value
            field = value
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

    private fun createCacheLayers() {
        if ((!isCacheLayerInitialized() || cachedLayer.isRecycled || partiallyCachedLayer.isRecycled) && isCachingEnabled) {
            cachedLayer = createLayerBitmap()
            partiallyCachedLayer = createLayerBitmap()
        }
    }

    override fun initializedPainter(pp: Painter?) {
        pp?.let { p ->
            rectAlloc.set(layerBounds)
            if (!pp.isInitialized) {
                p.initialize(
                    context,
                    canvasMatrix,
                    imageviewMatrix,
                    identityClip,
                    layerClipBounds
                )
            }
            p.onLayerChanged(selectedLayer)
            if (this::bitmapReference.isInitialized) {
                p.onReferenceLayerCreated(bitmapReference)
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

    fun undo() {
        if (clipAnimator.isRunning) {
            return
        }
        painter?.let { p ->
            if (p.doesHandleHistory()) {
                p.undo()
                return
            }
        }
        if (undoStack.size > 1) {
            val poppedState = undoStack.pop()
            poppedState.undo()
            redoStack.push(poppedState)

            updateAfterStateChange()
        }
    }

    fun redo() {
        if (clipAnimator.isRunning) {
            return
        }
        painter?.let { p ->
            if (p.doesHandleHistory()) {
                p.redo()
                return
            }
        }
        if (redoStack.isNotEmpty()) {
            val poppedState = redoStack.pop()
            poppedState.redo()
            undoStack.push(poppedState)

            updateAfterStateChange()
        }
    }

    private fun updateAfterStateChange() {
        cacheLayers()

        callOnLayerChangedListeners()

        painter?.onLayerChanged(selectedLayer)

        invalidate()
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
                drawLayers()
                return
            }

            if (isAllLayersCached && !isAnyLayerBlending()) {
                layersPaint.xfermode = null
                layersPaint.alpha = 255
                drawBitmap(partiallyCachedLayer, 0f, 0f, layersPaint)

                drawPainterLayer()

                layersPaint.xfermode = null
                layersPaint.alpha = 255

                drawBitmap(cachedLayer, 0f, 0f, layersPaint)
            } else {
                if (this@LayeredPaintView::partiallyCachedLayer.isInitialized) {
                    layersPaint.xfermode = null
                    layersPaint.alpha = 255
                    drawBitmap(partiallyCachedLayer, 0f, 0f, layersPaint)
                }

                drawPainterLayer()

                mergeLayersAtIndex(layerHolder.indexOf(selectedLayer) + 1, layerHolder.lastIndex)
            }
        }
    }

    fun isAnyLayerBlending(): Boolean =
        layerHolder.any { it.blendingModeObject != null }

    override fun Canvas.drawPainterLayer() {
        doOnSelectedLayer {
            if (this === layerHolder.first() && isCheckerBoardEnabled) {
                drawPaint(checkerPatternPaint)
            }

            this.draw(this@drawPainterLayer)

            painter?.draw(this@drawPainterLayer)
        }
    }

    protected open fun Canvas.drawLayers() {
        layerHolder.forEach { layer ->
            if (layer === layerHolder.first() && isCheckerBoardEnabled) {
                drawPaint(checkerPatternPaint)
            }

            layer.draw(this)

            if (layer === selectedLayer) {
                painter?.draw(this)
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


    private fun isHistorySizeExceeded() = undoStack.size > maximumHistorySize

    private fun removeFirstState() {
        undoStack.removeAt(0)
    }

    /**
     * Creates a new PaintLayer based on previous layers and adds it to list of layers.
     * @throws IllegalStateException If previous call to [addNewLayer(bitmap)] hasn't been made yet.
     */
    fun addNewLayer() {
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

    fun addNewLayer(layer: PaintLayer) {
        initializeBitmap(layer.bitmap)

        addNewLayerWithoutLayoutReset(layer)
    }

    private fun initializeBitmap(bitmap: Bitmap) {
        layerHolder.clear()
        isNewLayer = true
        bitmapWidth = bitmap.width
        bitmapHeight = bitmap.height
        layerClipBounds.set(0, 0, bitmapWidth, bitmapHeight)
        identityClip.set(layerClipBounds)
    }

    private fun addNewLayerWithoutLayoutReset(layer: PaintLayer) {
        selectedLayer = layer

        val initialLayers = layerHolder.toMutableList()

        layerHolder.add(selectedLayer!!)

        saveState(createState(initialLayers))

        if (!isViewInitialized) {
            requestLayout()
        }

        updateAfterStateChange()
    }

    private fun saveState(state: State?, ignorePainterHandleHistoryFlag: Boolean = true) {
        if (state == null || maximumHistorySize == 0) {
            return
        }
        if (painter?.doesHandleHistory() == true && !ignorePainterHandleHistoryFlag) {
            return
        }

        onStateChanged?.invoke()

        redoStack.clear()
        undoStack.push(state)

        initialPaintLayer = selectedLayer?.clone(true)

        if (isHistorySizeExceeded()) {
            removeFirstState()
        }
    }

    fun addNewLayerWithoutSavingHistory() {
        addNewLayerWithoutSavingHistory(createLayerBitmap())

        if (isViewInitialized) {
            cacheLayers()
        }

        invalidate()
    }

    private fun addNewLayerWithoutSavingHistory(bitmap: Bitmap) {
        addNewLayerWithoutSavingHistory(PaintLayer(bitmap, false, 1f))
    }

    fun addNewLayerWithoutSavingHistory(layer: PaintLayer) {
        selectedLayer = layer

        layerHolder.add(selectedLayer!!)

        callOnLayerChangedListeners()
    }

    private fun createLayerBitmap(): Bitmap {
        return Bitmap.createBitmap(
            bitmapWidth,
            bitmapHeight,
            Bitmap.Config.ARGB_8888
        )
    }

    private fun cacheLayers() {
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
                mergeLayersAtIndex(0, layerHolder.lastIndex)
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

                    mergeLayersAtIndex(0, getSelectedLayerIndex() - 1)

                    cachedLayer.eraseColor(Color.TRANSPARENT)

                    setBitmap(cachedLayer)

                    mergeLayersAtIndex(getSelectedLayerIndex() + 1, layerHolder.lastIndex)

                    bitmapReference.eraseColor(Color.TRANSPARENT)

                    setBitmap(bitmapReference)

                    drawBitmap(partiallyCachedLayer, 0f, 0f, layersPaint)

                    drawBitmap(cachedLayer, 0f, 0f, layersPaint)

                    drawPainterLayer()

                    painter?.onReferenceLayerCreated(bitmapReference)

                    isAllLayersCached = true
                }
            }
        }
    }

    private fun Canvas.mergeLayersAtIndex(from: Int, to: Int) {
        layerHolder.slice(from..to).forEach { layer ->
            layer.draw(this)

            if (layer === selectedLayer) {
                painter?.draw(this)
            }
        }
    }

    fun getLayerOpacityAt(index: Int): Float {
        checkIndex(index)
        return layerHolder[index].opacity
    }

    fun changeLayerOpacityAt(index: Int, opacity: Float) {
        checkIndex(index)

        layerHolder[index].apply {
            this.opacity = opacity
            saveState(createState())
        }

        cacheLayers()
        invalidate()
    }

    fun changeLayerOpacityAtWithoutStateSave(index: Int, opacity: Float) {
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

    fun setLayerBlendingModeAt(index: Int, blendingMode: PorterDuff.Mode) {
        checkIndex(index)

        layerHolder[index].apply {
            this.blendingMode = blendingMode
            saveState(createState())
        }

        cacheLayers()

        invalidate()
    }


    fun moveLayer(from: Int, to: Int) {
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

    private fun cantMoveLayers(from: Int, to: Int): Boolean {
        return from == to || from < 0 || to < 0 || from > layerHolder.lastIndex || to > layerHolder.lastIndex
    }

    fun moveSelectedLayerDown() {
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

    fun moveSelectedLayerUp() {
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

    fun lockLayer(index: Int) {
        changeLayerLockState(index, true)
    }


    fun unlockLayer(index: Int) {
        changeLayerLockState(index, false)
    }

    private fun changeLayerLockState(index: Int, shouldLock: Boolean) {
        checkIndex(index)

        layerHolder[index].apply {
            isLocked = shouldLock
            saveState(createState())
        }

        cacheLayers()

        callOnLayerChangedListeners()
    }

    fun isLayerAtIndexLocked(index: Int): Boolean {
        checkIndex(index)
        return layerHolder[index].isLocked
    }

    fun removeLayerAt(index: Int) {
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

    fun removeLayers(layersIndex: IntArray) {
        val initialLayers = layerHolder.toMutableList()
        removeLayersWithoutStateSave(layersIndex)
        saveState(createState(initialLayers))
    }

    private fun removeLayersWithoutStateSave(layersIndex: IntArray) {
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

    fun getLayerCount(): Int {
        return layerHolder.size
    }

    fun selectLayer(index: Int) {
        checkIndex(index)
        selectedLayer = layerHolder[index]
        painter?.onLayerChanged(selectedLayer)
        callOnLayerChangedListeners()
        cacheLayers()
        invalidate()
    }

    fun selectLayer(paintLayer: PaintLayer) {
        if (layerHolder.contains(paintLayer) && selectedLayer !== paintLayer) {
            selectedLayer = paintLayer
            painter?.onLayerChanged(selectedLayer)
            callOnLayerChangedListeners()
            cacheLayers()
            invalidate()
        }
    }

    fun getSelectedLayerIndex(): Int {
        return layerHolder.indexOf(selectedLayer)
    }

    fun getLayersBitmap(): List<Bitmap> {
        return layerHolder.map {
            it.bitmap
        }
    }

    fun mergeLayers(layersIndex: IntArray) {
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

        mergeCanvas.setBitmap(bottomLayer.bitmap)

        val sortedMinusBottomIndex = layersIndex.sorted().minus(lowerIndex)

        sortedMinusBottomIndex.map { layerHolder[it] }.forEach { layer ->
            layer.draw(mergeCanvas)

            if (layer === selectedLayer) {
                painter?.draw(mergeCanvas)
            }
        }

        removeLayersWithoutStateSave(sortedMinusBottomIndex.toIntArray())
        saveState(createState(initialLayers, bottomLayer))
    }

    fun duplicateSelectedLayer() {
        doOnSelectedLayer {
            duplicateLayer()
        }
    }

    fun duplicateLayerAt(index: Int) {
        checkIndex(index)
        layerHolder.getOrNull(index)?.duplicateLayer()
    }

    private fun PaintLayer.duplicateLayer() {
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
    fun getPaintLayers(): List<PaintLayer> {
        return layerHolder.toMutableList()
    }

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= layerHolder.size) {
            throw ArrayIndexOutOfBoundsException("provided index is out of bounds")
        }
    }

    fun setOnLayersChangedListener(onLayersChanged: OnLayersChanged) {
        onLayersChangedListener = onLayersChanged
        callListenerForFirstTime()
    }

    fun setOnLayersChangedListener(callback: ((layers: List<PaintLayer>, selectedLayerIndex: Int) -> Unit)) {
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
        undoStack.clear()
        redoStack.clear()
        selectedLayer = null
    }

    override fun convertToBitmap(): Bitmap? {
        if (layerHolder.isEmpty()) {
            return null
        }

        val finalBitmap = layerHolder.first().bitmap.let { layer ->
            Bitmap.createBitmap(
                layer.width,
                layer.height,
                layer.config ?: Bitmap.Config.ARGB_8888
            )
        }

        mergeCanvas.setBitmap(finalBitmap)

        layerHolder.forEach { layer ->
            layer.draw(mergeCanvas)
        }

        return Bitmap.createBitmap(
            finalBitmap,
            layerClipBounds.left,
            layerClipBounds.top,
            layerClipBounds.width(),
            layerClipBounds.height()
        )
    }

    fun setClipRectWithStateSave(
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


    private fun callListenerForFirstTime() {
        if (isViewInitialized && isFirstTimeToCallListener) {
            callOnLayerChangedListeners()
            isFirstTimeToCallListener = false
        }
    }

    private fun callOnLayerChangedListeners() {
        val layers = layerHolder.toList()
        val selectedLayerIndex = getSelectedLayerIndex()
        onLayersChangedListener?.onLayersChanged(layers, selectedLayerIndex)
        onLayersChanged?.invoke(layers, selectedLayerIndex)
    }

    fun setOnStateChanged(callback: () -> Unit) {
        onStateChanged = callback
    }

    private inner class State(
        val initialLayer: PaintLayer?,
        val initialLayers: MutableList<PaintLayer>?,
        val reference: PaintLayer? = selectedLayer,
        val initialClip: Rect? = null,
        val clonedClip: Rect? = null
    ) {
        val clonedLayer = reference?.clone(true)
        val clonedLayers = layerHolder.toMutableList()

        fun undo() {
            restoreState(initialLayer, initialLayers, initialClip)
        }

        fun redo() {
            restoreState(clonedLayer, clonedLayers, clonedClip)
        }

        private fun restoreState(
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

    private fun createState(
        initialLayers: MutableList<PaintLayer>? = null,
        referenceLayer: PaintLayer? = selectedLayer,
        initialClip: Rect? = null,
        clonedClip: Rect? = null,
    ) =
        State(initialPaintLayer, initialLayers, referenceLayer, initialClip, clonedClip)

    private fun doOnSelectedLayer(function: PaintLayer.() -> Unit) {
        selectedLayer?.let(function)
    }

    interface OnLayersChanged {
        fun onLayersChanged(layers: List<PaintLayer>, selectedLayerIndex: Int)
    }

}
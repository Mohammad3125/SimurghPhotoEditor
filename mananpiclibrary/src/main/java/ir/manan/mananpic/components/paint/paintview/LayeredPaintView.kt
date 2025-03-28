package ir.manan.mananpic.components.paint.paintview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.utils.gesture.TouchData
import ir.manan.mananpic.utils.gesture.detectors.translation.TranslationDetector
import java.util.Stack

class LayeredPaintView(context: Context, attrSet: AttributeSet?) :
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

    private var isFirstLayerCreation = true

    private var onLayersChanged: ((layers: List<PaintLayer>, selectedLayerIndex: Int) -> Unit)? =
        null
    private var onLayersChangedListener: OnLayersChanged? = null

    private val mergeCanvas = Canvas()

    private lateinit var bitmapReference: Bitmap

    private lateinit var cachedLayer: Bitmap

    private lateinit var partiallyCachedLayer: Bitmap

    private var isAllLayersCached: Boolean = false

    private var isFirstTimeToCallListener = false

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

    override var bitmap: Bitmap? = null
        set(value) {
            field = value
            if (value != null) {

                if (!value.isMutable) {
                    throw IllegalStateException("Bitmap should be mutable")
                }

                isNewBitmap = true
                bitmapWidth = value.width
                bitmapHeight = value.height
                isViewInitialized = false

                layerClipBounds.set(0, 0, bitmapWidth, bitmapHeight)
                identityClip.set(layerClipBounds)

                addNewLayer(value)

                requestLayout()
                invalidate()
            }
        }


    override fun onImageLaidOut() {
        rectAlloc.set(boundsRectangle)

        if (!isViewInitialized) {
            initializedPainter(painter)

            cachedLayer = createLayerBitmap()

            partiallyCachedLayer = createLayerBitmap()

            bitmapReference = createLayerBitmap()

            cacheLayers()

            isViewInitialized = true
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || resetMatrixAnimator.isRunning) {
            return false
        }

        if (isScalingEnabled) {
            scaleDetector.onTouchEvent(event)
        }

        if (isTranslationEnabled) {
            translationDetector.onTouchEvent(event)
        }

        if (painter?.doesTakeGestures() == true) {
            mappingMatrix.setConcat(canvasMatrix, imageviewMatrix)
            mappingMatrix.invert(mappingMatrix)
            event.transform(mappingMatrix)
        }

        if (isRotatingEnabled) {
            rotationDetector.onTouchEvent(event)
        }
        return true
    }

    override fun onMoveEnded(detector: TranslationDetector) {
        super.onMoveEnded(detector)
        cacheLayers()
    }


    private fun checkForStateSave() {
        if (undoStack.isNotEmpty() && (selectedLayer !== undoStack.peek().ref || undoStack.peek().isLayerChangeState)) {
            saveState(true, isMessage = true)
        } else if (redoStack.isNotEmpty() || undoStack.size == 0) {
            saveState(false, isMessage = true)
        }
    }

    override fun callPainterOnMoveBegin(touchData: TouchData) {
        checkForStateSave()
        super.callPainterOnMoveBegin(touchData)
        isAllLayersCached = false
    }


    override fun callPainterOnMoveEnd(touchData: TouchData) {
        checkForStateSave()
        super.callPainterOnMoveEnd(touchData)
        saveState()
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

            if (isAllLayersCached && !isAnyLayerBlending()) {
                layersPaint.xfermode = null
                layersPaint.alpha = 255
                drawBitmap(partiallyCachedLayer, 0f, 0f, layersPaint)

                drawLayer(canvas)

                layersPaint.xfermode = null
                layersPaint.alpha = 255

                drawBitmap(cachedLayer, 0f, 0f, layersPaint)
            } else {
                if (this@LayeredPaintView::partiallyCachedLayer.isInitialized) {
                    layersPaint.xfermode = null
                    layersPaint.alpha = 255
                    drawBitmap(partiallyCachedLayer, 0f, 0f, layersPaint)
                }

                drawLayer(canvas)

                for (i in layerHolder.indexOf(selectedLayer) + 1..layerHolder.lastIndex) {

                    val layer = layerHolder[i]

                    layersPaint.alpha = (255 * layer.opacity).toInt()

                    layersPaint.xfermode = layer.blendingModeObject

                    drawBitmap(layer.bitmap, 0f, 0f, layersPaint)
                }

            }

        }
    }

    fun isAnyLayerBlending(): Boolean =
        layerHolder.any { it.blendingModeObject != null }

    override fun drawLayer(canvas: Canvas) {
        canvas.apply {
            selectedLayer?.let { layer ->
                if (layer === layerHolder.first() && isCheckerBoardEnabled) {
                    drawPaint(checkerPatternPaint)
                }

                layersPaint.alpha = (255 * layer.opacity).toInt()
                layersPaint.xfermode = layer.blendingModeObject

                drawBitmap(layer.bitmap, 0f, 0f, layersPaint)

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
                saveState(isMessage = true)
            }

            Painter.PainterMessage.CACHE_LAYERS -> {
                cacheLayers()
            }
        }
    }

    private fun saveState(
        isSpecial: Boolean = false,
        isMessage: Boolean = false,
        shouldClone: Boolean = true,
        isLayerChange: Boolean = false,
        isClipChange: Boolean = false,
        targetClip: Rect = layerClipBounds,
        refLayer: PaintLayer? = selectedLayer
    ) {

        if (!isClipChange && painter?.doesHandleHistory() == true && !isMessage && undoStack.isNotEmpty()) {
            return
        }

        redoStack.clear()

        selectedLayer?.let { sl ->
            val copiedList = MutableList(layerHolder.size) {
                layerHolder[it]
            }
            undoStack.add(
                State(
                    refLayer!!,
                    refLayer.clone(shouldClone),
                    copiedList,
                    isSpecial,
                    isLayerChange,
                    Rect(targetClip)
                )
            )
        }

        if (isHistorySizeExceeded()) {
            removeFirstState()
        }

    }

    private fun isHistorySizeExceeded() = undoStack.size > maximumHistorySize

    private fun removeFirstState() {
        undoStack.removeAt(0)
    }

    fun undo() {
        painter?.let { p ->
            if (p.doesHandleHistory()) {
                p.undo()
                return
            }
        }
        swapStacks(undoStack, redoStack, true)
    }

    fun redo() {
        painter?.let { p ->
            if (p.doesHandleHistory()) {
                p.redo()
                return
            }
        }
        swapStacks(redoStack, undoStack)
    }

    private fun swapStacks(
        popStack: Stack<State>,
        pushStack: Stack<State>,
        isUndo: Boolean = false
    ) {
        if (popStack.isNotEmpty()) {

            val isFirstSnapshot = isUndo && popStack.size == 1

            val poppedState = if (isFirstSnapshot) popStack.peek() else popStack.pop()

            if ((popStack.isNotEmpty() && !isFirstSnapshot) && (pushStack.isEmpty() || (poppedState.clonedLayer == layerHolder.last() && !poppedState.isLayerChangeState) || poppedState.isSpecial)
            ) {
                poppedState.restoreState(this)
                val newPopped = popStack.pop()
                newPopped.restoreState(this)
                pushStack.push(poppedState)
                pushStack.push(newPopped)
            } else {
                if (!isFirstSnapshot) {
                    pushStack.push(poppedState)
                }
                poppedState.restoreState(this)
            }

            cacheLayers()

            callOnLayerChangedListeners(
                layerHolder.toList(),
                layerHolder.indexOf(selectedLayer)
            )

            painter?.onLayerChanged(selectedLayer)

            invalidate()
        }
    }

    fun addNewLayer() {
        addNewLayer(createLayerBitmap())
    }

    private fun addNewLayer(bitmap: Bitmap) {

        if (isFirstLayerCreation) {
            saveState(isMessage = false)
            isFirstLayerCreation = false
        }

        checkForStateSave()

        selectedLayer = PaintLayer(
            bitmap, Matrix(), false, 1f
        )

        layerHolder.add(selectedLayer!!)

        cacheLayers()

        // State of layer should be saved no matter if another painter handles history or not.
        saveState(isMessage = true)

        callOnLayerChangedListeners(layerHolder.toList(), layerHolder.size - 1)

        painter?.onLayerChanged(selectedLayer)

        invalidate()

    }

    fun addNewLayerWithoutSavingHistory() {
        addNewLayerWithoutSavingHistory(createLayerBitmap())

        if (isViewInitialized) {
            cacheLayers()
        }

        invalidate()
    }

    private fun addNewLayerWithoutSavingHistory(bitmap: Bitmap) {
        selectedLayer = PaintLayer(
            bitmap, Matrix(), false, 1f
        )

        layerHolder.add(selectedLayer!!)

        callOnLayerChangedListeners(layerHolder.toList(), layerHolder.indexOf(selectedLayer))
    }

    private fun createLayerBitmap(): Bitmap {
        return Bitmap.createBitmap(
            bitmapWidth,
            bitmapHeight,
            Bitmap.Config.ARGB_8888
        )
    }

    private fun cacheLayers() {
        if (!this::partiallyCachedLayer.isInitialized || !this::bitmapReference.isInitialized || !this::cachedLayer.isInitialized) {
            return
        }
        bitmap?.let { _ ->
            selectedLayer?.let { sv ->

                partiallyCachedLayer.eraseColor(Color.TRANSPARENT)

                mergeCanvas.setBitmap(partiallyCachedLayer)

                if (isCheckerBoardEnabled) {
                    mergeCanvas.drawPaint(checkerPatternPaint)
                }

                val selectedLayerIndex = layerHolder.indexOf(sv)

                mergeLayersAtIndex(0, selectedLayerIndex - 1)

                cachedLayer.eraseColor(Color.TRANSPARENT)

                mergeCanvas.setBitmap(cachedLayer)

                mergeLayersAtIndex(selectedLayerIndex + 1, layerHolder.lastIndex)

                mergeCanvas.setBitmap(bitmapReference)

                mergeCanvas.drawBitmap(partiallyCachedLayer, 0f, 0f, layersPaint)

                mergeCanvas.drawBitmap(cachedLayer, 0f, 0f, layersPaint)

                drawLayer(mergeCanvas)

                painter?.onReferenceLayerCreated(bitmapReference)

                isAllLayersCached = true
            }
        }

    }

    private fun mergeLayersAtIndex(from: Int, to: Int) {
        for (i in from..to) {

            val layer = layerHolder[i]

            layersPaint.alpha = (255 * layer.opacity).toInt()

            layersPaint.xfermode = layer.blendingModeObject

            mergeCanvas.drawBitmap(
                layer.bitmap,
                0f,
                0f,
                layersPaint
            )

            if (layer === selectedLayer) {
                painter?.draw(mergeCanvas)
            }
        }
    }

    fun getLayerOpacityAt(index: Int): Float {
        checkIndex(index)
        return layerHolder[index].opacity
    }

    fun changeLayerOpacityAt(index: Int, opacity: Float) {
        checkIndex(index)
        layerHolder[index].opacity = opacity
        cacheLayers()
        invalidate()
    }

    override fun setSelectedLayerBlendingMode(blendingMode: PorterDuff.Mode) {
        selectedLayer?.blendingMode = blendingMode

        cacheLayers()

        saveState(shouldClone = false)

        invalidate()
    }

    fun setLayerBlendingModeAt(index: Int, blendingMode: PorterDuff.Mode) {
        checkIndex(index)

        layerHolder[index].blendingMode = blendingMode

        cacheLayers()

        saveState(shouldClone = false)

        invalidate()
    }


    fun moveLayer(from: Int, to: Int) {
        if (cantMoveLayers(from, to)) {
            return
        }

        val layerFrom = layerHolder[from]
        layerHolder[from] = layerHolder[to]
        layerHolder[to] = layerFrom

        cacheLayers()

        saveState(shouldClone = false, isLayerChange = true)

        callOnLayerChangedListeners(
            layerHolder.toList(),
            layerHolder.indexOf(selectedLayer)
        )

        invalidate()
    }

    private fun cantMoveLayers(from: Int, to: Int): Boolean {
        return from == to || from < 0 || to < 0 || from > layerHolder.lastIndex || to > layerHolder.lastIndex
    }

    fun moveSelectedLayerDown() {
        selectedLayer?.let { sv ->
            val index = layerHolder.indexOf(selectedLayer)

            if (index > 0) {
                val pervIndex = index - 1
                val previousLayer = layerHolder[pervIndex]
                layerHolder[pervIndex] = sv
                layerHolder[index] = previousLayer

                cacheLayers()

                saveState(shouldClone = false, isLayerChange = true)

                callOnLayerChangedListeners(
                    layerHolder.toList(),
                    layerHolder.indexOf(selectedLayer)
                )

                invalidate()
            }
        }
    }

    fun moveSelectedLayerUp() {
        selectedLayer?.let { sv ->
            val index = layerHolder.indexOf(selectedLayer)

            if (index < layerHolder.lastIndex) {
                val nextIndex = index + 1
                val previousLayer = layerHolder[nextIndex]
                layerHolder[nextIndex] = sv
                layerHolder[index] = previousLayer

                cacheLayers()

                saveState(shouldClone = false, isLayerChange = true)

                callOnLayerChangedListeners(
                    layerHolder.toList(),
                    layerHolder.indexOf(selectedLayer)
                )

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
        layerHolder[index].isLocked = shouldLock
        cacheLayers()
    }

    fun isLayerAtIndexLocked(index: Int): Boolean {
        checkIndex(index)
        return layerHolder[index].isLocked
    }

    fun removeLayerAt(index: Int) {
        removeLayerAtWithoutStateSave(index)
        saveState()
    }

    fun removeLayers(layersIndex: IntArray) {
        removeLayersWithoutStateSave(layersIndex)
        saveState()
    }

    private fun removeLayersWithoutStateSave(layersIndex: IntArray) {
        layersIndex.forEach { checkIndex(it) }

        if (layersIndex.contains(getIndexOfSelectedLayer())) {
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

        callOnLayerChangedListeners(
            layerHolder.toList(),
            layerHolder.indexOf(selectedLayer)
        )

        invalidate()
    }

    private fun removeLayerAtWithoutStateSave(index: Int) {
        checkIndex(index)

        if (index == getIndexOfSelectedLayer()) {
            selectedLayer = when {
                layerHolder.size <= 1 -> null
                index > 0 -> layerHolder[index - 1]
                else -> layerHolder[1]
            }
            painter?.onLayerChanged(selectedLayer)
        }

        layerHolder.removeAt(index)

        cacheLayers()

        callOnLayerChangedListeners(
            layerHolder.toList(),
            layerHolder.indexOf(selectedLayer)
        )

        invalidate()
    }

    fun getLayerCount(): Int {
        return layerHolder.size
    }

    fun selectLayer(index: Int) {
        checkIndex(index)
        selectedLayer = layerHolder[index]
        painter?.onLayerChanged(selectedLayer)
        callOnLayerChangedListeners(
            layerHolder.toList(),
            layerHolder.indexOf(selectedLayer)
        )
        cacheLayers()
        invalidate()
    }

    fun selectLayer(paintLayer: PaintLayer) {
        if (layerHolder.contains(paintLayer) && selectedLayer !== paintLayer) {
            selectedLayer = paintLayer
            painter?.onLayerChanged(selectedLayer)
            callOnLayerChangedListeners(
                layerHolder.toList(),
                layerHolder.indexOf(selectedLayer)
            )
            cacheLayers()
            invalidate()
        }
    }

    fun getIndexOfSelectedLayer(): Int {
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

        val lowerIndex = layersIndex.min()
        val bottomLayer = layerHolder[lowerIndex]

        saveState(refLayer = bottomLayer, isSpecial = true)

        mergeCanvas.setBitmap(bottomLayer.bitmap)

        val sortedMinusBottomIndex = layersIndex.sorted().minus(lowerIndex)

        sortedMinusBottomIndex.map { layerHolder[it] }.forEach { layer ->
            layersPaint.alpha = (255 * layer.opacity).toInt()

            layersPaint.xfermode = layer.blendingModeObject

            mergeCanvas.drawBitmap(
                layer.bitmap,
                0f,
                0f,
                layersPaint
            )

            if (layer === selectedLayer) {
                painter?.draw(mergeCanvas)
            }
        }

        removeLayersWithoutStateSave(sortedMinusBottomIndex.toIntArray())
        saveState(refLayer = bottomLayer)
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
        selectedLayer?.bitmap = bitmap
        saveState()
        invalidate()
    }

    override fun convertToBitmap(): Bitmap? {
        if (layerHolder.isEmpty()) {
            return null
        }

        val finalBitmap = layerHolder.first().bitmap.let { layer ->
            Bitmap.createBitmap(layer.width, layer.height, layer.config ?: Bitmap.Config.ARGB_8888)
        }

        mergeCanvas.setBitmap(finalBitmap)

        layerHolder.forEach { layer ->
            layersPaint.alpha = (255 * layer.opacity).toInt()

            layersPaint.xfermode = layer.blendingModeObject

            mergeCanvas.drawBitmap(layer.bitmap, 0f, 0f, layersPaint)
        }

        return finalBitmap
    }


    private fun callListenerForFirstTime() {
        if (isViewInitialized && !isFirstLayerCreation && isFirstTimeToCallListener) {
            callOnLayerChangedListeners(
                layerHolder.toList(),
                layerHolder.indexOf(selectedLayer)
            )
            isFirstTimeToCallListener = false
        }
    }

    private fun callOnLayerChangedListeners(
        layers: List<PaintLayer>,
        selectedLayerIndex: Int
    ) {
        onLayersChangedListener?.onLayersChanged(layers, selectedLayerIndex)
        onLayersChanged?.invoke(layers, selectedLayerIndex)
    }

    fun setClipRectSavedState(rect: Rect, animate: Boolean = true, func: () -> Unit = {}) {
        saveState(shouldClone = true, isClipChange = true)
        super.setClipRect(rect, animate, func)
        saveState(shouldClone = true, isClipChange = true, targetClip = rect)
    }

    private class State(
        val ref: PaintLayer,
        val clonedLayer: PaintLayer,
        val layers: MutableList<PaintLayer>,
        val isSpecial: Boolean = false,
        val isLayerChangeState: Boolean,
        val clipBoundState: Rect
    ) {
        fun restoreState(paintView: LayeredPaintView) {
            if (!isLayerChangeState) {
                ref.set(clonedLayer.clone(true))
            }

            paintView.run {
                val i = layerHolder.indexOf(selectedLayer)
                selectedLayer = when {
                    i > -1 -> {
                        layers.getOrNull(i.coerceAtMost(layers.lastIndex))
                    }

                    layers.isNotEmpty() -> {
                        selectedLayer.takeIf { it in layers } ?: layers.first()
                    }

                    else -> {
                        null
                    }
                }

                layerHolder = MutableList(layers.size) {
                    layers[it]
                }

                if (clipBoundState != layerClipBounds) {
                    setClipRect(clipBoundState, true)
                }
            }

        }

        override fun equals(other: Any?): Boolean {
            other as State
            return clonedLayer == other.clonedLayer &&
                    layers.size == other.layers.size &&
                    layers.containsAll(other.layers) &&
                    isOrderOfLayersMatched(layers, other.layers)
        }

        private fun isOrderOfLayersMatched(
            firstLayers: List<PaintLayer>,
            secondLayers: List<PaintLayer>
        ): Boolean {

            repeat(firstLayers.size) { index ->
                if (firstLayers[index] !== secondLayers[index]) {
                    return false
                }
            }
            return true
        }

        override fun hashCode(): Int {
            var result = ref.hashCode()
            result = 31 * result + clonedLayer.hashCode()
            result = 31 * result + layers.hashCode()
            result = 31 * result + isSpecial.hashCode()
            result = 31 * result + isLayerChangeState.hashCode()
            return result
        }

    }

    interface OnLayersChanged {
        fun onLayersChanged(layers: List<PaintLayer>, selectedLayerIndex: Int)
    }

}
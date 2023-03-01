package ir.manan.mananpic.components.paint

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import ir.manan.mananpic.components.imageviews.MananGestureImageView
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.MananMatrixAnimator
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.detectors.TwoFingerRotationDetector
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MananPaintView(context: Context, attrSet: AttributeSet?) :
    MananGestureImageView(context, attrSet), Painter.MessageChannel {

    constructor(context: Context) : this(context, null)

    private val layersPaint = Paint().apply {
        isFilterBitmap = true
    }
    private var initialX = 0f
    private var initialY = 0f

    private var secondPointerInitialX = 0f
    private var secondPointerInitialY = 0f

    private var isMatrixGesture = false

    private var isNewGesture = true

    private var maximumScale = 0f

    private var rotHolder = 0f

    private var isFirstMove = true

    private var secondDxSum = 0f
    private var secondDySum = 0f

    private var firstDxSum = 0f
    private var firstDySum = 0f


    private var isMoved = false

    // Used to retrieve touch slopes.
    private var scaledTouchSlope = 0

    private val rectAlloc by lazy {
        RectF()
    }

    private val undoStack = Stack<State>()

    private val redoStack = Stack<State>()

    private val animatorExtraSpaceAroundAxes = dp(128)

    private var selectedLayer: PaintLayer? = null

    private val canvasMatrix by lazy {
        MananMatrix()
    }

    private val mappingMatrix by lazy {
        MananMatrix()
    }

    private val matrixAnimator by lazy {
        MananMatrixAnimator(canvasMatrix, RectF(boundsRectangle), 300L, FastOutSlowInInterpolator())
    }

    private var layerHolder = mutableListOf<PaintLayer>()

    private val touchPointMappedArray = FloatArray(2)

    private var isFirstLayerCreation = true


    private var onTapUp: ((Unit) -> Unit)? = null

    private var onDoubleTapUpInterface: OnDoubleTapUp? = null

    private var onLayersChanged: ((layers: List<PaintLayer>, selectedLayerIndex: Int) -> Unit)? =
        null
    private var onLayersChangedListener: OnLayersChanged? = null

    private val mergeCanvas = Canvas()

    private lateinit var cachedLayer: Bitmap

    private lateinit var partiallyCachedLayer: Bitmap

    private var isAllLayersCached: Boolean = false

    private var tot = 0f

    private var isFirstFingerMoved = false

    private var isFirstTimeToCallListener = false

    var isTouchEventHistoryEnabled = false

    var maximumHistorySize = 15
        set(value) {
            field = value
            while (isHistorySizeExceeded()) {
                removeFirstState()
            }
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {

        rectAlloc.set(boundsRectangle)

        super.onSizeChanged(w, h, oldw, oldh)

        mappingMatrix.setRectToRect(
            rectAlloc,
            boundsRectangle, Matrix.ScaleToFit.CENTER
        )

        rectAlloc.set(boundsRectangle)

        painter?.onSizeChanged(rectAlloc, mappingMatrix)
    }

    private var isViewInitialized = false

    var painter: Painter? = null
        set(value) {
            field = value
            value?.setOnMessageListener(this)

            if (!isViewInitialized) {
                requestLayout()
            }

            initializedPainter(field)
        }

    init {
        scaleDetector = ScaleGestureDetector(context, this).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                isQuickScaleEnabled = false
            }
        }

        rotationDetector = TwoFingerRotationDetector(this)

        scaledTouchSlope = ViewConfiguration.get(context).scaledTouchSlop

    }

    override fun onImageLaidOut() {
        context.resources.displayMetrics.run {
            maximumScale =
                max(widthPixels, heightPixels) / min(bitmapWidth, bitmapHeight) * 10f
        }

        rectAlloc.set(boundsRectangle)

        if (!isViewInitialized) {
            initializedPainter(painter)

            cachedLayer = createLayerBitmap()

            partiallyCachedLayer = createLayerBitmap()

            isViewInitialized = true
        }

        if (isFirstLayerCreation) {
            addNewLayer()
            isFirstLayerCreation = false
        }
    }

    private fun initializedPainter(pp: Painter?) {
        pp?.let { p ->
            p.initialize(canvasMatrix, rectAlloc)
            p.onLayerChanged(selectedLayer)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        super.onTouchEvent(event)
        event?.run {
            val totalPoints = pointerCount
            when (actionMasked) {
                MotionEvent.ACTION_DOWN -> {

                    // Save the initial points to later determine how much user has moved
                    // His/her finger across screen.
                    initialX = x
                    initialY = y

                    isFirstMove = true

                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (totalPoints == 2) {
                        secondPointerInitialX = getX(1)
                        secondPointerInitialY = getY(1)

                        isMatrixGesture = true
                    }
                }
                MotionEvent.ACTION_MOVE -> {

                    val finalSlope = abs(scaledTouchSlope / canvasMatrix.getRealScaleX())

                    val firstFingerDx = (x - initialX)
                    val firstFingerDy = (y - initialY)

                    if (!isFirstFingerMoved) {
                        firstDxSum += abs(firstFingerDx)
                        firstDySum += abs(firstFingerDy)
                        isFirstFingerMoved =
                            isFirstFingerMoved.or(firstDxSum >= finalSlope || firstDySum >= finalSlope)
                    }

                    // If there are currently 2 pointers on screen and user is not scaling then
                    // translate the canvas matrix.
                    if (totalPoints == 2 && isNewGesture) {
                        val secondPointerX = getX(1)
                        val secondPointerY = getY(1)

                        val secondPointerDx = secondPointerX - secondPointerInitialX
                        val secondPointerDy = secondPointerY - secondPointerInitialY


                        if (!isMoved) {
                            secondDxSum += abs(secondPointerDx)
                            secondDySum += abs(secondPointerDy)
                            isMoved =
                                isMoved.or(secondDxSum >= finalSlope || secondDySum >= finalSlope)
                        }

                        val dx = (secondPointerDx + firstFingerDx) / 2
                        val dy = (secondPointerDy + firstFingerDy) / 2

                        secondPointerInitialX = secondPointerX
                        secondPointerInitialY = secondPointerY

                        isMatrixGesture = true

                        canvasMatrix.postTranslate(dx, dy)

                        invalidate()
                    }

                    // Else if selector is not null and there is currently 1 pointer on
                    // screen and user is not performing any other gesture like moving or
                    // scaling, then call 'onMove' method of selector.
                    if (painter != null && selectedLayer != null && totalPoints == 1 && !isMatrixGesture && !selectedLayer!!.isLocked && isFirstFingerMoved) {

                        if (isFirstMove) {

                            checkForStateSave()

                            callPainterOnMoveBegin(this)
                        }

                        if (isTouchEventHistoryEnabled) {
                            repeat(historySize) {
                                callPainterOnMove(
                                    getHistoricalX(0, it),
                                    getHistoricalY(0, it),
                                )
                            }
                        }

                        callPainterOnMove(
                            x,
                            y,
                        )

                    }

                    initialX = x
                    initialY = y

                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {

                    if (shouldCallDoubleTapListener()) {
                        callOnDoubleTapListeners()
                    } else if (shouldEndMoveOnPainter()) {
                        checkForStateSave()
                        callPainterOnMoveEnd(x, y)
                    }

                    cacheLayers()

                    isMatrixGesture = false
                    isNewGesture = true

                    secondDxSum = 0f
                    secondDySum = 0f
                    isMoved = false

                    firstDxSum = 0f
                    firstDySum = 0f
                    isFirstFingerMoved = false

                    return false
                }
                else -> {
                    return false
                }
            }
        }
        return false
    }

    private fun checkForStateSave() {
        if (undoStack.isNotEmpty() && (selectedLayer !== undoStack.peek().ref || undoStack.peek().isLayerChangeState)) {
            saveState(true, isMessage = true)
        } else if (redoStack.isNotEmpty()) {
            saveState(false, isMessage = true)
        }
    }

    private fun callPainterOnMoveBegin(event: MotionEvent) {
        mapTouchPoints(initialX, initialY).let { points ->
            painter!!.onMoveBegin(points[0], points[1])
            isFirstMove = false
            isAllLayersCached = false
        }
    }

    private fun shouldCallDoubleTapListener(): Boolean = isMatrixGesture && !isMoved

    private fun shouldEndMoveOnPainter(): Boolean =
        painter != null && selectedLayer != null && (!isMatrixGesture || !isFirstMove) && !selectedLayer!!.isLocked

    private fun callPainterOnMoveEnd(ex: Float, ey: Float) {
        mapTouchPoints(ex, ey).let { points ->
            painter!!.onMoveEnded(points[0], points[1])
            saveState()
        }
    }

    private fun callPainterOnMove(ex: Float, ey: Float) {
        mapTouchPoints(ex, ey).let { points ->

            val dx = points[0] - initialX
            val dy = points[1] - initialY

            val gdx = ex - initialX
            val gdy = ey - initialY

            // Reset initial positions.
            initialX = ex
            initialY = ey

            if (dx == 0f && dy == 0f) {
                return
            }

            painter!!.onMove(
                points[0], points[1], gdx, gdy
            )

        }
    }

    override fun onRotateBegin(initialDegree: Float, px: Float, py: Float): Boolean {
        isAllLayersCached = isFirstMove
        return true
    }

    override fun onRotate(degree: Float, px: Float, py: Float): Boolean {
        canvasMatrix.postRotate(degree - rotHolder, px, py)
        tot += abs(degree - rotHolder)
        rotHolder = degree
        invalidate()
        return true
    }

    override fun onRotateEnded() {
        if (tot > 0.5f) {
            isMoved = true
        }

        tot = 0f
    }

    override fun onScaleBegin(p0: ScaleGestureDetector): Boolean {
        isAllLayersCached = isFirstMove
        return !matrixAnimator.isAnimationRunning()
    }

    override fun onScale(p0: ScaleGestureDetector): Boolean {
        p0.run {
            val sf = scaleFactor
            isMoved = true
            canvasMatrix.postScale(sf, sf, focusX, focusY)
            invalidate()
            return true
        }
    }

    override fun onScaleEnd(p0: ScaleGestureDetector) {
        isMoved = true
        super.onScaleEnd(p0)
    }

    /**
     * This function maps the touch location provided with canvas matrix to provide
     * correct coordinates of touch if canvas is scaled and or translated.
     */
    private fun mapTouchPoints(touchX: Float, touchY: Float): FloatArray {
        touchPointMappedArray[0] = touchX
        touchPointMappedArray[1] = touchY

        canvasMatrix.invert(mappingMatrix)
        mappingMatrix.mapPoints(touchPointMappedArray)

        mappingMatrix.reset()

        imageviewMatrix.invert(mappingMatrix)
        mappingMatrix.mapPoints(touchPointMappedArray)

        return touchPointMappedArray
    }

    fun resetPaint() {
        painter?.resetPaint()
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.run {

            // Concat the canvas to 'canvasMatrix'.
            concat(canvasMatrix)

            if (isAllLayersCached) {
                layersPaint.xfermode = null
                layersPaint.alpha = 255

                concat(imageviewMatrix)

                drawBitmap(cachedLayer, 0f, 0f, layersPaint)

            } else {
                super.onDraw(this)

                concat(imageviewMatrix)

                if (this@MananPaintView::partiallyCachedLayer.isInitialized) {
                    layersPaint.xfermode = null
                    layersPaint.alpha = 255
                    drawBitmap(partiallyCachedLayer, 0f, 0f, layersPaint)
                }

                selectedLayer?.let { layer ->

                    layersPaint.alpha = (255 * layer.opacity).toInt()
                    layersPaint.xfermode = layer.blendMode

                    drawBitmap(layer.bitmap, 0f, 0f, layersPaint)

                    save()
                    clipRect(0, 0, layer.bitmap.width, layer.bitmap.height)

                    painter?.draw(this)

                    restore()

                }

                for (i in layerHolder.indexOf(selectedLayer) + 1..layerHolder.lastIndex) {

                    val layer = layerHolder[i]

                    layersPaint.alpha = (255 * layer.opacity).toInt()

                    layersPaint.xfermode = layer.blendMode

                    drawBitmap(layer.bitmap, 0f, 0f, layersPaint)
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
                saveState(isMessage = true)
            }
            Painter.PainterMessage.CACHE_LAYERS -> {
                cacheLayers()
            }
        }
    }

    private fun animateCanvasBack() {
        matrixAnimator.run {
            startAnimation(maximumScale, animatorExtraSpaceAroundAxes)
            setOnMatrixUpdateListener {
                invalidate()
            }
        }
    }

    private fun saveState(
        isSpecial: Boolean = false,
        isMessage: Boolean = false,
        shouldClone: Boolean = true,
        isLayerChange: Boolean = false
    ) {

        if (painter?.doesHandleHistory() == true && !isMessage) {
            return
        }

        redoStack.clear()

        selectedLayer?.let { sl ->
            val copiedList = MutableList(layerHolder.size) {
                layerHolder[it]
            }
            undoStack.add(
                State(
                    sl,
                    sl.clone(shouldClone),
                    copiedList,
                    isSpecial,
                    isLayerChange
                )
            )
        }

        if (isHistorySizeExceeded()) {
            removeFirstState()
        }

    }

    private fun isHistorySizeExceeded() = undoStack.size > maximumHistorySize

    private fun removeFirstState() {
        undoStack.removeFirst()
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

        checkForStateSave()

        selectedLayer = PaintLayer(
            createLayerBitmap(), Matrix(), false, 1f
        )

        layerHolder.add(selectedLayer!!)

        cacheLayers()

        saveState()

        callOnLayerChangedListeners(layerHolder.toList(), layerHolder.indexOf(selectedLayer))

        painter?.onLayerChanged(selectedLayer)

        invalidate()

    }

    private fun createLayerBitmap(): Bitmap {
        return Bitmap.createBitmap(
            bitmapWidth,
            bitmapHeight,
            Bitmap.Config.ARGB_8888
        )
    }

    private fun cacheLayers() {

        bitmap?.let { mainBitmap ->
            selectedLayer?.let { sv ->

                partiallyCachedLayer.eraseColor(Color.TRANSPARENT)

                mergeCanvas.setBitmap(partiallyCachedLayer)

                mergeCanvas.drawBitmap(mainBitmap, 0f, 0f, layersPaint)

                val selectedLayerIndex = layerHolder.indexOf(sv)

                mergeLayersAtIndex(0, selectedLayerIndex - 1)

                cachedLayer.eraseColor(Color.TRANSPARENT)

                mergeCanvas.setBitmap(cachedLayer)

                mergeCanvas.drawBitmap(partiallyCachedLayer, 0f, 0f, layersPaint)

                mergeLayersAtIndex(selectedLayerIndex, layerHolder.lastIndex)

                isAllLayersCached = true
            }
        }

    }

    private fun mergeLayersAtIndex(from: Int, to: Int) {
        for (i in from..to) {

            val layer = layerHolder[i]

            layersPaint.alpha = (255 * layer.opacity).toInt()

            layersPaint.xfermode = layer.blendMode

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

    fun changeSelectedLayerOpacity(opacity: Float) {
        selectedLayer?.opacity = opacity
        invalidate()
    }

    fun changeLayerOpacityAt(index: Int, opacity: Float) {
        checkIndex(index)
        layerHolder[index].opacity = opacity
        cacheLayers()
        invalidate()
    }

    fun changeSelectedLayerBlendingMode(blendingMode: PorterDuff.Mode) {
        selectedLayer?.blendMode = PorterDuffXfermode(blendingMode)

        saveState(shouldClone = false)

        invalidate()
    }

    fun changedLayerBlendingModeAt(index: Int, blendingMode: PorterDuff.Mode) {
        checkIndex(index)

        layerHolder[index].blendMode = PorterDuffXfermode(blendingMode)

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

    fun removeLayerAt(index: Int) {
        checkIndex(index)

        if (index == getIndexOfSelectedLayer()) {
            selectedLayer = if (layerHolder.size > 1) {
                layerHolder[index - 1]
            } else {
                null
            }


            painter?.onLayerChanged(selectedLayer)

        }

        layerHolder.removeAt(index)

        cacheLayers()

        saveState()

        callOnLayerChangedListeners(
            layerHolder.toList(),
            layerHolder.indexOf(selectedLayer)
        )

        invalidate()
    }

    fun getLayerSize(): Int {
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

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= layerHolder.size) {
            throw ArrayIndexOutOfBoundsException("provided index is out of bounds")
        }
    }

    fun setOnDoubleTapUpListener(onDoubleTapUp: OnDoubleTapUp) {
        onDoubleTapUpInterface = onDoubleTapUp
    }

    fun setOnDoubleTapUpListener(callback: ((Unit) -> Unit)) {
        onTapUp = callback
    }

    fun setOnLayersChangedListener(onLayersChanged: OnLayersChanged) {
        onLayersChangedListener = onLayersChanged
        callListenerForFirstTime()
    }

    fun setOnLayersChangedListener(callback: ((layers: List<PaintLayer>, selectedLayerIndex: Int) -> Unit)) {
        onLayersChanged = callback
        callListenerForFirstTime()
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

    private fun callOnDoubleTapListeners() {
        onTapUp?.invoke(Unit)
        onDoubleTapUpInterface?.onDoubleTapUp()
    }

    private fun callOnLayerChangedListeners(
        layers: List<PaintLayer>,
        selectedLayerIndex: Int
    ) {
        onLayersChangedListener?.onLayersChanged(layers, selectedLayerIndex)
        onLayersChanged?.invoke(layers, selectedLayerIndex)
    }

    private class State(
        val ref: PaintLayer,
        val clonedLayer: PaintLayer,
        val layers: MutableList<PaintLayer>,
        val isSpecial: Boolean = false,
        val isLayerChangeState: Boolean
    ) {
        fun restoreState(paintView: MananPaintView) {
            if (!isLayerChangeState) {
                ref.set(clonedLayer.clone(true))
            }

            paintView.run {

                var i = layerHolder.indexOf(selectedLayer)
                if (i > layers.lastIndex) i = layers.lastIndex
                selectedLayer = layers[i]

                layerHolder = MutableList(layers.size) {
                    layers[it]
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

    interface OnDoubleTapUp {
        fun onDoubleTapUp()
    }

    interface OnLayersChanged {
        fun onLayersChanged(layers: List<PaintLayer>, selectedLayerIndex: Int)
    }

}
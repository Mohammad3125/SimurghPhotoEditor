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
import ir.manan.mananpic.components.selection.selectors.Selector
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.MananMatrixAnimator
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.detectors.TwoFingerRotationDetector
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MananPaintView(context: Context, attrSet: AttributeSet?) :
    MananGestureImageView(context, attrSet), Selector.OnDispatchToInvalidate {

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

    private var dxSum = 0f
    private var dySum = 0f

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

    private var wasLastOperationFromOtherStack = false

    private var tot = 0f

    private var isFirstTimeToCallListener = false

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
            value?.setOnInvalidateListener(this)
            if (isViewInitialized) {
                initializedPainter(field)
            } else {
                requestLayout()
                initializedPainter(field)
            }
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
            maximumScale = max(widthPixels, heightPixels) / min(bitmapWidth, bitmapHeight) * 10f
        }

        rectAlloc.set(boundsRectangle)

        if (!isViewInitialized) {
            initializedPainter(painter)

            cachedLayer = Bitmap.createBitmap(
                bitmapWidth,
                bitmapHeight,
                Bitmap.Config.ARGB_8888
            )

            partiallyCachedLayer = Bitmap.createBitmap(
                bitmapWidth,
                bitmapHeight,
                Bitmap.Config.ARGB_8888
            )

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
                    }
                }
                MotionEvent.ACTION_MOVE -> {


                    // Determine total amount that user moved his/her finger on screen.
                    val dx: Float
                    val dy: Float

                    // If there are currently 2 pointers on screen and user is not scaling then
                    // translate the canvas matrix.
                    if (totalPoints == 2 && isNewGesture) {
                        val secondPointerX = getX(1)
                        val secondPointerY = getY(1)

                        val secondPointerDx = secondPointerX - secondPointerInitialX
                        val secondPointerDy = secondPointerY - secondPointerInitialY

                        val finalSlope = abs(scaledTouchSlope * canvasMatrix.getOppositeScale())

                        if (!isMoved) {
                            dxSum += abs(secondPointerDx)
                            dySum += abs(secondPointerDy)
                            isMoved = isMoved.or(dxSum >= finalSlope || dySum >= finalSlope)
                        }


                        // Calculate total difference by taking difference of first and second pointer
                        // and their initial point then append them and finally divide by two because
                        // we have two pointers that add up and it makes the gesture double as fast.
                        dx = ((secondPointerDx) + (x - initialX)) / 2
                        dy = ((secondPointerDy) + (y - initialY)) / 2

                        // Reset initial positions.
                        initialX = x
                        initialY = y

                        secondPointerInitialX = secondPointerX
                        secondPointerInitialY = secondPointerY

                        isMatrixGesture = true

                        canvasMatrix.postTranslate(dx, dy)
                        invalidate()

                        return true
                    }
                    // Else if selector is not null and there is currently 1 pointer on
                    // screen and user is not performing any other gesture like moving or
                    // scaling, then call 'onMove' method of selector.
                    if (painter != null && selectedLayer != null && totalPoints == 1 && !isMatrixGesture && !selectedLayer!!.isLocked) {

                        if (isFirstMove) {

                            if ((undoStack.isNotEmpty()) && selectedLayer !== undoStack.peek().ref) {
                                saveState(true)
                            } else if (redoStack.isNotEmpty()) {
                                saveState(false)
                            }

                            val mappedPoints = mapTouchPoints(initialX, initialY)
                            painter!!.onMoveBegin(mappedPoints[0], mappedPoints[1])
                            isFirstMove = false
                            isAllLayersCached = false
                        }

                        repeat(historySize) {

                            val historicX = getHistoricalX(0, it)
                            val historicY = getHistoricalY(0, it)

                            callSelectorOnMove(
                                historicX,
                                historicY,
                                historicX - initialX,
                                historicY - initialY
                            )

                            initialX = x
                            initialY = y


                        }

                        callSelectorOnMove(
                            x,
                            y,
                            x - initialX,
                            y - initialY
                        )

                        initialX = x
                        initialY = y



                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {

                    if (shouldCallDoubleTapListener()) {
                        callOnDoubleTapListeners()
                    } else if (shouldEndMoveOnPainter()) {
                        callSelectorOnMoveEnded(x, y)
                    }

                    cacheLayers()

                    isMatrixGesture = false
                    isNewGesture = true

                    dxSum = 0f
                    dySum = 0f
                    isMoved = false

                    return false
                }
                else -> {
                    return false
                }
            }
        }
        return false
    }

    private fun shouldCallDoubleTapListener(): Boolean = isMatrixGesture && !isMoved

    private fun shouldEndMoveOnPainter(): Boolean =
        painter != null && selectedLayer != null && (!isMatrixGesture || !isFirstMove) && !selectedLayer!!.isLocked

    private fun callSelectorOnMoveEnded(ex: Float, ey: Float) {
        mapTouchPoints(ex, ey).let { points ->
            painter!!.onMoveEnded(points[0], points[1])
            saveState()
        }
    }

    private fun callSelectorOnMove(ex: Float, ey: Float, dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return

        // Calculate how much the canvas is scaled then use
        // that to slow down the translation by that factor.
        val s = canvasMatrix.getOppositeScale()
        val exactMapPoints = mapTouchPoints(ex, ey)

        painter!!.onMove(
            dx * s, dy * s, exactMapPoints[0], exactMapPoints[1]
        )

        // Reset initial positions.
        initialX = ex
        initialY = ey
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
        mappingMatrix.postTranslate(leftEdge, topEdge)
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

                drawBitmap(cachedLayer, imageviewMatrix, layersPaint)

            } else {
                super.onDraw(this)

                if (this@MananPaintView::partiallyCachedLayer.isInitialized) {
                    layersPaint.xfermode = null
                    layersPaint.alpha = 255
                    drawBitmap(partiallyCachedLayer, imageviewMatrix, layersPaint)
                }

                selectedLayer?.let { layer ->

                    layersPaint.alpha = (255 * layer.opacity).toInt()
                    layersPaint.xfermode = layer.blendMode

                    drawBitmap(layer.bitmap, imageviewMatrix, layersPaint)

                    painter?.draw(this)
                }

                for (i in layerHolder.indexOf(selectedLayer) + 1..layerHolder.lastIndex) {

                    val layer = layerHolder[i]

                    layersPaint.alpha = (255 * layer.opacity).toInt()

                    layersPaint.xfermode = layer.blendMode

                    drawBitmap(layer.bitmap, imageviewMatrix, layersPaint)
                }

            }

        }
    }

    override fun invalidateDrawings() {
        invalidate()
    }

    private fun animateCanvasBack() {
        matrixAnimator.run {
            startAnimation(maximumScale, animatorExtraSpaceAroundAxes)
            setOnMatrixUpdateListener {
                invalidate()
            }
        }
    }

    private fun saveState(isSpecial: Boolean = false) {

        if (redoStack.isNotEmpty()) {
            redoStack.clear()
        }

        selectedLayer?.let { sl ->
            val copiedList = MutableList(layerHolder.size) {
                layerHolder[it]
            }
            undoStack.add(
                State(
                    sl,
                    sl.clone(),
                    copiedList,
                    isSpecial
                )
            )
        }

    }

    fun undo() {
        swapStacks(undoStack, redoStack, true)
    }

    fun redo() {
        swapStacks(redoStack, undoStack, false)
    }

    private fun swapStacks(popStack: Stack<State>, pushStack: Stack<State>, indicator: Boolean) {
        if (popStack.isNotEmpty()) {
            val poppedState = popStack.pop()

            if (popStack.isNotEmpty() && (pushStack.isEmpty() || indicator != wasLastOperationFromOtherStack || poppedState.isSpecial)) {
                poppedState.restoreState(this)
                val newPopped = popStack.pop()
                newPopped.restoreState(this)
                pushStack.push(poppedState)
                pushStack.push(newPopped)
                wasLastOperationFromOtherStack = indicator
            } else {
                pushStack.push(poppedState)
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

        if ((undoStack.isNotEmpty()) && selectedLayer !== undoStack.peek().ref) {
            saveState(true)
        } else if (redoStack.isNotEmpty()) {
            saveState(false)
        }

        selectedLayer = PaintLayer(
            Bitmap.createBitmap(
                bitmapWidth,
                bitmapHeight,
                Bitmap.Config.ARGB_8888
            ), Matrix(), false, 1f
        )

        layerHolder.add(selectedLayer!!)

        cacheLayers()

        saveState()

        callOnLayerChangedListeners(layerHolder.toList(), layerHolder.indexOf(selectedLayer))

        painter?.onLayerChanged(selectedLayer)

        invalidate()

    }

    private fun cacheLayers() {

        bitmap?.let { mainBitmap ->
            selectedLayer?.let { sv ->
                mergeCanvas.setBitmap(partiallyCachedLayer)

                val s = cachedLayer.width / bitmapWidth.toFloat()

                mergeCanvas.save()

                mergeCanvas.scale(s, s)

                mergeCanvas.drawBitmap(mainBitmap, 0f, 0f, layersPaint)

                mergeCanvas.restore()

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

        saveState()

        invalidate()
    }

    fun changedLayerBlendingModeAt(index: Int, blendingMode: PorterDuff.Mode) {
        checkIndex(index)

        layerHolder[index].blendMode = PorterDuffXfermode(blendingMode)

        cacheLayers()

        saveState()
        invalidate()
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

                saveState()

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

                saveState()

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

        callOnLayerChangedListeners(layerHolder.toList(), layerHolder.indexOf(selectedLayer))

        invalidate()
    }

    fun getLayerSize(): Int {
        return layerHolder.size
    }

    fun selectLayer(index: Int) {
        checkIndex(index)
        selectedLayer = layerHolder[index]
        painter?.onLayerChanged(selectedLayer)
        callOnLayerChangedListeners(layerHolder.toList(), layerHolder.indexOf(selectedLayer))
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

    private fun callOnLayerChangedListeners(layers: List<PaintLayer>, selectedLayerIndex: Int) {
        onLayersChangedListener?.onLayersChanged(layers, selectedLayerIndex)
        onLayersChanged?.invoke(layers, selectedLayerIndex)
    }

    private class State(
        val ref: PaintLayer,
        val clonedLayer: PaintLayer,
        val layers: MutableList<PaintLayer>,
        val isSpecial: Boolean = false
    ) {
        fun restoreState(paintView: MananPaintView) {
            ref.bitmap = clonedLayer.bitmap.copy(clonedLayer.bitmap.config, true)
            ref.blendMode = clonedLayer.blendMode

            paintView.run {

                var i = layerHolder.indexOf(selectedLayer)
                if (i > layers.lastIndex) i = layers.lastIndex
                selectedLayer = layers[i]

                layerHolder = layers
            }

        }
    }

    interface OnDoubleTapUp {
        fun onDoubleTapUp()
    }

    interface OnLayersChanged {
        fun onLayersChanged(layers: List<PaintLayer>, selectedLayerIndex: Int)
    }

}
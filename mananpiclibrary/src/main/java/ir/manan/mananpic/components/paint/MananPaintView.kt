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
import kotlin.math.roundToInt

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

    private var isNewGesture = false

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

    override fun onRotateBegin(initialDegree: Float, px: Float, py: Float): Boolean {
        return true
    }

    override fun onRotate(degree: Float, px: Float, py: Float): Boolean {
        canvasMatrix.postRotate(degree - rotHolder, px, py)
        rotHolder = degree
        invalidate()
        return true
    }

    override fun onImageLaidOut() {
        context.resources.displayMetrics.run {
            maximumScale = max(widthPixels, heightPixels) / min(bitmapWidth, bitmapHeight) * 10f
        }

        rectAlloc.set(boundsRectangle)

        if (!isViewInitialized) {
            initializedPainter(painter)
            isViewInitialized = true
        }

        if (isFirstLayerCreation) {
            addNewLayer()
            isFirstLayerCreation = false
        }
    }

    private fun initializedPainter(pp: Painter?) {
        pp?.let { p ->
            p.initialize(context, canvasMatrix, rectAlloc)
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
                        }

                        isMoved = dxSum >= finalSlope || dySum >= finalSlope


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

                        if (isMoved) {
                            canvasMatrix.postTranslate(dx, dy)
                            invalidate()
                        }

                        return true
                    }
                    // Else if selector is not null and there is currently 1 pointer on
                    // screen and user is not performing any other gesture like moving or
                    // scaling, then call 'onMove' method of selector.
                    if (painter != null && selectedLayer != null && totalPoints == 1 && !isMatrixGesture && !selectedLayer!!.isLocked) {

                        if (isFirstMove) {

                            if (redoStack.isNotEmpty() || selectedLayer !== undoStack.peek().ref) {
                                saveState()
                            }

                            val mappedPoints = mapTouchPoints(initialX, initialY)
                            painter!!.onMoveBegin(mappedPoints[0], mappedPoints[1])
                            isFirstMove = false
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
                MotionEvent.ACTION_POINTER_UP -> {
                    isNewGesture = false
                    return false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isMatrixGesture && !isMoved) {
                        callOnDoubleTapListeners()
                    } else if (painter != null && selectedLayer != null && !isMatrixGesture && !isFirstMove && !selectedLayer!!.isLocked) {


                        val mappedPoints = mapTouchPoints(x, y)

                        painter!!.onMoveEnded(mappedPoints[0], mappedPoints[1])

                        saveState()

                    }

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

    override fun onScaleBegin(p0: ScaleGestureDetector): Boolean {
        isMatrixGesture = true
        return !matrixAnimator.isAnimationRunning()
    }

    override fun onScale(p0: ScaleGestureDetector): Boolean {
        p0.run {
            val sf = scaleFactor
            canvasMatrix.postScale(sf, sf, focusX, focusY)
            invalidate()
            return true
        }
    }

    override fun onScaleEnd(p0: ScaleGestureDetector) {
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

        return touchPointMappedArray
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        canvasMatrix.reset()
    }

    fun setImageBitmapWithoutMatrixReset(bm: Bitmap?) {
        super.setImageBitmap(bm)
    }

    fun resetPaint() {
        painter?.resetPaint()
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.run {

            // Concat the canvas to 'canvasMatrix'.
            concat(canvasMatrix)

            super.onDraw(this)

            layerHolder.forEach { layer ->

                layersPaint.alpha = (255 * layer.opacity).toInt()

                layersPaint.xfermode = layer.blendMode

                drawBitmap(layer.bitmap, leftEdge, topEdge, layersPaint)
            }

            painter?.draw(this)

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

    private fun saveState() {

        if (redoStack.isNotEmpty()) {
            redoStack.clear()
        }

        selectedLayer?.run {
            addToHistory()
        }
    }

    private fun addToHistory() {
        val copiedList = MutableList(layerHolder.size) {
            layerHolder[it]
        }
        undoStack.add(
            State(
                selectedLayer!!,
                selectedLayer!!.clone(),
                copiedList,
            )
        )
    }

    fun undo() {
        swapStacks(undoStack, redoStack)
    }

    fun redo() {
        swapStacks(redoStack, undoStack)
    }

    private fun swapStacks(popStack: Stack<State>, pushStack: Stack<State>) {
        if (popStack.isNotEmpty()) {
            val poppedState = popStack.pop()

            if (pushStack.isEmpty() && popStack.isNotEmpty()) {
                val newPopped = popStack.pop()
                newPopped.restoreState(this)
                pushStack.push(poppedState)
                pushStack.push(newPopped)
            } else {
                pushStack.push(poppedState)
                poppedState.restoreState(this)
            }

            callOnLayerChangedListeners(
                layerHolder.toList(),
                layerHolder.indexOf(selectedLayer)
            )

            painter?.onLayerChanged(selectedLayer)

            invalidate()
        }
    }

    fun addNewLayer() {

        if (redoStack.isNotEmpty() || (undoStack.isNotEmpty() && selectedLayer !== undoStack.peek().ref)) {
            saveState()
        }

        selectedLayer = PaintLayer(
            Bitmap.createBitmap(
                bitmapWidth.roundToInt(),
                bitmapHeight.roundToInt(),
                Bitmap.Config.ARGB_8888
            ), Matrix(), false, 1f
        )

        layerHolder.add(selectedLayer!!)

        saveState()

        callOnLayerChangedListeners(layerHolder.toList(), layerHolder.indexOf(selectedLayer))

        painter?.onLayerChanged(selectedLayer!!)

        invalidate()
    }

    fun changeSelectedLayerOpacity(opacity: Float) {
        selectedLayer?.opacity = opacity
        invalidate()
    }

    fun changeLayerOpacityAt(index: Int, opacity: Float) {
        checkIndex(index)
        layerHolder[index].opacity = opacity
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

        saveState()
        invalidate()
    }

    fun moveSelectedLayerDown() {

        if (selectedLayer == null) return

        val index = layerHolder.indexOf(selectedLayer)

        if (index > 0) {
            val pervIndex = index - 1
            val previousLayer = layerHolder[pervIndex]
            layerHolder[pervIndex] = selectedLayer!!
            layerHolder[index] = previousLayer

            saveState()

            callOnLayerChangedListeners(layerHolder.toList(), layerHolder.indexOf(selectedLayer))

            invalidate()
        }
    }

    fun moveSelectedLayerUp() {
        if (selectedLayer == null) return

        val index = layerHolder.indexOf(selectedLayer)

        if (index < layerHolder.lastIndex) {
            val nextIndex = index + 1
            val previousLayer = layerHolder[nextIndex]
            layerHolder[nextIndex] = selectedLayer!!
            layerHolder[index] = previousLayer

            saveState()

            callOnLayerChangedListeners(layerHolder.toList(), layerHolder.indexOf(selectedLayer))

            invalidate()
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
    }

    fun getIndexOfSelectedLayer(): Int {

        if (selectedLayer == null) {
            return -1
        }

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
    }

    fun setOnLayersChangedListener(callback: ((layers: List<PaintLayer>, selectedLayerIndex: Int) -> Unit)) {
        onLayersChanged = callback
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
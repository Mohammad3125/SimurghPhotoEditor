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

    private var historyCounter = 0

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

    private val stateHistory = mutableListOf<State>()

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

    private val layerHolder = mutableListOf<PaintLayer>()

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

    private var isPainterChanged = false
    var painter: Painter? = null
        set(value) {
            field = value
            value?.setOnInvalidateListener(this)
            isPainterChanged = true
            requestLayout()

        }

    private val touchPointMappedArray = FloatArray(2)

    private var isFirstLayerCreation = true


    private var onTapUp: ((Unit) -> Unit)? = null

    private var onDoubleTapUpInterface: OnDoubleTapUp? = null


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

        if (isFirstLayerCreation) {

            selectedLayer = PaintLayer(
                Bitmap.createBitmap(
                    bitmapWidth.roundToInt(),
                    bitmapHeight.roundToInt(),
                    Bitmap.Config.ARGB_8888
                ), Matrix(), false, 1f, PorterDuff.Mode.SRC
            )

            selectedLayer!!.let { layer ->
                addState(
                    BitmapState(
                        layer,
                        layer.bitmap.copy(layer.bitmap.config, true)
                    )
                )
            }

            layerHolder.add(selectedLayer!!)

            isFirstLayerCreation = false
        }

        if (isPainterChanged) {
            rectAlloc.set(boundsRectangle)
            painter?.initialize(context, canvasMatrix, rectAlloc, width, height)
            painter?.onLayerChanged(selectedLayer!!)
            isPainterChanged = false
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

                            if (historyCounter < 0) {

                                val times = (stateHistory.lastIndex + historyCounter + 1)

                                selectedLayer!!.let { layer ->
                                    stateHistory.add(
                                        times, BitmapState(
                                            layer,
                                            layer.bitmap.copy(layer.bitmap.config, true)
                                        )
                                    )
                                }

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
                        callListeners()
                    }

                    if (painter != null && selectedLayer != null && !isMatrixGesture && !isFirstMove && !selectedLayer!!.isLocked) {

                        selectedLayer!!.let { layer ->
                            addState(
                                BitmapState(
                                    layer,
                                    layer.bitmap.copy(layer.bitmap.config, true)
                                )
                            )
                        }

                        val mappedPoints = mapTouchPoints(x, y)

                        painter!!.onMoveEnded(mappedPoints[0], mappedPoints[1])
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

    private fun addState(state: State) {

        if (historyCounter < 0) {

            val t = (stateHistory.lastIndex + historyCounter + 1)
            val times = stateHistory.lastIndex - t
            historyCounter += times

            repeat(times) {
                stateHistory.removeAt(stateHistory.lastIndex)
            }

            println()
        } else {
            stateHistory.add(state)
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

            layerHolder.forEach {
                layersPaint.alpha = (255 * it.opacity).toInt()
                drawBitmap(it.bitmap, leftEdge, topEdge, layersPaint)
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

    /**
     * Undoes the state of selector (if selector is not null.)
     */
    fun undo() {

        var index = stateHistory.lastIndex + historyCounter

        if (index == stateHistory.lastIndex) {
            index -= 1
        }

        if (index > -1) {

            stateHistory[index].let { state ->
                state.restoreState()

                if (state is LayersState && !state.layers.contains(selectedLayer)) {
                    selectedLayer = state.stateRef
                }
            }
            painter?.onLayerChanged(selectedLayer)

            historyCounter--

            invalidate()
        }

    }

    fun redo() {
        val index = (stateHistory.lastIndex + historyCounter) + 1

        if (index <= stateHistory.lastIndex) {

            if (index != stateHistory.lastIndex) {
                historyCounter++

                stateHistory[index + 1].restoreState()
            } else {
                stateHistory[index].restoreState()
            }

            painter?.onLayerChanged(selectedLayer)

            invalidate()
        }
    }

    fun addNewLayer() {

        selectedLayer = PaintLayer(
            Bitmap.createBitmap(
                bitmapWidth.roundToInt(),
                bitmapHeight.roundToInt(),
                Bitmap.Config.ARGB_8888
            ), Matrix(), false, 1f, PorterDuff.Mode.SRC
        )
        addState(LayersState(selectedLayer!!, layerHolder, layerHolder.toMutableList()))

        layerHolder.add(selectedLayer!!)

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

    fun lockLayer(index: Int) {
        changeLayerLockState(index, true)
    }


    fun unlockLayer(index: Int) {
        changeLayerLockState(index, false)
    }

    private fun changeLayerLockState(index: Int, shouldLock: Boolean) {
        checkIndex(index)
        layerHolder[index].let { layer ->
            addState(LockState(layer, layer.isLocked))
            layer.isLocked = shouldLock
        }
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

        addState(LayersState(selectedLayer!!, layerHolder, layerHolder.toMutableList()))

        layerHolder.removeAt(index)
        invalidate()
    }

    fun getLayerSize(): Int {
        return layerHolder.size
    }

    fun selectLayer(index: Int) {
        checkIndex(index)
        selectedLayer = layerHolder[index]
        painter?.onLayerChanged(selectedLayer)
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

    private fun callListeners() {
        onTapUp?.invoke(Unit)
        onDoubleTapUpInterface?.onDoubleTapUp()
    }

    private sealed class State(val stateRef: PaintLayer) {
        abstract fun restoreState()
    }

    private class LayersState(
        stateRef: PaintLayer,
        val lHolder: MutableList<PaintLayer>,
        val layers: MutableList<PaintLayer>,
    ) :
        State(stateRef) {

        override fun restoreState() {
            lHolder.clear()
            lHolder.addAll(layers)
        }
    }

    private class BitmapState(stateRef: PaintLayer, val bitmap: Bitmap) : State(stateRef) {
        override fun restoreState() {
            stateRef.bitmap = this.bitmap
        }
    }

    private class LockState(stateRef: PaintLayer, val isLocked: Boolean) : State(stateRef) {
        override fun restoreState() {
            stateRef.isLocked = isLocked
        }

        override fun toString(): String {
            return "LockState  $isLocked"
        }
    }

    private class MatrixState(stateRef: PaintLayer, val matrix: Matrix) : State(stateRef) {
        override fun restoreState() {
            stateRef.layerMatrix.set(matrix)
        }
    }


    interface OnDoubleTapUp {
        fun onDoubleTapUp()
    }

}
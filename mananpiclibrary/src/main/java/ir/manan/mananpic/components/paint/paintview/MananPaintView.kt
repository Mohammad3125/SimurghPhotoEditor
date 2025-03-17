package ir.manan.mananpic.components.paint.paintview

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import androidx.core.animation.doOnEnd
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import ir.manan.mananpic.R
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.MananMatrixAnimator
import ir.manan.mananpic.utils.evaluators.MatrixEvaluator
import ir.manan.mananpic.utils.gesture.detectors.TwoFingerRotationDetector
import ir.manan.mananpic.utils.gesture.gestures.OnRotateListener
import ir.manan.mananpic.utils.gesture.gestures.RotationDetectorGesture
import java.util.Stack
import kotlin.math.abs

class MananPaintView(context: Context, attrSet: AttributeSet?) :
    View(context, attrSet), Painter.MessageChannel,
    ScaleGestureDetector.OnScaleGestureListener,
    OnRotateListener, GestureDetector.OnDoubleTapListener,
    GestureDetector.OnGestureListener {

    constructor(context: Context) : this(context, null)

    private val layersPaint by lazy {
        Paint().apply {
            isFilterBitmap = true
        }
    }

    private val saveLayerPaint by lazy {
        Paint().apply {
            isFilterBitmap = true
        }
    }

    private var initialX = 0f
    private var initialY = 0f

    private var secondPointerInitialX = 0f
    private var secondPointerInitialY = 0f

    private var isMatrixGesture = false

    private var isNewGesture = true

    private var rotHolder = 0f

    private var isFirstMove = true

    private var secondDxSum = 0f
    private var secondDySum = 0f

    private var firstDxSum = 0f
    private var firstDySum = 0f

    var isCheckerBoardEnabled = true
        set(value) {
            field = value
            cacheLayers()
            invalidate()
        }

    private var isMoved = false

    private val touchData = TouchData(0f, 0f, 0f, 0f, 0, 0f)

    // Used to retrieve touch slopes.
    private var scaledTouchSlope = 0

    private val rectAlloc by lazy {
        RectF()
    }

    private val undoStack = Stack<State>()

    private val redoStack = Stack<State>()

    private var selectedLayer: PaintLayer? = null

    private val canvasMatrix by lazy {
        MananMatrix()
    }

    private val mappingMatrix by lazy {
        MananMatrix()
    }

    private val painterTransformationMatrix by lazy {
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

    private var onDelegateTransform: ((transformationMatrix: Matrix) -> Unit)? = null

    var shouldDelegateGesture: Boolean = false

    private val mergeCanvas = Canvas()

    private lateinit var bitmapReference: Bitmap

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

    private var isViewInitialized = false

    var painter: Painter? = null
        set(value) {
            cacheLayers()

            painter?.release()

            field = value
            value?.setOnMessageListener(this)

            if (isViewInitialized) {
                initializedPainter(field)
            }
            requestLayout()
        }

    /**
     * Matrix that we later modify and assign to image matrix.
     */
    private val imageviewMatrix = MananMatrix()

    /**
     * Scale detector that is used to detect if user scaled matrix.
     * It is nullable; meaning a derived class could use scale gesture or not.
     */
    private var scaleDetector: ScaleGestureDetector? = null

    /**
     * Rotation detector that is used to detect if user performed rotation gesture.
     * It is nullable; meaning a derived class could use rotating gesture or not.
     */
    private var rotationDetector: RotationDetectorGesture? = null


    @Transient
    private val boundsRectangle = RectF()


    private val layerBounds = RectF()

    /**
     * Real width of current image's bitmap.
     * This value is available after [onImageLaidOut] has ben called.
     */
    private var bitmapWidth = 0

    /**
     * Real height of current image's bitmap.
     * This value is available after [onImageLaidOut] has ben called.
     */
    protected var bitmapHeight = 0

    /**
     * Later will be used to notify if imageview's bitmap has been changed.
     */
    private var isNewBitmap = true

    var bitmap: Bitmap? = null
        private set

    var isRotatingEnabled = true
    var isScalingEnabled = true
    var isTranslationEnabled = true

    // Used to animate the matrix in MatrixEvaluator
    private val endMatrix = MananMatrix()
    private val startMatrix = MananMatrix()


    var matrixAnimationDuration: Long = 500
        set(value) {
            field = value
            resetMatrixAnimator.duration = field
        }

    var matrixAnimationInterpolator: TimeInterpolator = FastOutSlowInInterpolator()
        set(value) {
            field = value
            resetMatrixAnimator.interpolator = field
        }


    private val resetMatrixAnimator by lazy {
        ValueAnimator.ofObject(MatrixEvaluator(), startMatrix, endMatrix).apply {
            interpolator = matrixAnimationInterpolator
            duration = matrixAnimationDuration
            addUpdateListener {
                canvasMatrix.set(it.animatedValue as MananMatrix)
                invalidate()
            }
        }
    }

    private val checkerPatternPaint by lazy {
        Paint().apply {
            shader = BitmapShader(
                BitmapFactory.decodeResource(resources, R.drawable.checker),
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT
            )
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

    override fun onSingleTapUp(p0: MotionEvent): Boolean {
        return true
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return false
    }

    override fun onSingleTapConfirmed(p0: MotionEvent): Boolean {
        return false
    }

    override fun onDoubleTap(p0: MotionEvent): Boolean {
        return true
    }

    override fun onDoubleTapEvent(p0: MotionEvent): Boolean {
        return false
    }

    override fun onDown(p0: MotionEvent): Boolean {
        return false
    }

    override fun onShowPress(p0: MotionEvent) {
    }


    override fun onLongPress(p0: MotionEvent) {

    }

    override fun onFling(
        e1: MotionEvent?,
        p0: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return false
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (isInLayout) {
            return
        }

        rectAlloc.set(boundsRectangle)

        super.onSizeChanged(w, h, oldw, oldh)

        resizeDrawable(w.toFloat() - paddingRight, h.toFloat() - paddingBottom)

        calculateBounds()

        mappingMatrix.setRectToRect(
            rectAlloc,
            boundsRectangle, Matrix.ScaleToFit.CENTER
        )

        rectAlloc.set(boundsRectangle)

        painter?.onSizeChanged(rectAlloc, mappingMatrix)
    }

    /**
     * Called when drawable is about to be resized to fit the view's dimensions.
     * @return Modified matrix.
     */
    private fun resizeDrawable(targetWidth: Float, targetHeight: Float) {
        imageviewMatrix.setRectToRect(
            RectF(
                0f,
                0f,
                bitmapWidth.toFloat(),
                bitmapHeight.toFloat()
            ),
            RectF(
                paddingLeft.toFloat(),
                paddingTop.toFloat(),
                targetWidth,
                targetHeight
            ),
            Matrix.ScaleToFit.CENTER
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (isNewBitmap && bitmap != null) {

            resizeDrawable(width.toFloat() - paddingRight, height.toFloat() - paddingBottom)

            calculateBounds()

            onImageLaidOut()

            isNewBitmap = false
        }
    }

    fun setImageBitmap(bitmap: Bitmap?, saveHistory: Boolean = false) {
        if (bitmap != null) {

            if (!bitmap.isMutable) {
                throw IllegalStateException("Bitmap should be mutable")
            }

            this.bitmap = bitmap
            isNewBitmap = true
            bitmapWidth = bitmap.width
            bitmapHeight = bitmap.height
            isViewInitialized = false

            if (saveHistory) {
                addNewLayer(bitmap)
            } else {
                addNewLayerWithoutSavingHistory(bitmap)
            }


            requestLayout()
            invalidate()
        }
    }

    /**
     * Calculates bounds of image with matrix values.
     */
    private fun calculateBounds() {
        imageviewMatrix.run {

            val matrixScale = imageviewMatrix.getScaleX(true)

            val leftEdge = getTranslationX()
            val topEdge = getTranslationY()

            val finalWidth = (bitmapWidth * matrixScale)
            val finalHeight = (bitmapHeight * matrixScale)

            val rightEdge = finalWidth + leftEdge
            val bottomEdge = finalHeight + topEdge

            boundsRectangle.set(leftEdge, topEdge, rightEdge, bottomEdge)

            layerBounds.set(0f, 0f, bitmapWidth.toFloat(), bitmapHeight.toFloat())

        }
    }

    private fun onImageLaidOut() {
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

    private fun initializedPainter(pp: Painter?) {
        pp?.let { p ->
            rectAlloc.set(layerBounds)
            if (!pp.isInitialized) {
                p.initialize(context, canvasMatrix, imageviewMatrix, RectF(layerBounds))
            }
            p.onLayerChanged(selectedLayer)
            if (this::bitmapReference.isInitialized) {
                p.onReferenceLayerCreated(bitmapReference)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        painter?.let { painter ->
            if (event == null || resetMatrixAnimator.isRunning) {
                return false
            }

            val copyEvent = MotionEvent.obtain(event)

            if (isScalingEnabled) {
                scaleDetector?.onTouchEvent(event)
            }


            if (painter.doesTakeGestures()) {
                mappingMatrix.setConcat(canvasMatrix, imageviewMatrix)
                mappingMatrix.invert(mappingMatrix)
                event.transform(mappingMatrix)
            }

            if (isRotatingEnabled) {
                rotationDetector?.onTouchEvent(event)
            }

            copyEvent.run {

                val totalPoints = pointerCount
                when (actionMasked) {
                    MotionEvent.ACTION_DOWN -> {

                        // Save the initial points to later determine how much user has moved
                        // His/her finger across screen.
                        initialX = x
                        initialY = y

                        isFirstMove = true

                        recycle()
                        return true
                    }

                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (totalPoints == 2) {
                            secondPointerInitialX = getX(1)
                            secondPointerInitialY = getY(1)

                            isMatrixGesture = true

                            painter.onTransformBegin()
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {

                        val finalSlope = if (painter.doesNeedTouchSlope()) {
                            abs(scaledTouchSlope / canvasMatrix.getRealScaleX())
                        } else {
                            0f
                        }

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
                            if (isTranslationEnabled) {
                                if (painter.doesTakeGestures()) {
                                    mapTouchPoints(dx, dy, true).let {
                                        painterTransformationMatrix.setTranslate(
                                            it[0],
                                            it[1]
                                        )

                                        if (shouldDelegateGesture) {
                                            onDelegateTransform?.invoke(painterTransformationMatrix)
                                        } else {
                                            painter.onTransformed(painterTransformationMatrix)
                                        }
                                    }
                                } else {
                                    canvasMatrix.postTranslate(dx, dy)
                                    invalidate()
                                }
                            }
                        }

                        // Else if selector is not null and there is currently 1 pointer on
                        // screen and user is not performing any other gesture like moving or
                        // scaling, then call 'onMove' method of selector.
                        if (selectedLayer != null && totalPoints == 1 && !isMatrixGesture && !selectedLayer!!.isLocked && isFirstFingerMoved) {

                            if (isFirstMove) {

                                checkForStateSave()

                                setTouchData(initialX, initialY, 0f, 0f, eventTime, pressure)
                                callPainterOnMoveBegin()
                            }

                            if (isTouchEventHistoryEnabled) {
                                repeat(historySize) {

                                    val histX = getHistoricalX(0, it)
                                    val histY = getHistoricalY(0, it)

                                    setTouchData(
                                        histX,
                                        histY,
                                        histX - initialX,
                                        histY - initialY,
                                        getHistoricalEventTime(0),
                                        getHistoricalPressure(0)
                                    )

                                    callPainterOnMove()
                                }
                            }

                            setTouchData(x, y, x - initialX, y - initialY, eventTime, pressure)
                            callPainterOnMove()

                        }

                        initialX = x
                        initialY = y

                        recycle()
                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {

                        if (shouldCallDoubleTapListener()) {
                            callOnDoubleTapListeners()
                        } else if (shouldEndMoveOnPainter()) {
                            checkForStateSave()
                            setTouchData(x, y, x - initialX, y - initialY, eventTime, pressure)
                            callPainterOnMoveEnd()
                        }

                        if (isMoved) {
                            painter.onTransformEnded()
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

                        recycle()
                        return false
                    }

                    else -> {
                        recycle()
                        return false
                    }
                }
            }
            return false
        }
        return false
    }

    private fun setTouchData(
        ex: Float,
        ey: Float,
        dx: Float,
        dy: Float,
        time: Long,
        pressure: Float
    ) {
        touchData.let { data ->
            mapTouchPoints(ex, ey).let { points ->
                data.ex = points[0]
                data.ey = points[1]
            }
            data.dx = dx
            data.dy = dy
            data.time = time
            data.pressure = pressure
        }

    }

    private fun checkForStateSave() {
        if (undoStack.isNotEmpty() && (selectedLayer !== undoStack.peek().ref || undoStack.peek().isLayerChangeState)) {
            saveState(true, isMessage = true)
        } else if (redoStack.isNotEmpty() || undoStack.size == 0) {
            saveState(false, isMessage = true)
        }
    }

    private fun callPainterOnMoveBegin() {
        painter!!.onMoveBegin(touchData)
        isFirstMove = false
        isAllLayersCached = false

    }

    private fun shouldCallDoubleTapListener(): Boolean = isMatrixGesture && !isMoved

    private fun shouldEndMoveOnPainter(): Boolean =
        selectedLayer != null && (!isMatrixGesture || !isFirstMove) && !selectedLayer!!.isLocked

    private fun callPainterOnMoveEnd() {
        painter!!.onMoveEnded(touchData)
        saveState()
    }

    private fun callPainterOnMove() {
        if (touchData.dx == 0f && touchData.dy == 0f) {
            return
        }
        painter!!.onMove(touchData)
    }

    override fun onRotateBegin(initialDegree: Float, px: Float, py: Float): Boolean {
        isAllLayersCached = isFirstMove
        return true
    }

    override fun onRotate(degree: Float, px: Float, py: Float): Boolean {
        painter?.apply {
            if (doesTakeGestures()) {
                painterTransformationMatrix.setRotate(degree - rotHolder, px, py)
                if (shouldDelegateGesture) {
                    onDelegateTransform?.invoke(painterTransformationMatrix)
                } else {
                    onTransformed(painterTransformationMatrix)
                }
            } else {
                canvasMatrix.postRotate(degree - rotHolder, px, py)
                invalidate()
            }
        }
        tot += abs(degree - rotHolder)
        rotHolder = degree
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

            painter?.apply {
                if (doesTakeGestures()) {
                    painterTransformationMatrix.setScale(sf, sf, focusX, focusY)

                    if (shouldDelegateGesture) {
                        onDelegateTransform?.invoke(painterTransformationMatrix)
                    } else {
                        onTransformed(painterTransformationMatrix)
                    }
                } else {
                    canvasMatrix.postScale(sf, sf, focusX, focusY)
                    invalidate()
                }
            }
            return true
        }
    }

    override fun onScaleEnd(p0: ScaleGestureDetector) {
        isMoved = true
    }

    /**
     * This function maps the touch location provided with canvas matrix to provide
     * correct coordinates of touch if canvas is scaled and or translated.
     */
    private fun mapTouchPoints(
        touchX: Float,
        touchY: Float,
        shouldMapVector: Boolean = false
    ): FloatArray {
        touchPointMappedArray[0] = touchX
        touchPointMappedArray[1] = touchY

        canvasMatrix.invert(mappingMatrix)
        if (shouldMapVector) {
            mappingMatrix.mapVectors(touchPointMappedArray)
        } else {
            mappingMatrix.mapPoints(touchPointMappedArray)
        }

        mappingMatrix.reset()

        imageviewMatrix.invert(mappingMatrix)
        if (shouldMapVector) {
            mappingMatrix.mapVectors(touchPointMappedArray)
        } else {
            mappingMatrix.mapPoints(touchPointMappedArray)
        }

        return touchPointMappedArray
    }

    fun resetPaint() {
        painter?.resetPaint()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.run {

            // Concat the canvas to 'canvasMatrix'.
            concat(canvasMatrix)

            concat(imageviewMatrix)
            if (isAllLayersCached && !isAnyLayerBlending()) {
                layersPaint.xfermode = null
                layersPaint.alpha = 255

                drawBitmap(partiallyCachedLayer, 0f, 0f, layersPaint)

                drawLayer(canvas)

                layersPaint.xfermode = null
                layersPaint.alpha = 255

                drawBitmap(cachedLayer, 0f, 0f, layersPaint)

            } else {
                if (this@MananPaintView::partiallyCachedLayer.isInitialized) {
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

    private fun isAnyLayerBlending(): Boolean =
        layerHolder.any { it.blendingModeObject != null }

    private fun drawLayer(canvas: Canvas) {
        canvas.apply {
            selectedLayer?.let { layer ->

                save()
                clipRect(0, 0, layer.bitmap.width, layer.bitmap.height)

                if (layer === layerHolder.first() && isCheckerBoardEnabled) {
                    drawPaint(checkerPatternPaint)
                }

                layersPaint.alpha = (255 * layer.opacity).toInt()
                layersPaint.xfermode = layer.blendingModeObject

                saveLayerPaint.xfermode = layer.blendingModeObject
                saveLayer(layerBounds, saveLayerPaint)

                drawBitmap(layer.bitmap, 0f, 0f, layersPaint)

                painter?.draw(this)

                restore()

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
        isLayerChange: Boolean = false
    ) {

        if (painter?.doesHandleHistory() == true && !isMessage && undoStack.isNotEmpty()) {
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
                    isLayerChange,
                    bitmapWidth,
                    bitmapHeight,
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

            var needLayout = false

            if ((popStack.isNotEmpty() && !isFirstSnapshot)
                && (pushStack.isEmpty()
                        || (poppedState.clonedLayer == layerHolder.last()
                        && !poppedState.isLayerChangeState)
                        || poppedState.isSpecial)
            ) {

                needLayout =
                    (poppedState.bWidth != bitmapWidth || poppedState.bHeight != bitmapHeight)

                poppedState.restoreState(this)
                val newPopped = popStack.pop()

                needLayout =
                    needLayout.or((newPopped.bWidth != bitmapWidth || newPopped.bHeight != bitmapHeight))

                newPopped.restoreState(this)
                pushStack.push(poppedState)
                pushStack.push(newPopped)
            } else {
                if (!isFirstSnapshot) {
                    pushStack.push(poppedState)
                }
                needLayout =
                    needLayout.or((poppedState.bWidth != bitmapWidth || poppedState.bHeight != bitmapHeight))
                poppedState.restoreState(this)
            }

            if (needLayout) {
                isViewInitialized = false
                isNewBitmap = true
                requestLayout()
            } else {
                cacheLayers()
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

        callOnLayerChangedListeners(layerHolder.toList(), layerHolder.indexOf(selectedLayer))

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
        bitmap?.let { mainBitmap ->
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
        selectedLayer?.blendingMode = blendingMode

        saveState(shouldClone = false)

        invalidate()
    }

    fun changedLayerBlendingModeAt(index: Int, blendingMode: PorterDuff.Mode) {
        checkIndex(index)

        layerHolder[index].blendingMode = blendingMode

        cacheLayers()

        saveState(shouldClone = false)

        invalidate()
    }

    fun getSelectedLayerBlendingMode(): PorterDuff.Mode {
        return selectedLayer?.blendingMode ?: PorterDuff.Mode.SRC
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

    fun changeSelectedLayerBitmap(bitmap: Bitmap) {
        selectedLayer?.bitmap = bitmap
        saveState()
        invalidate()
    }

    fun setOnTransformDelegate(func: (transformationMatrix: Matrix) -> Unit) {
        onDelegateTransform = func
    }

    fun convertToBitmap(): Bitmap? {
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

    fun resetTransformationMatrix() {

        if (canvasMatrix.isIdentity || resetMatrixAnimator.isRunning) {
            return
        }

        startMatrix.set(canvasMatrix)
        endMatrix.reset()
        resetMatrixAnimator.start()
    }

    fun doAfterResetTransformation(func: () -> Unit) {
        resetTransformationMatrix()

        if (canvasMatrix.isIdentity) {
            func.invoke()
        } else {
            resetMatrixAnimator.doOnEnd {
                func.invoke()
                // prevents this function to be called again.
                resetMatrixAnimator.listeners.clear()
            }
        }
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

    fun applyMatrix(matrix: Matrix) {
        canvasMatrix.postConcat(matrix)
        invalidate()
    }

    fun setMatrix(matrix: Matrix) {
        canvasMatrix.set(matrix)
        invalidate()
    }

    private class State(
        val ref: PaintLayer,
        val clonedLayer: PaintLayer,
        val layers: MutableList<PaintLayer>,
        val isSpecial: Boolean = false,
        val isLayerChangeState: Boolean,
        val bWidth: Int,
        val bHeight: Int,
    ) {
        fun restoreState(paintView: MananPaintView) {
            if (!isLayerChangeState) {
                ref.set(clonedLayer.clone(true))
            }

            paintView.run {

                bitmapWidth = bWidth
                bitmapHeight = bHeight

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
            result = 31 * result + bWidth
            result = 31 * result + bHeight
            return result
        }

    }

    interface OnDoubleTapUp {
        fun onDoubleTapUp()
    }

    interface OnLayersChanged {
        fun onLayersChanged(layers: List<PaintLayer>, selectedLayerIndex: Int)
    }

    data class TouchData(
        var ex: Float,
        var ey: Float,
        var dx: Float,
        var dy: Float,
        var time: Long,
        var pressure: Float
    )

}
package ir.manan.mananpic.components.paint.paintview

import android.animation.RectEvaluator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
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
import androidx.core.graphics.toRectF
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import ir.manan.mananpic.R
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.MananMatrixAnimator
import ir.manan.mananpic.utils.evaluators.MatrixEvaluator
import ir.manan.mananpic.utils.gesture.TouchData
import ir.manan.mananpic.utils.gesture.detectors.rotation.OnRotateListener
import ir.manan.mananpic.utils.gesture.detectors.rotation.RotationDetectorGesture
import ir.manan.mananpic.utils.gesture.detectors.rotation.TwoFingerRotationDetector
import ir.manan.mananpic.utils.gesture.detectors.translation.OnTranslationDetector
import ir.manan.mananpic.utils.gesture.detectors.translation.TranslationDetector
import kotlin.math.abs

open class MananPaintView(context: Context, attrSet: AttributeSet?) :
    View(context, attrSet), Painter.MessageChannel,
    ScaleGestureDetector.OnScaleGestureListener,
    OnRotateListener, OnTranslationDetector {

    constructor(context: Context) : this(context, null)

    protected val layersPaint by lazy {
        Paint().apply {
            isFilterBitmap = true
        }
    }

    protected var isMatrixGesture = false

    protected var rotationHolder = 0f

    protected var isFirstMove = true

    open var isCheckerBoardEnabled = true
        set(value) {
            field = value
            invalidate()
        }

    protected var isMoved = false

    // Used to retrieve touch slopes.
    protected var scaledTouchSlope = ViewConfiguration.get(context).scaledTouchSlop

    protected val rectAlloc by lazy {
        RectF()
    }


    protected open var selectedLayer: PaintLayer? = null

    protected val canvasMatrix by lazy {
        MananMatrix()
    }

    protected val mappingMatrix by lazy {
        MananMatrix()
    }

    protected val painterTransformationMatrix by lazy {
        MananMatrix()
    }

    protected val matrixAnimator by lazy {
        MananMatrixAnimator(canvasMatrix, RectF(boundsRectangle), 300L, FastOutSlowInInterpolator())
    }

    protected val touchPointMappedArray = FloatArray(2)

    protected var onDoubleFingerTapUp: ((Unit) -> Unit)? = null

    protected var onDoubleFingerTapUpInterface: OnDoubleFingerTapUp? = null

    protected var onDoubleTap: ((event: MotionEvent) -> Boolean)? = null

    protected var onDoubleTappedListener: OnDoubleTap? = null

    protected var onDelegateTransform: ((transformationMatrix: Matrix) -> Unit)? = null

    open var isGestureDelegationEnabled: Boolean = false

    protected var totalRotated = 0f

    protected var rotationSlope = 0.5f

    protected var isFirstFingerMoved = false

    open var isTouchEventHistoryEnabled = false
        set(value) {
            field = value
            translationDetector.isTouchEventHistoryEnabled = value
        }

    protected var isViewInitialized = false

    open var painter: Painter? = null
        set(value) {
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
    protected val imageviewMatrix = MananMatrix()

    /**
     * Scale detector that is used to detect if user scaled matrix.
     * It is nullable; meaning a derived class could use scale gesture or not.
     */
    protected var scaleDetector: ScaleGestureDetector = ScaleGestureDetector(context, this).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            isQuickScaleEnabled = false
        }
    }

    /**
     * Rotation detector that is used to detect if user performed rotation gesture.
     * It is nullable; meaning a derived class could use rotating gesture or not.
     */
    protected val rotationDetector: RotationDetectorGesture by lazy {
        TwoFingerRotationDetector(this)
    }

    protected val translationDetector by lazy {
        TranslationDetector(this)
    }

    private val gestureDetector: GestureDetector by lazy {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                callDoubleTapListeners(e)
                return true
            }
        })
    }

    protected val onMoveBeginTouchData by lazy {
        TouchData()
    }

    protected val boundsRectangle = RectF()

    protected val layerBounds = RectF()

    /**
     * Real width of current image's bitmap.
     * This value is available after [onImageLaidOut] has ben called.
     */
    protected var bitmapWidth = 0

    /**
     * Real height of current image's bitmap.
     * This value is available after [onImageLaidOut] has ben called.
     */
    protected var bitmapHeight = 0

    /**
     * Later will be used to notify if imageview's bitmap has been changed.
     */
    protected var isNewBitmap = true

    open var bitmap: Bitmap? = null
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

                selectedLayer = PaintLayer(value, Matrix(), false, 1f)

                requestLayout()
                invalidate()
            }
        }

    open var isRotatingEnabled = true
    open var isScalingEnabled = true
    open var isTranslationEnabled = true

    // Used to animate the matrix in MatrixEvaluator
    protected val endMatrix = MananMatrix()
    protected val startMatrix = MananMatrix()

    protected val endRect = Rect()
    protected val startRect = Rect()


    open var matrixAnimationDuration: Long = 500
        set(value) {
            field = value
            resetMatrixAnimator.duration = field
        }

    open var matrixAnimationInterpolator: TimeInterpolator = FastOutSlowInInterpolator()
        set(value) {
            field = value
            resetMatrixAnimator.interpolator = field
        }


    protected open val resetMatrixAnimator by lazy {
        ValueAnimator.ofObject(MatrixEvaluator(), startMatrix, endMatrix).apply {
            interpolator = matrixAnimationInterpolator
            duration = matrixAnimationDuration
            addUpdateListener {
                canvasMatrix.set(it.animatedValue as MananMatrix)
                invalidate()
            }
        }
    }

    protected open val clipAnimator by lazy {
        ValueAnimator.ofObject(RectEvaluator(), startRect, endRect).apply {
            interpolator = matrixAnimationInterpolator
            duration = matrixAnimationDuration
            addUpdateListener {
                changeClipSize(it.animatedValue as Rect)
                invalidate()
            }
        }
    }

    protected open val checkerPatternPaint by lazy {
        Paint().apply {
            shader = BitmapShader(
                BitmapFactory.decodeResource(resources, R.drawable.checker),
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT
            )
        }
    }

    val layerClipBounds by lazy {
        Rect()
    }

    val identityClip by lazy {
        Rect()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (isInLayout) {
            return
        }

        rectAlloc.set(boundsRectangle)

        super.onSizeChanged(w, h, oldw, oldh)

        resizeCanvas(w.toFloat(), h.toFloat())

        mappingMatrix.setRectToRect(
            rectAlloc,
            boundsRectangle, Matrix.ScaleToFit.CENTER
        )

        rectAlloc.set(boundsRectangle)

        painter?.onSizeChanged(rectAlloc, layerClipBounds, mappingMatrix)
    }

    /**
     * Called when drawable is about to be resized to fit the view's dimensions.
     * @return Modified matrix.
     */
    protected open fun resizeDrawable(targetWidth: Float, targetHeight: Float) {
        imageviewMatrix.setRectToRect(
            layerClipBounds.toRectF(),
            RectF(
                paddingLeft.toFloat(),
                paddingTop.toFloat(),
                targetWidth - paddingRight,
                targetHeight - paddingBottom
            ),
            Matrix.ScaleToFit.CENTER
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (isNewBitmap && bitmap != null) {

            resizeCanvas(width.toFloat(), height.toFloat())

            onImageLaidOut()

            isNewBitmap = false
        }
    }

    /**
     * Calculates bounds of image with matrix values.
     */
    protected open fun calculateBounds() {
        imageviewMatrix.run {

            val matrixScale = imageviewMatrix.getScaleX(true)

            val leftEdge = getTranslationX()
            val topEdge = getTranslationY()

            val finalWidth = (bitmapWidth * matrixScale)
            val finalHeight = (bitmapHeight * matrixScale)

            val rightEdge = finalWidth + leftEdge
            val bottomEdge = finalHeight + topEdge

            boundsRectangle.set(leftEdge, topEdge, rightEdge, bottomEdge)

            layerBounds.set(layerClipBounds)
        }
    }

    protected open fun onImageLaidOut() {
        rectAlloc.set(boundsRectangle)

        if (!isViewInitialized) {
            initializedPainter(painter)
            isViewInitialized = true
        }
    }

    protected open fun initializedPainter(pp: Painter?) {
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

            selectedLayer?.let { layer ->
                p.onReferenceLayerCreated(layer.bitmap)
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

        gestureDetector.onTouchEvent(event)

        if (isRotatingEnabled) {
            rotationDetector.onTouchEvent(event)
        }
        return true
    }

    override fun onMoveBegin(detector: TranslationDetector): Boolean {
        isFirstMove = true
        isMoved = false

        onMoveBeginTouchData.set(detector.getTouchData(0))

        if (detector.pointerCount == 2) {
            isMatrixGesture = true

            painter?.onTransformBegin()
        }

        return painter != null || detector.pointerCount > 1
    }

    override fun onMove(detector: TranslationDetector): Boolean {
        val firstPointerTouchData = detector.getTouchData(0)

        val finalSlope = if (painter?.doesNeedTouchSlope() == true) {
            abs(scaledTouchSlope / canvasMatrix.getRealScaleX())
        } else {
            0f
        }

        isFirstFingerMoved =
            isFirstFingerMoved.or(firstPointerTouchData.dxSum >= finalSlope || firstPointerTouchData.dySum >= finalSlope)


        if (detector.pointerCount == 2) {

            val secondPointerTouchData = detector.getTouchData(1)

            isMoved =
                isMoved.or(secondPointerTouchData.dxSum >= finalSlope || secondPointerTouchData.dySum >= finalSlope)

            val dx = (secondPointerTouchData.dx + firstPointerTouchData.dx) / 2
            val dy = (secondPointerTouchData.dy + firstPointerTouchData.dy) / 2

            isMatrixGesture = true

            if (isTranslationEnabled) {
                if (painter?.doesTakeGestures() == true) {
                    painterTransformationMatrix.setTranslate(dx, dy)
                    if (isGestureDelegationEnabled) {
                        onDelegateTransform?.invoke(painterTransformationMatrix)
                    } else {
                        painter?.onTransformed(painterTransformationMatrix)
                    }
                } else {
                    canvasMatrix.postTranslate(dx, dy)
                    invalidate()
                }
            }
        } else if (!isMatrixGesture && isFirstFingerMoved && selectedLayer?.isLocked == false) {

            if (isFirstMove) {
                mapTouchData(onMoveBeginTouchData)
                callPainterOnMoveBegin(onMoveBeginTouchData)
            }

            mapTouchData(firstPointerTouchData)
            callPainterOnMove(firstPointerTouchData)
        }
        return true
    }


    override fun onMoveEnded(detector: TranslationDetector) {
        val firstPointerTouchData = detector.getTouchData(0)

        if (canCallDoubleTapListeners()) {
            callOnDoubleTapListeners()
        } else if (canCallPainterOnMoveBegin()) {
            mapTouchData(firstPointerTouchData)
            canCallPainterMoveEnd(firstPointerTouchData)
        }

        if (isMoved) {
            painter?.onTransformEnded()
        }

        isMatrixGesture = false
        isMoved = false
        isFirstFingerMoved = false
    }


    protected open fun mapTouchData(touchData: TouchData) {
        mapTouchPoints(touchData.ex, touchData.ey).let { points ->
            touchData.ex = points[0]
            touchData.ey = points[1]
        }
    }

    protected open fun callPainterOnMoveBegin(touchData: TouchData) {
        painter!!.onMoveBegin(touchData)
        isFirstMove = false
    }

    protected open fun canCallDoubleTapListeners(): Boolean =
        isMatrixGesture && !isMoved && painter?.doesHandleHistory() == false

    protected open fun canCallPainterOnMoveBegin(): Boolean =
        selectedLayer != null && (!isMatrixGesture || !isFirstMove) && !selectedLayer!!.isLocked

    protected open fun canCallPainterMoveEnd(touchData: TouchData) {
        painter!!.onMoveEnded(touchData)
    }

    protected open fun callPainterOnMove(touchData: TouchData) {
        if (touchData.dx == 0f && touchData.dy == 0f) {
            return
        }
        painter!!.onMove(touchData)
    }

    override fun onRotateBegin(initialDegree: Float, px: Float, py: Float): Boolean {
        isMoved = false
        return true
    }

    override fun onRotate(degree: Float, px: Float, py: Float): Boolean {
        val rotationDelta = degree - rotationHolder

        if (painter?.doesTakeGestures() == true) {
            painterTransformationMatrix.setRotate(rotationDelta, px, py)
            if (isGestureDelegationEnabled) {
                onDelegateTransform?.invoke(painterTransformationMatrix)
            } else {
                painter?.onTransformed(painterTransformationMatrix)
            }
        } else {
            canvasMatrix.postRotate(rotationDelta, px, py)
            invalidate()
        }

        totalRotated += abs(rotationDelta)
        rotationHolder = degree
        return true
    }

    override fun onRotateEnded() {
        if (totalRotated > rotationSlope) {
            isMoved = true
        }

        totalRotated = 0f
    }

    override fun onScaleBegin(p0: ScaleGestureDetector): Boolean {
        isMoved = false
        return !matrixAnimator.isAnimationRunning()
    }

    override fun onScale(p0: ScaleGestureDetector): Boolean {
        p0.run {
            val sf = scaleFactor
            isMoved = true

            if (painter?.doesTakeGestures() == true) {
                mapTouchPoints(focusX, focusY, false).let {
                    painterTransformationMatrix.setScale(sf, sf, it[0], it[1])
                }

                if (isGestureDelegationEnabled) {
                    onDelegateTransform?.invoke(painterTransformationMatrix)
                } else {
                    painter?.onTransformed(painterTransformationMatrix)
                }
            } else {
                canvasMatrix.postScale(sf, sf, focusX, focusY)
                invalidate()
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
    protected open fun mapTouchPoints(
        touchX: Float,
        touchY: Float,
        vectorMapping: Boolean = false
    ): FloatArray {
        touchPointMappedArray[0] = touchX
        touchPointMappedArray[1] = touchY

        canvasMatrix.invert(mappingMatrix)
        if (vectorMapping) {
            mappingMatrix.mapVectors(touchPointMappedArray)
        } else {
            mappingMatrix.mapPoints(touchPointMappedArray)
        }

        mappingMatrix.reset()

        imageviewMatrix.invert(mappingMatrix)
        if (vectorMapping) {
            mappingMatrix.mapVectors(touchPointMappedArray)
        } else {
            mappingMatrix.mapPoints(touchPointMappedArray)
        }

        return touchPointMappedArray
    }

    open fun resetPaint() {
        painter?.resetPaint()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.run {

            // Concat the canvas to 'canvasMatrix'.
            concat(canvasMatrix)

            concat(imageviewMatrix)

            if (!layerClipBounds.isEmpty) {
                clipRect(layerClipBounds)
            }

            drawPainterLayer()
        }
    }

    protected open fun Canvas.drawPainterLayer() {
        selectedLayer?.let { layer ->
            if (isCheckerBoardEnabled) {
                drawPaint(checkerPatternPaint)
            }
            layer.draw(this)
            painter?.draw(this)
        }
    }

    protected open fun PaintLayer.draw(canvas: Canvas) {
        layersPaint.alpha = (255 * opacity).toInt()
        layersPaint.xfermode = blendingModeObject
        canvas.drawBitmap(this.bitmap, 0f, 0f, layersPaint)
    }

    override fun onSendMessage(message: Painter.PainterMessage) {
        when (message) {
            Painter.PainterMessage.INVALIDATE -> {
                invalidate()
            }

            else -> {

            }
        }
    }

    open fun setSelectedLayerLockState(lockedState: Boolean) {
        selectedLayer?.isLocked = lockedState
    }

    open fun setSelectedLayerOpacity(opacity: Float) {
        selectedLayer?.opacity = opacity
        invalidate()
    }

    open fun setSelectedLayerBlendingMode(blendingMode: PorterDuff.Mode) {
        selectedLayer?.blendingMode = blendingMode
        invalidate()
    }

    open fun setSelectedLayerBitmap(bitmap: Bitmap) {
        selectedLayer?.bitmap = bitmap
        invalidate()
    }

    open fun getSelectedLayerBlendingMode(): PorterDuff.Mode {
        return selectedLayer?.blendingMode ?: PorterDuff.Mode.SRC
    }

    open fun getSelectedLayerLockState(): Boolean {
        return selectedLayer?.isLocked == true
    }

    open fun getSelectedLayerOpacity(): Float {
        return selectedLayer?.opacity ?: 1f
    }

    open fun getSelectedLayerBitmap(): Bitmap? {
        return selectedLayer?.bitmap
    }

    open fun setOnDoubleFingerTapUpListener(onDoubleFingerTapUp: OnDoubleFingerTapUp) {
        onDoubleFingerTapUpInterface = onDoubleFingerTapUp
    }

    open fun setOnDoubleFingerTapUpListener(callback: ((Unit) -> Unit)) {
        onDoubleFingerTapUp = callback
    }

    open fun setOnTransformDelegate(func: (transformationMatrix: Matrix) -> Unit) {
        onDelegateTransform = func
    }

    open fun convertToBitmap(): Bitmap? {
        return selectedLayer?.bitmap
    }

    open fun resetTransformationMatrix(animate: Boolean = true) {
        if (canvasMatrix.isIdentity || resetMatrixAnimator.isRunning) {
            return
        }

        if (animate) {
            startMatrix.set(canvasMatrix)
            endMatrix.reset()
            resetMatrixAnimator.start()
        } else {
            canvasMatrix.reset()
            invalidate()
        }
    }

    open fun doAfterResetTransformation(func: () -> Unit) {
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

    protected open fun callOnDoubleTapListeners() {
        onDoubleFingerTapUp?.invoke(Unit)
        onDoubleFingerTapUpInterface?.onDoubleFingerTapUp()
    }

    open fun applyCanvasMatrix(matrix: Matrix) {
        canvasMatrix.postConcat(matrix)
        invalidate()
    }

    open fun setCanvasMatrix(matrix: Matrix) {
        canvasMatrix.set(matrix)
        invalidate()
    }

    open fun setClipRect(rect: Rect, animate: Boolean = true, func: () -> Unit = {}) {
        if (rect == layerClipBounds) {
            func.invoke()
            return
        }
        if (animate && !clipAnimator.isRunning) {
            startRect.set(layerClipBounds)
            endRect.set(rect)
            clipAnimator.start()

            clipAnimator.doOnEnd {
                func.invoke()
                clipAnimator.listeners.clear()
            }
        } else {
            changeClipSize(rect)
            func.invoke()
            invalidate()
        }
    }

    protected open fun changeClipSize(rect: Rect) {
        rectAlloc.set(boundsRectangle)

        layerClipBounds.set(rect)
        bitmapWidth = layerClipBounds.width()
        bitmapHeight = layerClipBounds.height()

        resizeCanvas(width.toFloat(), height.toFloat())

        mappingMatrix.setRectToRect(
            rectAlloc,
            boundsRectangle, Matrix.ScaleToFit.CENTER
        )

        rectAlloc.set(boundsRectangle)

        painter?.onSizeChanged(rectAlloc, layerClipBounds, mappingMatrix)
    }

    open fun isIdentityClip(): Boolean =
        layerClipBounds == identityClip

    protected open fun resizeCanvas(width: Float, height: Float) {
        resizeDrawable(width, height)

        calculateBounds()
    }

    fun setOnDoubleTapListener(func: ((event: MotionEvent) -> Boolean)) {
        onDoubleTap = func
    }

    fun setOnDoubleTapListener(listener: OnDoubleTap) {
        onDoubleTappedListener = listener
    }

    private fun callDoubleTapListeners(event: MotionEvent): Boolean {
        var isConsumed = false
        isConsumed = isConsumed.or(onDoubleTap?.invoke(event) == true)
        isConsumed = isConsumed.or(onDoubleTappedListener?.onDoubleTap(event) == true)
        return isConsumed
    }

    interface OnDoubleFingerTapUp {
        fun onDoubleFingerTapUp()
    }

    interface OnDoubleTap {
        fun onDoubleTap(event: MotionEvent): Boolean
    }

    interface OnLayersChanged {
        fun onLayersChanged(layers: List<PaintLayer>, selectedLayerIndex: Int)
    }

}
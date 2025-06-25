package ir.baboomeh.photolib.components.paint.paintview

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
import ir.baboomeh.photolib.R
import ir.baboomeh.photolib.components.paint.painters.painter.MessageChannel
import ir.baboomeh.photolib.components.paint.painters.painter.Painter
import ir.baboomeh.photolib.components.paint.painters.painter.PainterMessage
import ir.baboomeh.photolib.utils.evaluators.MatrixEvaluator
import ir.baboomeh.photolib.utils.gesture.TouchData
import ir.baboomeh.photolib.utils.gesture.detectors.rotation.OnRotateListener
import ir.baboomeh.photolib.utils.gesture.detectors.rotation.RotationDetectorGesture
import ir.baboomeh.photolib.utils.gesture.detectors.rotation.TwoFingerRotationDetector
import ir.baboomeh.photolib.utils.gesture.detectors.translation.OnTranslationDetector
import ir.baboomeh.photolib.utils.gesture.detectors.translation.TranslationDetector
import ir.baboomeh.photolib.utils.matrix.MananMatrix
import ir.baboomeh.photolib.utils.matrix.MananMatrixAnimator
import kotlin.math.abs

/**
 * Advanced paint view that provides comprehensive canvas transformation and painting capabilities.
 * 
 * This view serves as the foundation for sophisticated image editing and digital art applications.
 * It combines multi-touch gesture recognition with matrix-based transformations to provide
 * a smooth, responsive painting experience similar to professional graphics software.
 * 
 * **Core Features:**
 * - **Multi-touch Gestures**: Support for pan, pinch-zoom, and rotation gestures.
 * - **Matrix Transformations**: Smooth canvas transformations with animation support.
 * - **Painter Integration**: Seamless integration with painting tools and brushes.
 * - **Touch Mapping**: Precise coordinate transformation for accurate painting.
 * - **Performance Optimization**: Intelligent gesture detection and rendering optimization.
 * 
 * **Gesture System:**
 * - **Single Touch**: Direct painting and drawing operations.
 * - **Two Finger Pan**: Canvas translation with momentum.
 * - **Pinch Zoom**: Smooth scaling with configurable limits.
 * - **Two Finger Rotation**: Canvas rotation with precision control.
 * - **Double Tap**: Configurable zoom and tool actions.
 * 
 * **Transformation Features:**
 * - **Animated Transitions**: Smooth matrix animations with customizable duration.
 * - **Coordinate Mapping**: Automatic transformation between screen and canvas coordinates.
 * - **Gesture Delegation**: Optional gesture forwarding to painting tools.
 * - **Reset Functionality**: Return to default view state with animation.
 * 
 * **Layer Support:**
 * - **Single Layer Mode**: Basic painting on a single bitmap layer.
 * - **Layer Properties**: Opacity, blending modes, and lock states.
 * - **Bitmap Management**: Automatic bitmap handling and memory optimization.
 * - **Transparency Support**: Checkerboard background for alpha channel visualization.
 * 
 * **Technical Architecture:**
 * - Thread-safe operation with proper synchronization.
 * - Memory-efficient bitmap handling with recycling support.
 * - Extensible design for advanced features through inheritance.
 * - Robust error handling and state management.
 */
open class MananPaintView(context: Context, attrSet: AttributeSet?) :
    View(context, attrSet), MessageChannel,
    ScaleGestureDetector.OnScaleGestureListener,
    OnRotateListener, OnTranslationDetector {
    
    /** Alternative constructor for programmatic view creation without attributes. */
    constructor(context: Context) : this(context, null)

    /** Paint used for rendering layer bitmaps with filtering enabled for smooth scaling. */
    protected val layersPaint by lazy {
        Paint().apply {
            isFilterBitmap = true
        }
    }

    /** Flag indicating whether a matrix transformation gesture is in progress. */
    protected var isMatrixGesture = false

    /** Temporary holder for rotation values during gesture processing. */
    protected var rotationHolder = 0f

    /** Flag indicating if this is the first move in a gesture sequence. */
    protected var isFirstMove = true

    /**
     * Controls whether the checkerboard transparency background is displayed.
     * Useful for visualizing transparent areas in images.
     */
    open var isCheckerBoardEnabled = true
        set(value) {
            field = value
            invalidate()
        }

    /** Flag indicating whether the canvas has been moved during the current gesture. */
    protected var isMoved = false

    // Used to retrieve touch slopes.
    protected var scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /** Reusable rectangle for allocation-free calculations. */
    protected val rectAlloc by lazy {
        RectF()
    }

    /** Reference to the currently selected layer for painting operations. */
    protected open var selectedLayer: PaintLayer? = null

    /** Main transformation matrix for canvas zoom, pan, and rotation operations. */
    protected val canvasMatrix by lazy {
        MananMatrix()
    }

    /** Temporary matrix for coordinate mapping calculations. */
    protected val mappingMatrix by lazy {
        MananMatrix()
    }

    /** Matrix for painter-specific transformations when gesture delegation is enabled. */
    protected val painterTransformationMatrix by lazy {
        MananMatrix()
    }

    /** Animator for smooth matrix transitions and fit-to-screen operations. */
    protected val matrixAnimator by lazy {
        MananMatrixAnimator(canvasMatrix, RectF(boundsRectangle), 300L, FastOutSlowInInterpolator())
    }

    /** Reusable array for coordinate transformation to avoid memory allocations. */
    protected val touchPointMappedArray = FloatArray(2)

    /** Callback for double finger tap up events. */
    protected var onDoubleFingerTapUp: ((Unit) -> Unit)? = null

    /** Interface-based callback for double finger tap up events. */
    protected var onDoubleFingerTapUpInterface: OnDoubleFingerTapUp? = null

    /** Callback for double tap events with motion event data. */
    protected var onDoubleTap: ((event: MotionEvent) -> Boolean)? = null

    /** Interface-based callback for double tap events. */
    protected var onDoubleTappedListener: OnDoubleTap? = null

    /** Callback for delegating transformation operations to external handlers. */
    protected var onDelegateTransform: ((transformationMatrix: Matrix) -> Unit)? = null

    /** Controls whether gesture transformations are delegated to external handlers. */
    open var isGestureDelegationEnabled: Boolean = false

    /** Accumulator for total rotation amount to determine if significant rotation occurred. */
    protected var totalRotated = 0f

    /** Minimum rotation threshold to consider a rotation gesture significant. */
    protected var rotationSlope = 0.5f

    /** Flag indicating whether the first finger has moved during a multi-touch gesture. */
    protected var isFirstFingerMoved = false

    /**
     * Controls whether touch event history is enabled for improved gesture recognition.
     * Useful for smooth line drawing and gesture detection.
     */
    open var isTouchEventHistoryEnabled = false
        set(value) {
            field = value
            translationDetector.isTouchEventHistoryEnabled = value
        }

    /** Flag indicating whether the view has been fully initialized and is ready for operations. */
    protected var isViewInitialized = false

    /**
     * The active painter tool responsible for drawing operations.
     * Setting this will initialize the painter and enable drawing functionality.
     */
    open var painter: Painter? = null
        set(value) {
            // Clean up previous painter.
            painter?.release()

            field = value
            value?.setOnMessageListener(this)

            // Initialize painter if view is ready.
            if (isViewInitialized) {
                initializedPainter(field)
            }
            requestLayout()
        }

    /** Callback invoked when the painter is fully initialized and ready for use. */
    protected var onPainterInitializedListener: () -> Unit = {}

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
            // Disable quick scale to prevent accidental scaling.
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

    /** Translation detector for handling pan and drag gestures. */
    protected val translationDetector by lazy {
        TranslationDetector(this)
    }

    /** Gesture detector for handling double tap and other complex gestures. */
    private val gestureDetector: GestureDetector by lazy {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                callDoubleTapListeners(e)
                return true
            }
        })
    }

    /** Touch data object for move begin events to avoid repeated allocations. */
    protected val onMoveBeginTouchData by lazy {
        TouchData()
    }

    /** Rectangle defining the bounds of the canvas content area. */
    protected val boundsRectangle = RectF()

    /** Rectangle defining the bounds of the current layer. */
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
    protected var isNewLayer = true

    /** Controls whether rotation gestures are recognized and processed. */
    open var isRotatingEnabled = true
    
    /** Controls whether scaling gestures are recognized and processed. */
    open var isScalingEnabled = true
    
    /** Controls whether translation gestures are recognized and processed. */
    open var isTranslationEnabled = true

    // Used to animate the matrix in MatrixEvaluator
    protected val endMatrix = MananMatrix()
    protected val startMatrix = MananMatrix()

    /** Rectangle bounds for animation end state. */
    protected val endRect = Rect()
    /** Rectangle bounds for animation start state. */
    protected val startRect = Rect()

    /** Duration in milliseconds for matrix transformation animations. */
    open var matrixAnimationDuration: Long = 500
        set(value) {
            field = value
            resetMatrixAnimator.duration = field
        }

    /** Interpolator used for smooth matrix transformation animations. */
    open var matrixAnimationInterpolator: TimeInterpolator = FastOutSlowInInterpolator()
        set(value) {
            field = value
            resetMatrixAnimator.interpolator = field
        }

    /** Animator for smooth matrix reset transitions to identity state. */
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

    /** Animator for smooth clipping rectangle transitions. */
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

    /** Paint with checkerboard pattern shader for rendering transparency backgrounds. */
    protected open val checkerPatternPaint by lazy {
        Paint().apply {
            shader = BitmapShader(
                BitmapFactory.decodeResource(resources, R.drawable.checker),
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT
            )
        }
    }

    /** Clipping bounds for the layer content area. */
    val layerClipBounds by lazy {
        Rect()
    }

    /** Original identity clipping bounds for reset operations. */
    val identityClip by lazy {
        Rect()
    }

    /**
     * Called when the view size changes due to layout or orientation changes.
     * Updates canvas dimensions and recalculates transformation matrices.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (isInLayout) {
            return
        }

        // Store current bounds for comparison.
        rectAlloc.set(boundsRectangle)

        super.onSizeChanged(w, h, oldw, oldh)

        // Update canvas dimensions and transformation matrices.
        resizeCanvas(w.toFloat(), h.toFloat())

        // Calculate mapping matrix for coordinate transformations.
        mappingMatrix.setRectToRect(
            rectAlloc,
            boundsRectangle, Matrix.ScaleToFit.CENTER
        )

        rectAlloc.set(boundsRectangle)

        // Notify painter of size changes.
        painter?.onSizeChanged(rectAlloc, layerClipBounds, mappingMatrix)
    }

    /**
     * Called when drawable is about to be resized to fit the view's dimensions.
     * @return Modified matrix.
     */
    protected open fun resizeDrawable(targetWidth: Float, targetHeight: Float) {
        // Calculate transformation matrix to fit layer content within view bounds.
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

    /**
     * Handles view layout operations and initializes the view when bitmap dimensions are available.
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (isNewLayer && bitmapWidth != 0 && bitmapHeight != 0) {
            // Update canvas dimensions based on new bitmap.
            resizeCanvas(width.toFloat(), height.toFloat())

            // Complete initialization process.
            onImageLaidOut()

            isNewLayer = false
        }
    }

    /**
     * Calculates bounds of image with matrix values.
     */
    protected open fun calculateBounds() {
        imageviewMatrix.run {
            // Get current scale factor from the transformation matrix.
            val matrixScale = imageviewMatrix.getScaleX(true)

            // Calculate edge positions with current transformations.
            val leftEdge = getTranslationX()
            val topEdge = getTranslationY()

            // Calculate final dimensions with scale applied.
            val finalWidth = (bitmapWidth * matrixScale)
            val finalHeight = (bitmapHeight * matrixScale)

            val rightEdge = finalWidth + leftEdge
            val bottomEdge = finalHeight + topEdge

            // Update bounds rectangles with calculated values.
            boundsRectangle.set(leftEdge, topEdge, rightEdge, bottomEdge)
            layerBounds.set(layerClipBounds)
        }
    }

    /**
     * Called when the image layout is established and dimensions are available.
     * Initializes painters and completes view setup.
     */
    protected open fun onImageLaidOut() {
        rectAlloc.set(boundsRectangle)

        if (!isViewInitialized) {
            initializedPainter(painter)
            isViewInitialized = true
        }
    }

    /**
     * Initializes the painter with current view state and transformation matrices.
     * Sets up coordinate systems and provides layer references.
     */
    protected open fun initializedPainter(pp: Painter?) {
        pp?.apply {
            rectAlloc.set(layerBounds)

            if (!isInitialized) {
                // Initialize painter with transformation matrices and bounds.
                initialize(
                    context,
                    canvasMatrix,
                    imageviewMatrix,
                    identityClip,
                    layerClipBounds
                )

                // Notify initialization completion.
                onPainterInitializedListener.invoke()
            }

            // Provide current layer reference.
            onLayerChanged(selectedLayer)

            // Provide bitmap reference for advanced painting operations.
            selectedLayer?.let { layer ->
                onReferenceLayerCreated(layer.bitmap)
            }
        }
    }

    /**
     * Handles all touch events and distributes them to appropriate gesture detectors.
     * Manages coordinate transformations and painter integration.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null || resetMatrixAnimator.isRunning) {
            return false
        }

        // Process scaling gestures if enabled.
        if (isScalingEnabled) {
            scaleDetector.onTouchEvent(event)
        }

        // Process translation gestures if enabled.
        if (isTranslationEnabled) {
            translationDetector.onTouchEvent(event)
        }

        // Transform coordinates for painter if it handles gestures.
        if (painter?.doesTakeGestures() == true) {
            mappingMatrix.setConcat(canvasMatrix, imageviewMatrix)
            mappingMatrix.invert(mappingMatrix)
            event.transform(mappingMatrix)
        }

        // Process rotation gestures if enabled.
        if (isRotatingEnabled) {
            rotationDetector.onTouchEvent(event)
        }

        // Process complex gestures like double tap.
        gestureDetector.onTouchEvent(event)
        return true
    }

    /**
     * Called when a translation gesture begins.
     * Sets up initial state for the gesture sequence.
     */
    override fun onMoveBegin(detector: TranslationDetector): Boolean {
        isFirstMove = true
        isMoved = false

        // Store initial touch data for painter operations.
        onMoveBeginTouchData.set(detector.getTouchData(0))

        if (detector.pointerCount == 2) {
            isMatrixGesture = true
            // Notify painter of transformation start.
            painter?.onTransformBegin()
        }

        return painter != null || detector.pointerCount > 1
    }

    /**
     * Processes continuous movement during translation gestures.
     * Handles both single-finger painting and multi-finger transformations.
     */
    override fun onMove(detector: TranslationDetector): Boolean {
        val firstPointerTouchData = detector.getTouchData(0)

        // Calculate touch slope threshold based on current zoom level.
        val finalSlope = if (painter?.doesNeedTouchSlope() == true) {
            abs(scaledTouchSlop / canvasMatrix.getRealScaleX())
        } else {
            0f
        }

        // Track first finger movement for painting operations.
        isFirstFingerMoved =
            isFirstFingerMoved.or(firstPointerTouchData.dxSum >= finalSlope || firstPointerTouchData.dySum >= finalSlope)

        if (detector.pointerCount == 2) {
            // Handle two-finger transformation gestures.
            val secondPointerTouchData = detector.getTouchData(1)

            isMoved =
                isMoved.or(secondPointerTouchData.dxSum >= finalSlope || secondPointerTouchData.dySum >= finalSlope)

            // Calculate average movement for smooth transformation.
            val dx = (secondPointerTouchData.dx + firstPointerTouchData.dx) / 2
            val dy = (secondPointerTouchData.dy + firstPointerTouchData.dy) / 2

            isMatrixGesture = true

            if (isTranslationEnabled) {
                if (painter?.doesTakeGestures() == true) {
                    // Delegate transformation to painter.
                    mapTouchPoints(dx, dy, true).let { points ->
                        painterTransformationMatrix.setTranslate(points[0], points[1])
                    }
                    if (isGestureDelegationEnabled) {
                        onDelegateTransform?.invoke(painterTransformationMatrix)
                    } else {
                        painter?.onTransformed(painterTransformationMatrix)
                    }
                } else {
                    // Apply transformation to canvas matrix.
                    canvasMatrix.postTranslate(dx, dy)
                    invalidate()
                }
            }
        } else if (!isMatrixGesture && isFirstFingerMoved && selectedLayer?.isLocked == false) {
            // Handle single-finger painting operations.
            if (isFirstMove) {
                mapTouchData(onMoveBeginTouchData)
                callPainterOnMoveBegin(onMoveBeginTouchData)
            }

            mapTouchData(firstPointerTouchData)
            callPainterOnMove(firstPointerTouchData)
        }
        return true
    }

    /**
     * Called when translation gesture ends.
     * Finalizes painting operations and transformation states.
     */
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

        // Reset gesture state flags.
        isMatrixGesture = false
        isMoved = false
        isFirstFingerMoved = false
    }

    /**
     * Transforms touch coordinates from screen space to canvas space.
     * Applies inverse transformations for accurate coordinate mapping.
     */
    protected open fun mapTouchData(touchData: TouchData) {
        // Map touch position coordinates.
        mapTouchPoints(touchData.ex, touchData.ey).let { points ->
            touchData.ex = points[0]
            touchData.ey = points[1]
        }
        // Map touch movement vectors.
        mapTouchPoints(touchData.dx, touchData.dy, true).let { points ->
            touchData.dx = points[0]
            touchData.dy = points[1]
        }
    }

    /**
     * Initiates painter move begin operation with transformed coordinates.
     */
    protected open fun callPainterOnMoveBegin(touchData: TouchData) {
        painter!!.onMoveBegin(touchData)
        isFirstMove = false
    }

    /**
     * Determines if conditions are met for calling double tap listeners.
     */
    protected open fun canCallDoubleTapListeners(): Boolean =
        isMatrixGesture && !isMoved && painter?.doesHandleHistory() == false

    /**
     * Determines if conditions are met for calling painter move begin.
     */
    protected open fun canCallPainterOnMoveBegin(): Boolean =
        selectedLayer != null && (!isMatrixGesture || !isFirstMove) && selectedLayer?.isLocked == false

    /**
     * Calls painter move end operation with proper error handling.
     */
    protected open fun canCallPainterMoveEnd(touchData: TouchData) {
        painter!!.onMoveEnded(touchData)
    }

    /**
     * Calls painter move operation with movement validation.
     */
    protected open fun callPainterOnMove(touchData: TouchData) {
        if (touchData.dx == 0f && touchData.dy == 0f) {
            return
        }
        painter!!.onMove(touchData)
    }

    /**
     * Called when rotation gesture begins.
     */
    override fun onRotateBegin(initialDegree: Float, px: Float, py: Float): Boolean {
        isMoved = false
        return true
    }

    /**
     * Processes rotation gestures and applies transformations.
     */
    override fun onRotate(degree: Float, px: Float, py: Float): Boolean {
        val rotationDelta = degree - rotationHolder

        if (painter?.doesTakeGestures() == true) {
            // Delegate rotation to painter.
            painterTransformationMatrix.setRotate(rotationDelta, px, py)
            if (isGestureDelegationEnabled) {
                onDelegateTransform?.invoke(painterTransformationMatrix)
            } else {
                painter?.onTransformed(painterTransformationMatrix)
            }
        } else {
            // Apply rotation to canvas matrix.
            canvasMatrix.postRotate(rotationDelta, px, py)
            invalidate()
        }

        totalRotated += abs(rotationDelta)
        rotationHolder = degree
        return true
    }

    /**
     * Called when rotation gesture ends.
     */
    override fun onRotateEnded() {
        if (totalRotated > rotationSlope) {
            isMoved = true
        }

        totalRotated = 0f
    }

    /**
     * Called when scale gesture begins.
     */
    override fun onScaleBegin(p0: ScaleGestureDetector): Boolean {
        isMoved = false
        return !matrixAnimator.isAnimationRunning()
    }

    /**
     * Processes scale gestures and applies zoom transformations.
     */
    override fun onScale(p0: ScaleGestureDetector): Boolean {
        p0.run {
            val sf = scaleFactor
            isMoved = true

            if (painter?.doesTakeGestures() == true) {
                // Delegate scaling to painter.
                mapTouchPoints(focusX, focusY, false).let {
                    painterTransformationMatrix.setScale(sf, sf, it[0], it[1])
                }

                if (isGestureDelegationEnabled) {
                    onDelegateTransform?.invoke(painterTransformationMatrix)
                } else {
                    painter?.onTransformed(painterTransformationMatrix)
                }
            } else {
                // Apply scaling to canvas matrix.
                canvasMatrix.postScale(sf, sf, focusX, focusY)
                invalidate()
            }
            return true
        }
    }

    /**
     * Called when scale gesture ends.
     */
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

        // Apply inverse canvas matrix transformation.
        canvasMatrix.invert(mappingMatrix)
        if (vectorMapping) {
            mappingMatrix.mapVectors(touchPointMappedArray)
        } else {
            mappingMatrix.mapPoints(touchPointMappedArray)
        }

        mappingMatrix.reset()

        // Apply inverse image view matrix transformation.
        imageviewMatrix.invert(mappingMatrix)
        if (vectorMapping) {
            mappingMatrix.mapVectors(touchPointMappedArray)
        } else {
            mappingMatrix.mapPoints(touchPointMappedArray)
        }

        return touchPointMappedArray
    }

    /**
     * Resets the active painter to its initial state.
     */
    open fun resetPaint() {
        painter?.resetPaint()
    }

    /**
     * Main drawing method that renders the canvas content.
     * Applies transformations and delegates layer rendering.
     */
    override fun onDraw(canvas: Canvas) {
        canvas.run {
            // Concat the canvas to 'canvasMatrix'.
            concat(canvasMatrix)

            concat(imageviewMatrix)

            // Apply clipping if bounds are set.
            if (!layerClipBounds.isEmpty) {
                clipRect(layerClipBounds)
            }

            drawPainterLayer(canvas)
        }
    }

    /**
     * Renders the painter layer with transparency background and active painting.
     */
    protected open fun drawPainterLayer(canvas: Canvas) {
        canvas.apply {
            selectedLayer?.let { layer ->
                // Draw checkerboard background for transparency visualization.
                if (isCheckerBoardEnabled) {
                    drawPaint(checkerPatternPaint)
                }
                drawPaintLayer(canvas, layer)
                // Draw active painter content.
                painter?.draw(this)
            }
        }
    }

    /**
     * Renders a single paint layer with proper opacity and blending.
     */
    protected open fun drawPaintLayer(canvas: Canvas, paintLayer: PaintLayer) {
        paintLayer.apply {
            layersPaint.alpha = (255 * opacity).toInt()
            layersPaint.xfermode = blendingModeObject
            canvas.drawBitmap(this.bitmap, 0f, 0f, layersPaint)
        }
    }

    /**
     * Handles messages from the painter system.
     */
    override fun onSendMessage(message: PainterMessage) {
        when (message) {
            PainterMessage.INVALIDATE -> {
                invalidate()
            }

            else -> {
                // Handle other message types in derived classes.
            }
        }
    }

    /**
     * Sets the lock state of the currently selected layer.
     */
    open fun setSelectedLayerLockState(lockedState: Boolean) {
        selectedLayer?.isLocked = lockedState
    }

    /**
     * Sets the opacity of the currently selected layer.
     */
    open fun setSelectedLayerOpacity(opacity: Float) {
        selectedLayer?.opacity = opacity
        invalidate()
    }

    /**
     * Sets the blending mode of the currently selected layer.
     */
    open fun setSelectedLayerBlendingMode(blendingMode: PorterDuff.Mode) {
        selectedLayer?.blendingMode = blendingMode
        invalidate()
    }

    /**
     * Sets the bitmap of the currently selected layer.
     */
    open fun setSelectedLayerBitmap(bitmap: Bitmap) {
        selectedLayer?.bitmap = bitmap
        invalidate()
    }

    /**
     * Gets the blending mode of the currently selected layer.
     */
    open fun getSelectedLayerBlendingMode(): PorterDuff.Mode {
        return selectedLayer?.blendingMode ?: PorterDuff.Mode.SRC
    }

    /**
     * Gets the lock state of the currently selected layer.
     */
    open fun getSelectedLayerLockState(): Boolean {
        return selectedLayer?.isLocked == true
    }

    /**
     * Gets the opacity of the currently selected layer.
     */
    open fun getSelectedLayerOpacity(): Float {
        return selectedLayer?.opacity ?: 1f
    }

    /**
     * Gets the bitmap of the currently selected layer with optional clipping.
     */
    open fun getSelectedLayerBitmap(isClipped: Boolean = true): Bitmap? {
        return selectedLayer?.bitmap?.run {
            if (layerClipBounds == identityClip || !isClipped) {
                this
            } else {
                Bitmap.createBitmap(
                    this,
                    layerClipBounds.left,
                    layerClipBounds.top,
                    layerClipBounds.width(),
                    layerClipBounds.height(),
                )
            }
        }
    }

    /**
     * Sets the interface-based callback for double finger tap up events.
     */
    open fun setOnDoubleFingerTapUpListener(onDoubleFingerTapUp: OnDoubleFingerTapUp) {
        onDoubleFingerTapUpInterface = onDoubleFingerTapUp
    }

    /**
     * Sets the lambda-based callback for double finger tap up events.
     */
    open fun setOnDoubleFingerTapUpListener(callback: ((Unit) -> Unit)) {
        onDoubleFingerTapUp = callback
    }

    /**
     * Sets the callback for delegating transformation operations.
     */
    open fun setOnTransformDelegate(func: (transformationMatrix: Matrix) -> Unit) {
        onDelegateTransform = func
    }

    /**
     * Converts the current view content to a bitmap.
     * Returns the selected layer bitmap with clipping applied.
     */
    open fun convertToBitmap(): Bitmap? {
        return getSelectedLayerBitmap(true)
    }

    /**
     * Resets the transformation matrix to identity state with optional animation.
     */
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

    /**
     * Executes a function after transformation matrix is reset to identity.
     */
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

    /**
     * Invokes double tap listeners with proper event handling.
     */
    protected open fun callOnDoubleTapListeners() {
        onDoubleFingerTapUp?.invoke(Unit)
        onDoubleFingerTapUpInterface?.onDoubleFingerTapUp()
    }

    /**
     * Applies a matrix transformation to the canvas matrix.
     */
    open fun applyCanvasMatrix(matrix: Matrix) {
        canvasMatrix.postConcat(matrix)
        invalidate()
    }

    /**
     * Sets the canvas matrix to the specified matrix.
     */
    open fun setCanvasMatrix(matrix: Matrix) {
        canvasMatrix.set(matrix)
        invalidate()
    }

    /**
     * Sets the clipping rectangle with optional animation.
     */
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

    /**
     * Changes the clipping size and updates related transformations.
     */
    protected open fun changeClipSize(rect: Rect) {
        rectAlloc.set(boundsRectangle)

        layerClipBounds.set(rect)

        resizeCanvas(width.toFloat(), height.toFloat())

        mappingMatrix.setRectToRect(
            rectAlloc,
            boundsRectangle, Matrix.ScaleToFit.CENTER
        )

        rectAlloc.set(boundsRectangle)

        painter?.onSizeChanged(rectAlloc, layerClipBounds, mappingMatrix)
    }

    /**
     * Checks if the current clipping bounds are in identity state.
     */
    open fun isIdentityClip(): Boolean =
        layerClipBounds == identityClip

    /**
     * Resizes the canvas and recalculates bounds.
     */
    protected open fun resizeCanvas(width: Float, height: Float) {
        resizeDrawable(width, height)
        calculateBounds()
    }

    /**
     * Sets the lambda-based callback for double tap events.
     */
    fun setOnDoubleTapListener(func: ((event: MotionEvent) -> Boolean)) {
        onDoubleTap = func
    }

    /**
     * Sets the interface-based callback for double tap events.
     */
    fun setOnDoubleTapListener(listener: OnDoubleTap) {
        onDoubleTappedListener = listener
    }

    /**
     * Sets the callback for painter initialization completion.
     */
    open fun setOnPainterInitialized(onInitialized: () -> Unit) {
        onPainterInitializedListener = onInitialized
    }

    /**
     * Adds a new layer with the specified bitmap.
     * The bitmap must be mutable to allow painting operations.
     */
    open fun addNewLayer(bitmap: Bitmap?) {
        if (bitmap != null) {
            if (!bitmap.isMutable) {
                throw IllegalStateException("Bitmap should be mutable")
            }

            isNewLayer = true
            bitmapWidth = bitmap.width
            bitmapHeight = bitmap.height
            isViewInitialized = false

            layerClipBounds.set(0, 0, bitmapWidth, bitmapHeight)
            identityClip.set(layerClipBounds)

            selectedLayer = PaintLayer(bitmap, false, 1f)

            requestLayout()
            invalidate()
        }
    }

    /**
     * Calls double tap listeners with event validation.
     */
    private fun callDoubleTapListeners(event: MotionEvent): Boolean {
        var isConsumed = false
        isConsumed = isConsumed.or(onDoubleTap?.invoke(event) == true)
        isConsumed = isConsumed.or(onDoubleTappedListener?.onDoubleTap(event) == true)
        return isConsumed
    }

    /** Interface for handling double finger tap up events. */
    interface OnDoubleFingerTapUp {
        fun onDoubleFingerTapUp()
    }

    /** Interface for handling double tap events. */
    interface OnDoubleTap {
        fun onDoubleTap(event: MotionEvent): Boolean
    }

    /** Interface for handling layer change events. */
    interface OnLayersChanged {
        fun onLayersChanged(layers: List<PaintLayer>, selectedLayerIndex: Int)
    }
}

package ir.baboomeh.photolib.components.paint.painters.cropper

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import androidx.core.animation.doOnEnd
import androidx.core.graphics.createBitmap
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import ir.baboomeh.photolib.components.paint.painters.cropper.aspect_ratios.AspectRatio
import ir.baboomeh.photolib.components.paint.painters.cropper.aspect_ratios.AspectRatioFree
import ir.baboomeh.photolib.components.paint.painters.cropper.aspect_ratios.AspectRatioLocked
import ir.baboomeh.photolib.components.paint.painters.painter.Painter
import ir.baboomeh.photolib.components.paint.painters.painter.PainterMessage
import ir.baboomeh.photolib.components.paint.paintview.PaintLayer
import ir.baboomeh.photolib.utils.evaluators.MatrixEvaluator
import ir.baboomeh.photolib.utils.evaluators.RectFloatEvaluator
import ir.baboomeh.photolib.utils.extensions.dp
import ir.baboomeh.photolib.utils.extensions.perimeter
import ir.baboomeh.photolib.utils.gesture.TouchData
import ir.baboomeh.photolib.utils.matrix.MananMatrix
import kotlin.math.max
import kotlin.math.min

/**
 * A comprehensive image cropping tool that provides interactive crop frame manipulation
 * with aspect ratio controls, guidelines, and smooth animations.
 * 
 * This tool implements a full-featured cropping interface commonly found in photo editing
 * applications, supporting:
 * 
 * **Interactive Crop Frame:**
 * - Resizable crop rectangle with corner and edge handles
 * - Real-time preview with darkened overlay outside crop area
 * - Smooth handle-based resizing with visual feedback
 * - Automatic bounds checking to prevent invalid crop regions
 * 
 * **Aspect Ratio Management:**
 * - Support for locked aspect ratios (e.g., 1:1, 4:3, 16:9)
 * - Free-form cropping without ratio constraints
 * - Dynamic aspect ratio switching with smooth transitions
 * - Intelligent resize behavior that maintains ratios
 * 
 * **Visual Guidelines:**
 * - Rule of thirds grid lines for composition guidance
 * - Customizable guideline appearance and visibility
 * - Professional-grade visual feedback during editing
 * 
 * **User Experience Features:**
 * - Animated transitions for smooth interactions
 * - Gesture-based frame manipulation and canvas panning
 * - Auto-fitting to keep crop frame within image bounds
 * - Smart handle positioning with extended touch areas
 * 
 * **Advanced Functionality:**
 * - Direct cropping to create new bitmap with cropped content
 * - In-place clipping to modify existing layer content
 * - Matrix-based transformations for precise positioning
 * - Customizable colors, stroke widths, and visual styling
 * 
 * The tool automatically handles complex scenarios like maintaining aspect ratios
 * during resize operations, preventing invalid crop dimensions, and providing
 * smooth user interactions through gesture detection and animation systems.
 * 
 * **Usage Example:**
 * ```kotlin
 * val cropperTool = CropperTool(context)
 * cropperTool.setAspectRatio(AspectRatioLocked(16f, 9f)) // 16:9 aspect ratio
 * cropperTool.frameColor = Color.WHITE
 * cropperTool.isDrawGuidelineEnabled = true
 * 
 * // Get cropped bitmap
 * val croppedImage = cropperTool.crop()
 * 
 * // Or clip existing layer
 * cropperTool.clip()
 * ```
 */
open class CropperTool(context: Context) : Painter() {

    protected var selectedLayer: PaintLayer? = null

    protected val limitRect = RectF()

    // Paint used for drawing the frame.
    protected val framePaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE
        }
    }

    /** Color of the crop frame border */
    open var frameColor = Color.DKGRAY
        set(value) {
            framePaint.color = value
            field = value
        }

    /** Stroke width of the crop frame border */
    open var frameStrokeWidth = context.dp(2)
        set(value) {
            framePaint.strokeWidth = value
            field = value
        }

    // Paint used for drawing guidelines.
    protected val frameGuidelinePaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE
        }
    }

    /** Stroke width of the rule-of-thirds guideline grid */
    open var guidelineStrokeWidth = context.dp(1)
        set(value) {
            frameGuidelinePaint.strokeWidth = value
            field = value
        }

    /** Color of the rule-of-thirds guideline grid */
    open var guidelineColor = Color.DKGRAY
        set(value) {
            frameGuidelinePaint.color = value
            field = value
        }

    // Determines if guideline should be drawn or not.
    var isDrawGuidelineEnabled = true


    // Paint used for drawing the shadows around frame.
    protected val frameShadowsPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    /** Color of the darkened overlay area outside the crop frame */
    open var backgroundShadowColor = Color.BLACK
        set(value) {
            frameShadowsPaint.color = value
            field = value
        }

    /** Alpha transparency level of the darkened overlay (0-255) */
    open var backgroundShadowAlpha = 85
        set(value) {
            frameShadowsPaint.alpha = value
            field = value
        }

    // Paint used for drawing handle bars.
    protected val handleBarPaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE

        }
    }

    /** Stroke width of the corner and edge resize handles */
    open var handleBarStrokeWidth = context.dp(3)
        set(value) {
            handleBarPaint.strokeWidth = value
            field = value
        }

    /** Color of the corner and edge resize handles */
    open var handleBarColor = Color.DKGRAY
        set(value) {
            handleBarPaint.color = value
            field = value
        }

    /** Shape of the handle bar endpoints (ROUND or SQUARE) */
    open var handleBarCornerType = Paint.Cap.ROUND
        set(value) {
            handleBarPaint.strokeCap = value
            // If corners are round turn on anti-aliasing.
            handleBarPaint.isAntiAlias = value == Paint.Cap.ROUND
            field = value
        }

    /**
     * Determines color of handle bars when they are selected.
     * By default this value is same as [handleBarColor].
     */
    open var selectedHandleBarColor = handleBarColor

    // Rectangle that represents the crop frame.
    protected val frameRect by lazy {
        RectF()
    }

    /**
     * Returns the crop dimensions in the original image coordinate system.
     * This accounts for all transformations applied to the canvas.
     */
    open val cropperDimensions: Rect
        get() {
            inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)
            inverseMatrix.invert(inverseMatrix)
            tempRectF.set(frameRect)
            inverseMatrix.mapRect(tempRectF)
            tempRectF.round(tempRect)
            return tempRect
        }

    protected val allocRectF by lazy {
        RectF()
    }

    protected val pointHolder = FloatArray(2)

    // Handle bar dimensions for drawing.
    protected var frameHandleBar = FloatArray(48)

    // Dimension of guidelines.
    protected var guideLineDimension = FloatArray(16)

    // Map of points on frame that represents each handle bar like LEFT, TOP-LEFT and etc...
    protected lateinit var mapOfHandleBars: MutableMap<Pair<PointF, PointF>, HandleBar>

    // Later in code determines which handle bar has been pressed.
    protected var handleBar: HandleBar? = null

    // Variable to save aspect ratio of cropper.
    protected var aspectRatio: AspectRatio = AspectRatioFree()

    /** Extended touch area around handles to make them easier to grab */
    protected var excessTouchArea = context.dp(40)

    /** Half of the extended touch area for center calculations */
    protected var excessTouchAreaHalf = excessTouchArea * 0.5f

    protected lateinit var context: Context

    /** Matrix for canvas transformations (zoom, pan, etc.) */
    protected lateinit var canvasMatrix: MananMatrix

    /** Matrix for fitting content inside view bounds */
    protected lateinit var fitInsideMatrix: MananMatrix

    /** Temporary matrix for inverse transformations */
    protected val inverseMatrix = MananMatrix()

    // Used to animate the matrix in MatrixEvaluator
    protected val endMatrix = MananMatrix()
    protected val startMatrix = MananMatrix()

    /** Temporary matrix for calculations */
    protected val tempMatrix by lazy {
        Matrix()
    }

    /** Temporary rectangle for calculations */
    protected val tempRectF by lazy {
        RectF()
    }

    /** Temporary rectangle for integer bounds */
    protected val tempRect by lazy {
        Rect()
    }

    /** Starting rectangle for animations */
    protected val startRect by lazy {
        RectF()
    }

    /** Target rectangle for animations */
    protected val endRect by lazy {
        RectF()
    }

    /** Array for storing rectangle corner points */
    protected val basePoints by lazy {
        FloatArray(8)
    }

    /** Array for transformed corner points */
    protected val cc by lazy {
        FloatArray(8)
    }

    /** Canvas used for cropping operations */
    protected val cropCanvas by lazy {
        Canvas()
    }

    /** Rectangle storing the initial crop frame position */
    protected val startingRect by lazy {
        RectF()
    }

    /** Duration of crop frame animations in milliseconds */
    open var animationDuration: Long = 500
        set(value) {
            field = value
            animator.duration = field
        }

    /** Interpolator for smooth crop frame animations */
    open var animationInterpolator: TimeInterpolator = FastOutSlowInInterpolator()
        set(value) {
            field = value
            animator.interpolator = field
        }

    /** Animator for smooth transitions when adjusting crop frame */
    protected val animator by lazy {
        ValueAnimator.ofObject(MatrixEvaluator(), startMatrix, endMatrix).apply {
            interpolator = animationInterpolator
            duration = animationDuration
            addUpdateListener {
                canvasMatrix.set(it.animatedValue as MananMatrix)
                frameRect.set(rectEvaluator.evaluate(it.animatedFraction, startRect, endRect))
                setDrawingDimensions()
                sendMessage(PainterMessage.INVALIDATE)
            }
        }
    }

    /** Evaluator for interpolating between rectangle positions during animation */
    protected val rectEvaluator by lazy {
        RectFloatEvaluator()
    }

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        this.context = context
        canvasMatrix = transformationMatrix
        this.fitInsideMatrix = fitInsideMatrix

        tempRectF.set(layerBounds)

        fitInsideMatrix.mapRect(tempRectF)

        frameShadowsPaint.color = backgroundShadowColor
        frameShadowsPaint.alpha = backgroundShadowAlpha

        framePaint.color = frameColor

        frameGuidelinePaint.color = guidelineColor
        frameGuidelinePaint.strokeWidth = guidelineStrokeWidth

        handleBarPaint.strokeCap = handleBarCornerType
        handleBarPaint.isAntiAlias = handleBarCornerType == Paint.Cap.ROUND

        // Initialize limit rect that later will be used to limit resizing.
        limitRect.set(tempRectF)

        normalizeCropper(tempRectF.width(), tempRectF.height(), frameRect)
        fitCropperInsideLayer(setRect = true, animate = false)
        setDrawingDimensions()

        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)
    }

    /**
     * Calculates the initial crop frame size based on aspect ratio constraints.
     * Centers the frame within the available area.
     */
    protected open fun normalizeCropper(finalWidth: Float, finalHeight: Float, targetRect: RectF) {
        // Initialize drawing objects after the width and height has been determined.
        val pair = aspectRatio.normalizeAspectRatio(
            finalWidth, finalHeight
        )

        val t = limitRect.centerY() - (pair.second * 0.5f)
        val l = limitRect.centerX() - (pair.first * 0.5f)

        targetRect.set(
            l,
            t,
            pair.first + l,
            pair.second + t
        )
    }

    /**
     * Called when user starts touching/dragging. Determines which handle is being grabbed.
     */
    override fun onMoveBegin(touchData: TouchData) {
        mapPoints(touchData.ex, touchData.ey).let {
            // Figure out which handle bar is in range of the event.
            handleBar = figureOutWhichHandleIsInRangeOfEvent(
                PointF(
                    it[0], it[1]
                )
            )
        }

        startingRect.set(frameRect)

    }

    /**
     * Transforms screen coordinates to crop frame coordinate system.
     */
    protected open fun mapPoints(ex: Float, ey: Float): FloatArray {
        pointHolder[0] = ex
        pointHolder[1] = ey
        inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)
        inverseMatrix.mapPoints(pointHolder)
        return pointHolder
    }

    /**
     * Transforms coordinate array from crop frame to screen coordinates.
     */
    protected open fun mapArray(array: FloatArray) {
        canvasMatrix.invert(inverseMatrix)
        inverseMatrix.mapPoints(array)
    }

    /**
     * Transforms vector array (relative coordinates) using canvas matrix.
     */
    protected open fun mapVectorPoints(array: FloatArray) {
        canvasMatrix.mapVectors(array)
    }

    /**
     * Transforms touch delta values to crop frame coordinate system.
     */
    protected open fun mapInverseVector(touchData: TouchData) {
        inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)
        pointHolder[0] = touchData.dx
        pointHolder[1] = touchData.dy
        inverseMatrix.mapVectors(pointHolder)
        touchData.dx = pointHolder[0]
        touchData.dy = pointHolder[1]
    }

    /**
     * Handles continuous touch movement for resizing crop frame or panning canvas.
     */
    override fun onMove(touchData: TouchData) {
        if (animator.isRunning) {
            return
        }
        // Create a new rectangle to change it's dimensions indirectly to later be able to validate it's size.
        if (handleBar != null) {

            mapInverseVector(touchData)

            val changedRect = aspectRatio.resize(allocRectF.apply {
                set(frameRect)
            }, handleBar, touchData.dx, touchData.dy)


            val frameRectPerimeter = frameRect.perimeter()

            if (frameRectPerimeter < (startingRect.perimeter() / 3f) && allocRectF.perimeter() < frameRectPerimeter) {
                return
            }

            // Change color of handle bar indicating that user is changing size of cropper.
            handleBarPaint.color = selectedHandleBarColor

            mapRectToMatrix(changedRect)

            getOffsetValues(tempRectF).let {

                if (it[0] != 0f || it[1] != 0f) {
                    return
                }

                tempMatrix.setTranslate(it[0], it[1])
                tempMatrix.mapRect(changedRect)
                frameRect.set(changedRect)
            }

        } else {
            canvasMatrix.postTranslate(touchData.dx, touchData.dy)
        }
        // Reset the shadows,handle bar dimensions, handle bar map and etc based on new frame size.
        setDrawingDimensions()
        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Calculates offset needed to keep rectangle within limit bounds.
     * Returns [0,0] if no offset is needed.
     */
    protected open fun getOffsetValues(rect: RectF): FloatArray {
        // Validate that the rectangle is inside the view's bounds.
        val finalX = when {
            rect.right > limitRect.right -> limitRect.right - rect.right
            rect.left < limitRect.left -> limitRect.left - rect.left
            else -> 0f
        }

        val finalY = when {
            rect.bottom > limitRect.bottom -> limitRect.bottom - rect.bottom
            rect.top < limitRect.top -> limitRect.top - rect.top
            else -> 0f
        }

        pointHolder[0] = finalX
        pointHolder[1] = finalY

        return pointHolder
    }

    /**
     * Transforms rectangle coordinates and calculates screen bounds.
     */
    protected open fun mapRectToMatrix(changedRect: RectF) {
        setBoundsVariablesFromRect(changedRect, basePoints)

        basePoints.copyInto(cc)
        mapArray(cc)

        calculateMaximumBounds(cc, tempRectF)
    }

    /**
     * Calculates the bounding rectangle from transformed corner points.
     */
    protected open fun calculateMaximumBounds(cc: FloatArray, tempRect: RectF) {
        val minX = min(min(cc[0], cc[2]), min(cc[4], cc[6]))
        val maxX = max(max(cc[0], cc[2]), max(cc[4], cc[6]))
        val minY = min(min(cc[1], cc[3]), min(cc[5], cc[7]))
        val maxY = max(max(cc[1], cc[3]), max(cc[5], cc[7]))
        tempRect.set(minX, minY, maxX, maxY)
    }

    /**
     * Called when touch gesture ends. Ensures crop frame stays within valid bounds.
     */
    override fun onMoveEnded(touchData: TouchData) {
        fitCropperInsideLayer()
    }

    /**
     * Called when transformation gesture ends. Ensures crop frame stays within valid bounds.
     */
    override fun onTransformEnded() {
        fitCropperInsideLayer()
    }

    /**
     * Adjusts crop frame position and size to fit within layer bounds.
     * Optionally animates the transition for smooth user experience.
     */
    protected open fun fitCropperInsideLayer(
        setStartRect: Boolean = true,
        animate: Boolean = true,
        setMatrix: Boolean = false,
        setRect: Boolean = false
    ) {

        if (animator.isRunning && animate) {
            return
        }

        mapRectToMatrix(frameRect)

        val s = 1f / calculateRectScaleDifference(tempRectF)

        startMatrix.set(canvasMatrix)
        endMatrix.set(canvasMatrix)
        endRect.set(frameRect)

        if (setStartRect) {
            startRect.set(frameRect)
        }

        if (s > 1f) {
            endMatrix.postScale(s, s)
            canvasMatrix.postScale(s, s)
        } else {
            val invS = calculateRectScaleDifference(frameRect)

            endMatrix.postScale(invS, invS)
            canvasMatrix.postScale(invS, invS)

            inverseMatrix.setScale(invS, invS)
            inverseMatrix.mapRect(endRect)

            val offsetX = -(endRect.centerX() - limitRect.centerX())
            val offsetY = -(endRect.centerY() - limitRect.centerY())

            endRect.offset(offsetX, offsetY)

            endMatrix.postTranslate(offsetX, offsetY)
            canvasMatrix.postTranslate(offsetX, offsetY)
        }

        mapRectToMatrix(endRect)

        getOffsetValues(tempRectF).let {
            mapVectorPoints(it)
            endMatrix.postTranslate(-it[0], -it[1])
        }

        if (setMatrix) {
            canvasMatrix.set(endMatrix)
        }
        if (setRect) {
            frameRect.set(endRect)
        }

        if (animate) {
            animator.start()
        }
    }

    /**
     * Calculates the scale factor needed to fit rectangle within limit bounds.
     */
    protected open fun calculateRectScaleDifference(rect: RectF): Float {
        return min(limitRect.width() / rect.width(), limitRect.height() / rect.height())
    }


    /**
     * Renders the crop frame, handles, guidelines, and darkened overlay.
     */
    override fun draw(canvas: Canvas) {
        canvas.run {
            save()

            inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)
            inverseMatrix.invert(inverseMatrix)

            concat(inverseMatrix)

            save()

            canvas.clipRect(frameRect, Region.Op.DIFFERENCE)

            drawPaint(frameShadowsPaint)

            restore()

            drawLines(frameHandleBar, framePaint)

            // Draw guidelines.
            if (isDrawGuidelineEnabled) {
                drawLines(guideLineDimension, frameGuidelinePaint)
            }

            restore()


        }
    }

    /**
     * Resets the crop frame to default size and position with animation.
     */
    override fun resetPaint() {
        startMatrix.set(canvasMatrix)
        endMatrix.reset()
        startRect.set(frameRect)
        normalizeCropper(limitRect.width(), limitRect.height(), endRect)
        animator.start()
    }

    /**
     * Updates all drawing dimensions after crop frame changes.
     * Recalculates handle positions, touch areas, and guidelines.
     */
    protected open fun setDrawingDimensions() {
        createHandleBarsDimensions(frameRect)

        mapOfHandleBars = createHandleBarPointMap(frameRect)

        createGuideLines(frameRect)
    }

    /**
     * This method creates guidelines in given rectangle.
     * @param frame The frame that guidelines will be drawn inside it.
     */
    protected open fun createGuideLines(frame: RectF) {
        return frame.run {
            val offsetFromCenterX = (width() * 0.165f)
            val frameCenterX = centerX()
            val offsetFromCenterY = (height() * 0.165f)
            val frameCenterY = centerY()

            guideLineDimension[0] = frameCenterX - offsetFromCenterX
            guideLineDimension[1] = top
            guideLineDimension[2] = frameCenterX - offsetFromCenterX
            guideLineDimension[3] = bottom
            guideLineDimension[4] = frameCenterX + offsetFromCenterX
            guideLineDimension[5] = top
            guideLineDimension[6] = frameCenterX + offsetFromCenterX
            guideLineDimension[7] = bottom
            guideLineDimension[8] = left
            guideLineDimension[9] = frameCenterY - offsetFromCenterY
            guideLineDimension[10] = right
            guideLineDimension[11] = frameCenterY - offsetFromCenterY
            guideLineDimension[12] = left
            guideLineDimension[13] = frameCenterY + offsetFromCenterY
            guideLineDimension[14] = right
            guideLineDimension[15] = frameCenterY + offsetFromCenterY
        }
    }

    /**
     * Figures which handle bar is responsible for the current point in screen.
     * If there are no handle bars in area of touch this method return null.
     * @param point Represents the points that's been touched.
     * @return Returns the handle bar responsible for given point (nullable).
     */
    protected open fun figureOutWhichHandleIsInRangeOfEvent(point: PointF): HandleBar? {
        // Iterate over handle bar points and figure where the touch is located and which handle bar is touched.
        for (pair in mapOfHandleBars.keys) if ((point.x in pair.first.x..pair.second.x) && (point.y in pair.first.y..pair.second.y)) return mapOfHandleBars[pair]
        return null
    }

    /**
     * Calculates the positions that handle bars should locate.
     * Creates the visual resize handles at corners and edges of crop frame.
     * @param frame The rectangle the represents the overlay window.
     * @return Returns a [FloatArray] representing the location of lines that should be drawn on screen.
     */
    protected open fun createHandleBarsDimensions(frame: RectF) {
        frame.run {

            val frameCenterX = centerX()
            val frameCenterY = centerY()
            val handleBarSizeX = width() / 10
            val handleBarSizeY = height() / 10

            val offset = context.dp(1)
            val leftOffset = left + offset
            val topOffset = top + offset
            val rightOffset = right - offset
            val bottomOffset = bottom - offset

            frameHandleBar[0] = leftOffset
            frameHandleBar[1] = frameCenterY + handleBarSizeY
            frameHandleBar[2] = leftOffset
            frameHandleBar[3] = frameCenterY - handleBarSizeY
            frameHandleBar[4] = frameCenterX - handleBarSizeX
            frameHandleBar[5] = bottomOffset
            frameHandleBar[6] = frameCenterX + handleBarSizeX
            frameHandleBar[7] = bottomOffset
            frameHandleBar[8] = rightOffset
            frameHandleBar[9] = frameCenterY - handleBarSizeY
            frameHandleBar[10] = rightOffset
            frameHandleBar[11] = frameCenterY + handleBarSizeY
            frameHandleBar[12] = frameCenterX - handleBarSizeX
            frameHandleBar[13] = topOffset
            frameHandleBar[14] = frameCenterX + handleBarSizeX
            frameHandleBar[15] = topOffset
            frameHandleBar[16] = leftOffset
            frameHandleBar[17] = topOffset
            frameHandleBar[18] = leftOffset + handleBarSizeX
            frameHandleBar[19] = topOffset
            frameHandleBar[20] = leftOffset
            frameHandleBar[21] = topOffset
            frameHandleBar[22] = leftOffset
            frameHandleBar[23] = topOffset + handleBarSizeY
            frameHandleBar[24] = rightOffset - handleBarSizeX
            frameHandleBar[25] = topOffset
            frameHandleBar[26] = rightOffset
            frameHandleBar[27] = topOffset
            frameHandleBar[28] = rightOffset
            frameHandleBar[29] = topOffset
            frameHandleBar[30] = rightOffset
            frameHandleBar[31] = topOffset + handleBarSizeY
            frameHandleBar[32] = leftOffset
            frameHandleBar[33] = bottomOffset - handleBarSizeY
            frameHandleBar[34] = leftOffset
            frameHandleBar[35] = bottomOffset
            frameHandleBar[36] = leftOffset
            frameHandleBar[37] = bottomOffset
            frameHandleBar[38] = leftOffset + handleBarSizeX
            frameHandleBar[39] = bottomOffset
            frameHandleBar[40] = rightOffset - handleBarSizeX
            frameHandleBar[41] = bottomOffset
            frameHandleBar[42] = rightOffset
            frameHandleBar[43] = bottomOffset
            frameHandleBar[44] = rightOffset
            frameHandleBar[45] = bottomOffset
            frameHandleBar[46] = rightOffset
            frameHandleBar[47] = bottomOffset - handleBarSizeY
        }
    }

    /**
     * This method figures the touch area of each handle bar.
     * Creates extended touch areas around each handle for easier user interaction.
     * @param frame The rectangle the represents the overlay window.
     * @return Returns a map of handle bar area range and [HandleBar] itself.
     */
    protected open fun createHandleBarPointMap(
        frame: RectF
    ): MutableMap<Pair<PointF, PointF>, HandleBar> {

        return frame.run {
            // Store areas that handle are located + excess area.
            mutableMapOf<Pair<PointF, PointF>, HandleBar>(
                Pair(
                    Pair(
                        PointF(left - excessTouchArea, top - excessTouchArea),
                        PointF(left + excessTouchArea, top + excessTouchArea)
                    ), HandleBar.TOP_LEFT
                ),
                Pair(
                    Pair(
                        PointF(centerX() - excessTouchAreaHalf, top - excessTouchArea),
                        PointF(centerX() + excessTouchAreaHalf, top + excessTouchArea)
                    ), HandleBar.TOP
                ),
                Pair(
                    Pair(
                        PointF(
                            right - excessTouchArea, centerY() - excessTouchAreaHalf
                        ), PointF(right + excessTouchArea, centerY() + excessTouchAreaHalf)
                    ), HandleBar.RIGHT
                ),
                Pair(
                    Pair(
                        PointF(right - excessTouchArea, top - excessTouchArea),
                        PointF(right + excessTouchArea, top + excessTouchArea)
                    ), HandleBar.TOP_RIGHT
                ),
                Pair(
                    Pair(
                        PointF(left - excessTouchArea, centerY() - excessTouchAreaHalf),
                        PointF(left + excessTouchArea, centerY() + excessTouchAreaHalf)
                    ), HandleBar.LEFT
                ),
                Pair(
                    Pair(
                        PointF(left - excessTouchArea, bottom - excessTouchArea),
                        PointF(left + excessTouchArea, bottom + excessTouchArea)
                    ), HandleBar.BOTTOM_LEFT
                ),
                Pair(
                    Pair(
                        PointF(
                            centerX() - excessTouchAreaHalf, bottom - excessTouchArea
                        ), PointF(centerX() + excessTouchAreaHalf, bottom + excessTouchArea)
                    ), HandleBar.BOTTOM
                ),
                Pair(
                    Pair(
                        PointF(
                            right - excessTouchArea, bottom - excessTouchArea
                        ), PointF(right + excessTouchArea, bottom + excessTouchArea)
                    ), HandleBar.BOTTOM_RIGHT
                ),
            )
        }
    }

    /**
     * Changes the aspect ratio constraint for the crop frame.
     * @param newAspectRatio The new aspect ratio to apply
     * @param force Whether to force the change even if ratios are similar
     */
    open fun setAspectRatio(newAspectRatio: AspectRatio, force: Boolean = false) {
        if (!force && newAspectRatio is AspectRatioLocked && aspectRatio is AspectRatioLocked && (aspectRatio as AspectRatioLocked).getRatio() == newAspectRatio.getRatio()) {
            return
        }

        if (newAspectRatio is AspectRatioFree && aspectRatio is AspectRatioFree) return

        aspectRatio = newAspectRatio

        startRect.set(frameRect)

        normalizeCropper(limitRect.width(), limitRect.height(), frameRect)

        if (this::context.isInitialized) {
            fitCropperInsideLayer(false)
        } else {
            setDrawingDimensions()
        }

    }

    /**
     * Creates a new bitmap containing only the cropped portion of the image.
     * @return Cropped bitmap or null if no layer is selected
     */
    open fun crop(): Bitmap? {
        selectedLayer?.let { layer ->

            fitInsideMatrix.invert(startMatrix)

            tempRectF.set(frameRect)
            startMatrix.mapRect(tempRectF)

            val croppedBitmap = createBitmap(
                tempRectF.width().toInt(),
                tempRectF.height().toInt(),
                layer.bitmap.config ?: Bitmap.Config.ARGB_8888
            )

            cropCanvas.run {
                setBitmap(croppedBitmap)

                save()

                translate(-tempRectF.left, -tempRectF.top)

                concat(startMatrix)

                clipRect(frameRect)

                save()

                inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)

                concat(inverseMatrix)

                drawBitmap(layer.bitmap, 0f, 0f, framePaint)

                restoreToCount(1)
            }
            return croppedBitmap
        }
        return null
    }

    /**
     * Clips the current layer to only show content within the crop frame.
     * This modifies the existing layer bitmap directly.
     */
    open fun clip() {
        selectedLayer?.let { layer ->
            cropCanvas.run {
                val layerBitmapCopy =
                    layer.bitmap.copy(layer.bitmap.config ?: Bitmap.Config.ARGB_8888, true)

                layer.bitmap.eraseColor(Color.TRANSPARENT)

                setBitmap(layer.bitmap)
                inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)
                inverseMatrix.invert(inverseMatrix)
                save()
                concat(inverseMatrix)
                drawRect(frameRect, framePaint.apply {
                    style = Paint.Style.FILL
                })
                restore()

                drawBitmap(layerBitmapCopy, 0f, 0f, framePaint.apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                })

                framePaint.style = Paint.Style.STROKE
                framePaint.xfermode = null

                sendMessage(PainterMessage.INVALIDATE)
                sendMessage(PainterMessage.SAVE_HISTORY)
            }
        }
    }

    /**
     * Sets the crop frame to a specific rectangle.
     * @param rect Target rectangle in image coordinates
     * @param fit Whether to automatically fit frame within bounds
     * @param animate Whether to animate the transition
     * @param onEnd Callback executed when operation completes
     */
    open fun setFrame(
        rect: Rect,
        fit: Boolean = false,
        animate: Boolean = true,
        onEnd: () -> Unit = {}
    ) {
        inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)
        tempRectF.set(rect)
        inverseMatrix.mapRect(tempRectF)
        frameRect.set(tempRectF)

        when {
            fit && animate -> {
                fitCropperInsideLayer(animate = true, setRect = false)
                animator.doOnEnd {
                    onEnd()
                    animator.listeners.clear()
                }
            }

            fit && !animate -> {
                fitCropperInsideLayer(animate = false, setRect = true)
                setDrawingDimensions()
                onEnd()
                sendMessage(PainterMessage.INVALIDATE)
            }

            else -> {
                setDrawingDimensions()
                onEnd()
                sendMessage(PainterMessage.INVALIDATE)
            }
        }
    }

    override fun doesHandleHistory(): Boolean {
        return true
    }

    override fun doesNeedTouchSlope(): Boolean {
        return false
    }

    /**
     * Handles view size changes by updating limit bounds and crop frame position.
     */
    override fun onSizeChanged(newBounds: RectF, clipBounds: Rect, changeMatrix: Matrix) {
        /*
            Previous method:
            canvasMatrix.postConcat(changeMatrix)
            fitCropperInsideLayer(animate = false, setRect = true, setMatrix = true)
         */

        tempRectF.set(clipBounds)
        fitInsideMatrix.mapRect(tempRectF)
        limitRect.set(tempRectF)
        changeMatrix.mapRect(frameRect)
        setDrawingDimensions()
        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Converts rectangle corners to coordinate array format.
     * @param rect Source rectangle
     * @param dstArray Destination array for coordinates
     */
    protected open fun setBoundsVariablesFromRect(rect: RectF, dstArray: FloatArray) {
        dstArray[0] = rect.left
        dstArray[1] = rect.top
        dstArray[2] = rect.right
        dstArray[3] = rect.top
        dstArray[4] = rect.left
        dstArray[5] = rect.bottom
        dstArray[6] = rect.right
        dstArray[7] = rect.bottom
    }

    override fun onLayerChanged(layer: PaintLayer?) {
        selectedLayer = layer
    }
}
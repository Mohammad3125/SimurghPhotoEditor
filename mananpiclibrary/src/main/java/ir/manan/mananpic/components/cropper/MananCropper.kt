package ir.manan.mananpic.components.cropper

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.text.isDigitsOnly
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import ir.manan.mananpic.R
import ir.manan.mananpic.components.cropper.HandleBar.BOTTOM
import ir.manan.mananpic.components.cropper.HandleBar.BOTTOM_LEFT
import ir.manan.mananpic.components.cropper.HandleBar.BOTTOM_RIGHT
import ir.manan.mananpic.components.cropper.HandleBar.LEFT
import ir.manan.mananpic.components.cropper.HandleBar.RIGHT
import ir.manan.mananpic.components.cropper.HandleBar.TOP
import ir.manan.mananpic.components.cropper.HandleBar.TOP_LEFT
import ir.manan.mananpic.components.cropper.HandleBar.TOP_RIGHT
import ir.manan.mananpic.components.cropper.aspect_ratios.AspectRatioFree
import ir.manan.mananpic.components.cropper.aspect_ratios.AspectRatioLocked
import ir.manan.mananpic.components.imageviews.MananGestureImageView
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.detectors.MoveDetector
import kotlin.math.roundToInt

/**
 * A Component that draws guidelines and a resizable rectangle with shadows for unselected areas
 * representing an area of interest for image to crop.
 * This class is also responsible for cropping the image.
 */
class MananCropper(context: Context, attr: AttributeSet?) : MananGestureImageView(context, attr) {

    // Paint used for drawing the frame.
    private val framePaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE
        }
    }

    var frameColor = Color.DKGRAY
        set(value) {
            framePaint.color = value
            field = value
        }
    var frameStrokeWidth = dp(2)
        set(value) {
            framePaint.strokeWidth = value
            field = value
        }

    // Paint used for drawing guidelines.
    private val frameGuidelinePaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE
        }
    }

    var guidelineStrokeWidth = dp(1)
        set(value) {
            frameGuidelinePaint.strokeWidth = value
            field = value
        }
    var guidelineColor = Color.DKGRAY
        set(value) {
            frameGuidelinePaint.color = value
            field = value
        }

    // Determines if guideline should be drawn or not.
    var isDrawGuidelineEnabled = true


    // Paint used for drawing the shadows around frame.
    private val frameShadowsPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    var backgroundShadowColor = Color.BLACK
        set(value) {
            frameShadowsPaint.color = value
            field = value
        }
    var backgroundShadowAlpha = 255 / 3
        set(value) {
            frameShadowsPaint.alpha = value
            field = value
        }

    // Paint used for drawing handle bars.
    private val handleBarPaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE

        }
    }

    var handleBarStrokeWidth = dp(3)
        set(value) {
            handleBarPaint.strokeWidth = value
            field = value
        }
    var handleBarColor = Color.DKGRAY
        set(value) {
            handleBarPaint.color = value
            field = value
        }
    var handleBarCornerType = Paint.Cap.ROUND
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
    var selectedHandleBarColor = handleBarColor


    // Rectangle that represents the crop frame.
    private val frameRect by lazy {
        RectF()
    }


    val cropperDimensions: RectF
        get() = frameRect

    private val allocRectF by lazy {
        RectF()
    }


    // List of rectangles representing shadows around frame.
    private var frameShadows: List<RectF> = listOf(RectF(), RectF(), RectF(), RectF())

    // Handle bar dimensions for drawing.
    private var frameHandleBar = FloatArray(0)

    // Dimension of guidelines.
    private var guideLineDimension = FloatArray(0)

    // Map of points on frame that represents each handle bar like LEFT, TOP-LEFT and etc...
    private lateinit var mapOfHandleBars: MutableMap<Pair<PointF, PointF>, HandleBar>

    // Later in code determines which handle bar has been pressed.
    private var handleBar: HandleBar? = null

    private lateinit var limitRect: RectF

    // Variable to save aspect ratio of cropper.
    private var aspectRatio: AspectRatio = AspectRatioFree()

    // Later will be used to animate the change of aspect ration (if user changes it).
    private val rectAnimator by lazy {
        ValueAnimator().apply {
            interpolator = FastOutSlowInInterpolator()
            duration = 300

            addUpdateListener {
                val widthValue = getAnimatedValue("width") as Float
                val heightValue = getAnimatedValue("height") as Float
                val leftValue = getAnimatedValue("left") as Float
                val topValue = getAnimatedValue("top") as Float

                initializeDrawingObjects(leftValue, topValue, widthValue, heightValue)
                invalidate()
            }
        }
    }

    private var excessTouchArea = dp(40)
    private var excessTouchAreaHalf = excessTouchArea / 2

    init {
        moveDetector = MoveDetector(1, this)

        context.theme.obtainStyledAttributes(attr, R.styleable.MananCropper, 0, 0).apply {
            try {
                guidelineColor =
                    getColor(R.styleable.MananCropper_guidelineColor, Color.DKGRAY)

                guidelineStrokeWidth =
                    getDimension(
                        R.styleable.MananCropper_guidelineStrokeWidth,
                        guidelineStrokeWidth
                    )

                isDrawGuidelineEnabled =
                    getBoolean(R.styleable.MananCropper_isGuidelineEnabled, true)

                frameColor = getColor(R.styleable.MananCropper_frameColor, Color.DKGRAY)

                frameStrokeWidth =
                    getDimension(R.styleable.MananCropper_frameStrokeWidth, frameStrokeWidth)

                handleBarColor =
                    getColor(R.styleable.MananCropper_handleBarColor, Color.DKGRAY)

                handleBarStrokeWidth =
                    getDimension(
                        R.styleable.MananCropper_handleBarStrokeWidth,
                        handleBarStrokeWidth
                    )

                handleBarCornerType =
                    Paint.Cap.values()[getInt(
                        R.styleable.MananCropper_handleBarStrokeType,
                        Paint.Cap.ROUND.ordinal
                    )]

                backgroundShadowColor =
                    getColor(
                        R.styleable.MananCropper_backgroundShadowColor,
                        Color.BLACK
                    )

                val shadowAlpha =
                    getInt(R.styleable.MananCropper_backgroundShadowAlpha, 255 / 3)

                backgroundShadowAlpha =
                    if (shadowAlpha > 255) 255 else if (shadowAlpha < 0) 0 else shadowAlpha

                aspectRatio =
                    convertStringToAspectRatio(getString(R.styleable.MananCropper_aspectRatio))

                selectedHandleBarColor =
                    getColor(R.styleable.MananCropper_selectedHandlerBarColor, handleBarColor)

            } finally {
                recycle()
            }
        }
    }

    override fun onImageLaidOut() {
        super.onImageLaidOut()

        // Initialize limit rect that later will be used to limit resizing.
        limitRect = RectF(leftEdge, topEdge, rightEdge, bottomEdge)

        // Initialize drawing objects after the width and height has been determined.
        val pair = aspectRatio.normalizeAspectRatio(
            finalWidth,
            finalHeight
        )
        initializeDrawingObjects(leftEdge, topEdge, pair.first + leftEdge, pair.second + topEdge)
    }

    /**
     * Initialize or reinitialize objects associated with drawing on screen.
     * @param width Width of objects.
     * @param height Height of objects.
     */
    private fun initializeDrawingObjects(left: Float, top: Float, width: Float, height: Float) {
        frameRect.set(left, top, width, height)
        setDrawingDimensions()
    }

    private fun setDrawingDimensions() {
        frameHandleBar =
            createHandleBarsDimensions(frameRect)

        mapOfHandleBars =
            createHandleBarPointMap(frameRect)

        createFrameShadows(frameRect)

        guideLineDimension =
            createGuideLines(frameRect)
    }

    /**
     * This method creates guidelines in given rectangle.
     * @param frame The frame that guidelines will be drawn inside it.
     */
    private fun createGuideLines(frame: RectF): FloatArray {
        return frame.run {
            val offsetFromCenterX = (width() * 0.165f)
            val frameCenterX = centerX()
            val offsetFromCenterY = (height() * 0.165f)
            val frameCenterY = centerY()

            floatArrayOf(
                // Vertical left line.
                frameCenterX - offsetFromCenterX,
                top,
                frameCenterX - offsetFromCenterX,
                bottom,

                // Vertical right line.
                frameCenterX + offsetFromCenterX,
                top,
                frameCenterX + offsetFromCenterX,
                bottom,

                // Horizontal top line.
                left,
                frameCenterY - offsetFromCenterY,
                right,
                frameCenterY - offsetFromCenterY,

                // Horizontal bottom line.
                left,
                frameCenterY + offsetFromCenterY,
                right,
                frameCenterY + offsetFromCenterY
            )
        }
    }

    /**
     * Creates the shadows around the overlay windows.
     * @param frame The rectangle the represents the overlay window.
     * @return List of rectangles that surround the overlay window.
     */
    private fun createFrameShadows(frame: RectF) {
        frame.run {
            frameShadows[0].set(leftEdge, topEdge, rightEdge, top)
            frameShadows[1].set(leftEdge, top, left, bottom)
            frameShadows[2].set(right, top, rightEdge, bottom)
            frameShadows[3].set(leftEdge, bottom, rightEdge, bottomEdge)
        }
    }

    override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {

        // Figure out which handle bar is in range of the event.
        handleBar = figureOutWhichHandleIsInRangeOfEvent(
            PointF(
                initialX,
                initialY
            )
        )

        return true
    }

    override fun onMove(dx: Float, dy: Float): Boolean {
        // Create a new rectangle to change it's dimensions indirectly to later be able to validate it's size.
        val changedRect =
            aspectRatio.resize(allocRectF.apply {
                set(frameRect)
            }, handleBar, dx, dy)

        if (handleBar != null) {
            // Change color of handle bar indicating that user is changing size of cropper.
            handleBarPaint.color = selectedHandleBarColor

            // After validation set the frame's dimensions.
            frameRect.set(
                aspectRatio.validate(
                    frameRect,
                    changedRect,
                    limitRect
                )
            )

        } else {
            // If non of handle bars has been pressed, move the rectangle inside the view.
            frameRect.run {

                // Offset the rectangle.
                offset(
                    dx,
                    dy
                )

                // Validate that the rectangle is inside the view's bounds.
                val finalX =
                    when {
                        right > rightEdge -> rightEdge - right
                        left < leftEdge -> leftEdge - left
                        else -> 0f
                    }

                val finalY =
                    when {
                        bottom > bottomEdge -> bottomEdge - bottom
                        top < topEdge -> topEdge - top
                        else -> 0f
                    }

                // If rectangle wasn't inside bounds, offset them back.
                offset(
                    finalX,
                    finalY
                )
            }
        }
        // Reset the shadows,handle bar dimensions, handle bar map and etc based on new frame size.
        setDrawingDimensions()
        invalidate()

        return true
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        handleBarPaint.color = handleBarColor
        invalidate()
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return false
    }

    override fun onFling(
        e1: MotionEvent?,
        p0: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return false
    }

    override fun onDraw(canvas: Canvas) {
        // Draw image.
        super.onDraw(canvas)

        canvas.run {
            // Draw frame.
            drawRect(frameRect, framePaint)

            // Draw handle bars.
            drawLines(frameHandleBar, handleBarPaint)

            // Draw guidelines.
            if (isDrawGuidelineEnabled)
                drawLines(guideLineDimension, frameGuidelinePaint)

            // Draw shadows around frame.
            for (rect in frameShadows)
                drawRect(rect, frameShadowsPaint)
        }
    }

    /**
     * Figures which handle bar is responsible for the current point in screen.
     * If there are no handle bars in area of touch this method return null.
     * @param point Represents the points that's been touched.
     * @return Returns the handle bar responsible for given point (nullable).
     */
    private fun figureOutWhichHandleIsInRangeOfEvent(point: PointF): HandleBar? {
        // Iterate over handle bar points and figure where the touch is located and which handle bar is touched.
        for (pair in mapOfHandleBars.keys)
            if ((point.x in pair.first.x..pair.second.x) && (point.y in pair.first.y..pair.second.y))
                return mapOfHandleBars[pair]
        return null
    }

    /**
     * Calculates the positions that handle bars should locate.
     * @param frame The rectangle the represents the overlay window.
     * @return Returns a [FloatArray] representing the location of lines that should be drawn on screen.
     */
    private fun createHandleBarsDimensions(frame: RectF): FloatArray {
        frame.run {

            val frameCenterX = centerX()
            val frameCenterY = centerY()
            val handleBarSizeX = width() / 10
            val handleBarSizeY = height() / 10

            val offset = dp(1)
            val leftOffset = left + offset
            val topOffset = top + offset
            val rightOffset = right - offset
            val bottomOffset = bottom - offset
            return floatArrayOf(
                // Left.
                leftOffset,
                frameCenterY + handleBarSizeY,
                leftOffset,
                frameCenterY - handleBarSizeY,

                // Bottom.
                frameCenterX - handleBarSizeX,
                bottomOffset,
                frameCenterX + handleBarSizeX,
                bottomOffset,

                // Right.
                rightOffset,
                frameCenterY - handleBarSizeY,
                rightOffset,
                frameCenterY + handleBarSizeY,

                // Top.
                frameCenterX - handleBarSizeX,
                topOffset,
                frameCenterX + handleBarSizeX,
                topOffset,

                // Top left corner.
                leftOffset,
                topOffset,
                leftOffset + handleBarSizeX,
                topOffset,

                leftOffset,
                topOffset,
                leftOffset,
                topOffset + handleBarSizeY,

                // Top right corner.
                rightOffset - handleBarSizeX,
                topOffset,
                rightOffset,
                topOffset,

                rightOffset,
                topOffset,
                rightOffset,
                topOffset + handleBarSizeY,

                // Bottom left corner.
                leftOffset,
                bottomOffset - handleBarSizeY,
                leftOffset,
                bottomOffset,

                leftOffset,
                bottomOffset,
                leftOffset + handleBarSizeX,
                bottomOffset,


                // Bottom right corner.
                rightOffset - handleBarSizeX,
                bottomOffset,
                rightOffset,
                bottomOffset,


                rightOffset,
                bottomOffset,
                rightOffset,
                bottomOffset - handleBarSizeY
            )
        }
    }

    /**
     * This method figures the touch area of each handle bar.
     * @param frame The rectangle the represents the overlay window.
     * @return Returns a map of handle bar area range and [HandleBar] itself.
     */
    private fun createHandleBarPointMap(
        frame: RectF
    ): MutableMap<Pair<PointF, PointF>, HandleBar> {

        return frame.run {
            // Store areas that handle are located + excess area.
            mutableMapOf<Pair<PointF, PointF>, HandleBar>(
                Pair(
                    Pair(
                        PointF(left - excessTouchArea, top - excessTouchArea),
                        PointF(left + excessTouchArea, top + excessTouchArea)
                    ),
                    TOP_LEFT
                ),
                Pair(
                    Pair(
                        PointF(centerX() - excessTouchAreaHalf, top - excessTouchArea),
                        PointF(centerX() + excessTouchAreaHalf, top + excessTouchArea)
                    ), TOP
                ),
                Pair(
                    Pair(
                        PointF(
                            right - excessTouchArea,
                            centerY() - excessTouchAreaHalf
                        ),
                        PointF(right + excessTouchArea, centerY() + excessTouchAreaHalf)
                    ), RIGHT
                ),
                Pair(
                    Pair(
                        PointF(right - excessTouchArea, top - excessTouchArea),
                        PointF(right + excessTouchArea, top + excessTouchArea)
                    ),
                    TOP_RIGHT
                ),
                Pair(
                    Pair(
                        PointF(left - excessTouchArea, centerY() - excessTouchAreaHalf),
                        PointF(left + excessTouchArea, centerY() + excessTouchAreaHalf)
                    ), LEFT
                ),
                Pair(
                    Pair(
                        PointF(left - excessTouchArea, bottom - excessTouchArea),
                        PointF(left + excessTouchArea, bottom + excessTouchArea)
                    ),
                    BOTTOM_LEFT
                ),
                Pair(
                    Pair(
                        PointF(
                            centerX() - excessTouchAreaHalf,
                            bottom - excessTouchArea
                        ),
                        PointF(centerX() + excessTouchAreaHalf, bottom + excessTouchArea)
                    ),
                    BOTTOM
                ),
                Pair(
                    Pair(
                        PointF(
                            right - excessTouchArea,
                            bottom - excessTouchArea
                        ),
                        PointF(right + excessTouchArea, bottom + excessTouchArea)
                    ), BOTTOM_RIGHT
                ),
            )
        }
    }

    fun setAspectRatio(newAspectRatio: AspectRatio) {
        if (newAspectRatio is AspectRatioLocked && aspectRatio is AspectRatioLocked)
            if ((aspectRatio as AspectRatioLocked).getRatio() == newAspectRatio.getRatio())
                return

        if (newAspectRatio is AspectRatioFree && aspectRatio is AspectRatioFree) return

        aspectRatio = newAspectRatio

        val pair = aspectRatio.normalizeAspectRatio(finalWidth, finalHeight)

        // Animate the change of drawing objects.
        rectAnimator.run {
            setValues(
                PropertyValuesHolder.ofFloat("width", frameRect.right, pair.first + leftEdge),
                PropertyValuesHolder.ofFloat("height", frameRect.bottom, pair.second + topEdge),
                PropertyValuesHolder.ofFloat("left", frameRect.left, leftEdge),
                PropertyValuesHolder.ofFloat("top", frameRect.top, topEdge)
            )
            start()
        }

    }

    /**
     * Crops image with current dimension of cropper.
     * @throws [IllegalStateException] if drawable is null.
     * @return Cropped bitmap.
     */
    fun cropImage(): Bitmap {
        frameRect.run {

            if (bitmap == null) throw IllegalStateException("cannot crop a null bitmap")

            // Calculate bounds of drawable by dividing it by initial scale of current image.
            val le = (left - leftEdge)
            val te = (top - topEdge)
            val l = (le / matrixScale)
            val t = (te / matrixScale)
            var r = ((right - le - leftEdge) / matrixScale)
            var b = ((bottom - te - topEdge) / matrixScale)

            if (r > rightEdge) r = rightEdge
            if (b > bottomEdge) b = bottomEdge

            return Bitmap.createBitmap(
                bitmap!!,
                l.roundToInt(), t.roundToInt(), r.roundToInt(), b.roundToInt()
            )
        }
    }

    companion object {
        /**
         * This method converts a string representation of aspect-ratio into aspect ratio class.
         * String SHOULD be in this format: either "FREE" or ratio of width to height separated with hyphen like "16-9".
         * @param aspectRatioString String to convert it into [AspectRatio]. if null returns [AspectRatioFree].
         */
        fun convertStringToAspectRatio(aspectRatioString: String?): AspectRatio {
            // If string is null or it's value is "FREE" return 'AspectRatioFree'.
            if (aspectRatioString == null || (aspectRatioString.trim()
                    .equals("FREE", true))
            ) return AspectRatioFree()
            else {
                // Trim the string and split it with hyphen.
                val listRatios = aspectRatioString.trim().split("-")

                // If either it's size is greater than 2 or it's empty or null then this is not a valid string.
                if (listRatios.size > 2 || listRatios.isEmpty()) return AspectRatioFree()

                // Check that strings in list are digits only.
                listRatios.forEach { string -> if (!string.isDigitsOnly()) return AspectRatioFree() }

                // Finally return 'AspectRatioLocked' with given width and height ratio.
                return AspectRatioLocked(listRatios[0].toFloat(), listRatios[1].toFloat())

            }
        }
    }
}
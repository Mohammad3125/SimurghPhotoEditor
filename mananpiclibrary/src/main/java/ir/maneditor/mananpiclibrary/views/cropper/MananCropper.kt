package ir.maneditor.mananpiclibrary.views.cropper

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.isDigitsOnly
import ir.maneditor.mananpiclibrary.R
import ir.maneditor.mananpiclibrary.utils.dp
import ir.maneditor.mananpiclibrary.utils.invalidateAfter
import ir.maneditor.mananpiclibrary.views.cropper.HandleBar.*
import ir.maneditor.mananpiclibrary.views.cropper.aspect_ratios.AspectRatioFree
import ir.maneditor.mananpiclibrary.views.cropper.aspect_ratios.AspectRatioLocked
import kotlin.math.roundToInt

/**
 * A resizable view that shows guidelines and let user define an area of interest to crop images and etc....
 */
class MananCropper(context: Context, attr: AttributeSet?) : AppCompatImageView(context, attr) {

    // Paint used for drawing the frame.
    private val framePaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE
        }
    }

    var frameColor = 0
        set(value) {
            framePaint.color = value
            field = value
        }
    var frameStrokeWidth = 0f
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

    var guidelineStrokeWidth = 0f
        set(value) {
            frameGuidelinePaint.strokeWidth = value
            field = value
        }
    var guidelineColor = 0
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

    var backgroundShadowColor = 0
        set(value) {
            frameShadowsPaint.color = value
            field = value
        }
    var backgroundShadowAlpha = 0
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

    var handleBarStrokeWidth = 0f
        set(value) {
            handleBarPaint.strokeWidth = value
            field = value
        }
    var handleBarColor = 0
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


    // Rectangle that represents the crop frame.
    private lateinit var frameRect: RectF
    val cropperDimensions: RectF
        get() = frameRect


    // List of rectangles representing shadows around frame.
    private lateinit var frameShadows: List<RectF>

    // Handle bar dimensions for drawing.
    private lateinit var frameHandleBar: FloatArray

    // Dimension of guidelines.
    private lateinit var guideLineDimension: FloatArray

    // Map of points on frame that represents each handle bar like LEFT, TOP-LEFT and etc...
    private lateinit var mapOfHandleBars: MutableMap<Pair<PointF, PointF>, HandleBar>

    // Later in code determines which handle bar has been pressed.
    private var handleBar: HandleBar? = null

    // Used for saving initial touch points.
    private var initialX = 0f
    private var initialY = 0f

    // Variable to save aspect ratio of cropper.
    private var aspectRatio: AspectRatio = AspectRatioFree()

    init {
        context.theme.obtainStyledAttributes(attr, R.styleable.MananCropper, 0, 0).apply {
            try {
                guidelineColor =
                    getColor(R.styleable.MananCropper_guidelineColor, Color.DKGRAY)

                guidelineStrokeWidth =
                    getDimension(R.styleable.MananCropper_guidelineStrokeWidth, 1.dp)

                isDrawGuidelineEnabled =
                    getBoolean(R.styleable.MananCropper_isGuidelineEnabled, true)

                frameColor = getColor(R.styleable.MananCropper_frameColor, Color.DKGRAY)

                frameStrokeWidth = getDimension(R.styleable.MananCropper_frameStrokeWidth, 2.dp)

                handleBarColor =
                    getColor(R.styleable.MananCropper_handleBarColor, Color.DKGRAY)

                handleBarStrokeWidth =
                    getDimension(R.styleable.MananCropper_handleBarStrokeWidth, 3.dp)

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

            } finally {
                recycle()
            }
        }
        adjustViewBounds = true
    }


    /**
     * This method converts a string representation of aspect-ratio into aspect ratio class.
     * String SHOULD be in this format: either "FREE" or ratio of width to height separated with hyphen like "16-9".
     * @param aspectRatioString String to convert it into [AspectRatio]. if null returns [AspectRatioFree]
     */
    fun convertStringToAspectRatio(aspectRatioString: String?): AspectRatio {
        // If string is null or it's value is "FREE" return 'AspectRatioFree'.
        if (aspectRatioString == null || (aspectRatioString.trim() == "FREE")) return AspectRatioFree()
        else {
            // Trim the string and split it with hyphen.
            val listRatios = aspectRatioString.run {
                trim()
                split("-")
            }
            // If either it's size is greater than 2 or it's empty or null then this is not a valid string.
            if (listRatios.size > 2 || listRatios.isNullOrEmpty()) return AspectRatioFree()

            // Check that strings in list are digits only.
            for (string in listRatios)
                if (!string.isDigitsOnly()) return AspectRatioFree()

            // Finally return 'AspectRatioLocked' with given width and height ratio.
            return AspectRatioLocked(listRatios[0].toFloat(), listRatios[1].toFloat())

        }

    }

    // Secondary constructor to initialize the view programmatically.
    constructor(context: Context, width: Int, height: Int) : this(context, null) {
        layoutParams = ViewGroup.LayoutParams(width, height)
        initializeDrawingObjects(width, height)
    }

    fun setAspectRatio(newAspectRatio: AspectRatio) {
        if (newAspectRatio is AspectRatioLocked && aspectRatio is AspectRatioLocked)
            if ((aspectRatio as AspectRatioLocked).getRatio() == newAspectRatio.getRatio())
                return

        if (newAspectRatio is AspectRatioFree && aspectRatio is AspectRatioFree) return

        aspectRatio = newAspectRatio
        requestLayout()
    }

    /**
     * Initialize or reinitialize objects associated with drawing on screen.
     * @param width Width of objects.
     * @param height Height of objects.
     */
    private fun initializeDrawingObjects(width: Int, height: Int) {
        frameRect = createFrameRect(width.toFloat(), height.toFloat())
        setDrawingDimensions()
    }

    private fun setDrawingDimensions() {
        frameHandleBar =
            createHandleBarsDimensions(frameRect)

        mapOfHandleBars =
            createHandleBarPointMap(frameRect)

        frameShadows =
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
    private fun createFrameShadows(frame: RectF): List<RectF> {
        return frame.run {
            listOf(
                // Top shadow.
                RectF(0f, 0f, width.toFloat(), top),
                // Left shadow.
                RectF(0f, top, left, height.toFloat()),
                // Right shadow.
                RectF(right, top, width.toFloat(), bottom),
                // Bottom shadow.
                RectF(left, bottom, width.toFloat(), height.toFloat())
            )
        }
    }

    override fun onDraw(canvas: Canvas?) {
        // Draw image.
        super.onDraw(canvas)

        canvas!!.run {
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Initialize drawing objects after the width and height has been determined.
        if (aspectRatio is AspectRatioLocked) {
            val pair = (aspectRatio as AspectRatioLocked).normalizeAspectRatio(
                measuredWidth,
                measuredHeight,
                measuredWidth,
                measuredHeight
            )
            initializeDrawingObjects(pair.first, pair.second)
            return
        }
        // Initialize drawing object if aspect ratio is not locked.
        initializeDrawingObjects(measuredWidth, measuredHeight)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event!!.actionMasked) {
            MotionEvent.ACTION_DOWN -> {

                // Figure out which handle bar is in range of the event.
                handleBar = figureOutWhichHandleIsInRangeOfEvent(
                    PointF(
                        event.x,
                        event.y
                    )
                )

                // Save the initial points the user touched to later calculate the difference between
                // Initial point and ongoing event to figure out how much the view should resize.
                initialX = event.x
                initialY = event.y

                performClick()

                // Only show interest if user touched a handle bar, otherwise don't
                // request to intercept the event because the parent is going to move
                // the cropper and if we intercept it, it won't work.
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Difference between initial touch point and current ongoing event.
                val differenceY = (event.y - initialY)
                val differenceX = (event.x - initialX)


                invalidateAfter {
                    // Create a new rectangle to change it's dimensions indirectly to later be able to validate it's size
                    val changedRect =
                        aspectRatio.resize(RectF(frameRect), handleBar, differenceX, differenceY)

                    initialY += differenceY
                    initialX += differenceX

                    if (handleBar != null) {

                        // After validation set the frame's dimensions.
                        frameRect.set(
                            aspectRatio.validate(
                                frameRect,
                                changedRect,
                                width.toFloat(),
                                height.toFloat()
                            )
                        )
                    } else {
                        // If non of handle bars has been pressed, move the rectangle inside the view.
                        frameRect.run {

                            // Offset the rectangle.
                            offset(
                                differenceX,
                                differenceY
                            )

                            // Validate that the rectangle is inside the view's bounds.
                            val finalX =
                                when {
                                    right > width -> width - right
                                    left < 0f -> -left
                                    else -> 0f
                                }

                            val finalY =
                                when {
                                    bottom > height -> height - bottom
                                    top < 0f -> -top
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
                }
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                return false
            }
        }
        return true
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
     * This method creates the overlay window based on width and height of view.
     * @return Returns a [RectF] representing the overlay window.
     */
    private fun createFrameRect(width: Float, height: Float): RectF {
        return RectF(0f, 0f, width, height)
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

            val leftOffset = left + 1.dp
            val topOffset = top + 1.dp
            val rightOffset = right - 1.dp
            val bottomOffset = bottom - 1.dp
            return floatArrayOf(
                // Left
                leftOffset,
                frameCenterY + handleBarSizeY,
                leftOffset,
                frameCenterY - handleBarSizeY,

                // Bottom
                frameCenterX - handleBarSizeX,
                bottomOffset,
                frameCenterX + handleBarSizeX,
                bottomOffset,

                // Right
                rightOffset,
                frameCenterY - handleBarSizeY,
                rightOffset,
                frameCenterY + handleBarSizeY,

                // Top
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

            // Figure out some extra touch area for better touch experience.
            val excessTouchArea = 24.dp

            // Store areas that handle are located + excess area.
            mutableMapOf<Pair<PointF, PointF>, HandleBar>(
                Pair(
                    Pair(PointF(left, top), PointF(left + excessTouchArea, top + excessTouchArea)),
                    TOP_LEFT
                ),
                Pair(
                    Pair(
                        PointF(centerX() - excessTouchArea, top),
                        PointF(centerX() + excessTouchArea, top + excessTouchArea)
                    ), TOP
                ),
                Pair(
                    Pair(
                        PointF(
                            right - excessTouchArea,
                            centerY() - excessTouchArea
                        ),
                        PointF(right, centerY() + excessTouchArea)
                    ), RIGHT
                ),
                Pair(
                    Pair(
                        PointF(right - excessTouchArea, top),
                        PointF(right, top + excessTouchArea)
                    ),
                    TOP_RIGHT
                ),
                Pair(
                    Pair(
                        PointF(left, centerY() - excessTouchArea),
                        PointF(left + excessTouchArea, centerY() + excessTouchArea)
                    ), LEFT
                ),
                Pair(
                    Pair(
                        PointF(left, bottom - excessTouchArea),
                        PointF(left + excessTouchArea, bottom + excessTouchArea)
                    ),
                    BOTTOM_LEFT
                ),
                Pair(
                    Pair(
                        PointF(
                            centerX() - excessTouchArea,
                            bottom - excessTouchArea
                        ),
                        PointF(centerX() + excessTouchArea, bottom)
                    ),
                    BOTTOM
                ),
                Pair(
                    Pair(
                        PointF(
                            right - excessTouchArea,
                            bottom - excessTouchArea
                        ),
                        PointF(right, bottom)
                    ), BOTTOM_RIGHT
                ),
            )
        }
    }

    /**
     * Crops image with current dimension of cropper.
     * @return Cropped bitmap.
     */
    fun cropImage(): Bitmap {
        frameRect.run {
            return Bitmap.createBitmap(
                drawable.toBitmap(width, height),
                left.roundToInt(),
                top.roundToInt(),
                width().roundToInt(),
                height().roundToInt()
            )
        }
    }
}
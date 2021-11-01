package ir.maneditor.mananpiclibrary.views.cropper

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import ir.maneditor.mananpiclibrary.R
import ir.maneditor.mananpiclibrary.utils.dp
import ir.maneditor.mananpiclibrary.utils.invalidateAfter
import ir.maneditor.mananpiclibrary.views.cropper.HandleBar.*

/**
 * A resizable view that shows guidelines and let user define an area of interest to crop images and etc....
 */
class MananCropper(context: Context, attr: AttributeSet?) : View(context, attr) {

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

            } finally {
                recycle()
            }
        }
    }

    // Secondary constructor to initialize the view programmatically.
    constructor(context: Context, width: Int, height: Int) : this(context, null) {
        layoutParams = ViewGroup.LayoutParams(width, height)
        initializeDrawingObjects(width, height)
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
        canvas!!.run {
            // Draw frame
            drawRect(frameRect, framePaint)

            // Draw handle bars
            drawLines(frameHandleBar, handleBarPaint)

            // Draw guidelines
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


                // Set rectangle's dimensions in variables to not change them directly
                // because they need to be validated.
                var fTop = frameRect.top
                var fLeft = frameRect.left
                var fBottom = frameRect.bottom
                var fRight = frameRect.right

                invalidateAfter {
                    when (handleBar) {
                        TOP, BOTTOM -> {
                            if (handleBar == BOTTOM)
                                fBottom += differenceY
                            else fTop += differenceY
                            initialY += differenceY
                        }
                        RIGHT, LEFT -> {
                            if (handleBar == RIGHT)
                                fRight += differenceX
                            else fLeft += differenceX
                            initialX += differenceX
                        }
                        TOP_LEFT, TOP_RIGHT -> {
                            fTop += differenceY
                            if (handleBar == TOP_LEFT)
                                fLeft += differenceX
                            else fRight += differenceX

                            initialY += differenceY
                            initialX += differenceX
                        }
                        BOTTOM_LEFT, BOTTOM_RIGHT -> {
                            fBottom += differenceY
                            if (handleBar == BOTTOM_LEFT)
                                fLeft += differenceX
                            else fRight += differenceX


                            initialY += differenceY
                            initialX += differenceX
                        }
                        // If non of handle bars has been pressed, move the rectangle inside the view.
                        else -> {

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
                            // Change the initial x and y to set reference point to last touch.
                            initialY += differenceY
                            initialX += differenceX
                        }
                    }

                    if (handleBar != null) {

                        // Validate if rectangle(frame) is inside the view's bounds.
                        if (fRight > width) fRight = width.toFloat()
                        if (fLeft < 0f) fLeft = 0f

                        if (fBottom > height) fBottom = height.toFloat()
                        if (fTop < 0f) fTop = 0f

                        frameRect.run {

                            val frameWidth = frameRect.width()
                            val frameHeight = frameRect.height()

                            // 24dp for left handle clearance + 24dp for right handle and 24dp in between
                            val minWidth = 64.dp
                            // 24dp for top handle clearance + 24dp for bottom handle and 24dp in between
                            val minHeight = 64.dp

                            if (frameWidth - (fLeft - left) < minWidth)
                                fLeft = left + (frameWidth - minWidth)
                            if (frameWidth - (right - fRight) < minWidth)
                                fRight = right
                            if (frameHeight - (fTop - top) < minHeight)
                                fTop = top + (frameHeight - minHeight)
                            if (frameHeight - (bottom - fBottom) < minHeight)
                                fBottom = bottom

                            // After validation set the frame's dimensions.
                            frameRect.set(fLeft, fTop, fRight, fBottom)
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

}
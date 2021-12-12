package ir.maneditor.mananpiclibrary.components

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import ir.maneditor.mananpiclibrary.R
import ir.maneditor.mananpiclibrary.components.imageviews.MananGestureImageView
import ir.maneditor.mananpiclibrary.utils.dp
import ir.maneditor.mananpiclibrary.utils.gesture.detectors.MoveDetector
import kotlin.math.min

/**
 * Component that let user get color from a bitmap.
 */
class MananDropper(context: Context, attributeSet: AttributeSet?) :
    MananGestureImageView(context, attributeSet) {

    constructor(context: Context) : this(context, null)

    // Paint for drawing enlarged bitmap.
    private val enlargedBitmapPaint by lazy { Paint(Paint.ANTI_ALIAS_FLAG) }

    // Matrix to later enlarge the bitmap with.
    private val enlargedBitmapMatrix by lazy { Matrix() }

    // Path to clip a circle and drawing enlarge bitmap in it.
    private val enlargedBitmapPath by lazy { Path() }

    // Ring around circle showing color of current pixel that user is pointing at.
    private val colorRingPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }

    // Stroke width of color ring.
    var colorRingStrokeWidth = 0f
        set(value) {
            colorRingPaint.strokeWidth = value
            field = value
        }

    // Inner circle radius for enlarged bitmap.
    var circlesRadius = 0f
        set(value) {
            field = value
            invalidate()
        }

    // Paint for drawing cross in center of circle for better indicating selected pixels.
    private val centerCrossPaint by lazy {
        Paint().apply {
            style = Paint.Style.FILL
        }
    }

    // Stroke width of center cross.
    var centerCrossStrokeWidth = 1.dp
        set(value) {
            centerCrossPaint.strokeWidth = value
            field = value
        }

    // Color of center cross.
    var centerCrossColor = Color.WHITE
        set(value) {
            centerCrossPaint.color = value
            field = value
        }

    // Determines the size of cross lines (not to be confused with stroke width).
    var centerCrossLineSize = 4.dp

    // Later indicating current position of user fingers.
    private var dropperXPosition = 0f
    private var dropperYPosition = 0f

    // Determines if view should view circle (when user lifts his/her finger, circle should disappear.)
    private var showCircle = false

    // This offset is used for times where current circle exceeds the bounds of image so we offset it.
    private var offsetY = 0f

    // This variable will later be used to determine how much the circle that shows enlarged bitmap
    // should offset from user finger.
    private var circleOffsetFromCenter = 0f

    // Lazily create bitmap to view inside the circle.
    private var bitmapToViewInCircle: Bitmap? = null

    // Right of bitmap + paddings and matrix translations
    private var rightEdge = 0f

    // Bottom of bitmap + paddings and matrix translations.
    private var bottomEdge = 0f

    // Left of bitmap = left padding + matrix translations.
    private var leftEdge = 0f

    // Top of bitmap = top padding + matrix translations.
    private var topEdge = 0f

    /**
     * The last color that was detected by dropper.
     */
    private var lastSelectedColor: Int = 0

    /**
     * Last color selected before user lifts his/her finger from screen.
     */
    private var onLastColorDetected: ((color: Int) -> Unit)? = null

    /**
     * Called everytime a new color get detected by dropper.
     */
    private var onColorDetected: ((color: Int) -> Unit)? = null

    /**
     * Interface for last color that get detected.
     */
    private var interfaceOnLastColorDetected: OnLastColorDetected? = null

    /**
     * Interface that get invoked when any color change happen.
     */
    private var interfaceOnColorDetected: OnColorDetected? = null

    init {
        moveDetector = MoveDetector(1, this)
        scaleType = ScaleType.MATRIX

        context.theme.obtainStyledAttributes(attributeSet, R.styleable.FrameDropper, 0, 0).run {
            try {
                colorRingStrokeWidth =
                    getDimension(
                        R.styleable.FrameDropper_colorRingStrokeWidth,
                        colorRingStrokeWidth
                    )

                circlesRadius =
                    getDimension(R.styleable.FrameDropper_colorRingCircleRadius, circlesRadius)


                centerCrossStrokeWidth = getDimension(
                    R.styleable.FrameDropper_centerCrossStrokeWidth,
                    centerCrossStrokeWidth
                )

                centerCrossColor =
                    getColor(R.styleable.FrameDropper_centerCrossColor, centerCrossColor)

                centerCrossLineSize = getDimension(
                    R.styleable.FrameDropper_centerCrossLineSize,
                    centerCrossLineSize
                )

            } finally {
                recycle()
            }
        }
    }

    override fun onImageLaidOut() {
        super.onImageLaidOut()

        bitmapToViewInCircle = getImageViewBitmap()
        if (bitmapToViewInCircle == null) return

        // Figure out bounds of image from bitmap + paddings + matrix translations
        bitmapToViewInCircle!!.run {
            leftEdge = paddingLeft + getMatrixValue(Matrix.MTRANS_X, true)
            topEdge = paddingTop + getMatrixValue(Matrix.MTRANS_Y)
            rightEdge = width + leftEdge
            bottomEdge = height + topEdge
        }

        // If circles radius weren't set in xml file, then calculate it based on minimum dimension of
        // current view / 10.
        if (circlesRadius == 0f)
            circlesRadius =
                min(
                    (bitmapToViewInCircle!!.width),
                    (bitmapToViewInCircle!!.height)
                ) * 0.25f

        // If color ring stroke width wasn't set in xml file, then calculate it based on current radius
        // of circle.
        colorRingStrokeWidth = circlesRadius * 0.1f

        // This variable will later offset the circle.
        // We offset the circle to be visible to user which
        // area of picture they're using otherwise their finger
        // will block it.
        circleOffsetFromCenter = (circlesRadius * 1.5f)

        // If color ring stroke width wasn't set in xml file, then calculate it based on current radius
        // of circle.
        if (colorRingStrokeWidth == 0f)
            colorRingStrokeWidth = circlesRadius * 0.1f

        // This variable will later offset the circle.
        // We offset the circle to be visible to user which
        // area of picture they're using otherwise their finger
        // will block it.
        circleOffsetFromCenter = (circlesRadius * 1.5f)

    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float): Boolean {
        // Get position of current x and y.
        dropperXPosition = ex
        dropperYPosition = ey

        // Limit point to do not go further than view's dimensions.
        if (dropperXPosition > rightEdge) dropperXPosition = rightEdge
        if (dropperXPosition < leftEdge) dropperXPosition = leftEdge
        if (dropperYPosition > bottomEdge) dropperYPosition = bottomEdge
        if (dropperYPosition < topEdge) dropperYPosition = topEdge

        // Offset the Circle in case circle exceeds the height of layout y coordinate.
        offsetY = if (dropperYPosition - circleOffsetFromCenter * 1.5f <= 0f) {
            (height - dropperYPosition)
        } else 0f

        // Translate enlarged bitmap by padding left and top to center it.
        enlargedBitmapMatrix.setTranslate(
            leftEdge,
            topEdge
        )

        // Scale the image to be more visible to user.
        enlargedBitmapMatrix.postScale(
            2f,
            2f,
            dropperXPosition,
            dropperYPosition + circleOffsetFromCenter - offsetY
        )

        // If user's finger is on the image, then show the circle.
        showCircle = true

        // Invalidate to draw content on the screen.
        invalidate()

        // Return true to show interest in consuming event.
        return true
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // If any bitmap hasn't been set yet then do not show interest in event.
        if (bitmapToViewInCircle == null) return false

        super.onTouchEvent(event)
        return when (event!!.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                performClick()
                true
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                // Call interfaces.
                onLastColorDetected?.invoke(lastSelectedColor)
                interfaceOnLastColorDetected?.onLastColorDetected(lastSelectedColor)

                // If user lifts his/her finger then don't show the circle anymore.
                showCircle = false
                // Reset offset.
                offsetY = 0f
                // Invalidate to draw content on the screen.
                invalidate()
                // We're no longer interested in consuming event.
                false
            }
            else -> {
                false
            }

        }
    }

    override fun dispatchDraw(canvas: Canvas?) {
        super.dispatchDraw(canvas)
        if (showCircle && bitmapToViewInCircle != null) {
            canvas!!.run {
                val bitmapToView = bitmapToViewInCircle!!

                var xPositionWithPadding =
                    dropperXPosition - leftEdge
                var yPositionWithPadding =
                    dropperYPosition - topEdge

                // Validate the x position to not exceed bitmap dimension (otherwise it throws exception.)
                if (xPositionWithPadding >= bitmapToView.width)
                    xPositionWithPadding = bitmapToView.width - 1f

                // Validate the y position to not exceed bitmap dimension (otherwise it throws exception.)
                if (yPositionWithPadding >= bitmapToView.height)
                    yPositionWithPadding = bitmapToView.height - 1f

                // Finally get the color of current selected pixel (the pixel user pointing at) and set
                // The ring color to that.
                lastSelectedColor = bitmapToView.getPixel(
                    xPositionWithPadding.toInt(),
                    yPositionWithPadding.toInt()
                )

                colorRingPaint.color = lastSelectedColor

                // Call interfaces.
                interfaceOnColorDetected?.onColorDetected(lastSelectedColor)
                onColorDetected?.invoke(lastSelectedColor)

                // Variables to store positions of drawing to reuse them.
                val xPositionForDrawings = dropperXPosition
                val yPositionForDrawings = dropperYPosition - circleOffsetFromCenter + offsetY

                // Draw ring that indicates color of current pixel.
                drawCircle(
                    xPositionForDrawings,
                    yPositionForDrawings,
                    circlesRadius,
                    colorRingPaint
                )

                // Add circle to path object to later clip it and draw bitmap in it.
                enlargedBitmapPath.addCircle(
                    xPositionForDrawings,
                    yPositionForDrawings,
                    circlesRadius - colorRingPaint.strokeWidth * 0.47f,
                    Path.Direction.CW
                )

                // Save the canvas to current state.
                save()

                // Clip the circle.
                clipPath(enlargedBitmapPath)

                // Draw the background color in case there are transparent areas so it will be better visible to user.
                drawColor(Color.WHITE)

                // Finally draw the enlarged bitmap inside the circle.
                drawBitmap(bitmapToView, enlargedBitmapMatrix, enlargedBitmapPaint)

                // Restore the canvas to the saved point to not mess following drawing operations.
                restore()

                // Clear any paths.
                enlargedBitmapPath.rewind()

                // If center cross color is white (meaning user didn't choose any preferred color)
                // then change the color of to black if it's on a white pixel.
                if (centerCrossColor == Color.WHITE)
                    centerCrossPaint.color =
                        if (lastSelectedColor.red > 230 && lastSelectedColor.blue > 230 && lastSelectedColor.green > 230)
                            Color.BLACK else Color.WHITE

                // Draw horizontal cross in center of circle.
                drawLine(
                    dropperXPosition - centerCrossLineSize,
                    yPositionForDrawings,
                    dropperXPosition + centerCrossLineSize,
                    yPositionForDrawings,
                    centerCrossPaint
                )

                // Draw vertical cross in center of circle.
                drawLine(
                    xPositionForDrawings,
                    dropperYPosition - centerCrossLineSize - circleOffsetFromCenter + offsetY,
                    xPositionForDrawings,
                    dropperYPosition + centerCrossLineSize - circleOffsetFromCenter + offsetY,
                    centerCrossPaint
                )
            }
        }
    }

    /**
     * Returns bitmap from imageview via it's drawable. This method resizes the returned drawable into
     * view's bounds.
     * @return resized [Bitmap] that user is going to pick color from.
     */
    private fun getImageViewBitmap(): Bitmap? {
        val mDrawable = drawable
        return if (mDrawable != null && mDrawable is BitmapDrawable) {
            val currentScale = getMatrixValue(
                Matrix.MSCALE_X,
                true
            )
            Bitmap.createScaledBitmap(
                mDrawable.bitmap,
                (mDrawable.intrinsicWidth.toFloat() * currentScale).toInt(),
                (mDrawable.intrinsicHeight.toFloat() * currentScale).toInt(),
                true
            )
        } else null
    }

    /**
     * Set listener that get invoked everytime that color by color dropper get changed.
     */
    fun setOnColorDetected(listener: (Int) -> Unit) {
        onColorDetected = listener
    }

    /**
     * Set listener that get invoked everytime that color by color dropper get changed.
     * @param listener Interface [OnColorDetected]
     */
    fun setOnColorDetected(listener: OnColorDetected) {
        interfaceOnColorDetected = listener
    }

    /**
     * Set listener that get invoked before user lifts his/her finger from screen.
     * This listener returns the last color that was selected before user lifts his/her finger
     * up from screen.
     */
    fun setOnLastColorDetected(listener: (Int) -> Unit) {
        onLastColorDetected = listener
    }

    /**
     * Set listener that get invoked before user lifts his/her finger from screen.
     * This listener returns the last color that was selected before user lifts his/her finger
     * up from screen.
     * @param listener Interface [OnLastColorDetected]
     */
    fun setOnLastColorDetected(listener: OnLastColorDetected) {
        interfaceOnLastColorDetected = listener
    }


    /**
     * Interface definition of a callback that get invoked when any color changes by
     * dropper get detected.
     */
    interface OnColorDetected {
        fun onColorDetected(color: Int)
    }

    /**
     * Interface definition of a callback that get invoked when user lifts his/her finger
     * up. This callback should be used when you want to get the last color that was
     * detected by dropper.
     */
    interface OnLastColorDetected {
        fun onLastColorDetected(color: Int)
    }
}
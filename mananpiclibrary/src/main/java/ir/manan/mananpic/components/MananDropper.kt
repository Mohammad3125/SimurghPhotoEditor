package ir.manan.mananpic.components

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import ir.manan.mananpic.R
import ir.manan.mananpic.components.imageviews.MananGestureImageView
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.detectors.MoveDetector
import kotlin.math.min

/**
 * Component that lets user pick a color from a bitmap by enlarging the bitmap and viewing it in a circle.
 * This component has listeners like [setOnColorDetected] and [setOnLastColorDetected] to report the selected
 * colors from user.
 */
class MananDropper(context: Context, attributeSet: AttributeSet?) :
    MananGestureImageView(context, attributeSet) {

    constructor(context: Context) : this(context, null)

    // Matrix to later enlarge the bitmap with.
    private val enlargedBitmapMatrix by lazy { Matrix() }

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

    // Paint that later be used to draw circle shaped bitmap with help of BitmapShader.
    private val bitmapCirclePaint by lazy {
        Paint()
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
    var centerCrossStrokeWidth = dp(1)
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
    var centerCrossLineSize = dp(4)

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

        // Get display matrix to use width and height of device to pick a size for circle.
        val displayMetrics = context.resources.displayMetrics

        // Calculate circles radius by taking smallest size between width and height of device and device
        // it by 5.
        circlesRadius = min(displayMetrics.widthPixels, displayMetrics.heightPixels).toFloat() / 5f

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

    override fun onImageLaidOut() {
        super.onImageLaidOut()
        bitmapToViewInCircle = getImageViewBitmap()
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
        // If any bitmap hasn't been set yet then do not show interest in event.
        if (bitmapToViewInCircle == null) return false

        return showDropper(initialX, initialY)
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float): Boolean {
        return showDropper(ex, ey)
    }

    private fun showDropper(ex: Float, ey: Float): Boolean {
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
        lastSelectedColor =
            bitmapToView.getPixel(xPositionWithPadding.toInt(), yPositionWithPadding.toInt())

        // Call interfaces.
        interfaceOnColorDetected?.onColorDetected(lastSelectedColor)
        onColorDetected?.invoke(lastSelectedColor)

        // If center cross color is white (meaning user didn't choose any preferred color)
        // then change the color of to black if it's on a white pixel.
        if (centerCrossColor == Color.WHITE)
            centerCrossPaint.color =
                if (lastSelectedColor.red > 230 && lastSelectedColor.blue > 230 && lastSelectedColor.green > 230)
                    Color.BLACK else Color.WHITE

        // Create a new BitmapShader to later be used to draw on circle.
        // Note that although creating objects in a method that is
        // called few times a second is a bad idea but if bitmap
        // size or view size get changed then shader would be
        // set to last bitmap and it would mess up the color
        // picking mechanism.
        bitmapCirclePaint.shader =
            BitmapShader(bitmapToView, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(enlargedBitmapMatrix)
            }


        // Invalidate to draw content on the screen.
        invalidate()

        // Return true to show interest in consuming event.
        return true
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        // Call interfaces.
        onLastColorDetected?.invoke(lastSelectedColor)
        interfaceOnLastColorDetected?.onLastColorDetected(lastSelectedColor)

        // If user lifts his/her finger then don't show the circle anymore.
        showCircle = false
        // Reset offset.
        offsetY = 0f
        // Invalidate to draw content on the screen.
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas?) {
        super.dispatchDraw(canvas)
        if (showCircle && bitmapToViewInCircle != null) {
            canvas?.run {
                colorRingPaint.color = lastSelectedColor

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

                // Same logic as shader but with clipping.
                // Since clipping is not available in lower
                // APIs we use BitmapShader instead.

                /*

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
                drawBitmap(bitmapToViewInCircle!!, enlargedBitmapMatrix, enlargedBitmapPaint)

                // Restore the canvas to the saved point to not mess following drawing operations.
                restore()

                // Clear any paths.
                enlargedBitmapPath.rewind()

                 */

                // Draw a circle shaped bitmap with paint that contains
                // bitmap shader.
                drawCircle(
                    xPositionForDrawings,
                    yPositionForDrawings,
                    circlesRadius - colorRingPaint.strokeWidth * 0.47f,
                    bitmapCirclePaint
                )

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
            Bitmap.createScaledBitmap(
                mDrawable.bitmap,
                (bitmapWidth).toInt(),
                (bitmapHeight).toInt(),
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
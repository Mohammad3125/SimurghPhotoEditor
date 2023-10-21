package ir.manan.mananpic.components.paint.painters.colorpicker

import android.content.Context
import android.graphics.*
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import ir.manan.mananpic.components.MananDropper
import ir.manan.mananpic.components.MananDropper.OnColorDetected
import ir.manan.mananpic.components.MananDropper.OnLastColorDetected
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.dp
import kotlin.math.min

class ColorDropper : Painter() {


    private var referenceLayer: Bitmap? = null

    // Matrix to later enlarge the bitmap with.
    private val enlargedBitmapMatrix by lazy { Matrix() }

    // Colors for shadow behind enlarged bitmap circle.
    private val circleShadowColors by lazy {
        intArrayOf(Color.BLACK, Color.TRANSPARENT)
    }

    // Paint used for drawing shadow behind enlarged bitmap circle.
    // This paint will later use RadialGradient to create shadow.
    private val circleShadowPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG)
    }

    // Circle that would be drawn on shadow circle to prevent shadow
    // to be visible on transparent pixels.
    private val circleShadowOverlayPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
    }

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
            sendMessage(PainterMessage.INVALIDATE)
        }

    // Paint for drawing cross in center of circle for better indicating selected pixels.
    private val centerCrossPaint by lazy {
        Paint().apply {
            style = Paint.Style.FILL
        }
    }

    // Stroke width of center cross.
    var centerCrossStrokeWidth = 0f
        set(value) {
            centerCrossPaint.strokeWidth = value
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    // Color of center cross.
    var centerCrossColor = Color.WHITE
        set(value) {
            centerCrossPaint.color = value
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    // Determines the size of cross lines (not to be confused with stroke width).
    var centerCrossLineSize = 0f
        set(value) {
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    // Later indicating current position of user fingers.
    private var dropperXPosition = 0f
    private var dropperYPosition = 0f

    // Determines if view should view circle (when user lifts his/her finger, circle should disappear.)
    private var showCircle = true

    // This offset is used for times where current circle exceeds the bounds of image so we offset it.
    private var offsetY = 0f

    // This variable will later be used to determine how much the circle that shows enlarged bitmap
    // should offset from user finger.
    private var circleOffsetFromCenter = 0f

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
    private var interfaceOnLastColorDetected: MananDropper.OnLastColorDetected? = null

    /**
     * Interface that get invoked when any color change happen.
     */
    private var interfaceOnColorDetected: MananDropper.OnColorDetected? = null


    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        bounds: RectF
    ) {

        // Get display matrix to use width and height of device to pick a size for circle.
        val displayMetrics = context.resources.displayMetrics

        if (centerCrossLineSize == 0f) {
            centerCrossLineSize = context.dp(4)
        }

        if (centerCrossStrokeWidth == 0f) {
            centerCrossStrokeWidth = context.dp(1)
        }

        // Calculate circles radius by taking smallest size between width and height of device and device
        // it by 5.
        if (circlesRadius == 0f) {
            circlesRadius =
                min(displayMetrics.widthPixels, displayMetrics.heightPixels).toFloat() / 5f
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

    override fun onMoveBegin(initialX: Float, initialY: Float) {
        showDropper(initialX, initialY)
    }

    override fun onMove(ex: Float, ey: Float, dx: Float, dy: Float) {
        showDropper(ex, ey)
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        showDropper(lastX, lastY)
        // Call interfaces.
        onLastColorDetected?.invoke(lastSelectedColor)
        interfaceOnLastColorDetected?.onLastColorDetected(lastSelectedColor)

        // If user lifts his/her finger then don't show the circle anymore.
        showCircle = false
        // Reset offset.
        offsetY = 0f
        // Invalidate to draw content on the screen.
        sendMessage(PainterMessage.INVALIDATE)
    }

    private fun showDropper(ex: Float, ey: Float) {
        referenceLayer?.let { refBitmap ->
            // Get position of current x and y.
            dropperXPosition = ex
            dropperYPosition = ey

            val bitWidth = refBitmap.width
            val bitHeight = refBitmap.height

            dropperXPosition = dropperXPosition.coerceIn(0f, bitWidth - 1f)
            dropperYPosition = dropperYPosition.coerceIn(0f, bitHeight - 1f)

            // Offset the Circle in case circle exceeds the height of layout y coordinate.
            offsetY = if (dropperYPosition - circleOffsetFromCenter * 1.5f <= 0f) {
                (refBitmap.height - dropperYPosition)
            } else 0f

            // Scale the image to be more visible to user.
            enlargedBitmapMatrix.setScale(
                2f,
                2f,
                dropperXPosition,
                dropperYPosition + circleOffsetFromCenter - offsetY
            )

            // If user's finger is on the image, then show the circle.
            showCircle = true


            // Finally get the color of current selected pixel (the pixel user pointing at) and set
            // The ring color to that.
            lastSelectedColor =
                refBitmap.getPixel(dropperXPosition.toInt(), dropperYPosition.toInt())

            // Call interfaces.
            interfaceOnColorDetected?.onColorDetected(lastSelectedColor)
            onColorDetected?.invoke(lastSelectedColor)

            // If center cross color is white (meaning user didn't choose any preferred color)
            // then change the color of to black if it's on a white pixel.
            if (centerCrossColor == Color.WHITE)
                centerCrossPaint.color =
                    if (lastSelectedColor.red > 230 && lastSelectedColor.blue > 230 && lastSelectedColor.green > 230)
                        Color.BLACK else Color.WHITE

            bitmapCirclePaint.shader.setLocalMatrix(enlargedBitmapMatrix)

            // Create a RadialGradient for shadow behind the enlarged bitmap circle.
            circleShadowPaint.shader = RadialGradient(
                dropperXPosition,
                dropperYPosition - circleOffsetFromCenter + offsetY,
                circlesRadius + (circlesRadius * 0.2f),
                circleShadowColors,
                null,
                Shader.TileMode.CLAMP
            )


            // Invalidate to draw content on the screen.
            sendMessage(PainterMessage.INVALIDATE)

            // Return true to show interest in consuming event.
        }
    }

    override fun resetPaint() {
        dropperXPosition = 0f
        dropperYPosition = 0f
        offsetY = 0f
    }

    override fun draw(canvas: Canvas) {
        if (!showCircle) {
            return
        }

        canvas.run {
            referenceLayer?.let {
                colorRingPaint.color = lastSelectedColor

                // Variables to store positions of drawing to reuse them.
                val xPositionForDrawings = dropperXPosition
                val yPositionForDrawings = dropperYPosition - circleOffsetFromCenter + offsetY

                // Draw shadow behind the enlarged bitmap circle.
                drawCircle(
                    xPositionForDrawings,
                    yPositionForDrawings,
                    circlesRadius + (circlesRadius * 0.2f),
                    circleShadowPaint
                )

                // Draw circle on top of shadow to prevent shadow to be visible
                // on transparent pixels.
                drawCircle(
                    xPositionForDrawings,
                    yPositionForDrawings,
                    circlesRadius - colorRingPaint.strokeWidth * 0.46f, circleShadowOverlayPaint
                )

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

    override fun onReferenceLayerCreated(reference: Bitmap) {
        referenceLayer = reference

        bitmapCirclePaint.shader =
            BitmapShader(reference, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(enlargedBitmapMatrix)
            }

        if (dropperXPosition == 0f && dropperYPosition == 0f) {
            showDropper(reference.width * 0.5f, reference.height * 0.5f)
        } else {
            showDropper(dropperXPosition, dropperYPosition)
        }

        showCircle = true

        sendMessage(PainterMessage.INVALIDATE)
    }

    override fun doesHandleHistory(): Boolean {
        return true
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

}
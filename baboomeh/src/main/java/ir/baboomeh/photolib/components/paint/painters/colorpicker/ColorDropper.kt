package ir.baboomeh.photolib.components.paint.painters.colorpicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.blue
import androidx.core.graphics.get
import androidx.core.graphics.green
import androidx.core.graphics.red
import ir.baboomeh.photolib.components.paint.Painter
import ir.baboomeh.photolib.utils.MananMatrix
import ir.baboomeh.photolib.utils.dp
import ir.baboomeh.photolib.utils.gesture.TouchData
import kotlin.math.min

open class ColorDropper : Painter() {
    protected var referenceLayer: Bitmap? = null

    // Matrix to later enlarge the bitmap with.
    protected val enlargedBitmapMatrix by lazy { Matrix() }

    // Colors for shadow behind enlarged bitmap circle.
    protected val circleShadowColors by lazy {
        intArrayOf(Color.BLACK, Color.TRANSPARENT)
    }

    // Paint used for drawing shadow behind enlarged bitmap circle.
    // This paint will later use RadialGradient to create shadow.
    protected val circleShadowPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG)
    }

    // Circle that would be drawn on shadow circle to prevent shadow
    // to be visible on transparent pixels.
    protected val circleShadowOverlayPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
    }

    // Ring around circle showing color of current pixel that user is pointing at.
    protected val colorRingPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }

    // Stroke width of color ring.
    open var colorRingStrokeWidth = Float.NaN
        set(value) {
            colorRingPaint.strokeWidth = value
            field = value
        }

    // Paint that later be used to draw circle shaped bitmap with help of BitmapShader.
    protected val bitmapCirclePaint by lazy {
        Paint()
    }

    // Inner circle radius for enlarged bitmap.
    open var circlesRadius = Float.NaN
        set(value) {
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    // Paint for drawing cross in center of circle for better indicating selected pixels.
    protected val centerCrossPaint by lazy {
        Paint().apply {
            style = Paint.Style.FILL
        }
    }

    // Stroke width of center cross.
    open var centerCrossStrokeWidth = Float.NaN
        set(value) {
            centerCrossPaint.strokeWidth = value
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    // Color of center cross.
    open var centerCrossColor = Color.WHITE
        set(value) {
            centerCrossPaint.color = value
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    // Determines the size of cross lines (not to be confused with stroke width).
    open var centerCrossLineSize = Float.NaN
        set(value) {
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    // Later indicating current position of user fingers.
    protected var dropperXPosition = 0f
    protected var dropperYPosition = 0f

    // Determines if view should view circle (when user lifts his/her finger, circle should disappear.)
    protected var showCircle = true

    // This offset is used for times where current circle exceeds the bounds of image so we offset it.
    protected var offsetY = 0f

    // This variable will later be used to determine how much the circle that shows enlarged bitmap
    // should offset from user finger.
    protected var circleOffsetFromCenter = 0f

    /**
     * The last color that was detected by dropper.
     */
    protected var lastSelectedColor: Int = 0

    /**
     * Last color selected before user lifts his/her finger from screen.
     */
    protected var lastColorDetectedCallback: ((color: Int) -> Unit)? = null

    /**
     * Called everytime a new color get detected by dropper.
     */
    protected var colorDetectedCallback: ((color: Int) -> Unit)? = null

    /**
     * Interface for last color that get detected.
     */
    protected var interfaceOnLastColorDetected: OnLastColorDetected? = null

    /**
     * Interface that get invoked when any color change happen.
     */
    protected var interfaceOnColorDetected: OnColorDetected? = null

    protected val clipBounds by lazy {
        RectF()
    }


    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)

        this.clipBounds.set(clipBounds)

        if (centerCrossLineSize.isNaN()) {
            centerCrossLineSize = context.dp(4)
        }

        if (centerCrossStrokeWidth.isNaN()) {
            centerCrossStrokeWidth = context.dp(1)
        }

        // Calculate circles radius by taking smallest size between width and height of device and device
        // it by 5.
        if (circlesRadius.isNaN()) {
            circlesRadius =
                min(clipBounds.width(), clipBounds.height()) / 5f
        }

        // This variable will later offset the circle.
        // We offset the circle to be visible to user which
        // area of picture they're using otherwise their finger
        // will block it.
        circleOffsetFromCenter = (circlesRadius * 1.5f)

        // If color ring stroke width wasn't set, then calculate it based on current radius
        // of circle.
        if (colorRingStrokeWidth.isNaN())
            colorRingStrokeWidth = circlesRadius * 0.1f
    }

    override fun onMoveBegin(touchData: TouchData) {
        calculateToShowDropperAt(touchData.ex, touchData.ey)
    }

    override fun onMove(touchData: TouchData) {
        calculateToShowDropperAt(touchData.ex, touchData.ey)
    }

    override fun onMoveEnded(touchData: TouchData) {
        calculateToShowDropperAt(touchData.ex, touchData.ey)
        // Call interfaces.
        lastColorDetectedCallback?.invoke(lastSelectedColor)
        interfaceOnLastColorDetected?.onLastColorDetected(lastSelectedColor)

        // If user lifts his/her finger then don't show the circle anymore.
        showCircle = false
        // Reset offset.
        offsetY = 0f
        // Invalidate to draw content on the screen.
        sendMessage(PainterMessage.INVALIDATE)
    }

    protected open fun calculateToShowDropperAt(ex: Float, ey: Float) {
        referenceLayer?.let { refBitmap ->
            // Get position of current x and y.
            dropperXPosition = ex
            dropperYPosition = ey

            dropperXPosition = dropperXPosition.coerceIn(clipBounds.left, clipBounds.right - 1f)
            dropperYPosition = dropperYPosition.coerceIn(clipBounds.top, clipBounds.bottom - 1f)

            // Offset the Circle in case circle exceeds the height of layout y coordinate.
            offsetY = if (dropperYPosition - circleOffsetFromCenter * 1.5f <= clipBounds.top) {
                (clipBounds.bottom - dropperYPosition)
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
                refBitmap[dropperXPosition.toInt(), dropperYPosition.toInt()]

            // Call interfaces.
            interfaceOnColorDetected?.onColorDetected(lastSelectedColor)
            colorDetectedCallback?.invoke(lastSelectedColor)

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
            referenceLayer?.let { reference ->

                drawBitmap(reference, 0f, 0f, circleShadowPaint)

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
            calculateToShowDropperAt(reference.width * 0.5f, reference.height * 0.5f)
        } else {
            calculateToShowDropperAt(dropperXPosition, dropperYPosition)
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
    open fun setOnColorDetected(listener: (Int) -> Unit) {
        colorDetectedCallback = listener
    }

    /**
     * Set listener that get invoked everytime that color by color dropper get changed.
     * @param listener Interface [OnColorDetected]
     */
    open fun setOnColorDetected(listener: OnColorDetected) {
        interfaceOnColorDetected = listener
    }

    /**
     * Set listener that get invoked before user lifts his/her finger from screen.
     * This listener returns the last color that was selected before user lifts his/her finger
     * up from screen.
     */
    open fun setOnLastColorDetected(listener: (Int) -> Unit) {
        lastColorDetectedCallback = listener
    }

    /**
     * Set listener that get invoked before user lifts his/her finger from screen.
     * This listener returns the last color that was selected before user lifts his/her finger
     * up from screen.
     * @param listener Interface [OnLastColorDetected]
     */
    open fun setOnLastColorDetected(listener: OnLastColorDetected) {
        interfaceOnLastColorDetected = listener
    }

    override fun onSizeChanged(newBounds: RectF, clipBounds: Rect, changeMatrix: Matrix) {
        this.clipBounds.set(clipBounds)
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
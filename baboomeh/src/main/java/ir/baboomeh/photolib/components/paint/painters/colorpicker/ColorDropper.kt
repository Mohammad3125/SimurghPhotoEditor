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
import ir.baboomeh.photolib.components.paint.painters.painter.Painter
import ir.baboomeh.photolib.components.paint.painters.painter.PainterMessage
import ir.baboomeh.photolib.utils.extensions.dp
import ir.baboomeh.photolib.utils.gesture.TouchData
import ir.baboomeh.photolib.utils.matrix.MananMatrix
import kotlin.math.min

/**
 * A color picker tool that allows users to sample colors from an image by touching the screen.
 * 
 * This tool provides an intuitive color sampling experience with:
 * 
 * **Visual Features:**
 * - Magnified preview circle showing enlarged pixels around touch point
 * - Color ring displaying the currently sampled color
 * - Crosshair indicator for precise pixel selection
 * - Shadow effects for better visual contrast
 * - Smart positioning to avoid finger occlusion
 * 
 * **Functionality:**
 * - Real-time color sampling during touch movement
 * - Automatic color detection with pixel-perfect accuracy
 * - Callback system for color change notifications
 * - Support for both lambda and interface-based callbacks
 * - Intelligent contrast adjustment for crosshair visibility
 * 
 * **User Experience:**
 * - Circle appears on touch and follows finger movement
 * - Automatic offset positioning to keep preview visible
 * - Bounds checking to ensure sampling within image limits
 * - Smooth visual transitions and animations
 * 
 * The tool is designed for photo editing applications where precise color selection
 * is essential. It provides both immediate feedback during interaction and final
 * color selection when the user lifts their finger.
 * 
 * **Usage Example:**
 * ```kotlin
 * val colorDropper = ColorDropper()
 * colorDropper.setOnColorDetected { color ->
 *     // Handle real-time color changes
 * }
 * colorDropper.setOnLastColorDetected { finalColor ->
 *     // Handle final color selection
 * }
 * ```
 */
open class ColorDropper : Painter() {
    
    /**
     * Reference to the bitmap layer from which colors will be sampled.
     * This is typically set automatically when the painter is initialized.
     */
    protected var referenceLayer: Bitmap? = null

    /**
     * Matrix used to scale and position the magnified bitmap preview.
     * Applied to create the enlarged view inside the preview circle.
     */
    protected val enlargedBitmapMatrix by lazy { Matrix() }

    /**
     * Color array for creating the radial gradient shadow behind the preview circle.
     * Transitions from black to transparent for a natural drop shadow effect.
     */
    protected val circleShadowColors by lazy {
        intArrayOf(Color.BLACK, Color.TRANSPARENT)
    }

    /**
     * Paint used for drawing the shadow behind the magnified preview circle.
     * Uses a RadialGradient shader to create smooth shadow falloff.
     */
    protected val circleShadowPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG)
    }

    /**
     * Paint for drawing a white overlay circle to prevent shadow visibility
     * on transparent pixels within the preview area.
     */
    protected val circleShadowOverlayPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
    }

    /**
     * Paint for drawing the colored ring around the preview circle.
     * The ring color matches the currently sampled pixel color.
     */
    protected val colorRingPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }

    /**
     * Stroke width of the color ring in pixels.
     * When set, automatically updates the paint configuration.
     */
    open var colorRingStrokeWidth = Float.NaN
        set(value) {
            colorRingPaint.strokeWidth = value
            field = value
        }

    /**
     * Paint used to draw the magnified bitmap as a circle using BitmapShader.
     * Provides the enlarged pixel view within the preview circle.
     */
    protected val bitmapCirclePaint by lazy {
        Paint()
    }

    /**
     * Radius of the preview circle in pixels.
     * Controls the size of both the magnified view and color ring.
     */
    open var circlesRadius = Float.NaN
        set(value) {
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    /**
     * Paint for drawing the crosshair in the center of the preview circle.
     * Helps users see exactly which pixel is being sampled.
     */
    protected val centerCrossPaint by lazy {
        Paint().apply {
            style = Paint.Style.FILL
        }
    }

    /**
     * Stroke width of the center crosshair lines in pixels.
     */
    open var centerCrossStrokeWidth = Float.NaN
        set(value) {
            centerCrossPaint.strokeWidth = value
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    /**
     * Color of the center crosshair.
     * Automatically adjusts for contrast when set to white (default behavior).
     */
    open var centerCrossColor = Color.WHITE
        set(value) {
            centerCrossPaint.color = value
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    /**
     * Length of the crosshair lines in pixels (not stroke width).
     * Determines how far the crosshair extends from the center point.
     */
    open var centerCrossLineSize = Float.NaN
        set(value) {
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    /**
     * Current X position of the color dropper (where user is touching).
     */
    protected var dropperXPosition = 0f
    
    /**
     * Current Y position of the color dropper (where user is touching).
     */
    protected var dropperYPosition = 0f

    /**
     * Flag indicating whether the preview circle should be visible.
     * Set to false when user lifts finger to hide the preview.
     */
    protected var showCircle = true

    /**
     * Vertical offset applied to prevent the preview circle from going outside bounds.
     * Automatically calculated based on touch position and circle size.
     */
    protected var offsetY = 0f

    /**
     * Distance the preview circle is offset from the user's finger.
     * Prevents the finger from obscuring the magnified view.
     */
    protected var circleOffsetFromCenter = 0f

    /**
     * The most recently detected color value as an ARGB integer.
     */
    protected var lastSelectedColor: Int = 0

    /**
     * Callback invoked when user lifts finger, providing the final selected color.
     */
    protected var lastColorDetectedCallback: ((color: Int) -> Unit)? = null

    /**
     * Callback invoked continuously as colors change during touch movement.
     */
    protected var colorDetectedCallback: ((color: Int) -> Unit)? = null

    /**
     * Interface-based callback for final color selection.
     */
    protected var interfaceOnLastColorDetected: OnLastColorDetected? = null

    /**
     * Interface-based callback for continuous color detection.
     */
    protected var interfaceOnColorDetected: OnColorDetected? = null

    /**
     * Bounds rectangle for clipping the color sampling area.
     */
    protected val clipBounds by lazy {
        RectF()
    }

    /**
     * Initializes the color dropper with view dimensions and default settings.
     * Calculates appropriate sizes for UI elements based on available space.
     */
    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)

        this.clipBounds.set(clipBounds)

        // Set default crosshair line size if not specified
        if (centerCrossLineSize.isNaN()) {
            centerCrossLineSize = context.dp(4)
        }

        // Set default crosshair stroke width if not specified
        if (centerCrossStrokeWidth.isNaN()) {
            centerCrossStrokeWidth = context.dp(1)
        }

        // Calculate circle radius as 1/5 of the smallest dimension for good proportions
        if (circlesRadius.isNaN()) {
            circlesRadius =
                min(clipBounds.width(), clipBounds.height()) / 5f
        }

        // Offset circle by 1.5x radius to keep it visible above user's finger
        circleOffsetFromCenter = (circlesRadius * 1.5f)

        // Set default color ring stroke width proportional to circle size
        if (colorRingStrokeWidth.isNaN())
            colorRingStrokeWidth = circlesRadius * 0.1f
    }

    /**
     * Called when user starts touching the screen to sample colors.
     * Initializes the color dropper at the touch position.
     */
    override fun onMoveBegin(touchData: TouchData) {
        calculateToShowDropperAt(touchData.ex, touchData.ey)
    }

    /**
     * Called continuously while user moves their finger.
     * Updates the dropper position and samples new colors in real-time.
     */
    override fun onMove(touchData: TouchData) {
        calculateToShowDropperAt(touchData.ex, touchData.ey)
    }

    /**
     * Called when user lifts their finger from the screen.
     * Finalizes color selection and hides the preview circle.
     */
    override fun onMoveEnded(touchData: TouchData) {
        calculateToShowDropperAt(touchData.ex, touchData.ey)
        // Notify listeners of final color selection
        lastColorDetectedCallback?.invoke(lastSelectedColor)
        interfaceOnLastColorDetected?.onLastColorDetected(lastSelectedColor)

        // Hide the preview circle until next touch
        showCircle = false
        // Reset positioning offset
        offsetY = 0f
        // Trigger redraw to remove the circle
        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Core logic for positioning the color dropper and sampling colors.
     * Handles bounds checking, offset calculations, and color detection.
     * 
     * @param ex X coordinate of touch position
     * @param ey Y coordinate of touch position
     */
    protected open fun calculateToShowDropperAt(ex: Float, ey: Float) {
        referenceLayer?.let { refBitmap ->
            // Store current touch position
            dropperXPosition = ex
            dropperYPosition = ey

            // Clamp position to image bounds to prevent sampling outside the image
            dropperXPosition = dropperXPosition.coerceIn(clipBounds.left, clipBounds.right - 1f)
            dropperYPosition = dropperYPosition.coerceIn(clipBounds.top, clipBounds.bottom - 1f)

            // Calculate vertical offset to keep circle visible when near top edge
            offsetY = if (dropperYPosition - circleOffsetFromCenter * 1.5f <= clipBounds.top) {
                (clipBounds.bottom - dropperYPosition)
            } else 0f

            // Configure matrix for 2x magnification of the preview area
            enlargedBitmapMatrix.setScale(
                2f,
                2f,
                dropperXPosition,
                dropperYPosition + circleOffsetFromCenter - offsetY
            )

            // Show the preview circle during active sampling
            showCircle = true

            // Sample the color at the current pixel position
            lastSelectedColor =
                refBitmap[dropperXPosition.toInt(), dropperYPosition.toInt()]

            // Notify listeners of color change
            interfaceOnColorDetected?.onColorDetected(lastSelectedColor)
            colorDetectedCallback?.invoke(lastSelectedColor)

            // Auto-adjust crosshair color for contrast on light backgrounds
            if (centerCrossColor == Color.WHITE)
                centerCrossPaint.color =
                    if (lastSelectedColor.red > 230 && lastSelectedColor.blue > 230 && lastSelectedColor.green > 230)
                        Color.BLACK else Color.WHITE

            // Apply magnification matrix to bitmap shader
            bitmapCirclePaint.shader.setLocalMatrix(enlargedBitmapMatrix)

            // Create shadow gradient for the preview circle
            circleShadowPaint.shader = RadialGradient(
                dropperXPosition,
                dropperYPosition - circleOffsetFromCenter + offsetY,
                circlesRadius + (circlesRadius * 0.2f),
                circleShadowColors,
                null,
                Shader.TileMode.CLAMP
            )

            // Request redraw to update visual feedback
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Resets the color dropper to initial state.
     * Clears position data and visual state.
     */
    override fun resetPaint() {
        dropperXPosition = 0f
        dropperYPosition = 0f
        offsetY = 0f
    }

    /**
     * Renders the color dropper UI on the canvas.
     * Draws the magnified preview, color ring, shadow, and crosshair.
     */
    override fun draw(canvas: Canvas) {
        if (!showCircle) {
            return
        }

        canvas.run {
            referenceLayer?.let { reference ->
                drawBitmap(reference, 0f, 0f, circleShadowPaint)

                // Set ring color to currently sampled color
                colorRingPaint.color = lastSelectedColor

                // Calculate drawing position (with offset applied)
                val xPositionForDrawings = dropperXPosition
                val yPositionForDrawings = dropperYPosition - circleOffsetFromCenter + offsetY

                // Draw drop shadow behind the preview circle
                drawCircle(
                    xPositionForDrawings,
                    yPositionForDrawings,
                    circlesRadius + (circlesRadius * 0.2f),
                    circleShadowPaint
                )

                // Draw white overlay to hide shadow on transparent areas
                drawCircle(
                    xPositionForDrawings,
                    yPositionForDrawings,
                    circlesRadius - colorRingPaint.strokeWidth * 0.46f, 
                    circleShadowOverlayPaint
                )

                // Draw colored ring showing sampled color
                drawCircle(
                    xPositionForDrawings,
                    yPositionForDrawings,
                    circlesRadius,
                    colorRingPaint
                )

                // Draw magnified bitmap view inside the circle
                drawCircle(
                    xPositionForDrawings,
                    yPositionForDrawings,
                    circlesRadius - colorRingPaint.strokeWidth * 0.47f,
                    bitmapCirclePaint
                )

                // Draw horizontal crosshair line
                drawLine(
                    dropperXPosition - centerCrossLineSize,
                    yPositionForDrawings,
                    dropperXPosition + centerCrossLineSize,
                    yPositionForDrawings,
                    centerCrossPaint
                )

                // Draw vertical crosshair line
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
     * Called when the reference bitmap is created or updated.
     * Sets up the bitmap shader and initializes default sampling position.
     */
    override fun onReferenceLayerCreated(reference: Bitmap) {
        referenceLayer = reference

        // Configure bitmap shader for magnified preview
        bitmapCirclePaint.shader =
            BitmapShader(reference, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(enlargedBitmapMatrix)
            }

        // Initialize to center of image if no previous position
        if (dropperXPosition == 0f && dropperYPosition == 0f) {
            calculateToShowDropperAt(reference.width * 0.5f, reference.height * 0.5f)
        } else {
            calculateToShowDropperAt(dropperXPosition, dropperYPosition)
        }

        showCircle = true
        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Indicates this tool handles its own history management.
     */
    override fun doesHandleHistory(): Boolean {
        return true
    }

    /**
     * Sets a lambda callback for continuous color detection during touch movement.
     * 
     * @param listener Function that receives color values as they change
     */
    open fun setOnColorDetected(listener: (Int) -> Unit) {
        colorDetectedCallback = listener
    }

    /**
     * Sets an interface callback for continuous color detection during touch movement.
     * 
     * @param listener Interface implementation for color change notifications
     */
    open fun setOnColorDetected(listener: OnColorDetected) {
        interfaceOnColorDetected = listener
    }

    /**
     * Sets a lambda callback for final color selection when user lifts finger.
     * 
     * @param listener Function that receives the final selected color
     */
    open fun setOnLastColorDetected(listener: (Int) -> Unit) {
        lastColorDetectedCallback = listener
    }

    /**
     * Sets an interface callback for final color selection when user lifts finger.
     * 
     * @param listener Interface implementation for final color selection
     */
    open fun setOnLastColorDetected(listener: OnLastColorDetected) {
        interfaceOnLastColorDetected = listener
    }

    /**
     * Called when the view size changes.
     * Updates the clipping bounds for color sampling.
     */
    override fun onSizeChanged(newBounds: RectF, clipBounds: Rect, changeMatrix: Matrix) {
        this.clipBounds.set(clipBounds)
    }

    /**
     * Interface for continuous color detection callbacks.
     * Invoked whenever the sampled color changes during touch movement.
     */
    interface OnColorDetected {
        /**
         * Called when a new color is detected by the dropper.
         * 
         * @param color The newly detected color as an ARGB integer
         */
        fun onColorDetected(color: Int)
    }

    /**
     * Interface for final color selection callbacks.
     * Invoked when the user lifts their finger to finalize color selection.
     */
    interface OnLastColorDetected {
        /**
         * Called when the user finalizes their color selection.
         * 
         * @param color The final selected color as an ARGB integer
         */
        fun onLastColorDetected(color: Int)
    }
}

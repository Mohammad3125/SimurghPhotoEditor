package ir.simurgh.photolib.components.paint.painters.painting.brushes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import androidx.core.graphics.scale
import kotlin.math.max

/**
 * A brush implementation that uses a bitmap image as the brush shape.
 *
 * This brush type allows for complex, custom brush shapes by using any bitmap
 * as the stamp. The bitmap is scaled to match the brush size and can be tinted
 * with the brush color. This enables realistic brush effects like watercolor,
 * chalk, or custom texture brushes.
 *
 * The brush automatically handles:
 * - Scaling the bitmap to match the brush size
 * - Color tinting using PorterDuff blend modes
 * - Caching scaled versions for performance
 * - Proper positioning and rendering
 *
 * @param brushBitmap The bitmap to use as the brush shape (can be null initially)
 */
open class BitmapBrush(
    size: Int = 1,
    color: Int = Color.BLACK,
    opacity: Float = 1f,
    opacityJitter: Float = 0f,
    opacityVariance: Float = 0f,
    opacityVarianceSpeed: Float = 0.6f,
    opacityVarianceEasing: Float = 0.1f,
    sizePressureSensitivity: Float = 0.6f,
    minimumPressureSize: Float = 0.3f,
    maximumPressureSize: Float = 1f,
    isSizePressureSensitive: Boolean = false,
    opacityPressureSensitivity: Float = 0.5f,
    minimumPressureOpacity: Float = 0f,
    maximumPressureOpacity: Float = 1f,
    isOpacityPressureSensitive: Boolean = false,
    spacing: Float = 0.1f,
    scatter: Float = 0f,
    angle: Float = 0f,
    angleJitter: Float = 0f,
    sizeJitter: Float = 0f,
    sizeVariance: Float = 1f,
    sizeVarianceSensitivity: Float = 0.1f,
    sizeVarianceEasing: Float = 0.08f,
    squish: Float = 0f,
    hueJitter: Int = 0,
    smoothness: Float = 0f,
    alphaBlend: Boolean = false,
    autoRotate: Boolean = false,
    hueFlow: Float = 0f,
    hueDistance: Int = 0,
    startTaperSpeed: Float = 0.03f,
    startTaperSize: Float = 1f,
    texture: Bitmap? = null,
    textureTransformation: Matrix? = null,
    open var brushBitmap: Bitmap? = null
) : Brush(size,
    color,
    opacity,
    opacityJitter,
    opacityVariance,
    opacityVarianceSpeed,
    opacityVarianceEasing,
    sizePressureSensitivity,
    minimumPressureSize,
    maximumPressureSize,
    isSizePressureSensitive,
    opacityPressureSensitivity,
    minimumPressureOpacity,
    maximumPressureOpacity,
    isOpacityPressureSensitive,
    spacing,
    scatter,
    angle,
    angleJitter,
    sizeJitter,
    sizeVariance,
    sizeVarianceSensitivity,
    sizeVarianceEasing,
    squish,
    hueJitter,
    smoothness,
    alphaBlend,
    autoRotate,
    hueFlow,
    hueDistance,
    startTaperSpeed,
    startTaperSize,
    texture,
    textureTransformation) {

    /** Paint object used for rendering the bitmap with color tinting */
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    // Original bitmap dimensions
    /** Original width of the brush bitmap */
    protected var stampWidth = 0f

    /** Original height of the brush bitmap */
    protected var stampHeight = 0f

    // Scaled bitmap dimensions  
    /** Scaled width of the brush bitmap */
    protected var stampScaledWidth = 0f

    /** Scaled height of the brush bitmap */
    protected var stampScaledHeight = 0f

    // Half dimensions for centering
    /** Half of the scaled width for centering calculations */
    protected var stampScaledWidthHalf = 0f

    /** Half of the scaled height for centering calculations */
    protected var stampScaledHeightHalf = 0f

    /** Scale factor applied to the bitmap */
    protected var stampScale = 0f

    /**
     * Blending mode for the brush strokes.
     * When set, updates the paint's transfer mode accordingly.
     */
    override var brushBlending: PorterDuff.Mode = PorterDuff.Mode.SRC_OVER
        set(value) {
            field = value
            if (field == PorterDuff.Mode.SRC_OVER) {
                paint.xfermode = null
            } else {
                paint.xfermode = PorterDuffXfermode(value)
            }
        }

    /**
     * Size of the brush in pixels.
     * When changed, recalculates the scaling parameters for the bitmap.
     */
    override var size: Int = size
        set(value) {
            field = value
            calculateSize(field)
        }

    /**
     * Color of the brush strokes.
     * When changed, updates the color filter to tint the bitmap.
     */
    override var color: Int = color
        set(value) {
            field = value
            paint.colorFilter = PorterDuffColorFilter(field, PorterDuff.Mode.SRC_IN)
        }

    /** Cached scaled version of the bitmap for performance */
    protected lateinit var scaledStamp: Bitmap

    /** Last size used for scaling, used to detect when re-scaling is needed */
    protected var lastSize = 0

    init {
        this.color = color
        this.size = size
    }

    /**
     * Changes the bitmap used for this brush.
     *
     * @param newBitmap The new bitmap to use (can be null)
     * @param recycleCurrentBitmap Whether to recycle the current bitmap to free memory
     */
    open fun changeBrushBitmap(newBitmap: Bitmap?, recycleCurrentBitmap: Boolean) {
        if (recycleCurrentBitmap) {
            brushBitmap?.recycle()
        }

        brushBitmap = newBitmap
        calculateSize(size)
    }

    /**
     * Calculates scaling parameters based on the current brush size.
     *
     * The bitmap is scaled so that its largest dimension matches the brush size,
     * maintaining the original aspect ratio.
     *
     * @param size The target brush size in pixels
     */
    protected open fun calculateSize(size: Int) {
        brushBitmap?.let { bitmap ->
            stampWidth = bitmap.width.toFloat()
            stampHeight = bitmap.height.toFloat()

            // Scale based on the larger dimension to maintain aspect ratio
            stampScale = size / max(stampWidth, stampHeight)

            // Calculate scaled dimensions
            stampScaledWidth = stampWidth * stampScale
            stampScaledHeight = stampHeight * stampScale

            // Calculate half dimensions for centering
            stampScaledWidthHalf = stampScaledWidth * 0.5f
            stampScaledHeightHalf = stampScaledHeight * 0.5f
        }
    }

    /**
     * Draws the bitmap brush stamp on the canvas.
     *
     * The bitmap is drawn centered at the current canvas origin with the
     * specified opacity. If the size has changed since the last draw,
     * a new scaled version of the bitmap is created and cached.
     *
     * @param canvas The canvas to draw on (already positioned and transformed)
     * @param opacity The opacity to apply to the stamp (0-255)
     */
    override fun draw(canvas: Canvas, opacity: Int) {
        if (brushBitmap == null) {
            return // No bitmap to draw
        }

        // Check if we need to create a new scaled bitmap
        if (lastSize != size) {
            lastSize = size

            // Ensure minimum size of 1 pixel
            var w = stampScaledWidth.toInt()
            if (w < 1) w = 1

            var h = stampScaledHeight.toInt()
            if (h < 1) h = 1

            // Create and cache the scaled bitmap
            scaledStamp = brushBitmap!!.scale(w, h, true)
            scaledStamp.prepareToDraw()
        }

        // Apply opacity and draw the bitmap
        paint.alpha = opacity

        canvas.drawBitmap(
            scaledStamp,
            -stampScaledWidthHalf,  // Center horizontally
            -stampScaledHeightHalf, // Center vertically
            paint
        )
    }
}

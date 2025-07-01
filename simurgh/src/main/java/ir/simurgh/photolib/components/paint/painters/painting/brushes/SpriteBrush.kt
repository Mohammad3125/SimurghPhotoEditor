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
import kotlin.random.Random

/**
 * A brush implementation that uses multiple bitmap images to create animated or varied brush strokes.
 *
 * This brush type cycles through a collection of bitmaps, either randomly or sequentially,
 * to create dynamic brush effects. It's perfect for creating natural-looking brushes
 * that vary in texture, such as:
 * - Animated brush effects (sequential mode)
 * - Natural texture variation (random mode)
 * - Multi-texture brushes (like scattered leaves, stars, etc.)
 *
 * Features:
 * - Multiple bitmap support with automatic cycling
 * - Random or sequential bitmap selection
 * - Optional color tinting of bitmap sprites
 * - Automatic scaling and caching for performance
 * - Memory management for bitmap collections
 *
 * @param bitmaps List of bitmaps to use as brush sprites (can be null initially)
 */
open class SpriteBrush(
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
    protected open var bitmaps: List<Bitmap>? = null
) : Brush(
    size,
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
    textureTransformation
) {

    /** Paint object used for rendering the bitmap sprites */
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * Whether to apply color tinting to the bitmap sprites.
     * When enabled, sprites are tinted with the brush color.
     * When disabled, sprites retain their original colors.
     */
    open var isColoringEnabled = false
        set(value) {
            field = value
            if (value) {
                paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            } else {
                paint.colorFilter = null
            }
        }

    /**
     * Color of the brush strokes (used for tinting when coloring is enabled).
     * When changed and coloring is enabled, updates the color filter.
     */
    override var color: Int = color
        set(value) {
            field = value
            if (isColoringEnabled) {
                paint.colorFilter = PorterDuffColorFilter(field, PorterDuff.Mode.SRC_IN)
            }
        }

    // Original bitmap dimensions (based on first bitmap)
    /** Original width of the sprite bitmaps */
    protected var stampWidth = 0f

    /** Original height of the sprite bitmaps */
    protected var stampHeight = 0f

    // Scaled bitmap dimensions
    /** Scaled width of the sprite bitmaps */
    protected var stampScaledWidth = 0f

    /** Scaled height of the sprite bitmaps */
    protected var stampScaledHeight = 0f

    // Half dimensions for centering
    /** Half of the scaled width for centering calculations */
    protected var stampScaledWidthHalf = 0f

    /** Half of the scaled height for centering calculations */
    protected var stampScaledHeightHalf = 0f

    /** Scale factor applied to the bitmaps */
    protected var stampScale = 0f

    /** Cached scaled versions of all sprite bitmaps for performance */
    protected val scaledStamps = mutableListOf<Bitmap>()

    /** Last size used for scaling, used to detect when re-scaling is needed */
    protected var lastSize = 0

    /**
     * Whether to select sprites randomly or sequentially.
     * true = random selection for each stamp
     * false = sequential cycling through sprites
     */
    open var isRandom = true
        set(value) {
            field = value
            counter = 0 // Reset counter when mode changes
        }

    /** Counter for sequential sprite selection */
    protected var counter = 0

    /**
     * Size of the brush in pixels.
     * When changed, recalculates the scaling parameters for all bitmaps.
     */
    override var size: Int = size
        set(value) {
            field = value
            calculateSize(field)
        }

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

    init {
        this.color = color
        this.size = size
    }
    /**
     * Calculates scaling parameters based on the current brush size.
     * Uses the first bitmap in the collection as the reference for scaling.
     *
     * @param size The target brush size in pixels
     */
    protected open fun calculateSize(size: Int) {
        bitmaps?.get(0)?.let { bitmap ->
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
     * Changes the collection of bitmaps used for this sprite brush.
     *
     * @param newBitmaps The new list of bitmaps to use (can be null)
     * @param recycleCurrentBitmaps Whether to recycle the current bitmaps to free memory
     */
    open fun changeBrushes(newBitmaps: List<Bitmap>?, recycleCurrentBitmaps: Boolean) {
        if (recycleCurrentBitmaps) {
            bitmaps?.forEach { it.recycle() }
        }

        bitmaps = newBitmaps
        calculateSize(size)
    }

    /**
     * Draws a sprite brush stamp on the canvas.
     *
     * Selects a bitmap from the collection (randomly or sequentially based on isRandom),
     * scales it if necessary, and draws it centered at the current canvas origin.
     *
     * @param canvas The canvas to draw on (already positioned and transformed)
     * @param opacity The opacity to apply to the stamp (0-255)
     */
    override fun draw(canvas: Canvas, opacity: Int) {
        bitmaps?.let { bitmaps ->
            // Check if we need to create new scaled bitmaps
            if (lastSize != size) {
                lastSize = size

                // Ensure minimum size of 1 pixel
                var w = stampScaledWidth.toInt()
                if (w < 1) w = 1

                var h = stampScaledHeight.toInt()
                if (h < 1) h = 1

                // Create scaled versions of all bitmaps
                scaledStamps.clear()
                scaledStamps.addAll(bitmaps.map { it.scale(w, h, true) })

                // Prepare all scaled bitmaps for efficient drawing
                scaledStamps.forEach { it.prepareToDraw() }
            }

            // Apply opacity
            paint.alpha = opacity

            // Select bitmap based on selection mode
            val finalBitmap = if (isRandom) {
                // Random selection
                scaledStamps[Random.nextInt(0, bitmaps.size)]
            } else {
                // Sequential selection
                val b = scaledStamps[counter]

                // Increment counter and wrap around if necessary
                if (++counter >= bitmaps.size) {
                    counter = 0
                }

                b
            }

            // Draw the selected bitmap centered at the canvas origin
            canvas.drawBitmap(
                finalBitmap,
                -stampScaledWidthHalf,  // Center horizontally
                -stampScaledHeightHalf, // Center vertically
                paint
            )
        }
    }
}

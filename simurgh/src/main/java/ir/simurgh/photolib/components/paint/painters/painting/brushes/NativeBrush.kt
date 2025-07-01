package ir.simurgh.photolib.components.paint.painters.painting.brushes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * A brush implementation that uses native Android drawing primitives.
 *
 * This brush creates smooth, anti-aliased strokes using basic shapes like
 * circles and rectangles. It supports softness/hardness effects through
 * radial gradients, making it ideal for smooth painting and blending.
 *
 * Features:
 * - Circle and rectangle brush shapes
 * - Adjustable softness for smooth edges
 * - Hardware-accelerated rendering
 * - Efficient memory usage (no bitmap caching needed)
 * - Automatic gradient generation for softness
 */
open class NativeBrush(
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
    softness: Float = 0.2f,
    /** The shape type of this brush */
    open var brushShape: BrushShape = BrushShape.CIRCLE
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

    /** Paint object for drawing the brush shapes */
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * Softness of the brush edges (0.0 = hard edges, 1.0 = very soft/feathered edges).
     * When changed, automatically regenerates the radial gradient shader.
     */
    open var softness = softness
        set(value) {
            field = value
            createHardnessShader()
        }

    // Temporary arrays for gradient creation (reused for performance)
    /** Color array holder for gradient creation */
    protected val colorsHolder = IntArray(2)

    /** Position stops array holder for gradient creation */
    protected val stopsHolder = FloatArray(2)

    // Caching variables to avoid unnecessary shader regeneration
    /** Last size used for shader creation */
    protected var lastSize = 0

    /** Last color used for shader creation */
    protected var lastColor = Color.TRANSPARENT

    /** Half of the brush size, used for radius calculations */
    protected var sizeHalf = size * 0.5f

    /**
     * Size of the brush in pixels.
     * When changed, updates the radius and invalidates the shader cache.
     */
    override var size: Int = size
        set(value) {
            field = value
            sizeHalf = value * 0.5f
        }

    /** The radial gradient shader used for softness effects */
    protected open var hardnessShader: RadialGradient? = null

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
        this.size = size
        this.softness = softness
    }

    /**
     * Creates a radial gradient shader for softness effects.
     *
     * The gradient transitions from the full brush color at the center
     * to transparent at the edges, with the transition point controlled
     * by the softness property.
     */
    protected open fun createHardnessShader() {
        if (sizeHalf == 0f) {
            return // Can't create shader with zero radius
        }

        // Set up gradient colors (solid to transparent)
        colorsHolder[0] = color
        colorsHolder[1] = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))

        // Set up gradient stops (softness controls transition point)
        stopsHolder[0] = 1f - softness  // Where transition begins
        stopsHolder[1] = 1f            // Where it ends (edge)

        // Create the radial gradient
        hardnessShader = RadialGradient(
            0f,         // Center X
            0f,         // Center Y
            sizeHalf,   // Radius
            colorsHolder,
            stopsHolder,
            Shader.TileMode.CLAMP
        )

        paint.shader = hardnessShader
    }

    /**
     * Draws the native brush stamp on the canvas.
     *
     * For circle brushes, creates a radial gradient if size or color changed.
     * For rectangle brushes, uses solid color fill.
     *
     * @param canvas The canvas to draw on (already positioned and transformed)
     * @param opacity The opacity to apply to the stamp (0-255)
     */
    override fun draw(canvas: Canvas, opacity: Int) {
        // Check if we need to regenerate the gradient shader for circles
        if ((lastSize != size || lastColor != color) && brushShape == BrushShape.CIRCLE) {
            lastSize = size
            lastColor = color
            createHardnessShader()
        } else if (brushShape != BrushShape.CIRCLE) {
            // Rectangle brushes don't use gradients
            paint.shader = null
            paint.color = color
        }

        // Apply opacity
        paint.alpha = opacity

        // Draw the appropriate shape
        if (brushShape == BrushShape.CIRCLE) {
            canvas.drawCircle(0f, 0f, sizeHalf, paint)
        } else {
            canvas.drawRect(-sizeHalf, -sizeHalf, sizeHalf, sizeHalf, paint)
        }
    }

    /**
     * Enumeration of available brush shapes for native brushes.
     */
    enum class BrushShape {
        /** Rectangular brush shape */
        RECT,

        /** Circular brush shape with gradient softness support */
        CIRCLE
    }
}

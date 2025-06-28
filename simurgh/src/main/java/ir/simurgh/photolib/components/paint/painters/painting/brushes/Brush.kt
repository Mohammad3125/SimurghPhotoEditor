package ir.simurgh.photolib.components.paint.painters.painting.brushes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import androidx.annotation.ColorInt

/**
 * Abstract base class for all brush implementations in the painting system.
 *
 * This class defines the common properties and behavior that all brushes share,
 * including size, color, opacity, pressure sensitivity, spacing, and various
 * visual effects like jitter, scatter, and hue manipulation.
 *
 * Concrete implementations must define how to draw the brush stroke and
 * handle the specific blending mode for their rendering approach.
 */
abstract class Brush(
    /** Base size of the brush in pixels */
    open var size: Int = 1,
    /** Primary color of the brush strokes */
    @field:ColorInt open var color: Int = Color.BLACK,
    /** Base opacity of the brush (0.0 = transparent, 1.0 = opaque) */
    open var opacity: Float = 1f,
    /** Random opacity variation amount (0.0 = no jitter, 1.0 = full random opacity) */
    open var opacityJitter: Float = 0f,
    /** Speed-based opacity variance (-1.0 to 1.0, negative decreases opacity with speed) */
    open var opacityVariance: Float = 0f,
    /** Speed at which opacity variance responds to movement changes */
    open var opacityVarianceSpeed: Float = 0.6f,
    /** How quickly opacity variance transitions smooth out (lower = smoother) */
    open var opacityVarianceEasing: Float = 0.1f,
    /** How much brush size responds to pressure changes (0.0 = no response, 1.0 = full response) */
    open var sizePressureSensitivity: Float = 0.6f,
    /** Minimum size multiplier when pressure is at minimum */
    open var minimumPressureSize: Float = 0.3f,
    /** Maximum size multiplier when pressure is at maximum */
    open var maximumPressureSize: Float = 1f,
    /** Whether brush size should respond to pressure changes */
    open var isSizePressureSensitive: Boolean = false,
    /** How much brush opacity responds to pressure changes */
    open var opacityPressureSensitivity: Float = 0.5f,
    /** Minimum opacity multiplier when pressure is at minimum */
    open var minimumPressureOpacity: Float = 0f,
    /** Maximum opacity multiplier when pressure is at maximum */
    open var maximumPressureOpacity: Float = 1f,
    /** Whether brush opacity should respond to pressure changes */
    open var isOpacityPressureSensitive: Boolean = false,
    /** Spacing between brush stamps as fraction of brush size (0.1 = close together, 1.0 = far apart) */
    open var spacing: Float = 0.1f,
    /** Random position offset for brush stamps */
    open var scatter: Float = 0f,
    /** Base rotation angle for brush stamps in degrees */
    open var angle: Float = 0f,
    /** Random angle variation in degrees (0.0 = no jitter, 1.0 = full 360° random) */
    open var angleJitter: Float = 0f,
    /** Random size variation (0.0 = no jitter, 1.0 = up to 100% size variation) */
    open var sizeJitter: Float = 0f,
    /** Speed-based size variance (1.0 = no change, <1.0 decreases with speed, >1.0 increases) */
    open var sizeVariance: Float = 1f,
    /** How sensitive size variance is to movement speed changes */
    open var sizeVarianceSensitivity: Float = 0.1f,
    /** How quickly size variance transitions smooth out */
    open var sizeVarianceEasing: Float = 0.08f,
    /** Brush squish factor (0.0 = normal, 1.0 = completely flattened vertically) */
    open var squish: Float = 0f,
    /** Random hue shift amount in degrees (0 = no shift, 360 = full spectrum) */
    open var hueJitter: Int = 0,
    /** Line smoothing amount (0.0 = no smoothing, higher values = more smoothing) */
    open var smoothness: Float = 0f,
    /** Whether to use alpha blending mode for smoother opacity mixing */
    open var alphaBlend: Boolean = false,
    /** Whether brush should automatically rotate based on stroke direction */
    open var autoRotate: Boolean = false,
    /** Speed of hue color cycling (0.0 = no flow, higher = faster cycling) */
    open var hueFlow: Float = 0f,
    /** Maximum hue shift distance in degrees for hue flow oscillation */
    open var hueDistance: Int = 0,
    /** Speed at which stroke tapers in at the beginning */
    open var startTaperSpeed: Float = 0.03f,
    /** Initial size multiplier for stroke tapering (< 1.0 = starts small, > 1.0 = starts large) */
    open var startTaperSize: Float = 1f,
    /** Optional texture bitmap to apply to brush strokes */
    open var texture: Bitmap? = null,
    /** Transformation matrix for texture positioning and scaling */
    open var textureTransformation: Matrix? = null
) {

    /**
     * Calculated spacing width in pixels.
     * This is automatically computed based on size and spacing properties.
     */
    var spacedWidth: Float = 0.0f
        get() = size * spacing
        private set

    /** The blending mode used when drawing this brush to the canvas */
    internal abstract var brushBlending: PorterDuff.Mode

    /**
     * Draws a single brush stamp on the provided canvas.
     *
     * This method is called for each point along a stroke and should render
     * the brush at the current canvas transformation state with the specified opacity.
     *
     * @param canvas The canvas to draw on (already transformed to brush position)
     * @param opacity The opacity to use for this stamp (0-255)
     */
    abstract fun draw(canvas: Canvas, opacity: Int)
}

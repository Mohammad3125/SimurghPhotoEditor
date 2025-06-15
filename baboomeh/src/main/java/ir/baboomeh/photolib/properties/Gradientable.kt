package ir.baboomeh.photolib.properties

import android.graphics.Shader

/**
 * Interface for objects that support gradient coloring capabilities.
 *
 * This interface extends [ComplexColor] to provide gradient-specific functionality
 * including linear, radial, and sweep gradients. Objects implementing this interface
 * can have gradients applied instead of solid colors.
 *
 * Supported gradient types:
 * - **Linear Gradient**: Color transitions along a straight line
 * - **Radial Gradient**: Color transitions radiating from a center point
 * - **Sweep Gradient**: Color transitions rotating around a center point
 *
 * All gradient methods support:
 * - Custom color arrays with multiple color stops
 * - Position arrays to control color distribution
 * - Tile modes for handling areas outside the gradient bounds
 *
 * Example usage:
 * ```kotlin
 * val gradientable: Gradientable = textPainter
 *
 * // Apply a linear gradient from red to blue
 * gradientable.applyLinearGradient(
 *     x0 = 0f, y0 = 0f, x1 = 100f, y1 = 0f,
 *     colors = intArrayOf(Color.RED, Color.BLUE),
 *     position = null // Even distribution
 * )
 *
 * // Apply a radial gradient with multiple colors
 * gradientable.applyRadialGradient(
 *     centerX = 50f, centerY = 50f, radius = 50f,
 *     colors = intArrayOf(Color.WHITE, Color.GRAY, Color.BLACK),
 *     stops = floatArrayOf(0f, 0.5f, 1f)
 * )
 *
 * // Transform the gradient
 * gradientable.rotateColor(45f)
 * gradientable.scaleColor(1.5f)
 *
 * // Remove the gradient
 * gradientable.removeGradient()
 * ```
 */
interface Gradientable : ComplexColor {

    /**
     * Creates and applies a linear gradient shader.
     *
     * Linear gradients transition colors along a straight line defined by
     * start and end points. The gradient can have multiple color stops
     * at specific positions along the line.
     *
     * @param x0 X coordinate of the gradient start point
     * @param y0 Y coordinate of the gradient start point
     * @param x1 X coordinate of the gradient end point
     * @param y1 Y coordinate of the gradient end point
     * @param colors Array of sRGB colors to distribute along the gradient line.
     *              Must contain at least 2 colors.
     * @param position Array of relative positions [0..1] for each color.
     *                May be null for even distribution. If provided, must have
     *                the same length as colors array.
     * @param tileMode How to handle areas outside the gradient bounds.
     *                Default is MIRROR for smooth transitions.
     *
     * @throws IllegalArgumentException if colors array has less than 2 colors
     * @throws IllegalArgumentException if position array length doesn't match colors length
     */
    fun applyLinearGradient(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        colors: IntArray,
        position: FloatArray?,
        tileMode: Shader.TileMode = Shader.TileMode.MIRROR
    )

    /**
     * Creates and applies a radial gradient shader.
     *
     * Radial gradients transition colors radiating outward from a center point.
     * The gradient forms concentric circles with colors transitioning from
     * the center to the edge.
     *
     * @param centerX X coordinate of the gradient center
     * @param centerY Y coordinate of the gradient center
     * @param radius Radius of the gradient circle. Must be positive.
     * @param colors Array of sRGB colors to distribute from center to edge.
     *              Must contain at least 2 colors.
     * @param stops Array of relative positions [0..1] for each color.
     *             May be null for even distribution. Position 0 is at the center,
     *             position 1 is at the edge. If provided, must have the same
     *             length as colors array and values must be monotonic.
     * @param tileMode How to handle areas outside the gradient radius.
     *                Default is MIRROR for smooth transitions.
     *
     * @throws IllegalArgumentException if radius is not positive
     * @throws IllegalArgumentException if colors array has less than 2 colors
     * @throws IllegalArgumentException if stops array length doesn't match colors length
     * @throws IllegalArgumentException if stops array values are not monotonic
     */
    fun applyRadialGradient(
        centerX: Float,
        centerY: Float,
        radius: Float,
        colors: IntArray,
        stops: FloatArray?,
        tileMode: Shader.TileMode = Shader.TileMode.MIRROR
    )

    /**
     * Creates and applies a sweep (angular) gradient shader.
     *
     * Sweep gradients transition colors in a circular pattern around a center point,
     * similar to a color wheel. The gradient starts at the 3 o'clock position
     * and sweeps clockwise.
     *
     * @param cx X coordinate of the gradient center
     * @param cy Y coordinate of the gradient center
     * @param colors Array of sRGB colors to distribute around the center.
     *              Must contain at least 2 colors.
     * @param positions Array of relative positions [0..1] for each color.
     *                 May be null for even angular distribution. Position 0
     *                 corresponds to 3 o'clock, position 1 to a full rotation.
     *                 If provided, must have the same length as colors array
     *                 and values must be monotonic.
     *
     * @throws IllegalArgumentException if colors array has less than 2 colors
     * @throws IllegalArgumentException if positions array length doesn't match colors length
     * @throws IllegalArgumentException if positions array values are not monotonic
     */
    fun applySweepGradient(
        cx: Float,
        cy: Float,
        colors: IntArray,
        positions: FloatArray?,
    )

    /**
     * Checks if a gradient is currently applied to this object.
     *
     * @return true if any gradient (linear, radial, or sweep) is applied,
     *         false if using solid color or no gradient
     */
    fun isGradientApplied(): Boolean

    /**
     * Returns the current gradient color positions.
     *
     * @return Array of relative positions [0..1] for gradient colors,
     *         or null if no gradient is applied or positions weren't specified
     */
    fun reportPositions(): FloatArray?

    /**
     * Returns the current gradient colors.
     *
     * @return Array of sRGB color values used in the gradient,
     *         or null if no gradient is applied
     */
    fun reportColors(): IntArray?

    /**
     * Removes any applied gradient and reverts to solid color.
     *
     * After calling this method:
     * - [isGradientApplied] will return false
     * - [reportPositions] and [reportColors] will return null
     * - The object will use its base color for rendering
     */
    fun removeGradient()
}

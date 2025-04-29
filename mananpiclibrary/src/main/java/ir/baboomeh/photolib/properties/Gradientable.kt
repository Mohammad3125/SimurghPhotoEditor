package ir.baboomeh.photolib.properties

import android.graphics.Shader

/**
 * Interface definition for a view that is capable of gradient coloring.
 */
interface Gradientable : ComplexColor {

    /**
     * Create a shader that draws a linear gradient along a line.
     *
     * @param x0           The x-coordinate for the start of the gradient line
     * @param y0           The y-coordinate for the start of the gradient line
     * @param x1           The x-coordinate for the end of the gradient line
     * @param y1           The y-coordinate for the end of the gradient line
     * @param colors       The sRGB colors to be distributed along the gradient line
     * @param position    May be null. The relative positions [0..1] of
     *                     each corresponding color in the colors array. If this is null,
     *                     the the colors are distributed evenly along the gradient line.
     * @param tileMode         The Shader tiling mode
     * @param rotation     Rotation of gradient.
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
     * Create a shader that draws a radial gradient given the center and radius.
     *
     * @param centerX  The x-coordinate of the center of the radius
     * @param centerY  The y-coordinate of the center of the radius
     * @param radius   Must be positive. The radius of the circle for this gradient.
     * @param colors   The sRGB colors to be distributed between the center and edge of the circle
     * @param stops    May be <code>null</code>. Valid values are between <code>0.0f</code> and
     *                 <code>1.0f</code>. The relative position of each corresponding color in
     *                 the colors array. If <code>null</code>, colors are distributed evenly
     *                 between the center and edge of the circle.
     * @param tileMode The Shader tiling mode
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
     * A Shader that draws a sweep gradient around a given point.
     *
     * @param cx       The x-coordinate of the center
     * @param cy       The y-coordinate of the center
     * @param colors   The sRGB colors to be distributed between around the center.
     *                 There must be at least 2 colors in the array.
     * @param positions May be NULL. The relative position of
     *                 each corresponding color in the colors array, beginning
     *                 with 0 and ending with 1.0. If the values are not
     *                 monotonic, the drawing may produce unexpected results.
     *                 If positions is NULL, then the colors are automatically
     *                 spaced evenly.
     *
     */
    fun applySweepGradient(
        cx: Float,
        cy: Float,
        colors: IntArray,
        positions: FloatArray?,
    )

    fun isGradientApplied(): Boolean


    fun reportPositions() : FloatArray?


    fun reportColors() : IntArray?


    /**
     * Removes any gradient that's been applied to the target.
     */
    fun removeGradient()
}
package ir.maneditor.mananpiclibrary.properties

import android.graphics.Shader

/**
 * Interface definition for a view that is capable of gradient coloring.
 */
interface Gradientable {

    /**
     * Applies a linear gradient to the view.
     * @param x0 Starting point of gradient in x direction.
     * @param y0 Starting point of gradient in y direction.
     * @param x1 Ending point of gradient in x direction.
     * @param y1 Ending point of gradient in y direction.
     * @param colors This parameter represents the colors of gradient.
     * @param position This parameter defines the position of each color (can be null to distribute the colors evenly).
     * @param tileMode This parameter represents the tile mode of shader.
     *  @param rotation The rotation to be applied to the gradient. default is 0.
     * @see android.graphics.LinearGradient
     */
    fun applyLinearGradient(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        colors: IntArray,
        position: FloatArray?,
        tileMode: Shader.TileMode = Shader.TileMode.MIRROR,
        rotation: Float = 0f
    )


    /**
     * Applies a radial gradient to the view.
     * @param centerX  Defines the center of gradient on x direction.
     * @param centerY  Defines the center of gradient on y direction.
     * @param radius  Defines the radius of gradient.
     * @param colors  Represents the colors of gradient.
     * @param stops The relative position of each corresponding color in the colors array. If null, colors are distributed evenly between the center and edge of the circle. This value may be null.
     * @param tileMode Represents the tile mode of shader.
     * @param rotation The rotation to be applied to the gradient. default is 0.
     * @see android.graphics.RadialGradient
     */
    fun applyRadialGradient(
        centerX: Float,
        centerY: Float,
        radius: Float,
        colors: IntArray,
        stops: FloatArray?,
        tileMode: Shader.TileMode = Shader.TileMode.MIRROR,
        rotation: Float = 0f
    )


    /**
     * Applies a sweep gradient to the view.
     * @param cx The x-coordinate of the center.
     * @param cy The y-coordinate of the center.
     * @param colors Represents the colors of gradient.
     * @param positions May be NULL. The relative position of each corresponding color in the colors array, beginning with 0 and ending with 1.0. If the values are not monotonic, the drawing may produce unexpected results. If positions is NULL, then the colors are automatically spaced evenly. This value may be null.
     * @param rotation The rotation to be applied to the gradient. default is 0.
     * @see android.graphics.SweepGradient
     */
    fun applySweepGradient(
        cx: Float,
        cy: Float,
        colors: IntArray,
        positions: FloatArray?,
        rotation: Float = 0f
    )


    /**
     * Removes any gradient that's been applied to the target.
     */
    fun removeGradient()
}
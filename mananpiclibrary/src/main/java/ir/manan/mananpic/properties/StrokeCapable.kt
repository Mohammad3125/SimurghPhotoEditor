package ir.manan.mananpic.properties

import android.util.TypedValue
import androidx.annotation.ColorInt

/**
 * Interface definition for a class that is capable of applying stroke on itself.
 */
interface StrokeCapable {
    /**
     * Sets stroke in pixels. Stroke of 0f means no stroke is applied.
     * Stroke values should be more than 0 (negative values are not accepted.)
     *
     *
     * Use [TypedValue] or [ir.manan.mananpic.utils.dp] extension function to convert a dp number to pixels.
     * @param strokeRadiusPx Stroke radius in pixels.
     * @param strokeColor Color of stroke.
     */
    fun setStroke(strokeRadiusPx: Float, @ColorInt strokeColor: Int)

    fun getStrokeColor(): Int

    fun getStrokeWidth() : Float

}
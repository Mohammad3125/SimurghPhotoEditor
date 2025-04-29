package ir.baboomeh.photolib.properties

import androidx.annotation.ColorInt

interface Backgroundable {
    fun setBackground(padding: Float, radius: Float, @ColorInt color: Int)

    fun getBackgroundPadding(): Float

    fun getBackgroundRadius(): Float

    @ColorInt
    fun getBackgroundColor(): Int

    fun setBackgroundState(isEnabled: Boolean)

    fun getBackgroundState(): Boolean
}
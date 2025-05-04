package ir.baboomeh.photolib.properties

import androidx.annotation.ColorInt

interface Shadowable {
    fun getShadowDx(): Float

    fun getShadowDy(): Float

    fun getShadowRadius(): Float

    @ColorInt
    fun getShadowColor(): Int

    fun setShadow(radius: Float, dx: Float, dy: Float, @ColorInt shadowColor: Int)

    fun clearShadow()
}

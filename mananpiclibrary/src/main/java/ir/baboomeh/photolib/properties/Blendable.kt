package ir.baboomeh.photolib.properties

import android.graphics.PorterDuff

interface Blendable {
    fun setBlendMode(blendMode: PorterDuff.Mode)

    fun clearBlend()

    fun getBlendMode() : PorterDuff.Mode
}
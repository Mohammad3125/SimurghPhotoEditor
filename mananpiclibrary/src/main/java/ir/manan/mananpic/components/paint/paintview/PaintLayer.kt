package ir.manan.mananpic.components.paint.paintview

import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

data class PaintLayer(
    var bitmap: Bitmap,
    var isLocked: Boolean = false,
    var opacity: Float = 1f,
) {
    var blendingMode = PorterDuff.Mode.SRC
        set(value) {
            field = value
            blendingModeObject = if (blendingMode != PorterDuff.Mode.SRC) {
                PorterDuffXfermode(blendingMode)
            } else {
                null
            }
        }
    var blendingModeObject: PorterDuffXfermode? = null

    fun clone(cloneBitmap: Boolean): PaintLayer {
        return PaintLayer(
            if (cloneBitmap) bitmap.copy(
                bitmap.config ?: Bitmap.Config.ARGB_8888,
                true
            ) else bitmap,
            isLocked,
            opacity
        ).also { copied ->
            copied.blendingMode = blendingMode
        }
    }

    fun set(otherLayer: PaintLayer) {
        bitmap = otherLayer.bitmap
        isLocked = otherLayer.isLocked
        opacity = otherLayer.opacity
        blendingMode = otherLayer.blendingMode
    }

    override fun equals(other: Any?): Boolean {
        other as PaintLayer
        return (bitmap === other.bitmap) &&
                (isLocked == other.isLocked) &&
                (opacity == other.opacity) &&
                (blendingMode == other.blendingMode)
    }

    override fun hashCode(): Int {
        var result = bitmap.hashCode()
        result = 31 * result + isLocked.hashCode()
        result = 31 * result + opacity.hashCode()
        result = 31 * result + blendingMode.hashCode()
        return result
    }
}

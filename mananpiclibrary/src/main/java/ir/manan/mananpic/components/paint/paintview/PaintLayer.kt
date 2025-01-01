package ir.manan.mananpic.components.paint.paintview

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

data class PaintLayer(
    var bitmap: Bitmap,
    val layerMatrix: Matrix,
    var isLocked: Boolean = false,
    var opacity: Float,
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

    fun clone(shouldCloneBitmap: Boolean): PaintLayer {
        return PaintLayer(
            if (shouldCloneBitmap) bitmap.copy(
                bitmap.config ?: Bitmap.Config.ARGB_8888,
                true
            ) else bitmap,
            Matrix(layerMatrix),
            isLocked,
            opacity
        ).also { copied ->
            copied.blendingMode = blendingMode
        }
    }

    fun set(otherLayer: PaintLayer) {
        bitmap = otherLayer.bitmap
        layerMatrix.set(otherLayer.layerMatrix)
        isLocked = otherLayer.isLocked
        opacity = otherLayer.opacity
        blendingModeObject = otherLayer.blendingModeObject
    }

    override fun equals(other: Any?): Boolean {
        other as PaintLayer
        return (bitmap.sameAs(other.bitmap)) &&
                (isLocked == other.isLocked) &&
                (opacity == other.opacity) &&
                (blendingModeObject == other.blendingModeObject)
    }

    override fun hashCode(): Int {
        var result = bitmap.hashCode()
        result = 31 * result + layerMatrix.hashCode()
        result = 31 * result + isLocked.hashCode()
        result = 31 * result + opacity.hashCode()
        result = 31 * result + blendingModeObject.hashCode()
        return result
    }
}

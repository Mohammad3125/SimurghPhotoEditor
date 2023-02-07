package ir.manan.mananpic.components.paint

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

data class PaintLayer(
    var bitmap: Bitmap,
    val layerMatrix: Matrix,
    var isLocked: Boolean = false,
    var opacity: Float,
    var blendMode: PorterDuffXfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
) {
    fun clone(): PaintLayer {
        return PaintLayer(
            bitmap.copy(bitmap.config, true),
            Matrix(layerMatrix),
            isLocked,
            opacity,
            blendMode
        )
    }

    fun set(otherLayer: PaintLayer) {
        bitmap = otherLayer.bitmap
        layerMatrix.set(otherLayer.layerMatrix)
        isLocked = otherLayer.isLocked
        opacity = otherLayer.opacity
        blendMode = otherLayer.blendMode
    }

    override fun equals(other: Any?): Boolean {
        other as PaintLayer
        return (bitmap.sameAs(other.bitmap)) &&
                (isLocked == other.isLocked) &&
                (opacity == other.opacity) &&
                (blendMode == other.blendMode)
    }

    override fun hashCode(): Int {
        var result = bitmap.hashCode()
        result = 31 * result + layerMatrix.hashCode()
        result = 31 * result + isLocked.hashCode()
        result = 31 * result + opacity.hashCode()
        result = 31 * result + blendMode.hashCode()
        return result
    }
}

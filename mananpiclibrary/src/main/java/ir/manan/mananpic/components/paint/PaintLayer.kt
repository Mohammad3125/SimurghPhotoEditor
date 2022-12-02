package ir.manan.mananpic.components.paint

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PorterDuff

data class PaintLayer(
    var bitmap: Bitmap,
    val layerMatrix: Matrix,
    var isLocked: Boolean = false,
    var opacity: Float,
    var porterDuffMode: PorterDuff.Mode
) {
    fun clone(): PaintLayer {
        return PaintLayer(
            bitmap.copy(bitmap.config, true),
            Matrix(layerMatrix),
            isLocked,
            opacity,
            porterDuffMode
        )
    }
}

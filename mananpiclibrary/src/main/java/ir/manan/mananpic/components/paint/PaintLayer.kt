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
}

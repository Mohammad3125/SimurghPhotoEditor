package ir.baboomeh.photolib.components.paint.painters.selection.clippers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

open class BitmapMaskClipper(bitmap: Bitmap?, var maskBitmap: Bitmap?) : Clipper(bitmap) {

    constructor() : this(null, null)

    protected val bitmapPaint by lazy {
        Paint()
    }

    protected val canvasOperation by lazy {
        Canvas()
    }

    override fun clip() {
        doIfBitmapAndMaskNotNull { bit, mask ->
            changePaintPorterDuffMode(PorterDuff.Mode.DST_OUT) {
                drawMaskLayer(bit, mask)
            }
        }
    }

    override fun cut(): Bitmap? {
        val copiedBitmap = copy()

        doIfBitmapAndMaskNotNull { bit, mask ->
            if (isMaskEmpty(mask)) {
                return null
            }
            changePaintPorterDuffMode(PorterDuff.Mode.DST_OUT) {
                drawMaskLayer(bit, mask)
            }
        }

        return copiedBitmap
    }

    override fun copy(): Bitmap? {
        doIfBitmapAndMaskNotNull { bit, mask ->
            if (isMaskEmpty(mask)) {
                return null
            }

            val copy = bit.copy(bit.config ?: Bitmap.Config.ARGB_8888, true)

            changePaintPorterDuffMode(PorterDuff.Mode.DST_IN) {
                drawMaskLayer(copy, mask)
            }

            return copy
        }
        return null
    }

    protected open fun isMaskEmpty(mask: Bitmap): Boolean {
        //Quickly check if mask is empty.
        mask.copy(mask.config ?: Bitmap.Config.ARGB_8888, true).apply {
            eraseColor(Color.TRANSPARENT)
        }.also { emptyMask ->
            return emptyMask.sameAs(mask)
        }
    }

    protected open fun drawMaskLayer(targetBitmap: Bitmap, mask: Bitmap) {
        canvasOperation.setBitmap(targetBitmap)
        canvasOperation.drawBitmap(mask, 0f, 0f, bitmapPaint)
    }

    protected inline fun changePaintPorterDuffMode(
        mode: PorterDuff.Mode,
        operation: () -> Unit
    ) {
        bitmapPaint.xfermode = PorterDuffXfermode(mode)
        operation()
        bitmapPaint.xfermode = null
    }

    protected inline fun doIfBitmapAndMaskNotNull(operation: (bit: Bitmap, mask: Bitmap) -> Unit) {
        bitmap?.let { bit ->
            maskBitmap?.let { mask ->
                operation(bit, mask)
                return
            }
        }
        throw IllegalStateException("bitmap or mask bitmap was null")
    }


    override fun getClippingBounds(rect: RectF) {
    }
}
package ir.manan.mananpic.components.paint.painters.selection.clippers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

class BitmapMaskClipper(bitmap: Bitmap?, var maskBitmap: Bitmap?) : Clipper(bitmap) {

    constructor() : this(null, null)

    private val bitmapPaint by lazy {
        Paint()
    }

    private val canvasOperation by lazy {
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
            changePaintPorterDuffMode(PorterDuff.Mode.DST_OUT) {
                drawMaskLayer(bit, mask)
            }
        }

        return copiedBitmap
    }

    override fun copy(): Bitmap? {
        doIfBitmapAndMaskNotNull { bit, mask ->

            val copy = bit.copy(bit.config, true)

            changePaintPorterDuffMode(PorterDuff.Mode.DST_IN) {
                drawMaskLayer(copy, mask)
            }

            return copy
        }
        return null
    }

    private fun drawMaskLayer(targetBitmap: Bitmap, mask: Bitmap) {
        canvasOperation.setBitmap(targetBitmap)
        canvasOperation.drawBitmap(mask, 0f, 0f, bitmapPaint)
    }

    private inline fun changePaintPorterDuffMode(mode: PorterDuff.Mode, operation: () -> Unit) {
        bitmapPaint.xfermode = PorterDuffXfermode(mode)
        operation()
        bitmapPaint.xfermode = null
    }

    private inline fun doIfBitmapAndMaskNotNull(operation: (bit: Bitmap, mask: Bitmap) -> Unit) {
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
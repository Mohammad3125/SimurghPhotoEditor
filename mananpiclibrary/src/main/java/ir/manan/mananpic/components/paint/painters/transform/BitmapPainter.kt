package ir.manan.mananpic.components.paint.painters.transform

import android.graphics.*
import ir.manan.mananpic.properties.Blendable

class BitmapPainter(var bitmap: Bitmap) : Transformable(), Blendable {

    private val bitmapPaint = Paint().apply {
        isFilterBitmap = true
    }

    private var blendMode: PorterDuff.Mode = PorterDuff.Mode.SRC_OVER

    override fun getBounds(bounds: RectF) {
        bounds.set(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
    }

    override fun draw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)
    }

    override fun setBlendMode(blendMode: PorterDuff.Mode) {
        bitmapPaint.xfermode = PorterDuffXfermode(blendMode)
        this.blendMode = blendMode
        invalidate()
    }

    override fun clearBlend() {
        bitmapPaint.xfermode = null
        blendMode = PorterDuff.Mode.SRC
        invalidate()
    }

    override fun clone(): Transformable {
        return BitmapPainter(bitmap).also {
            if (blendMode != PorterDuff.Mode.SRC) {
                it.setBlendMode(blendMode)
            }
        }
    }

    override fun getBlendMode(): PorterDuff.Mode {
        return blendMode
    }
}
package ir.baboomeh.photolib.components.paint.painters.brushpaint.brushes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import androidx.core.graphics.scale
import kotlin.math.max

open class BitmapBrush(
    open var brushBitmap: Bitmap? = null
) : Brush() {

    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
    }

    protected var stampWidth = 0f
    protected var stampHeight = 0f

    protected var stampScaledWidth = 0f
    protected var stampScaledHeight = 0f

    protected var stampScaledWidthHalf = 0f
    protected var stampScaledHeightHalf = 0f

    protected var stampScale = 0f

    override var brushBlending: PorterDuff.Mode = PorterDuff.Mode.SRC_OVER
        set(value) {
            field = value
            if (field == PorterDuff.Mode.SRC_OVER) {
                paint.xfermode = null
            } else {
                paint.xfermode = PorterDuffXfermode(value)
            }
        }

    override var size: Int = 14
        set(value) {
            field = value
            calculateSize(field)
        }

    override var color: Int = Color.BLACK
        set(value) {
            field = value
            paint.colorFilter = PorterDuffColorFilter(field, PorterDuff.Mode.SRC_IN)
        }

    protected lateinit var scaledStamp: Bitmap

    protected var lastSize = 0

    open fun changeBrushBitmap(newBitmap: Bitmap?, recycleCurrentBitmap: Boolean) {
        if (recycleCurrentBitmap) {
            brushBitmap?.recycle()
        }

        brushBitmap = newBitmap
        calculateSize(size)
    }

    protected open fun calculateSize(size: Int) {
        brushBitmap?.let { bitmap ->
            stampWidth = bitmap.width.toFloat()
            stampHeight = bitmap.height.toFloat()

            stampScale = size / max(stampWidth, stampHeight)

            stampScaledWidth = stampWidth * stampScale
            stampScaledHeight = stampHeight * stampScale

            stampScaledWidthHalf = stampScaledWidth * 0.5f
            stampScaledHeightHalf = stampScaledHeight * 0.5f
        }


    }

    override fun draw(canvas: Canvas, opacity: Int) {
        if (brushBitmap == null) {
            return
        }

        if (lastSize != size) {
            lastSize = size
            var w = stampScaledWidth.toInt()
            if (w < 1) w = 1

            var h = stampScaledHeight.toInt()
            if (h < 1) h = 1

            scaledStamp = brushBitmap!!.scale(w, h, true)

            scaledStamp.prepareToDraw()
        }

        paint.alpha = opacity

        canvas.drawBitmap(
            scaledStamp,
            -stampScaledWidthHalf,
            -stampScaledHeightHalf,
            paint
        )

    }

}

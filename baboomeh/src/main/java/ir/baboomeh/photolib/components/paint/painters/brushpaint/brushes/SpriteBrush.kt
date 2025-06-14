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
import kotlin.random.Random

open class SpriteBrush(protected var bitmaps: List<Bitmap>? = null) : Brush() {

    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    var isColoringEnabled = false
        set(value) {
            field = value
            if (value) {
                paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            } else {
                paint.colorFilter = null
            }
        }

    override var color: Int = Color.BLACK
        set(value) {
            field = value
            if (isColoringEnabled) {
                paint.colorFilter = PorterDuffColorFilter(field, PorterDuff.Mode.SRC_IN)
            }
        }

    protected var stampWidth = 0f
    protected var stampHeight = 0f

    protected var stampScaledWidth = 0f
    protected var stampScaledHeight = 0f

    protected var stampScaledWidthHalf = 0f
    protected var stampScaledHeightHalf = 0f

    protected var stampScale = 0f

    protected val scaledStamps = mutableListOf<Bitmap>()

    protected var lastSize = 0

    var isRandom = true
        set(value) {
            field = value
            counter = 0
        }

    protected var counter = 0

    override var size: Int = 14
        set(value) {
            field = value
            calculateSize(field)
        }

    override var brushBlending: PorterDuff.Mode = PorterDuff.Mode.SRC_OVER
        set(value) {
            field = value
            if (field == PorterDuff.Mode.SRC_OVER) {
                paint.xfermode = null
            } else {
                paint.xfermode = PorterDuffXfermode(value)
            }
        }

    private fun calculateSize(size: Int) {
        bitmaps?.get(0)?.let { bitmap ->
            stampWidth = bitmap.width.toFloat()
            stampHeight = bitmap.height.toFloat()

            stampScale = size / max(stampWidth, stampHeight)

            stampScaledWidth = stampWidth * stampScale
            stampScaledHeight = stampHeight * stampScale

            stampScaledWidthHalf = stampScaledWidth * 0.5f
            stampScaledHeightHalf = stampScaledHeight * 0.5f
        }


    }

    open fun changeBrushes(newBitmaps: List<Bitmap>?, recycleCurrentBitmaps: Boolean) {
        if (recycleCurrentBitmaps) {
            bitmaps?.forEach { it.recycle() }
        }

        bitmaps = newBitmaps
        calculateSize(size)
    }


    override fun draw(canvas: Canvas, opacity: Int) {
        bitmaps?.let { bitmaps ->
            if (lastSize != size) {
                lastSize = size
                var w = stampScaledWidth.toInt()
                if (w < 1) w = 1

                var h = stampScaledHeight.toInt()
                if (h < 1) h = 1

                scaledStamps.clear()
                scaledStamps.addAll(bitmaps.map { it.scale(w, h, true) })
                scaledStamps.forEach { it.prepareToDraw() }
            }

            paint.alpha = opacity

            val finalBitmap = if (isRandom) {
                scaledStamps[Random.nextInt(0, bitmaps.size)]
            } else {
                val b = scaledStamps[counter]

                if (++counter >= bitmaps.size) {
                    counter = 0
                }

                b
            }

            canvas.drawBitmap(
                finalBitmap,
                -stampScaledWidthHalf,
                -stampScaledHeightHalf,

                paint
            )

        }
    }
}
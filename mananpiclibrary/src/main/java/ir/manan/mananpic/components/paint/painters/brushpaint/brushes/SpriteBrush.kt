package ir.manan.mananpic.components.paint.painters.brushpaint.brushes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.scale
import kotlin.math.max
import kotlin.random.Random

class SpriteBrush(var bitmaps: List<Bitmap>?) : Brush() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var stampWidth = 0f
    private var stampHeight = 0f

    private var stampScaledWidth = 0f
    private var stampScaledHeight = 0f

    private var stampScaledWidthHalf = 0f
    private var stampScaledHeightHalf = 0f

    private var stampScale = 0f

    private val scaledStamps = mutableListOf<Bitmap>()

    private var lastSize = 0

    private val dstRect by lazy {
        RectF()
    }

    var isRandom = true
        set(value) {
            field = value
            counter = 0
        }

    private var counter = 0

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

    fun changeBrushes(newBitmaps : List<Bitmap>?, recycleCurrentBitmaps: Boolean) {
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

            val finalBitmap = if(isRandom) {
                scaledStamps[Random.nextInt(0,bitmaps.size)]
            } else {
                val b = scaledStamps[counter]

                if(++counter >= bitmaps.size) {
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
package ir.manan.mananpic.components.paint.painters.brushpaint

import android.graphics.*
import android.os.Build
import androidx.core.graphics.scale
import kotlin.math.max

class BitmapBrush(
    private var brushBitmap: Bitmap? = null
) : Brush() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = LightingColorFilter(Color.BLACK, Color.BLACK)
    }

    private var stampWidth = 0f
    private var stampHeight = 0f

    private var stampScaledWidth = 0f
    private var stampScaledHeight = 0f

    private var stampScaledWidthHalf = 0f
    private var stampScaledHeightHalf = 0f

    private var stampScale = 0f

    override var brushBlending: PorterDuff.Mode = PorterDuff.Mode.SRC_OVER
        set(value) {
            field = value
            if (field == PorterDuff.Mode.SRC_OVER) {
                paint.xfermode = null
            } else {
                paint.xfermode = PorterDuffXfermode(value)
            }
        }

    override var size: Float = 14f
        set(value) {
            field = value
            calculateSize(field)
        }

    override var color: Int = Color.BLACK
        set(value) {
            field = value
            paint.colorFilter = LightingColorFilter(field, field)
        }

    override var spacing: Float = 1f
        set(value) {
            field = value
            calculateSize(size)
        }

    override var texture: Bitmap? = null
        set(value) {
            field = value
            if (value == null) {
                paint.xfermode = null
            } else {
//                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
        }

    private lateinit var scaledStamp: Bitmap

    private var lastSize = 0f

    fun changeBrushBitmap(newBitmap: Bitmap?,recycleCurrentBitmap: Boolean) {
        if (recycleCurrentBitmap) {
            brushBitmap?.recycle()
        }

        brushBitmap = newBitmap
        calculateSize(size)
    }

    private fun calculateSize(size: Float) {
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                scaledStamp.reconfigure(
                    scaledStamp.width,
                    scaledStamp.height,
                    Bitmap.Config.HARDWARE
                )
            }

            scaledStamp.prepareToDraw()
        }

        paint.alpha = opacity

        if (paint.shader != null) {
            paint.colorFilter = null
        }

        canvas.drawBitmap(
            scaledStamp,
            -stampScaledWidthHalf,
            -stampScaledHeightHalf,
            paint
        )

    }

}

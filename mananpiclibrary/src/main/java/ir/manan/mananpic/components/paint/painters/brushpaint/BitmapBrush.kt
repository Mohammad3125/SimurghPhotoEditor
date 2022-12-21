package ir.manan.mananpic.components.paint.painters.brushpaint

import android.graphics.*
import android.os.Build
import androidx.core.graphics.scale
import kotlin.math.max

class BitmapBrush(
    val brushBitmap: Bitmap,
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

    override var spacing: Float = 0.1f
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
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
        }

    private lateinit var scaledStamp: Bitmap

    private var lastSize = 0f

    init {
        size = 80f
        color = Color.GREEN
        angleJitter = 0.7f
        spacing = 0.1f
        opacity = 0.5f
        scatter = 0.2f

        // TODO: size jitter can be less expansive if scaling size and down sizing size jitter
        sizeJitter = 1f

        hueFlow = 10f
        hueDistance = 30

//        alphaBlend = true

        // newSize = size +  (((size * (1f + sizeJitter)) - size) * 0.5f)

        // newJitter = 1f - (size / newSize)

        // calculating jitter scale = newJitter + (((0, 100f) / 100f) * newJitter)

//        hueJitter = 40
//        sizeVariance = 0.3f
//        opacityJitter = 1f
    }

    private fun calculateSize(size: Float) {
        stampWidth = brushBitmap.width.toFloat()
        stampHeight = brushBitmap.height.toFloat()

        stampScale = size / max(stampWidth, stampHeight)

        stampScaledWidth = stampWidth * stampScale
        stampScaledHeight = stampHeight * stampScale

        stampScaledWidthHalf = stampScaledWidth * 0.5f
        stampScaledHeightHalf = stampScaledHeight * 0.5f

    }

    override fun draw(canvas: Canvas, opacity: Int) {
        if (lastSize != size) {
            lastSize = size
            var w = stampScaledWidth.toInt()
            if (w < 1) w = 1

            var h = stampScaledHeight.toInt()
            if (h < 1) h = 1

            scaledStamp = brushBitmap.scale(w, h, true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                scaledStamp.reconfigure(scaledStamp.width,scaledStamp.height,Bitmap.Config.HARDWARE)
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

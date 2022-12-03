package ir.manan.mananpic.components.paint.painters.brushpaint

import android.graphics.*

class NativeBrush : Brush() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    var hardness = 0.2f
        set(value) {
            field = value
            createHardnessShader()
        }
    private val colorsHolder = IntArray(2)
    private val stopsHolder = FloatArray(2)

    override var color: Int = Color.BLACK
        set(value) {
            field = value
            createHardnessShader()
        }

    private var lastSize = 0f

    private var sizeHalf = 0f

    override var size: Float = 1f
        set(value) {
            field = value
            sizeHalf = value * 0.5f
        }

    private var hardnessShader: RadialGradient? = null

    override var texture: Bitmap? = null
        set(value) {
            field = value
            if (value == null) {
                paint.xfermode = null
            } else {
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            }
        }

    private fun createHardnessShader() {
        val sizeHalf = size * 0.5f

        colorsHolder[0] = color
        colorsHolder[1] = Color.TRANSPARENT

        stopsHolder[0] = hardness
        stopsHolder[1] = 1f - hardness

        hardnessShader = RadialGradient(
            0f,
            0f,
            sizeHalf,
            colorsHolder,
            stopsHolder,
            Shader.TileMode.CLAMP
        )

        paint.shader = hardnessShader
    }

    init {
        color = Color.BLACK
        size = 30f
        hardness = 0.8f
        spacing = 0.05f
        opacity = 0.4f

//        sizeVariance = 0.4f
//        angleJitter = 1f
//        sizeJitter = 1f
//        squish = 0.4f
//        angle = 45f
//        alphaBlend = true

//        hueJitter = 50
//        hueFlow = 12f
//        hueDistance = 30

        smoothness = 0.30f

    }

    override fun draw(canvas: Canvas, opacity: Int) {
        if (lastSize != size) {
            lastSize = size
            createHardnessShader()
        }

        paint.alpha = opacity


        // easy size jitter here
        canvas.drawCircle(0f, 0f, sizeHalf, paint)
    }
}
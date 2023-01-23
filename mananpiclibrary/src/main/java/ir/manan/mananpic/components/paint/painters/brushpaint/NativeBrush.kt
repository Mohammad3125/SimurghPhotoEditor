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

    private var lastSize = 0

    private var sizeHalf = 0f

    override var size: Int = 1
        set(value) {
            field = value
            sizeHalf = value * 0.5f
        }

    private var hardnessShader: RadialGradient? = null

    override var texture: Bitmap? = null
        set(value) {
            field = value
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

    private fun createHardnessShader() {
        colorsHolder[0] = color
        colorsHolder[1] = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))

        stopsHolder[0] = 1f - hardness
        stopsHolder[1] = 1f

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
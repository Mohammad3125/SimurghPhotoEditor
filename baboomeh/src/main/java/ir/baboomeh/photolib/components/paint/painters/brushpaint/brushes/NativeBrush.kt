package ir.baboomeh.photolib.components.paint.painters.brushpaint.brushes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader

open class NativeBrush : Brush() {

    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    var softness = 0.2f
        set(value) {
            field = value
            createHardnessShader()
        }
    protected val colorsHolder = IntArray(2)
    protected val stopsHolder = FloatArray(2)

    protected var lastSize = 0
    protected var lastColor = Color.TRANSPARENT

    protected var sizeHalf = 0f

    var brushShape = BrushShape.CIRCLE

    override var size: Int = 1
        set(value) {
            field = value
            sizeHalf = value * 0.5f
        }

    protected open var hardnessShader: RadialGradient? = null

    override var brushBlending: PorterDuff.Mode = PorterDuff.Mode.SRC_OVER
        set(value) {
            field = value
            if (field == PorterDuff.Mode.SRC_OVER) {
                paint.xfermode = null
            } else {
                paint.xfermode = PorterDuffXfermode(value)
            }
        }

    protected open fun createHardnessShader() {
        if (sizeHalf == 0f) {
            return
        }

        colorsHolder[0] = color
        colorsHolder[1] = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))

        stopsHolder[0] = 1f - softness
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
        if ((lastSize != size || lastColor != color) && brushShape == BrushShape.CIRCLE) {
            lastSize = size
            lastColor = color
            createHardnessShader()
        } else if (brushShape != BrushShape.CIRCLE) {
            paint.shader = null
            paint.color = color
        }

        paint.alpha = opacity

        if (brushShape == BrushShape.CIRCLE) {
            canvas.drawCircle(0f, 0f, sizeHalf, paint)
        } else {
            canvas.drawRect(-sizeHalf, -sizeHalf, sizeHalf, sizeHalf, paint)
        }
    }

    enum class BrushShape {
        RECT,
        CIRCLE
    }
}
package ir.maneditor.mananpiclibrary.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import ir.maneditor.mananpiclibrary.properties.*
import ir.maneditor.mananpiclibrary.utils.invalidateAfter
import ir.maneditor.mananpiclibrary.utils.sp

class MananTextView(context: Context, attr: AttributeSet?) : AppCompatTextView(context, attr),
    Pathable, Shadowable,
    Texturable, Colorable,
    Scalable, Gradientable {


    constructor(context: Context) : this(context, null)

    private var fontSize = textSize

    private var textPathEffect: PathEffect? = null
    private var blurFilter: BlurMaskFilter? = null
    private val rotationMatrix = Matrix().apply {
        setRotate(0f)
    }

    override fun applyPath(on: Float, off: Float, radius: Float, strokeWidth: Float) {
        invalidateAfter {
            if (textPathEffect == null) textPathEffect =
                ComposePathEffect(
                    DashPathEffect(floatArrayOf(on, off), 0f),
                    CornerPathEffect(radius)
                )

            paint.apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                pathEffect = textPathEffect
            }
        }
    }

    override fun applyPath(onAndOff: Float, radius: Float, strokeWidth: Float) {
        applyPath(onAndOff, onAndOff, radius, strokeWidth)
    }


    override fun applyShadow(shadowRadius: Float) {
        applyShadow(shadowRadius, BlurMaskFilter.Blur.NORMAL)
    }


    /**
     * This method applies shadow on the text with provided radius and filter.
     * This method might force the view to run in software rendering.
     *
     * @param shadowRadius Shadow radius that is going to be applied.
     * @param filter Represents style of the shadow with enums.
     */
    override fun applyShadow(shadowRadius: Float, filter: BlurMaskFilter.Blur) {
        invalidateAfter {
            if (blurFilter == null) blurFilter = BlurMaskFilter(shadowRadius, filter)
            paint.maskFilter = blurFilter

            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
    }

    /**
     * Applies a texture to the text with default tileMode of [Shader.TileMode.REPEAT]
     * @param bitmap The bitmap texture that is going to be applied to the view.
     */
    override fun applyTexture(bitmap: Bitmap) {
        applyTexture(bitmap, Shader.TileMode.REPEAT)
    }


    /**
     * Applies a texture to the text with default tileMode of [Shader.TileMode.REPEAT]
     * @param bitmap The bitmap texture that is going to be applied to the view.
     * @param tileMode The bitmap mode [Shader.TileMode]
     */
    override fun applyTexture(bitmap: Bitmap, tileMode: Shader.TileMode) {
        invalidateAfter {
            paint.shader = BitmapShader(bitmap, tileMode, tileMode)
        }
    }


    override fun removeShadow() {
        invalidateAfter {
            paint.maskFilter = null
            blurFilter = null
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
    }

    override fun removePath() {
        invalidateAfter {
            paint.pathEffect = null
            paint.style = Paint.Style.FILL
            textPathEffect = null
        }
    }


    override fun removeTexture() {
        invalidateAfter {
            paint.shader = null
        }
    }

    override fun removeGradient() {
        invalidateAfter {
            paint.shader = null
        }
    }

    override fun applyColorResource(color: Int) {
        setTextColor(ContextCompat.getColor(context, color))
    }

    override fun applyColor(color: Int) {
        setTextColor(color)
    }

    override fun applyScale(scaleFactor: Float) {
        fontSize *= scaleFactor
        if (fontSize < 18.sp) fontSize = 18.sp
        if (fontSize > 85.sp) fontSize = 85.sp

        textSize = fontSize
    }

    override fun applyLinearGradient(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        colors: IntArray,
        position: FloatArray?,
        tileMode: Shader.TileMode,
        rotation: Float
    ) {
        invalidateAfter {
            paint.shader = applyRotationToTheShader(
                LinearGradient(x0, y0, x1, y1, colors, position, tileMode),
                rotation
            )
        }
    }

    override fun applyRadialGradient(
        centerX: Float,
        centerY: Float,
        radius: Float,
        colors: IntArray,
        stops: FloatArray?,
        tileMode: Shader.TileMode,
        rotation: Float
    ) {
        invalidateAfter {
            paint.shader =
                applyRotationToTheShader(
                    RadialGradient(
                        centerX,
                        centerY,
                        radius,
                        colors,
                        stops,
                        tileMode
                    ), rotation
                )
        }
    }

    override fun applySweepGradient(
        cx: Float,
        cy: Float,
        colors: IntArray,
        positions: FloatArray?,
        rotation: Float
    ) {
        invalidateAfter {
            paint.shader =
                applyRotationToTheShader(SweepGradient(cx, cy, colors, positions), rotation)
        }
    }

    private fun applyRotationToTheShader(shader: Shader, rotation: Float): Shader {
        rotationMatrix.setRotate(rotation)
        return shader.apply {
            setLocalMatrix(rotationMatrix)
        }
    }

}
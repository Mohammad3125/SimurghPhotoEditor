package ir.maneditor.mananpiclibrary.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import ir.maneditor.mananpiclibrary.properties.*
import ir.maneditor.mananpiclibrary.utils.invalidateAfter
import ir.maneditor.mananpiclibrary.utils.sp

class MananTextView(context: Context, attr: AttributeSet?) : AppCompatTextView(context, attr),
    Pathable, Blurable,
    Texturable, Colorable,
    Scalable, Gradientable {


    constructor(context: Context) : this(context, null)

    private var fontSize = textSize

    /**
     * Minimum size of text allowed.
     * Dimension is in SP format: [android.util.TypedValue.COMPLEX_UNIT_SP]
     */
    var minimumTextSize = sp(4)

    private val rotationMatrix = Matrix().apply {
        setRotate(0f)
    }

    init {
        Log.i("1", "min text size $minimumTextSize text size $textSize")
    }

    override fun applyPath(on: Float, off: Float, radius: Float, strokeWidth: Float) {
        invalidateAfter {
            paint.apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth

                pathEffect = ComposePathEffect(
                    DashPathEffect(floatArrayOf(on, off), 0f),
                    CornerPathEffect(radius)
                )
            }
        }
    }

    override fun applyPath(onAndOff: Float, radius: Float, strokeWidth: Float) {
        applyPath(onAndOff, onAndOff, radius, strokeWidth)
    }


    override fun applyBlur(shadowRadius: Float) {
        applyBlur(shadowRadius, BlurMaskFilter.Blur.NORMAL)
    }


    /**
     * This method applies shadow on the text with provided radius and filter.
     * This method might force the view to run in software rendering.
     *
     * @param shadowRadius Shadow radius that is going to be applied.
     * @param filter Represents style of the shadow with enums.
     */
    override fun applyBlur(shadowRadius: Float, filter: BlurMaskFilter.Blur) {
        invalidateAfter {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            paint.maskFilter = BlurMaskFilter(shadowRadius, filter)
        }
    }

    /**
     * Applies a texture to the text with default tileMode of [Shader.TileMode.REPEAT]
     * @param bitmap The bitmap texture that is going to be applied to the view.
     */
    override fun applyTexture(bitmap: Bitmap, opacity: Float) {
        applyTexture(bitmap, Shader.TileMode.REPEAT, opacity)
    }


    /**
     * Applies a texture to the text with default tileMode of [Shader.TileMode.REPEAT]
     * @param bitmap The bitmap texture that is going to be applied to the view.
     * @param tileMode The bitmap mode [Shader.TileMode]
     */
    override fun applyTexture(bitmap: Bitmap, tileMode: Shader.TileMode, opacity: Float) {
        invalidateAfter {
            paint.shader = BitmapShader(bitmap, tileMode, tileMode).apply {
                alpha = opacity
            }
        }
    }


    override fun removeBlur() {
        invalidateAfter {
            paint.maskFilter = null
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
    }

    override fun removePath() {
        invalidateAfter {
            paint.pathEffect = null
            paint.style = Paint.Style.FILL
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

    override fun applyScale(scaleFactor: Float, widthLimit: Int, heightLimit: Int) {
        if (width < widthLimit && height < heightLimit || scaleFactor < 1f) {
            fontSize *= scaleFactor
            if (fontSize < minimumTextSize) fontSize = minimumTextSize
            textSize = fontSize
        }
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
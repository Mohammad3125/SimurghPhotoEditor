package ir.maneditor.mananpiclibrary

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import ir.maneditor.mananpiclibrary.properties.*

class MananTextView(context: Context, attr: AttributeSet?) : AppCompatTextView(context, attr),
    Pathable, Shadowable,
    Texturable, Colorable,
    Scalable {


    constructor(context: Context) : this(context, null)


    private var textPathEffect: PathEffect? = null
    private var blurFilter: BlurMaskFilter? = null
    private var bitmapShader: Shader? = null


    override fun applyPath(on: Float, off: Float, radius: Float, strokeWidth: Float) {
        if (textPathEffect == null) textPathEffect =
            ComposePathEffect(DashPathEffect(floatArrayOf(on, off), 0f), CornerPathEffect(radius))

        paint.apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            pathEffect = textPathEffect
        }

        invalidate()
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
        if (blurFilter == null) blurFilter = BlurMaskFilter(shadowRadius, filter)
        paint.maskFilter = blurFilter

        setLayerType(LAYER_TYPE_SOFTWARE, null)

        invalidate()
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
        if (bitmapShader == null) bitmapShader = BitmapShader(bitmap, tileMode, tileMode)
        paint.shader = bitmapShader
        invalidate()
    }


    override fun removeShadow() {
        paint.maskFilter = null
        blurFilter = null
        setLayerType(LAYER_TYPE_HARDWARE, null)
        invalidate()
    }

    override fun removePath() {
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
        textPathEffect = null
        invalidate()
    }


    override fun removeTexture() {
        paint.shader = null
        bitmapShader = null
        invalidate()
    }

    override fun applyColorResource(color: Int) {
        setTextColor(ContextCompat.getColor(context, color))
    }

    override fun applyColor(color: Int) {
        setTextColor(color)
    }

    override fun applyScale(scaleFactor: Float) {
        textSize *= scaleFactor
        if (textSize < 18.sp) textSize = 18f
        if (textSize > 85.sp) textSize = 85f
    }


}
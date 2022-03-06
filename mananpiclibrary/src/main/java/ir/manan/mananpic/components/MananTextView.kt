package ir.manan.mananpic.components

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.doOnPreDraw
import ir.manan.mananpic.properties.*
import ir.manan.mananpic.utils.MananFactory
import ir.manan.mananpic.utils.MananMatrix
import kotlin.math.max
import kotlin.math.min

/**
 * Component that extends the current [AppCompatTextView] class and adds few functionalities like
 * implementing [Pathable] and [Blurable] etc...
 */
class MananTextView(context: Context, attr: AttributeSet?) : AppCompatTextView(context, attr),
    Pathable, Blurable,
    Texturable,
    Gradientable, MananComponent, Bitmapable {


    constructor(context: Context) : this(context, null)

    private val bounds = RectF()

    private var isDrawnOnce = false

    private val rotationMatrix = Matrix().apply {
        setRotate(0f)
    }

    private val shaderMatrix by lazy {
        MananMatrix()
    }

    private var shaderRotationHolder = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun applyPath(on: Float, off: Float, radius: Float, strokeWidth: Float) {
        paint.apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth

            pathEffect = ComposePathEffect(
                DashPathEffect(floatArrayOf(on, off), 0f),
                CornerPathEffect(radius)
            )
        }
        invalidate()
    }

    override fun applyPath(onAndOff: Float, radius: Float, strokeWidth: Float) {
        applyPath(onAndOff, onAndOff, radius, strokeWidth)
    }


    override fun applyBlur(blurRadius: Float) {
        applyBlur(blurRadius, BlurMaskFilter.Blur.NORMAL)
    }


    /**
     * This method applies blur on the text with provided radius and filter.
     * This method might force the view to run in software rendering.
     *
     * @param blurRadius Blur radius that is going to be applied.
     * @param filter Represents style of the blur with enums.
     */
    override fun applyBlur(blurRadius: Float, filter: BlurMaskFilter.Blur) {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        paint.maskFilter = BlurMaskFilter(blurRadius, filter)
        invalidate()
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
        paint.shader = BitmapShader(bitmap, tileMode, tileMode).apply {
            alpha = opacity
            setLocalMatrix(shaderMatrix)
        }
        invalidate()
    }

    override fun shiftTexture(dx: Float, dy: Float) {
        paint.shader?.run {
            shaderMatrix.postTranslate(dx, dy)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun scaleTexture(scaleFactor: Float, pivotX: Float, pivotY: Float) {
        paint.shader?.run {
            shaderMatrix.postScale(scaleFactor, scaleFactor, pivotX, pivotY)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun rotateTexture(rotateTo: Float, pivotX: Float, pivotY: Float) {
        paint.shader?.run {
            shaderMatrix.postRotate(
                rotateTo - shaderRotationHolder,
                pivotX,
                pivotY
            )
            shaderRotationHolder = rotateTo
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        bounds.set(x, y, x + width, y + height)
    }


    override fun removeBlur() {
        paint.maskFilter = null
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        invalidate()
    }

    override fun removePath() {
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
        invalidate()
    }

    override fun applyRotation(degree: Float) {
        rotation = degree
    }

    override fun applyScale(scaleFactor: Float) {
        if (!isDrawnOnce) {
            doOnPreDraw {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize * scaleFactor)
            }
        } else {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize * scaleFactor)
        }
        scaleTexture(scaleFactor, 0f, 0f)
    }

    override fun applyMovement(dx: Float, dy: Float) {
        x += dx
        y += dy
    }

    override fun removeTexture() {
        paint.shader = null
        invalidate()
    }

    override fun removeGradient() {
        paint.shader = null
        invalidate()
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
        paint.shader = applyRotationToTheShader(
            LinearGradient(x0, y0, x1, y1, colors, position, tileMode),
            rotation
        )
        invalidate()
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
        invalidate()
    }

    override fun applySweepGradient(
        cx: Float,
        cy: Float,
        colors: IntArray,
        positions: FloatArray?,
        rotation: Float
    ) {
        paint.shader =
            applyRotationToTheShader(SweepGradient(cx, cy, colors, positions), rotation)
        invalidate()
    }

    private fun applyRotationToTheShader(shader: Shader, rotation: Float): Shader {
        rotationMatrix.setRotate(rotation)
        return shader.apply {
            setLocalMatrix(rotationMatrix)
        }
    }

    override fun reportBound(): RectF {
        return bounds.apply {
            set(x, y, x + width, y + height)
        }
    }

    override fun reportRotation(): Float {
        return rotation
    }

    override fun reportBoundPivotX(): Float {
        return bounds.centerX()
    }

    override fun reportBoundPivotY(): Float {
        return bounds.centerY()
    }

    override fun reportPivotX(): Float {
        return pivotX
    }

    override fun reportPivotY(): Float {
        return pivotY
    }

    override fun toBitmap(config: Bitmap.Config): Bitmap {
        val textBounds = reportBound()
        val outputBitmap =
            Bitmap.createBitmap(
                textBounds.width().toInt(),
                textBounds.height().toInt(),
                config
            )

        val canvas = Canvas(outputBitmap)
        draw(canvas)

        return outputBitmap
    }

    override fun toBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        val textBounds = reportBound()

        // Determine how much the desired width and height is scaled base on
        // smallest desired dimension divided by maximum text dimension.
        val totalScaled = min(width, height) / max(textBounds.width(), textBounds.height())

        // Create output bitmap matching desired width,height and config.
        val outputBitmap = Bitmap.createBitmap(width, height, config)

        // Calculate extra width and height remaining to later use to center the image inside bitmap.
        val extraWidth = (width / totalScaled) - textBounds.width()
        val extraHeight = (height / totalScaled) - textBounds.height()

        Canvas(outputBitmap).run {
            scale(totalScaled, totalScaled)
            // Finally translate to center the content.
            translate(extraWidth * 0.5f, extraHeight * 0.5f)
            draw(this)
        }

        return outputBitmap
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        isDrawnOnce = true
    }

    override fun clone(): View {
        return MananFactory.createTextView(context, text.toString(), maxLines).also { textView ->
            textView.setLayerType(layerType, null)
            textView.setTextColor(currentTextColor)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX,textSize)
            textView.typeface = typeface
            textView.paint.style = paint.style
            textView.paint.strokeWidth = paint.strokeWidth
            textView.paint.pathEffect = paint.pathEffect
            textView.shaderRotationHolder = shaderRotationHolder
            doOnPreDraw {
                textView.shaderMatrix.set(shaderMatrix)
                textView.paint.shader = paint.shader
                if (textView.paint.shader != null) {
                    textView.paint.shader.setLocalMatrix(shaderMatrix)
                }
            }
            textView.paint.maskFilter = paint.maskFilter
            textView.setShadowLayer(
                shadowRadius,
                shadowDx,
                shadowDy,
                shadowColor
            )
            textView.setLayerType(layerType, null)
        }
    }
}
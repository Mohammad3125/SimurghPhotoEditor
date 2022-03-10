package ir.manan.mananpic.components

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.view.doOnPreDraw
import ir.manan.mananpic.properties.*
import ir.manan.mananpic.utils.MananFactory
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A custom textview created because of clipping issues with current [android.widget.TextView] in android framework.
 *
 * This class does not clip the text in any case. This way all fonts, especially the non-standard one do not get clipped.
 *
 */
class MananTextView(context: Context, attr: AttributeSet?) : View(context, attr),
    MananComponent, Bitmapable, Pathable, Blurable, Texturable, Gradientable, StrokeCapable {
    constructor(context: Context) : this(context, null)

    private var shadowRadius = 0f
    private var shadowDx = 0f
    private var shadowDy = 0f
    private var shadowLColor = 0

    private val textPaint by lazy {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    /**
     * Size of current text. default if 8sp.
     * Values will be interpreted as pixels.
     * Use [android.util.TypedValue] or [ir.manan.mananpic.utils.sp] to convert a sp number to pixels.
     */
    var textSize = sp(8)
        set(value) {
            textPaint.textSize = value
            field = value
            requestLayout()
        }

    /**
     * Text of current text view.
     */
    var text = ""
        set(value) {
            field = value
            requestLayout()
        }


    var textColor = Color.BLACK
        set(value) {
            textPaint.color = value
            field = value
            invalidate()
        }

    private val finalBounds by lazy {
        RectF()
    }

    /**
     * Baseline of text to be drawn.
     */
    private var textBaseLine = 0f


    /**
     * Extra space used to expand the width and height of view to prevent clipping in special cases like Blur mask and so on.
     */
    private var extraSpace = 0f

    private val shaderMatrix by lazy {
        MananMatrix()
    }

    private var shaderRotationHolder = 0f

    private var textStrokeWidth = 0f
    private var strokeColor: Int = Color.BLACK

    private var bitmapShader: BitmapShader? = null

    override fun reportBound(): RectF {
        return finalBounds.apply {
            set(x, y, width + x, height + y)
        }
    }

    override fun reportRotation(): Float {
        return rotation
    }

    override fun reportBoundPivotX(): Float {
        return finalBounds.centerX()
    }

    override fun reportBoundPivotY(): Float {
        return finalBounds.centerY()
    }

    override fun reportPivotX(): Float {
        return pivotX
    }

    override fun reportPivotY(): Float {
        return pivotX
    }

    override fun applyRotation(degree: Float) {
        rotation = degree
    }

    override fun applyScale(scaleFactor: Float) {
        textSize *= scaleFactor
        scaleTexture(scaleFactor, 0f, (extraSpace * 2) + (paddingTop + paddingBottom))
    }

    override fun applyMovement(dx: Float, dy: Float) {
        x += dx
        y += dy
    }

    override fun clone(): View {
        return MananFactory.createTextView(context, text).also { textView ->
            textView.setLayerType(layerType, null)
            textView.textSize = textSize
            textView.textColor = textColor
            textView.textPaint.typeface = textPaint.typeface
            textView.textPaint.style = textPaint.style
            textView.textPaint.strokeWidth = textPaint.strokeWidth
            textView.textPaint.pathEffect = textPaint.pathEffect
            textView.textSize += 0.001f
            textView.extraSpace = extraSpace
            textView.textBaseLine = textView.textBaseLine
            textView.strokeColor = strokeColor
            textView.textStrokeWidth = textStrokeWidth
            textView.shaderRotationHolder = shaderRotationHolder
            textView.bitmapShader = bitmapShader
            doOnPreDraw {
                textView.shaderMatrix.set(shaderMatrix)
                textView.textPaint.shader = bitmapShader
                if (textView.textPaint.shader != null) {
                    textView.textPaint.shader.setLocalMatrix(shaderMatrix)
                }
            }
            textView.textPaint.maskFilter = textPaint.maskFilter
            textView.setShadowLayer(
                shadowRadius,
                shadowDx,
                shadowDy,
                shadowLColor
            )
        }
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fontMetrics = textPaint.fontMetrics

        val finalExtraSpace = extraSpace * 2

        val textWidth = textPaint.measureText(text) + finalExtraSpace + paddingLeft + paddingRight

        val textHeight =
            abs(fontMetrics.ascent) + fontMetrics.descent + fontMetrics.leading + finalExtraSpace + paddingTop + paddingBottom

        pivotX = textWidth * 0.5f
        pivotY = textHeight * 0.5f

        setMeasuredDimension(
            if (suggestedMinimumWidth > textWidth) suggestedMinimumWidth else textWidth.toInt(),
            if (suggestedMinimumHeight > textHeight) suggestedMinimumWidth else textHeight.toInt()
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        finalBounds.set(x, y, width + x, height + y)

        textBaseLine = height - textPaint.fontMetrics.descent
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.run {
            super.onDraw(this)

            save()

            translate(extraSpace + paddingLeft, -(extraSpace + paddingBottom))

            if (textStrokeWidth > 0f) {
                val currentColor = textColor
                val currentStyle = textPaint.style
                textPaint.style = Paint.Style.STROKE
                textPaint.strokeWidth = textStrokeWidth
                val currentShader = textPaint.shader
                textPaint.shader = null
                textColor = strokeColor

                drawText(text, 0f, textBaseLine, textPaint)

                textPaint.shader = currentShader
                textPaint.style = currentStyle
                textPaint.strokeWidth = 0f
                textColor = currentColor

            }

            drawText(text, 0f, textBaseLine, textPaint)

            restore()
        }
    }

    /**
     * Sets type face of current text.
     */
    fun setTypeFace(typeFace: Typeface) {
        textPaint.typeface = typeFace
        requestLayout()
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

    override fun applyPath(on: Float, off: Float, radius: Float, strokeWidth: Float) {
        textPaint.apply {

            if (textStrokeWidth == 0f) {
                this.strokeWidth = strokeWidth
            }

            style = Paint.Style.STROKE

            pathEffect = ComposePathEffect(
                DashPathEffect(floatArrayOf(on, off), 0f),
                CornerPathEffect(radius)
            )

            // Hack to apply path effect on stroke.
            // Will not apply path effect on stroke if this hack is not used.
            textSize += 0.001f

            invalidate()
        }
    }

    override fun applyPath(onAndOff: Float, radius: Float, strokeWidth: Float) {
        applyPath(onAndOff, onAndOff, radius, strokeWidth)
    }

    override fun removePath() {
        if (textPaint.pathEffect != null) {
            textPaint.pathEffect = null
            textPaint.style = Paint.Style.FILL
            // Hack to remove path if stroke has been applied.
            textSize -= 0.001f
            invalidate()
        }
    }

    override fun applyBlur(blurRadius: Float) {
        applyBlur(blurRadius, BlurMaskFilter.Blur.NORMAL)
    }

    override fun applyBlur(blurRadius: Float, filter: BlurMaskFilter.Blur) {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        textPaint.maskFilter = BlurMaskFilter(blurRadius, filter)
        // Add extra offset to prevent clipping because of software layer.
        val newExtraSpace = if (blurRadius > textStrokeWidth) {
            blurRadius
        } else {
            textStrokeWidth
        }

        shiftTexture(0f, (newExtraSpace - extraSpace) * 2)

        extraSpace = newExtraSpace

        requestLayout()
    }

    override fun removeBlur() {
        textPaint.maskFilter = null

        // Clear extra space by setting it to size of stroke width.
        // If stroke width exists then we don't go lower than that,
        // if it doesn't then extra space would be set to 0.
        extraSpace = textStrokeWidth

        setLayerType(LAYER_TYPE_NONE, null)
        requestLayout()
    }

    override fun applyTexture(bitmap: Bitmap, opacity: Float) {
        applyTexture(bitmap, Shader.TileMode.REPEAT, opacity)
    }

    /**
     * Applies a texture to the text with default tileMode of [Shader.TileMode.REPEAT]
     * @param bitmap The bitmap texture that is going to be applied to the view.
     * @param tileMode The bitmap mode [Shader.TileMode]
     */
    override fun applyTexture(bitmap: Bitmap, tileMode: Shader.TileMode, opacity: Float) {
        bitmapShader = BitmapShader(bitmap, tileMode, tileMode).apply {
            alpha = opacity
            setLocalMatrix(shaderMatrix)
        }

        textPaint.shader = BitmapShader(bitmap, tileMode, tileMode).apply {
            alpha = opacity
            setLocalMatrix(shaderMatrix)
        }
        invalidate()
    }

    override fun shiftTexture(dx: Float, dy: Float) {
        textPaint.shader?.run {
            shaderMatrix.postTranslate(dx, dy)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun scaleTexture(scaleFactor: Float, pivotX: Float, pivotY: Float) {
        textPaint.shader?.run {
            shaderMatrix.postScale(scaleFactor, scaleFactor, pivotX, pivotY)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun rotateTexture(rotateTo: Float, pivotX: Float, pivotY: Float) {
        textPaint.shader?.run {
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

    override fun removeTexture() {
        bitmapShader = null
        textPaint.shader = null
        invalidate()
    }

    override fun removeGradient() {
        textPaint.shader = null
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
        textPaint.shader =
            LinearGradient(x0, y0, x1, y1, colors, position, tileMode).apply {
                setLocalMatrix(shaderMatrix.apply {
                    setRotate(rotation)
                })
            }

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
        textPaint.shader =
            RadialGradient(
                centerX,
                centerY,
                radius,
                colors,
                stops,
                tileMode
            ).apply {
                setLocalMatrix(shaderMatrix.apply {
                    setRotate(rotation)
                })
            }
        invalidate()
    }

    override fun applySweepGradient(
        cx: Float,
        cy: Float,
        colors: IntArray,
        positions: FloatArray?,
        rotation: Float
    ) {
        textPaint.shader =
            SweepGradient(cx, cy, colors, positions).apply {
                setLocalMatrix(shaderMatrix.apply {
                    setRotate(rotation)
                })
            }
        invalidate()
    }

    override fun setStroke(strokeRadiusPx: Float, strokeColor: Int) {
        if (strokeRadiusPx < 0f) throw IllegalStateException("Stroke width should be a positive number")
        textStrokeWidth = strokeRadiusPx
        this.strokeColor = strokeColor
        shiftTexture(0f, (strokeRadiusPx - extraSpace) * 2)
        extraSpace = strokeRadiusPx
        requestLayout()
    }

    fun setShadowLayer(radius: Float, dx: Float, dy: Float, shadowColor: Int) {
        shadowRadius = radius
        shadowDx = dx
        shadowDy = dy
        shadowLColor = shadowColor
        textPaint.setShadowLayer(radius, dx, dy, shadowColor)
        invalidate()
    }

    fun clearShadowLayer() {
        textPaint.clearShadowLayer()
        shadowRadius = 0f
        shadowDx = 0f
        shadowDy = 0f
        shadowLColor = 0
        invalidate()
    }
}
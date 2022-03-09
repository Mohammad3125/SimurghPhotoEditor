package ir.manan.mananpic.components

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import ir.manan.mananpic.properties.*
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
class MananCustomTextView(context: Context, attr: AttributeSet?) : View(context, attr),
    MananComponent, Bitmapable, Pathable, Blurable, Texturable {
    constructor(context: Context) : this(context, null)

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
        scaleTexture(scaleFactor, 0f, 0f)
    }

    override fun applyMovement(dx: Float, dy: Float) {
        x += dx
        y += dy
    }

    override fun clone(): View {
        // Not yet implemented.
        return this
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fontMetrics = textPaint.fontMetrics

        val finalBlurRadius = extraSpace * 2

        val textWidth = textPaint.measureText(text) + finalBlurRadius

        val textHeight =
            abs(fontMetrics.ascent) + fontMetrics.descent + fontMetrics.leading + finalBlurRadius

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

            translate(extraSpace, -(extraSpace))

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

    override fun removePath() {
        textPaint.pathEffect = null
        textPaint.style = Paint.Style.FILL
        invalidate()
    }

    override fun applyBlur(blurRadius: Float) {
        applyBlur(blurRadius, BlurMaskFilter.Blur.NORMAL)
    }

    override fun applyBlur(blurRadius: Float, filter: BlurMaskFilter.Blur) {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        textPaint.maskFilter = BlurMaskFilter(blurRadius, filter)
        // Add extra offset to prevent clipping because of software layer.
        extraSpace = blurRadius
        requestLayout()
    }

    override fun removeBlur() {
        textPaint.maskFilter = null
        extraSpace = 0f
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
        textPaint.shader = null
        invalidate()
    }


}
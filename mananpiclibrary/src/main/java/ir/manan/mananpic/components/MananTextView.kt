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

/**
 * A custom textview created because of clipping issues with current [android.widget.TextView] in android framework.
 *
 * This class does not clip the text in any case. This way all fonts, especially the non-standard one do not get clipped.
 *
 */
class MananTextView(context: Context, attr: AttributeSet?) : View(context, attr),
    MananComponent, Bitmapable, Pathable, Texturable, Gradientable, StrokeCapable, Blurable,
    java.io.Serializable {
    constructor(context: Context) : this(context, null)

    private var shadowRadius = 0f
    private var shadowDx = 0f
    private var shadowDy = 0f
    private var shadowLColor = 0

    @Transient
    private val textPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
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

    /* Allocations --------------------------------------------------------------------------------------------  */

    @Transient
    private val finalBounds = RectF()

    @Transient
    private val mappingMatrix = Matrix()

    @Transient
    private val shaderMatrix = MananMatrix()

    @Transient
    private val textBoundsRect = Rect()

    /**
     * Baseline of text to be drawn.
     */
    private var textBaseLineY = 0f

    private var textBaseLineX = 0f

    /**
     * Extra space used to expand the width and height of view to prevent clipping in special cases like Blur mask and so on.
     */
    private var extraSpace = 0f

    private var shaderRotationHolder = 0f

    var textStrokeWidth = 0f
    var strokeColor: Int = Color.BLACK

    @Transient
    private var paintShader: Shader? = null

    private val finalTexts by lazy {
        mutableMapOf<String, Float>()
    }

    /**
     * Sets alignment of text when drawn.
     * - [Alignment.CENTER] Centers text.
     * - [Alignment.LEFT] Draws text to left of view.
     * - [Alignment.RIGHT] Draws text to right of view.
     */
    var alignmentText: Alignment = Alignment.CENTER
        set(value) {
            field = value
            invalidate()
        }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        // Minimum size of a small font cache recommended in OpenGlRendered properties.
        textPaint.textSize = 256f
    }

    override fun reportBound(): RectF {
        finalBounds.set(
            x,
            y,
            width + x,
            height + y
        )
        mappingMatrix.run {
            setScale(scaleX, scaleY, finalBounds.centerX(), finalBounds.centerY())
            mapRect(finalBounds)
        }
        return finalBounds
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
        scaleX *= scaleFactor
        scaleY *= scaleFactor
    }

    override fun reportScaleX(): Float {
        return scaleX
    }

    override fun reportScaleY(): Float {
        return scaleY
    }

    override fun applyMovement(dx: Float, dy: Float) {
        x += dx
        y += dy
    }

    override fun clone(): View {
        return MananFactory.createTextView(context, text).also { textView ->
            textView.setLayerType(layerType, null)
            textView.textPaint.textSize = textPaint.textSize
            textView.scaleX = scaleX
            textView.scaleY = scaleY
            textView.alignmentText = alignmentText
            textView.textColor = textColor
            textView.textPaint.typeface = textPaint.typeface
            textView.textPaint.style = textPaint.style
            textView.textPaint.strokeWidth = textPaint.strokeWidth
            textView.textPaint.pathEffect = textPaint.pathEffect

            textView.extraSpace = extraSpace
            textView.textBaseLineY = textBaseLineY
            textView.textBaseLineX = textBaseLineX
            textView.strokeColor = strokeColor
            textView.textStrokeWidth = textStrokeWidth
            textView.shaderRotationHolder = shaderRotationHolder
            textView.paintShader = paintShader
            doOnPreDraw {
                textView.shaderMatrix.set(shaderMatrix)
                textView.textPaint.shader = paintShader
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
            textView.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
        }
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fontMetrics = textPaint.fontMetrics

        finalTexts.clear()
        val texts = text.split("\n", ignoreCase = true)
        val widths = texts.map { string ->
            textPaint.getTextBounds(
                string,
                0,
                string.length,
                textBoundsRect
            )
            textBoundsRect.width().toFloat()
        }
        for (i in texts.indices) {
            finalTexts[texts[i]] = widths[i]
        }

        val finalExtraSpace = extraSpace * 2

        val textWidth =
            widths.maxOf { it } + paddingLeft + paddingRight + finalExtraSpace

        textPaint.getTextBounds(text, 0, text.length, textBoundsRect)

        val textHeight =
            (textBoundsRect.height() + paddingTop + paddingBottom + finalExtraSpace) * finalTexts.size

        pivotX = textWidth * 0.5f
        pivotY = textHeight * 0.5f

        setMeasuredDimension(
            if (suggestedMinimumWidth > textWidth) suggestedMinimumWidth else textWidth.toInt(),
            if (suggestedMinimumHeight > textHeight) suggestedMinimumWidth else textHeight.toInt()
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        finalBounds.set(
            x,
            y,
            width + x,
            height + y
        )

        textBaseLineY = height.toFloat() - textBoundsRect.bottom
        textBaseLineX = -textBoundsRect.left.toFloat()

    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.run {
            super.onDraw(this)

            var finalTranslateX = 0f
            var finalTranslateY = 0f

            when (alignmentText) {
                Alignment.LEFT -> {
                    finalTranslateX = (extraSpace * 0.5f)
                    finalTranslateY = extraSpace
                }
                Alignment.CENTER -> {
                    finalTranslateY = extraSpace
                }
                Alignment.RIGHT -> {
                    finalTranslateX = -(extraSpace * 0.5f)
                    finalTranslateY = extraSpace
                }
            }

            translate(
                (finalTranslateX + (paddingLeft - paddingRight)),
                -(finalTranslateY + (paddingBottom - paddingTop))
            )

            val toShift = ((this@MananTextView.height.toFloat() / finalTexts.size))

            if (textStrokeWidth > 0f) {
                val currentColor = textColor
                val currentStyle = textPaint.style
                textPaint.style = Paint.Style.STROKE
                textPaint.strokeWidth = textStrokeWidth
                val currentShader = textPaint.shader
                textPaint.shader = null
                textPaint.color = strokeColor

                drawTexts(this, toShift)

                textPaint.shader = currentShader
                textPaint.style = currentStyle
                textPaint.strokeWidth = 0f
                textPaint.color = currentColor

            }

            drawTexts(this, toShift)

        }
    }

    private fun drawTexts(canvas: Canvas, toShift: Float) {
        var i = 0
        finalTexts.forEach { map ->

            val totalTranslated = height - (toShift * (finalTexts.size - (i)))
            shiftTextureWithoutInvalidation(0f, totalTranslated)

            canvas.drawText(
                map.key,
                ((width - map.value) * Alignment.getNumber(alignmentText)) + textBaseLineX,
                textBaseLineY - (toShift * (finalTexts.size - (i + 1))),
                textPaint
            )

            shiftTextureWithoutInvalidation(0f, -totalTranslated)

            i++
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
        val lastXScale = scaleX
        val lastYScale = scaleY
        scaleX = 1f
        scaleY = 1f
        val textBounds = reportBound()
        return Bitmap.createBitmap(
            textBounds.width().toInt(),
            textBounds.height().toInt(),
            config
        ).also { bitmap ->
            draw(Canvas(bitmap))
            scaleX = lastXScale
            scaleY = lastYScale
        }
    }

    override fun toBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        val lastXScale = scaleX
        val lastYScale = scaleY
        scaleX = 1f
        scaleY = 1f

        val textBounds = reportBound()

        // Determine how much the desired width and height is scaled base on
        // smallest desired dimension divided by maximum text dimension.
        var totalScaled = width / textBounds.width()

        if (textBounds.height() * totalScaled > height) {
            totalScaled = height / textBounds.height()
        }

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

        scaleX = lastXScale
        scaleY = lastYScale

        return outputBitmap
    }

    override fun applyPath(on: Float, off: Float, radius: Float, strokeWidth: Float) {
        textPaint.apply {

            if (textStrokeWidth == 0f) {
                this.strokeWidth = strokeWidth
            }

            style = Paint.Style.STROKE

            val wasPathNull = pathEffect == null

            pathEffect = ComposePathEffect(
                DashPathEffect(floatArrayOf(on, off), 0f),
                CornerPathEffect(radius)
            )

            if (wasPathNull) {
                textPaint.textSize += 0.001f
            }

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
            textPaint.textSize -= 0.001f
            invalidate()
        }
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
        paintShader = BitmapShader(bitmap, tileMode, tileMode).apply {
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

    private fun shiftTextureWithoutInvalidation(dx: Float, dy: Float) {
        textPaint.shader?.run {
            shaderMatrix.postTranslate(dx, dy)
            setLocalMatrix(shaderMatrix)
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

    override fun applyBlur(blurRadius: Float) {
        applyBlur(blurRadius, BlurMaskFilter.Blur.NORMAL)
    }

    override fun applyBlur(blurRadius: Float, filter: BlurMaskFilter.Blur) {
        if (layerType != LAYER_TYPE_SOFTWARE) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
        textPaint.maskFilter = BlurMaskFilter(blurRadius, filter)
        // Add extra offset to prevent clipping because of software layer.
        val newExtraSpace = if (blurRadius > textStrokeWidth) {
            blurRadius
        } else {
            textStrokeWidth
        }

        shiftTextureWithAlignment(newExtraSpace)

        extraSpace = newExtraSpace

        requestLayout()
    }

    override fun removeBlur() {
        textPaint.maskFilter = null

        // Clear extra space by setting it to size of stroke width.
        // If stroke width exists then we don't go lower than that,
        // if it doesn't then extra space would be set to 0.
        extraSpace = textStrokeWidth

        setLayerType(LAYER_TYPE_HARDWARE, null)
        requestLayout()
    }


    override fun removeTexture() {
        paintShader = null
        textPaint.shader = null
        invalidate()
    }

    override fun removeGradient() {
        paintShader = null
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
        paintShader = LinearGradient(x0, y0, x1, y1, colors, position, tileMode).apply {
            setLocalMatrix(shaderMatrix.apply {
                setRotate(rotation)
            })
        }

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
        paintShader = RadialGradient(
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
        paintShader = SweepGradient(cx, cy, colors, positions).apply {
            setLocalMatrix(shaderMatrix.apply {
                setRotate(rotation)
            })
        }

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

        shiftTextureWithAlignment(strokeRadiusPx)

        extraSpace = strokeRadiusPx
        requestLayout()
        invalidate()
    }

    private fun shiftTextureWithAlignment(currentStroke: Float) {
        val diffCurrentStrokeWithLast = (currentStroke - extraSpace)

        var finalShiftValueX = 0f

        val finalShiftValueY = diffCurrentStrokeWithLast * 2

        when (alignmentText) {
            Alignment.LEFT -> {

            }
            Alignment.RIGHT -> {
                finalShiftValueX = finalShiftValueY
            }
            Alignment.CENTER -> {
                finalShiftValueX = diffCurrentStrokeWithLast
            }
        }

        shiftTexture(finalShiftValueX, finalShiftValueY)
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

    /**
     * Determines alignment of drawn text in [MananTextView].
     */
    enum class Alignment {
        LEFT,
        RIGHT,
        CENTER;

        companion object {
            fun getNumber(alignment: Alignment): Float {
                return when (alignment) {
                    LEFT -> 0f
                    RIGHT -> 1f
                    else -> 0.5f
                }
            }
        }

    }
}
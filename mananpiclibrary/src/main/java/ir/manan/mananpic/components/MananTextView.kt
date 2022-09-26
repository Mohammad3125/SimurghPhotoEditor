package ir.manan.mananpic.components

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.view.doOnPreDraw
import androidx.core.view.setPadding
import ir.manan.mananpic.properties.*
import ir.manan.mananpic.utils.MananFactory
import ir.manan.mananpic.utils.MananMatrix
import kotlin.math.max
import kotlin.math.min

/**
 * A custom textview created because of clipping issues with current [android.widget.TextView] in android framework.
 *
 * This class does not clip the text in any case. This way all fonts, especially the non-standard one do not get clipped.
 *
 */
class MananTextView(context: Context, attr: AttributeSet?) : View(context, attr),
    MananComponent, Bitmapable, Pathable, Texturable, Gradientable, StrokeCapable, Blurable,
    Colorable, java.io.Serializable, Shadowable {

    constructor(context: Context) : this(context, null)

    private var shadowRadius = 0f
    private var trueShadowRadius = 0f
    private var shadowDx = 0f
    private var shadowDy = 0f
    private var shadowLColor = Color.YELLOW
    private var isShadowCleared = true

    private var rawWidth = 0f
    private var rawHeight = 0f

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
    var textStrokeColor: Int = Color.BLACK

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


    private var pathOnValue = 0f
    private var pathOffValue = 0f
    private var pathStrokeWidth = 0f
    private var pathRadius = 0f

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
            setScale(
                (rawWidth / width) * scaleX,
                (rawHeight / height) * scaleY,
                finalBounds.centerX(),
                finalBounds.centerY()
            )
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

    override fun applyScale(xFactor: Float, yFactor: Float) {
        scaleX *= xFactor
        scaleY *= yFactor
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
            textView.textStrokeColor = textStrokeColor
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
            textView.setShadow(
                trueShadowRadius,
                shadowDx,
                shadowDy,
                shadowLColor
            )
            textView.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
        }
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
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

        rawWidth = widths.maxOf { it } + extraSpace
        val textWidth =
            rawWidth + paddingLeft + paddingRight

        textPaint.getTextBounds(text, 0, text.length, textBoundsRect)

        rawHeight = ((textBoundsRect.height().toFloat() + extraSpace) * finalTexts.size)

        val textHeight =
            (rawHeight + paddingTop + paddingBottom)

        pivotX = textWidth * 0.5f
        pivotY = textHeight * 0.5f

        setMeasuredDimension(
            max(suggestedMinimumWidth, textWidth.toInt()),
            max(suggestedMinimumHeight, textHeight.toInt())
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

        //textBaseLineY = height.toFloat - textBoundsRect.bottom; // for sticking at bottom
        textBaseLineY =
            ((rawHeight + height) * 0.5f) - textBoundsRect.bottom

        textBaseLineX = -textBoundsRect.left.toFloat()

    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.run {
            super.onDraw(this)

            var finalTranslateX = 0f
            var finalTranslateY = 0f

            val halfSpace = extraSpace * 0.5f
            when (alignmentText) {
                Alignment.LEFT -> {
                    finalTranslateX = halfSpace + paddingRight
                    finalTranslateY = halfSpace
                }
                Alignment.CENTER -> {
                    finalTranslateY = halfSpace
                }
                Alignment.RIGHT -> {
                    finalTranslateX = -(halfSpace + paddingLeft)
                    finalTranslateY = halfSpace
                }
            }

            translate(
                (finalTranslateX + (paddingLeft - paddingRight)),
                -(finalTranslateY + (paddingBottom - paddingTop))
            )

            val toShift = ((rawHeight / finalTexts.size))

            if (shadowRadius > 0) {
                val currentColor = textColor
                val currentStyle = textPaint.style
                val currentShader = textPaint.shader
                textPaint.shader = null
                textPaint.style = Paint.Style.FILL_AND_STROKE
                textPaint.strokeWidth = textStrokeWidth

                textPaint.setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowLColor)

                drawTexts(this, toShift)

                textPaint.shader = currentShader
                textPaint.style = currentStyle
                textPaint.strokeWidth = 0f
                textPaint.color = currentColor

                textPaint.clearShadowLayer()
            }

            if (textStrokeWidth > 0f && textPaint.pathEffect == null) {
                val currentColor = textColor
                val currentStyle = textPaint.style
                val currentPath = textPaint.pathEffect
                val currentShader = textPaint.shader
                textPaint.style = Paint.Style.STROKE
                textPaint.strokeWidth = textStrokeWidth
                textPaint.shader = null
                textPaint.pathEffect = null
                textPaint.color = textStrokeColor

                drawTexts(this, toShift)

                textPaint.shader = currentShader
                textPaint.style = currentStyle
                textPaint.strokeWidth = 0f
                textPaint.pathEffect = currentPath
                textPaint.color = currentColor

            }

            drawTexts(this, toShift)

        }
    }

    private fun drawTexts(canvas: Canvas, toShift: Float) {
        var i = 0
        finalTexts.forEach { map ->

            val totalTranslated = rawHeight - (toShift * (finalTexts.size - (i)))
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

    override fun toBitmap(config: Bitmap.Config, ignoreAxisScale: Boolean): Bitmap {
        if (ignoreAxisScale) {
            return Bitmap.createBitmap(
                width,
                height,
                config
            ).also { bitmap ->
                draw(Canvas(bitmap))
            }
        } else {
            val wStroke = width
            val hStroke = height

            var w = wStroke * scaleX
            var h = hStroke * scaleY
            val s = max(wStroke, hStroke) / max(w, h)
            w *= s
            h *= s
            return Bitmap.createBitmap(
                w.toInt(),
                h.toInt(),
                config
            ).also { bitmap ->
                draw(Canvas(bitmap).also { canvas ->
                    canvas.scale(w / wStroke, h / hStroke)
                })
            }
        }
    }

    override fun toBitmap(
        width: Int,
        height: Int,
        config: Bitmap.Config,
        ignoreAxisScale: Boolean
    ): Bitmap {
        val wStroke = this.width
        val hStroke = this.height

        var w = if (ignoreAxisScale) wStroke.toFloat() else wStroke * scaleX
        var h = if (ignoreAxisScale) hStroke.toFloat() else hStroke * scaleY

        val s = max(wStroke, hStroke) / max(w, h)

        w *= s
        h *= s

        val scale = min(width.toFloat() / w, height.toFloat() / h)

        val ws = (w * scale)
        val hs = (h * scale)

        val outputBitmap = Bitmap.createBitmap(width, height, config)

        val extraWidth = width - ws
        val extraHeight = height - hs

        Canvas(outputBitmap).run {
            translate(extraWidth * 0.5f, extraHeight * 0.5f)

            scale(ws / wStroke, hs / hStroke)

            draw(this)
        }

        return outputBitmap
    }

    override fun applyPath(on: Float, off: Float, radius: Float, strokeWidth: Float) {
        textPaint.apply {

            pathOnValue = on
            pathOffValue = off
            pathRadius = radius
            pathStrokeWidth = strokeWidth

            if (textStrokeWidth == 0f) {
                this.strokeWidth = pathStrokeWidth
            }

            style = Paint.Style.STROKE

            pathEffect = ComposePathEffect(
                DashPathEffect(floatArrayOf(pathOnValue, pathOffValue), 0f),
                CornerPathEffect(pathRadius)
            )

            textPaint.textSize += 0.0001f

            invalidate()
        }
    }

    override fun applyPath(onAndOff: Float, radius: Float, strokeWidth: Float) {
        applyPath(onAndOff, onAndOff, radius, strokeWidth)
    }

    override fun removePath() {
        pathOnValue = 0f
        pathOffValue = 0f
        pathRadius = 0f
        pathStrokeWidth = 0f
        if (textPaint.pathEffect != null) {
            textPaint.pathEffect = null
            textPaint.style = Paint.Style.FILL
            textPaint.textSize -= 0.001f
            invalidate()
        }
    }

    override fun applyTexture(bitmap: Bitmap) {
        applyTexture(bitmap, Shader.TileMode.MIRROR)
    }

    /**
     * Applies a texture to the text with default tileMode of [Shader.TileMode.REPEAT]
     * @param bitmap The bitmap texture that is going to be applied to the view.
     * @param tileMode The bitmap mode [Shader.TileMode]
     */
    override fun applyTexture(bitmap: Bitmap, tileMode: Shader.TileMode) {
        paintShader = BitmapShader(bitmap, tileMode, tileMode).apply {
            setLocalMatrix(shaderMatrix)
        }

        textPaint.shader = BitmapShader(bitmap, tileMode, tileMode).apply {
            setLocalMatrix(shaderMatrix)
        }
        invalidate()
    }

    override fun shiftColor(dx: Float, dy: Float) {
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

    override fun scaleColor(scaleFactor: Float) {
        textPaint.shader?.run {
            shaderMatrix.postScale(scaleFactor, scaleFactor, pivotX, pivotY)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun rotateColor(rotation: Float) {
        textPaint.shader?.run {
            shaderMatrix.postRotate(
                rotation - shaderRotationHolder,
                pivotX,
                pivotY
            )
            shaderRotationHolder = rotation
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun resetComplexColorMatrix() {
        textPaint.shader?.run {
            shaderMatrix.reset()
            shaderRotationHolder = 0f
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
        tileMode: Shader.TileMode
    ) {
        paintShader = LinearGradient(x0, y0, x1, y1, colors, position, tileMode)

        textPaint.shader =
            LinearGradient(x0, y0, x1, y1, colors, position, tileMode)

        invalidate()
    }

    override fun applyRadialGradient(
        centerX: Float,
        centerY: Float,
        radius: Float,
        colors: IntArray,
        stops: FloatArray?,
        tileMode: Shader.TileMode
    ) {
        paintShader = RadialGradient(
            centerX,
            centerY,
            radius,
            colors,
            stops,
            tileMode
        )

        textPaint.shader =
            RadialGradient(
                centerX,
                centerY,
                radius,
                colors,
                stops,
                tileMode
            )
        invalidate()
    }

    override fun applySweepGradient(
        cx: Float,
        cy: Float,
        colors: IntArray,
        positions: FloatArray?
    ) {
        paintShader = SweepGradient(cx, cy, colors, positions)

        textPaint.shader =
            SweepGradient(cx, cy, colors, positions)

        invalidate()
    }

    override fun setStroke(strokeRadiusPx: Float, strokeColor: Int) {
        if (strokeRadiusPx < 0f) throw IllegalStateException("Stroke width should be a positive number")
        textStrokeWidth = strokeRadiusPx
        this.textStrokeColor = strokeColor

        shiftTextureWithAlignment(strokeRadiusPx)

        extraSpace = strokeRadiusPx
        requestLayout()
        shadowRadius -= 0.00001f
        invalidate()
    }

    override fun getStrokeColor(): Int {
        return textStrokeColor
    }

    override fun getStrokeWidth(): Float {
        return textStrokeWidth
    }

    private fun shiftTextureWithAlignment(currentStroke: Float) {
        val diffCurrentStrokeWithLast = (currentStroke - extraSpace)

        var finalShiftValueX = 0f

        when (alignmentText) {
            Alignment.LEFT -> {

            }
            Alignment.RIGHT -> {
                finalShiftValueX = diffCurrentStrokeWithLast
            }
            Alignment.CENTER -> {
                finalShiftValueX = diffCurrentStrokeWithLast * 0.5f
            }
        }

        shiftColor(finalShiftValueX, diffCurrentStrokeWithLast)
    }

    override fun getShadowDx(): Float {
        return shadowDx
    }

    override fun getShadowDy(): Float {
        return shadowDy
    }

    override fun getShadowRadius(): Float {
        return trueShadowRadius
    }

    override fun getShadowColor(): Int {
        return shadowLColor
    }

    override fun setShadow(radius: Float, dx: Float, dy: Float, shadowColor: Int) {
        val ds = context.resources.displayMetrics
        val wP = ds.widthPixels
        val hP = ds.heightPixels
        val mx = max(wP, hP)
        setPadding(mx)

        if (isShadowCleared) {
            when (alignmentText) {
                Alignment.LEFT -> {
                    shiftColor(0f, paddingBottom.toFloat())
                }
                Alignment.RIGHT -> {
                    shiftColor(paddingLeft * 2f, paddingBottom.toFloat())
                }
                Alignment.CENTER -> {
                    shiftColor((paddingLeft.toFloat()), paddingBottom.toFloat())
                }
            }
            isShadowCleared = false
        }

        shadowRadius = radius
        trueShadowRadius = radius
        shadowDx = dx
        shadowDy = dy
        shadowLColor = shadowColor
        invalidate()
    }

    override fun clearShadow() {
        var prePadX = paddingLeft.toFloat()
        val prePadY = paddingBottom.toFloat()

        when (alignmentText) {
            Alignment.LEFT -> {
                prePadX = 0f
            }
            Alignment.RIGHT -> {
                prePadX = paddingLeft.toFloat() * 2f
            }
            Alignment.CENTER -> {
            }
        }

        setPadding(0)

        shiftColor(-prePadX, -prePadY) // Alignment center

        when (alignmentText) {
            Alignment.LEFT -> {
                shiftColor(0f, paddingBottom.toFloat())
            }
            Alignment.RIGHT -> {
                shiftColor(paddingLeft * 2f, paddingBottom.toFloat())
            }
            Alignment.CENTER -> {
                shiftColor((paddingLeft.toFloat()), paddingBottom.toFloat())
            }
        }
        textPaint.clearShadowLayer()
        shadowRadius = 0f
        shadowDx = 0f
        shadowDy = 0f
        shadowLColor = Color.YELLOW
        isShadowCleared = true
        invalidate()
    }

    override fun changeColor(color: Int) {
        textColor = color
    }

    override fun getColor(): Int {
        return textColor
    }

    override fun getOnValue(): Float {
        return pathOnValue
    }

    override fun getOffValue(): Float {
        return pathOffValue
    }

    override fun getPathRadius(): Float {
        return pathRadius
    }

    override fun getPathStrokeWidth(): Float {
        return pathStrokeWidth
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
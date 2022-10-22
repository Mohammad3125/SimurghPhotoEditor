package ir.manan.mananpic.components

import android.content.Context
import android.graphics.*
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.view.doOnPreDraw
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
    MananComponent, Bitmapable, Pathable, Texturable, Gradientable, StrokeCapable,
    Blendable,
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

    @RequiresApi(api = 21)
    var letterSpacing = 0f
        set(value) {
            if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
                field = value
                textPaint.letterSpacing = field
                requestLayout()
            } else {
                throw IllegalStateException("letter spacing is only available after api 21")
            }
        }

    @Transient
    private var paintShader: Shader? = null

    private val finalTexts by lazy {
        mutableListOf<String>()
    }

    private val finalWidths by lazy {
        mutableListOf<Float>()
    }

    private val finalXBaseLine by lazy {
        mutableListOf<Int>()
    }

    private val finalYBaseLine by lazy {
        mutableListOf<Int>()
    }

    private val finalHeights by lazy {
        mutableListOf<Float>()
    }

    var lineSpacing = 0f
        set(value) {
            field = value
            requestLayout()
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

    private var blendMode = PorterDuff.Mode.SRC

    private var gradientColors: IntArray? = null

    private var gradientPositions: FloatArray? = null

    init {
//        setLayerType(LAYER_TYPE_HARDWARE, textPaint)
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
                scaleX,
                scaleY,
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
        return pivotY
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
            if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
                textView.letterSpacing = letterSpacing
            }

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
            if (blendMode != PorterDuff.Mode.SRC) {
                textView.setBlendMode(blendMode)
            }
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
        finalWidths.clear()
        finalXBaseLine.clear()
        finalYBaseLine.clear()
        finalHeights.clear()

        val texts = text.split("\n", ignoreCase = true)

        var maxWidth = 0f

        var maxHeight = 0f

        val isOneLine = texts.size == 1
        var isFirstPass = true

        texts.map { string ->
            textPaint.getTextBounds(
                string,
                0,
                string.length,
                textBoundsRect
            )
            val w = textBoundsRect.width().toFloat()
            val h = textBoundsRect.height().toFloat()

            if (w > maxWidth) {
                maxWidth = w
            }

            if (h > maxHeight) {
                maxHeight = h
            }

            finalTexts.add(string)
            finalWidths.add(w)
            finalXBaseLine.add(textBoundsRect.left)
            finalYBaseLine.add(textBoundsRect.bottom)

            var finalExtra = extraSpace

            if (!isOneLine && !isFirstPass) {
                finalExtra += lineSpacing
            }
            finalHeights.add(h + finalExtra)

            isFirstPass = false

        }

        rawWidth = maxWidth + extraSpace

        val textWidth =
            rawWidth + paddingLeft + paddingRight

        rawHeight = finalHeights.sum()

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

            if (shadowRadius > 0) {
                val currentColor = textColor
                val currentStyle = textPaint.style
                val currentShader = textPaint.shader
                textPaint.shader = null
                textPaint.style = Paint.Style.FILL_AND_STROKE
                textPaint.strokeWidth = textStrokeWidth

                textPaint.setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowLColor)

                drawTexts(this)

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

                drawTexts(this)

                textPaint.shader = currentShader
                textPaint.style = currentStyle
                textPaint.strokeWidth = 0f
                textPaint.pathEffect = currentPath
                textPaint.color = currentColor

            }

            drawTexts(this)

        }
    }

    private fun drawTexts(canvas: Canvas) {

        var acc = 0f
        finalTexts.forEachIndexed { index, s ->

            val toTransfer = acc

            shiftTextureWithoutInvalidation(0f, toTransfer)

            acc += finalHeights[index]

            canvas.drawText(
                s,
                ((width - finalWidths[index]) * Alignment.getNumber(alignmentText)) - finalXBaseLine[index],
                (if (!isShadowCleared) (height - rawHeight) * 0.5f else 0f) + (acc - finalYBaseLine[index]),
                textPaint
            )

            shiftTextureWithoutInvalidation(0f, -toTransfer)
        }
    }

    /**
     * Sets type face of current text.
     * @param style Style of typeface, [Typeface.ITALIC],[Typeface.BOLD],[Typeface.BOLD_ITALIC],[Typeface.NORMAL]
     */
    fun setTypeface(typeface: Typeface, style: Int) {
        val finalTypeface =
            if (style > 0) {
                Typeface.create(typeface, style)
            } else {
                typeface
            }
        textPaint.typeface = finalTypeface
        requestLayout()
    }

    /**
     * Sets type face of current text.
     */
    fun setTypeface(typeface: Typeface) {
        textPaint.typeface = typeface
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

    override fun removeTexture() {
        paintShader = null
        textPaint.shader = null
        invalidate()
    }

    override fun removeGradient() {
        paintShader = null
        textPaint.shader = null
        gradientColors = null
        gradientPositions = null
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

        gradientColors = colors
        gradientPositions = position

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

        gradientColors = colors
        gradientPositions = stops

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
        gradientColors = colors
        gradientPositions = positions

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
        shadowRadius = radius
        trueShadowRadius = radius
        shadowDx = dx
        shadowDy = dy
        shadowLColor = shadowColor
        invalidate()
    }

    override fun clearShadow() {
        textPaint.clearShadowLayer()
        shadowRadius = 0f
        shadowDx = 0f
        shadowDy = 0f
        shadowLColor = Color.YELLOW
        isShadowCleared = true
        invalidate()
    }

    override fun isGradientApplied(): Boolean {
        return (textPaint.shader != null && (textPaint.shader is LinearGradient || textPaint.shader is RadialGradient || textPaint.shader is SweepGradient))
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

    override fun setBlendMode(blendMode: PorterDuff.Mode) {
        textPaint.xfermode = PorterDuffXfermode(blendMode)
        this.blendMode = blendMode
        invalidate()
    }

    override fun clearBlend() {
        textPaint.xfermode = null
        blendMode = PorterDuff.Mode.SRC
        invalidate()
    }

    override fun getBlendMode(): PorterDuff.Mode {
        return blendMode
    }

    override fun reportPositions(): FloatArray? {
        return gradientPositions
    }

    override fun reportColors(): IntArray? {
        return gradientColors
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
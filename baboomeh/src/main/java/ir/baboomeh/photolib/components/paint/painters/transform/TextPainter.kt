package ir.baboomeh.photolib.components.paint.painters.transform

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposePathEffect
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.TextPaint
import androidx.annotation.ColorInt
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.createBitmap
import androidx.core.graphics.green
import androidx.core.graphics.red
import ir.baboomeh.photolib.properties.Bitmapable
import ir.baboomeh.photolib.properties.Blendable
import ir.baboomeh.photolib.properties.Colorable
import ir.baboomeh.photolib.properties.Gradientable
import ir.baboomeh.photolib.properties.Opacityable
import ir.baboomeh.photolib.properties.Pathable
import ir.baboomeh.photolib.properties.Shadowable
import ir.baboomeh.photolib.properties.StrokeCapable
import ir.baboomeh.photolib.properties.TextBackgroundable
import ir.baboomeh.photolib.properties.Texturable
import ir.baboomeh.photolib.utils.matrix.MananMatrix
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

open class TextPainter : Transformable(), Pathable, Texturable, Gradientable, StrokeCapable,
    Blendable, Bitmapable,
    Colorable, Shadowable, Opacityable, TextBackgroundable {


    protected var isUnified: Boolean = false

    protected var extraSpaceHalf: Float = 0f

    protected val backgroundPath by lazy {
        Path()
    }
    protected val unifiedBackgroundPath by lazy {
        Path()
    }
    protected val connectorPath by lazy {
        Path()
    }

    protected val blocHolder by lazy {
        mutableListOf<TextBloc>()
    }

    protected var textShadowRadius = 0f
    protected var textShadowDx = 0f
    protected var textShadowDy = 0f
    protected var textShadowColor = Color.argb(120, 255, 255, 0)

    protected var rawWidth = 0f
    protected var rawHeight = 0f

    protected var opacityHolder: Int = 255

    protected val textPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }


    protected val dstOutMode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

    protected val textBounds = RectF()

    /**
     * Text of current text view.
     */
    open var text = ""
        set(value) {
            field = value
            notifyBoundsChanged()
        }


    open var textColor = Color.BLACK
        set(value) {
            textPaint.color = value
            field = value
            invalidate()
        }

    /* Allocations --------------------------------------------------------------------------------------------  */

    protected val shaderMatrix = MananMatrix()

    protected val textBoundsRect = Rect()

    /**
     * Baseline of text to be drawn.
     */
    protected var textBaseLineY = 0f

    protected var textBaseLineX = 0f

    /**
     * Extra space used to expand the width and height of view to prevent clipping in special cases like Blur mask and so on.
     */
    protected var extraSpace = 0f

    protected var shaderRotationHolder = 0f

    protected var isTextBackgroundEnabled = false

    protected var backgroundPaddingSize = 0f

    protected var textBackgroundRadius = 12f

    @ColorInt
    protected var textBackgroundColor: Int = Color.GRAY

    protected val backgroundPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = textBackgroundColor
        }
    }

    protected val firstBackgroundRadiusArray = FloatArray(8) {
        textBackgroundRadius
    }

    protected var textStrokeWidth = 0f
    protected var textStrokeColor: Int = Color.BLACK

    open var letterSpacing = 0f
        set(value) {
            field = value
            textPaint.letterSpacing = field
            notifyBoundsChanged()
        }

    open var lineSpacing = textPaint.fontMetrics.bottom
        set(value) {
            field = value
            notifyBoundsChanged()
        }

    /**
     * Sets alignment of text when drawn.
     * - [Alignment.CENTER] Centers text.
     * - [Alignment.LEFT] Draws text to left of view.
     * - [Alignment.RIGHT] Draws text to right of view.
     */
    open var alignmentText: Alignment = Alignment.CENTER
        set(value) {
            field = value
            notifyBoundsChanged()
            invalidate()
        }

    open var typeface: Typeface = Typeface.DEFAULT
        set(value) {
            field = value
            typefaceStyle = value.style
            textPaint.typeface = typeface
            lineSpacing = textPaint.fontMetrics.bottom
        }

    open var typefaceStyle = Typeface.NORMAL

    protected var pathOnValue = 0f
    protected var pathOffValue = 0f
    protected var textPathStrokeWidth = 0f
    protected var textPathRadius = 0f

    protected var textBlendMode = PorterDuff.Mode.SRC

    protected var gradientColors: IntArray? = null

    protected var gradientPositions: FloatArray? = null

    open var underlineSize = 0f
        set(value) {
            field = value
            notifyBoundsChanged()
        }

    open var isStrikethrough = false
        set(value) {
            field = value
            textPaint.isStrikeThruText = field
            notifyBoundsChanged()
        }

    protected var currentTexture: Bitmap? = null

    init {
        // Minimum size of a small font cache recommended in OpenGlRendered properties.
        textPaint.textSize = 256f
    }


    override fun clone(): TextPainter {
        return TextPainter().also { textPainter ->
            textPainter.textPaint.textSize = textPaint.textSize
            textPainter.alignmentText = alignmentText
            textPainter.textColor = textColor
            textPainter.typeface = Typeface.create(typeface, typefaceStyle)
            textPainter.typefaceStyle = typefaceStyle
            textPainter.textPaint.style = textPaint.style
            textPainter.textPaint.strokeWidth = textPaint.strokeWidth
            textPainter.textPaint.pathEffect = textPaint.pathEffect
            textPainter.textPaint.shader = textPaint.shader
            textPainter.letterSpacing = letterSpacing
            textPainter.lineSpacing = lineSpacing
            textPainter.underlineSize = underlineSize
            textPainter.isStrikethrough = isStrikethrough
            textPainter.extraSpace = extraSpace
            textPainter.textBaseLineY = textBaseLineY
            textPainter.textBaseLineX = textBaseLineX
            textPainter.setStroke(getStrokeWidth(), getStrokeColor())
            textPainter.setBackground(
                getBackgroundPadding(),
                getBackgroundRadius(),
                getBackgroundColor()
            )
            textPainter.setBackgroundState(getBackgroundState())
            textPainter.setBackgroundUnifiedState(isUnified)
            textPainter.shaderRotationHolder = shaderRotationHolder

            val childBounds = RectF()
            getBounds(childBounds)

            when (textPaint.shader) {
                is LinearGradient -> {
                    textPainter.applyLinearGradient(
                        0f,
                        childBounds.height() * 0.5f,
                        childBounds.width(),
                        childBounds.height() * 0.5f,
                        gradientColors!!,
                        gradientPositions!!,
                    )
                }

                is RadialGradient -> {
                    textPainter.applyRadialGradient(
                        childBounds.width() * 0.5f,
                        childBounds.height() * 0.5f,
                        childBounds.width() * 0.5f,
                        gradientColors!!,
                        gradientPositions!!,
                    )
                }

                is SweepGradient -> {
                    textPainter.applySweepGradient(
                        childBounds.width() * 0.5f,
                        childBounds.height() * 0.5f,
                        gradientColors!!,
                        gradientPositions!!,
                    )
                }
            }

            getTexture()?.let { t ->
                textPainter.applyTexture(t)
            }

            textPainter.shaderMatrix.set(shaderMatrix)

            if (textPainter.textPaint.shader != null) {
                textPainter.textPaint.shader.setLocalMatrix(shaderMatrix)
            }

            textPainter.textPaint.maskFilter = textPaint.maskFilter
            if (textBlendMode != PorterDuff.Mode.SRC) {
                textPainter.setBlendMode(textBlendMode)
            }
            textPainter.setShadow(
                textShadowRadius,
                textShadowDx,
                textShadowDy,
                textShadowColor
            )

            textPainter.text = text

            textPainter.setOpacity(getOpacity())
        }
    }

    protected open fun drawTexts(canvas: Canvas) {
        canvas.apply {
            var acc = 0f

            blocHolder.forEach { textBloc ->
                acc += textBloc.height

                val w =
                    ((rawWidth - textBloc.width) * Alignment.getNumber(alignmentText)) - textBloc.baselineX

                val h = acc - textBloc.baselineY

                drawText(
                    textBloc.text,
                    w,
                    h,
                    textPaint
                )

                if (underlineSize > 0f) {
                    drawRect(
                        w + textBloc.baselineX,
                        h,
                        w + textBloc.width + textBloc.baselineX,
                        h + underlineSize, textPaint
                    )
                }
            }
        }
    }

    protected open fun drawTextBackground(canvas: Canvas) {
        canvas.apply {
            val maxRect = RectF(Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)
            connectorPath.rewind()
            backgroundPath.rewind()
            unifiedBackgroundPath.rewind()
            blocHolder.forEachIndexed { index, textBloc ->
                textBloc.backgroundRect.let { currentBackground ->

                    val finalLineSpacing = if (index == 0) 0f else lineSpacing

                    backgroundPath.addRoundRect(
                        currentBackground.left,
                        currentBackground.top + finalLineSpacing,
                        currentBackground.right,
                        currentBackground.bottom,
                        firstBackgroundRadiusArray, Path.Direction.CCW
                    )

                    if (index > 0) {
                        blocHolder[index - 1].backgroundRect.let { lastBackground ->
                            val lineSpacingSecondary = if (index < 2) 0f else lineSpacing

                            val maxRadiusAllowed =
                                abs(currentBackground.width() - lastBackground.width()) / 4f

                            when {
                                textBackgroundRadius > maxRadiusAllowed -> {
                                    maxRect.left =
                                        min(
                                            maxRect.left,
                                            min(lastBackground.left, currentBackground.left)
                                        )
                                    maxRect.top =
                                        min(
                                            maxRect.top,
                                            min(lastBackground.top, currentBackground.top)
                                        )
                                    maxRect.right =
                                        max(
                                            maxRect.right,
                                            max(lastBackground.right, currentBackground.right)
                                        )
                                    maxRect.bottom =
                                        max(
                                            maxRect.bottom,
                                            max(lastBackground.bottom, currentBackground.bottom)
                                        ) + (finalLineSpacing.takeIf { textBloc.isSandwich } ?: 0f)


                                    unifiedBackgroundPath.addRoundRect(
                                        maxRect,
                                        firstBackgroundRadiusArray,
                                        Path.Direction.CCW
                                    )
                                }

                                currentBackground.width() == lastBackground.width() -> {
                                    backgroundPath.addRoundRect(
                                        lastBackground.left,
                                        lastBackground.top,
                                        currentBackground.right,
                                        currentBackground.bottom,
                                        firstBackgroundRadiusArray, Path.Direction.CCW
                                    )
                                }

                                currentBackground.width() > lastBackground.width() -> {

                                    val finalBottom = currentBackground.top + lineSpacing

                                    val finalTop = if (blocHolder[index - 1].isSandwich) {
                                        blocHolder[index - 2].backgroundRect.bottom
                                    } else {
                                        lastBackground.top
                                    }

                                    backgroundPath.addRoundRect(
                                        (currentBackground.left + lastBackground.left) * 0.5f,
                                        (lastBackground.top + lineSpacingSecondary + currentBackground.top) * 0.5f,
                                        (currentBackground.right + lastBackground.right) * 0.5f,
                                        finalBottom,
                                        0f, 0f, Path.Direction.CCW
                                    )

                                    if (alignmentText != TextPainter.Alignment.LEFT) {
                                        connectorPath.addRoundRect(
                                            currentBackground.left,
                                            finalTop,
                                            lastBackground.left,
                                            finalBottom,
                                            firstBackgroundRadiusArray, Path.Direction.CCW
                                        )
                                    } else {
                                        backgroundPath.addRoundRect(
                                            lastBackground.left,
                                            (lastBackground.top + lineSpacingSecondary + lastBackground.bottom) * 0.5f,
                                            (currentBackground.right + lastBackground.right) * 0.5f,
                                            currentBackground.bottom - extraSpaceHalf,
                                            firstBackgroundRadiusArray,
                                            Path.Direction.CCW
                                        )
                                    }
                                    if (alignmentText != TextPainter.Alignment.RIGHT) {
                                        connectorPath.addRoundRect(
                                            lastBackground.right,
                                            finalTop,
                                            currentBackground.right,
                                            finalBottom,
                                            firstBackgroundRadiusArray, Path.Direction.CCW
                                        )
                                    } else {
                                        backgroundPath.addRoundRect(
                                            (currentBackground.left + lastBackground.left) * 0.5f,
                                            (lastBackground.top + lineSpacingSecondary + lastBackground.bottom) * 0.5f,
                                            currentBackground.right,
                                            currentBackground.bottom - extraSpaceHalf,
                                            firstBackgroundRadiusArray,
                                            Path.Direction.CCW
                                        )
                                    }
                                }

                                else -> {

                                    val finalBottom = if (textBloc.isSandwich) {
                                        blocHolder[index + 1].backgroundRect.top
                                    } else {
                                        currentBackground.bottom
                                    }

                                    backgroundPath.addRoundRect(
                                        (lastBackground.left + currentBackground.left) * 0.5f,
                                        currentBackground.top,
                                        (currentBackground.right + lastBackground.right) * 0.5f,
                                        (lastBackground.bottom + lineSpacing + currentBackground.bottom) * 0.5f,
                                        0f, 0f, Path.Direction.CCW
                                    )

                                    if (alignmentText != TextPainter.Alignment.LEFT) {
                                        connectorPath.addRoundRect(
                                            lastBackground.left,
                                            lastBackground.bottom,
                                            currentBackground.left,
                                            finalBottom,
                                            firstBackgroundRadiusArray, Path.Direction.CCW
                                        )
                                    } else {
                                        backgroundPath.addRoundRect(
                                            lastBackground.left,
                                            lastBackground.top,
                                            currentBackground.right,
                                            currentBackground.bottom - extraSpaceHalf,
                                            firstBackgroundRadiusArray,
                                            Path.Direction.CCW
                                        )
                                    }

                                    if (alignmentText != TextPainter.Alignment.RIGHT) {
                                        connectorPath.addRoundRect(
                                            currentBackground.right,
                                            lastBackground.bottom,
                                            lastBackground.right,
                                            finalBottom,
                                            firstBackgroundRadiusArray, Path.Direction.CCW
                                        )
                                    } else {
                                        backgroundPath.addRoundRect(
                                            currentBackground.left,
                                            lastBackground.top,
                                            currentBackground.right,
                                            currentBackground.bottom - extraSpaceHalf,
                                            firstBackgroundRadiusArray,
                                            Path.Direction.CCW
                                        )
                                    }

                                }
                            }

                        }
                    }
                }
            }

            backgroundPath.op(connectorPath, Path.Op.DIFFERENCE)
            backgroundPath.op(unifiedBackgroundPath, Path.Op.UNION)

            drawPath(backgroundPath, backgroundPaint)
        }
    }

    /**
     * Sets type face of current text.
     * @param style Style of typeface, [Typeface.ITALIC],[Typeface.BOLD],[Typeface.BOLD_ITALIC],[Typeface.NORMAL]
     */
    open fun setTypeface(typeface: Typeface, style: Int) {
        textPaint.typeface = Typeface.create(typeface, style)
        typefaceStyle = style
        lineSpacing = textPaint.fontMetrics.bottom
    }

    open fun setTextStyle(style: Int) {
        setTypeface(typeface, style)
    }

    override fun applyPath(on: Float, off: Float, radius: Float, strokeWidth: Float) {
        textPaint.apply {

            pathOnValue = on
            pathOffValue = off
            textPathRadius = radius
            textPathStrokeWidth = strokeWidth

            if (textStrokeWidth == 0f) {
                this.strokeWidth = textPathStrokeWidth
            }

            style = Paint.Style.STROKE

            pathEffect = ComposePathEffect(
                DashPathEffect(floatArrayOf(pathOnValue, pathOffValue), 0f),
                CornerPathEffect(textPathRadius)
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
        textPathRadius = 0f
        textPathStrokeWidth = 0f
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
        removeGradient()
        currentTexture = bitmap

        textPaint.shader = BitmapShader(bitmap, tileMode, tileMode).apply {
            setLocalMatrix(shaderMatrix)
        }
        invalidate()
    }

    override fun getTexture(): Bitmap? {
        return currentTexture
    }

    override fun shiftColor(dx: Float, dy: Float) {
        textPaint.shader?.run {
            shaderMatrix.postTranslate(dx, dy)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun scaleColor(scaleFactor: Float) {
        textPaint.shader?.run {
            shaderMatrix.postScale(scaleFactor, scaleFactor, 0f, 0f)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun rotateColor(rotation: Float) {
        textPaint.shader?.run {
            shaderMatrix.postRotate(
                rotation - shaderRotationHolder,
                0f,
                0f
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

    override fun concatColorMatrix(matrix: Matrix) {
        textPaint.shader?.run {
            shaderMatrix.postConcat(matrix)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun removeTexture() {
        currentTexture = null
        textPaint.shader = null
        invalidate()
    }

    override fun removeGradient() {
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
        removeTexture()
        gradientColors = colors
        gradientPositions = position

        textPaint.shader =
            LinearGradient(x0, y0, x1, y1, colors, position, tileMode).apply {
                setLocalMatrix(shaderMatrix)
            }

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
        removeTexture()
        gradientColors = colors
        gradientPositions = stops

        textPaint.shader =
            RadialGradient(
                centerX,
                centerY,
                radius,
                colors,
                stops,
                tileMode
            ).apply {
                setLocalMatrix(shaderMatrix)
            }
        invalidate()
    }

    override fun applySweepGradient(
        cx: Float,
        cy: Float,
        colors: IntArray,
        positions: FloatArray?
    ) {
        removeTexture()
        gradientColors = colors
        gradientPositions = positions

        textPaint.shader =
            SweepGradient(cx, cy, colors, positions).apply {
                setLocalMatrix(shaderMatrix)
            }

        invalidate()
    }

    override fun setStroke(strokeRadiusPx: Float, strokeColor: Int) {
        if (strokeRadiusPx < 0f) throw IllegalStateException("Stroke width should be a positive number")
        textStrokeWidth = strokeRadiusPx
        this.textStrokeColor = strokeColor
        notifyBoundsChanged()
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
        return textShadowDx
    }

    override fun getShadowDy(): Float {
        return textShadowDy
    }

    override fun getShadowRadius(): Float {
        return textShadowRadius
    }

    override fun getShadowColor(): Int {
        return textShadowColor
    }

    override fun setShadow(radius: Float, dx: Float, dy: Float, shadowColor: Int) {
        textShadowRadius = radius
        textShadowDx = dx
        textShadowDy = dy
        this.textShadowColor = shadowColor
        invalidate()
    }

    override fun clearShadow() {
        textPaint.clearShadowLayer()
        textShadowRadius = 0f
        textShadowDx = 0f
        textShadowDy = 0f
        textShadowColor = Color.YELLOW
        invalidate()
    }


    override fun toBitmap(config: Bitmap.Config): Bitmap? {
        return createBitmap(rawWidth.toInt(), rawHeight.toInt(), config).also { bitmap ->
            draw(Canvas(bitmap))
        }
    }

    override fun toBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap? {
        val scale = min(width.toFloat() / rawWidth, height.toFloat() / rawHeight)

        val ws = (rawWidth * scale)
        val hs = (rawHeight * scale)

        val outputBitmap = createBitmap(width, height, config)

        val extraWidth = width - ws
        val extraHeight = height - hs

        Canvas(outputBitmap).run {
            translate(extraWidth * 0.5f, extraHeight * 0.5f)

            scale(ws / rawWidth, hs / rawHeight)

            draw(this)
        }

        return outputBitmap
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
        return textPathRadius
    }

    override fun getPathStrokeWidth(): Float {
        return textPathStrokeWidth
    }

    override fun setBlendMode(blendMode: PorterDuff.Mode) {
        textPaint.xfermode = PorterDuffXfermode(blendMode)
        backgroundPaint.xfermode = textPaint.xfermode
        this.textBlendMode = blendMode
        invalidate()
    }

    override fun clearBlend() {
        textPaint.xfermode = null
        backgroundPaint.xfermode = null
        textBlendMode = PorterDuff.Mode.SRC
        invalidate()
    }

    override fun getBlendMode(): PorterDuff.Mode {
        return textBlendMode
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

    override fun getBounds(bounds: RectF) {
        blocHolder.clear()

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

            maxWidth = max(w, maxWidth)
            maxHeight = max(h, maxHeight)

            val baselineX = textBoundsRect.left
            var baselineY = textBoundsRect.bottom

            var finalExtra = 0f

            if (!isOneLine && !isFirstPass) {
                finalExtra = lineSpacing
            }

            if (underlineSize > 0f && underlineSize > textBoundsRect.bottom) {
                baselineY = underlineSize.toInt()
                finalExtra += (underlineSize - textBoundsRect.bottom)
            }

            isFirstPass = false

            blocHolder.add(TextBloc(string, w, h + finalExtra, baselineX, baselineY))
        }

        extraSpace = textStrokeWidth + if (isTextBackgroundEnabled) backgroundPaddingSize else 0f

        rawWidth = maxWidth + extraSpace

        rawHeight = blocHolder.sumOf { it.height.toDouble() }.toFloat() + extraSpace

        extraSpaceHalf = extraSpace * 0.5f

        if (isTextBackgroundEnabled) {
            blocHolder.foldIndexed(0f) { index, accumulatedHeight, textBloc ->
                (accumulatedHeight + textBloc.height).also { newHeight ->
                    val leftRect =
                        ((rawWidth - textBloc.width) * Alignment.getNumber(alignmentText)) - extraSpaceHalf
                    val topRect = newHeight - textBloc.height - extraSpaceHalf
                    val rightRect = leftRect + textBloc.width + extraSpace
                    val bottomRect = newHeight + extraSpaceHalf
                    textBloc.backgroundRect.set(leftRect, topRect, rightRect, bottomRect)

                    textBloc.isSandwich = index > 0 &&
                            (blocHolder.getOrNull(index + 1)?.width
                                ?: 0f) > textBloc.width && blocHolder[index - 1].width > textBloc.width
                }
            }
        }

        textBounds.set(0f, 0f, rawWidth, rawHeight)
        bounds.set(0f, 0f, rawWidth, rawHeight)
    }

    override fun draw(canvas: Canvas) {
        canvas.run {

            var finalTranslateX = 0f
            var finalTranslateY = 0f

            val halfSpace = extraSpace * 0.5f
            when (alignmentText) {
                Alignment.LEFT -> {
                    finalTranslateX = halfSpace
                    finalTranslateY = halfSpace
                }

                Alignment.CENTER -> {
                    finalTranslateY = halfSpace
                }

                Alignment.RIGHT -> {
                    finalTranslateX = -halfSpace
                    finalTranslateY = halfSpace
                }
            }

            translate(
                finalTranslateX,
                finalTranslateY
            )

            val opacityFactor = opacityHolder / 255f

            if (isTextBackgroundEnabled) {

                backgroundPaint.color =
                    textBackgroundColor.calculateColorAlphaWithOpacityFactor(opacityFactor)

                if (isUnified) {
                    textBounds.offset(-finalTranslateX, -finalTranslateY)
                    drawRoundRect(
                        textBounds,
                        textBackgroundRadius,
                        textBackgroundRadius,
                        backgroundPaint
                    )
                    textBounds.offset(finalTranslateX, finalTranslateY)
                } else {
                    drawTextBackground(canvas)
                }
                backgroundPaint.color = textBackgroundColor
                backgroundPaint.alpha = opacityHolder
            }

            if (textShadowRadius > 0) {
                val transformedColor =
                    textShadowColor.calculateColorAlphaWithOpacityFactor(opacityFactor)

                val currentStyle = textPaint.style
                val currentShader = textPaint.shader
                textPaint.shader = null
                textPaint.style = Paint.Style.FILL_AND_STROKE
                textPaint.strokeWidth = textStrokeWidth
                textPaint.color = transformedColor

                textPaint.setShadowLayer(
                    textShadowRadius,
                    textShadowDx,
                    textShadowDy,
                    transformedColor
                )

                //TODO: dst-put

                drawTexts(canvas)

                textPaint.clearShadowLayer()
                textPaint.shader = currentShader
                textPaint.style = currentStyle
                textPaint.strokeWidth = 0f
            }

            if (textStrokeWidth > 0f) {
                textPaint.alpha = 255
                saveLayer(-finalTranslateX, -finalTranslateY, rawWidth, rawHeight, textPaint)
            }

            val currentMode = textPaint.xfermode

            if (textStrokeWidth > 0f && textPaint.pathEffect == null) {

                val currentStyle = textPaint.style
                val currentPath = textPaint.pathEffect
                val currentShader = textPaint.shader

                textPaint.style = Paint.Style.STROKE
                textPaint.strokeWidth = textStrokeWidth
                textPaint.shader = null
                textPaint.pathEffect = null
                textPaint.xfermode = null
                textPaint.color =
                    textStrokeColor.calculateColorAlphaWithOpacityFactor(opacityFactor)

                drawTexts(canvas)

                textPaint.xfermode = dstOutMode
                textPaint.color = Color.BLACK
                textPaint.style = Paint.Style.FILL

                drawTexts(canvas)

                textPaint.xfermode = null
                textPaint.shader = currentShader
                textPaint.style = currentStyle
                textPaint.strokeWidth = 0f
                textPaint.pathEffect = currentPath
            }

            textPaint.color = textColor.calculateColorAlphaWithOpacityFactor(opacityFactor)
            drawTexts(canvas)

            if (textStrokeWidth > 0f) {
                textPaint.xfermode = currentMode
                restore()
            }

        }
    }

    override fun getOpacity(): Int {
        return opacityHolder
    }

    override fun setOpacity(opacity: Int) {
        textPaint.alpha = opacity
        backgroundPaint.alpha = opacity
        opacityHolder = opacity
        invalidate()
    }

    override fun setBackground(padding: Float, radius: Float, @ColorInt color: Int) {
        val shouldChangeBounds = (backgroundPaddingSize != padding || textBackgroundRadius != radius)
        backgroundPaddingSize = padding
        textBackgroundColor = color
        textBackgroundRadius = radius
        firstBackgroundRadiusArray.fill(textBackgroundRadius)
        if (shouldChangeBounds) {
            notifyBoundsChanged()
        } else {
            invalidate()
        }
    }

    override fun getBackgroundPadding(): Float {
        return backgroundPaddingSize
    }

    override fun getBackgroundRadius(): Float {
        return textBackgroundRadius
    }

    override fun getBackgroundColor(): Int {
        return textBackgroundColor
    }

    override fun getBackgroundState(): Boolean {
        return isTextBackgroundEnabled
    }

    override fun setBackgroundState(isEnabled: Boolean) {
        isTextBackgroundEnabled = isEnabled
        notifyBoundsChanged()
    }

    override fun setBackgroundUnifiedState(isUnified: Boolean) {
        this.isUnified = isUnified
        invalidate()
    }

    override fun isBackgroundUnified(): Boolean {
        return isUnified
    }

    protected fun @receiver: ColorInt Int.calculateColorAlphaWithOpacityFactor(
        factor: Float
    ): Int =
        if (factor == 1f) this else Color.argb(
            (this.alpha * factor).roundToInt(),
            this.red,
            this.green,
            this.blue,
        )

    protected data class TextBloc(
        val text: String,
        val width: Float,
        val height: Float,
        val baselineX: Int,
        val baselineY: Int,
        var isSandwich: Boolean = false,
        val backgroundRect: RectF = RectF()
    )

}
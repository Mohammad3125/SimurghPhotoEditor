package ir.manan.mananpic.components.paint.painters.transform

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
import ir.manan.mananpic.components.MananTextView
import ir.manan.mananpic.properties.Bitmapable
import ir.manan.mananpic.properties.Blendable
import ir.manan.mananpic.properties.Colorable
import ir.manan.mananpic.properties.Gradientable
import ir.manan.mananpic.properties.Pathable
import ir.manan.mananpic.properties.Shadowable
import ir.manan.mananpic.properties.StrokeCapable
import ir.manan.mananpic.properties.Texturable
import ir.manan.mananpic.utils.MananMatrix
import kotlin.math.min

class TextPainter : Transformable(), Pathable, Texturable, Gradientable, StrokeCapable,
    Blendable, Bitmapable,
    Colorable, Shadowable {

    private val backgroundPath by lazy {
        Path()
    }
    private val connectorPath by lazy {
        Path()
    }

    private val backgroundPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = backgroundColor
        }
    }

    @ColorInt
    var backgroundColor: Int = Color.GRAY
        set(value) {
            field = value
            backgroundPaint.color = field
            invalidate()
        }

    private var shadowRadius = 0f
    private var trueShadowRadius = 0f
    private var shadowDx = 0f
    private var shadowDy = 0f
    private var shadowLColor = Color.YELLOW
    private var isShadowCleared = true

    private var rawWidth = 0f
    private var rawHeight = 0f

    private val textPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }


    private val dstOutMode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

    private val textBounds = RectF()

    /**
     * Text of current text view.
     */
    var text = ""
        set(value) {
            field = value
            indicateBoundsChange()
        }


    var textColor = Color.BLACK
        set(value) {
            textPaint.color = value
            field = value
            invalidate()
        }

    /* Allocations --------------------------------------------------------------------------------------------  */

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

    var isTextBackgroundEnabled = false
        set(value) {
            field = value
            indicateBoundsChange()
        }

    var backgroundPaddingSize = 50f
        set(value) {
            field = value
            indicateBoundsChange()
        }

    var backgroundRadius = 12f
        set(value) {
            field = value
            firstBackgroundRadiusArray.fill(value)
            indicateBoundsChange()
        }

    private val firstBackgroundRadiusArray = FloatArray(8) {
        backgroundRadius
    }

    var textStrokeWidth = 0f
    var textStrokeColor: Int = Color.BLACK

    var letterSpacing = 0f
        set(value) {
            field = value
            textPaint.letterSpacing = field
            indicateBoundsChange()
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

    var lineSpacing = textPaint.fontMetrics.bottom
        set(value) {
            field = value
            indicateBoundsChange()
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

    var typeface: Typeface = Typeface.DEFAULT
        set(value) {
            field = value
            typefaceStyle = value.style
            textPaint.typeface = typeface
            lineSpacing = textPaint.fontMetrics.bottom
        }

    private var typefaceStyle = Typeface.NORMAL

    private var pathOnValue = 0f
    private var pathOffValue = 0f
    private var pathStrokeWidth = 0f
    private var pathRadius = 0f

    private var blendMode = PorterDuff.Mode.SRC

    private var gradientColors: IntArray? = null

    private var gradientPositions: FloatArray? = null

    var underlineSize = 0f
        set(value) {
            field = value
            indicateBoundsChange()
        }

    var isStrikethrough = false
        set(value) {
            field = value
            textPaint.isStrikeThruText = field
            indicateBoundsChange()
        }

    private var currentTexture: Bitmap? = null

    init {
        // Minimum size of a small font cache recommended in OpenGlRendered properties.
        textPaint.textSize = 256f
    }


    override fun clone(): TextPainter {
        return TextPainter().also { textPainter ->
            textPainter.textPaint.textSize = textPaint.textSize
            textPainter.alignmentText = alignmentText
            textPainter.textColor = textColor
            textPainter.setTypeface(typeface, typefaceStyle)
            textPainter.textPaint.style = textPaint.style
            textPainter.textPaint.strokeWidth = textPaint.strokeWidth
            textPainter.textPaint.pathEffect = textPaint.pathEffect
            textPainter.letterSpacing = letterSpacing
            textPainter.underlineSize = underlineSize
            textPainter.extraSpace = extraSpace
            textPainter.textBaseLineY = textBaseLineY
            textPainter.textBaseLineX = textBaseLineX
            textPainter.textStrokeColor = textStrokeColor
            textPainter.textStrokeWidth = textStrokeWidth
            textPainter.isTextBackgroundEnabled = isTextBackgroundEnabled
            textPainter.backgroundRadius = backgroundRadius
            textPainter.backgroundColor = backgroundColor
            textPainter.shaderRotationHolder = shaderRotationHolder

            getTexture()?.let { t ->
                textPainter.applyTexture(t)
            }

            textPainter.shaderMatrix.set(shaderMatrix)

            if (textPainter.textPaint.shader != null) {
                textPainter.textPaint.shader.setLocalMatrix(shaderMatrix)
            }

            textPainter.textPaint.maskFilter = textPaint.maskFilter
            if (blendMode != PorterDuff.Mode.SRC) {
                textPainter.setBlendMode(blendMode)
            }
            textPainter.setShadow(
                trueShadowRadius,
                shadowDx,
                shadowDy,
                shadowLColor
            )

            gradientColors?.let {
                textPainter.gradientColors = it.clone()
            }
            gradientPositions?.let {
                textPainter.gradientPositions = it.clone()
            }

            textPainter.text = text
        }
    }

    private fun Canvas.drawTexts() {

        var acc = 0f

        finalTexts.forEachIndexed { index, s ->

            acc += finalHeights[index]

            val w =
                ((rawWidth - finalWidths[index]) * Alignment.getNumber(alignmentText)) - finalXBaseLine[index]

            val h = acc - finalYBaseLine[index]

            drawText(
                s,
                w,
                h,
                textPaint
            )

            if (underlineSize > 0f) {
                drawRect(
                    w + finalXBaseLine[index],
                    h,
                    w + finalWidths[index] + finalXBaseLine[index],
                    h + underlineSize, textPaint
                )
            }

        }

    }

    private fun Canvas.drawTextBackground() {
        var acc = 0f
        val halfExtraSpace = extraSpace * 0.5f
        connectorPath.rewind()
        backgroundPath.rewind()
        finalTexts.forEachIndexed { index, s ->

            acc += finalHeights[index]

            val leftRect =
                ((rawWidth - finalWidths[index]) * Alignment.getNumber(alignmentText)) - halfExtraSpace

            val topRect = acc - finalHeights[index] - halfExtraSpace

            val rightRect = leftRect + finalWidths[index] + extraSpace

            val bottomRect = acc + halfExtraSpace

            val rectWidth = rightRect - leftRect

            val finalLineSpacing = if (index == 0) 0f else lineSpacing

            backgroundPath.addRoundRect(
                leftRect,
                topRect + finalLineSpacing,
                rightRect,
                bottomRect,
                firstBackgroundRadiusArray, Path.Direction.CCW
            )

            if (index > 0) {

                val lastLeftRect =
                    ((rawWidth - finalWidths[index - 1]) * Alignment.getNumber(alignmentText)) - halfExtraSpace

                val lastBottom = acc - finalHeights[index] + halfExtraSpace

                val lastRightRect = lastLeftRect + finalWidths[index - 1] + extraSpace

                val lastTop = lastBottom - finalHeights[index - 1] - halfExtraSpace

                val lastWidth = lastRightRect - lastLeftRect

                val lineSpacingSecondary = if (index < 2) 0f else lineSpacing

                if (rectWidth == lastWidth) {
                    backgroundPath.addRoundRect(
                        lastLeftRect,
                        lastTop,
                        rightRect,
                        bottomRect,
                        firstBackgroundRadiusArray, Path.Direction.CCW
                    )
                } else if (rectWidth > lastWidth) {

                    backgroundPath.addRoundRect(
                        (leftRect + lastLeftRect) * 0.5f,
                        (lastTop + lineSpacingSecondary + topRect) * 0.5f,
                        (rightRect + lastRightRect) * 0.5f,
                        topRect + lineSpacing,
                        0f, 0f, Path.Direction.CCW
                    )

                    val topConnectors =
                        ((lastTop + lineSpacingSecondary + topRect) * 0.5f) - backgroundRadius

                    if (alignmentText != TextPainter.Alignment.LEFT) {
                        connectorPath.addRoundRect(
                            leftRect,
                            topConnectors,
                            lastLeftRect,
                            topRect + lineSpacing,
                            firstBackgroundRadiusArray, Path.Direction.CCW
                        )
                    } else {
                        backgroundPath.addRoundRect(
                            lastLeftRect,
                            (lastTop + lineSpacingSecondary + lastBottom) * 0.5f,
                            (rightRect + lastRightRect) * 0.5f,
                            bottomRect - halfExtraSpace,
                            firstBackgroundRadiusArray,
                            Path.Direction.CCW
                        )
                    }
                    if (alignmentText != TextPainter.Alignment.RIGHT) {
                        connectorPath.addRoundRect(
                            lastRightRect,
                            topConnectors,
                            rightRect,
                            topRect + lineSpacing,
                            firstBackgroundRadiusArray, Path.Direction.CCW
                        )
                    } else {
                        backgroundPath.addRoundRect(
                            (leftRect + lastLeftRect) * 0.5f,
                            (lastTop + lastBottom) * 0.5f,
                            rightRect,
                            bottomRect - halfExtraSpace,
                            firstBackgroundRadiusArray,
                            Path.Direction.CCW
                        )
                    }
                } else {
                    backgroundPath.addRoundRect(
                        (lastLeftRect + leftRect) * 0.5f,
                        topRect,
                        (rightRect + lastRightRect) * 0.5f,
                        (lastBottom + lineSpacing + bottomRect) * 0.5f,
                        0f, 0f, Path.Direction.CCW
                    )

                    val bottomConnectors =
                        ((lastBottom + lineSpacing + bottomRect) * 0.5f) + backgroundRadius

                    if (alignmentText != TextPainter.Alignment.LEFT) {
                        connectorPath.addRoundRect(
                            lastLeftRect,
                            lastBottom,
                            leftRect,
                            bottomConnectors,
                            firstBackgroundRadiusArray, Path.Direction.CCW
                        )
                    } else {
                        backgroundPath.addRoundRect(
                            lastLeftRect,
                            lastTop,
                            rightRect,
                            bottomRect - halfExtraSpace,
                            firstBackgroundRadiusArray,
                            Path.Direction.CCW
                        )
                    }

                    if (alignmentText != TextPainter.Alignment.RIGHT) {
                        connectorPath.addRoundRect(
                            rightRect,
                            lastBottom,
                            lastRightRect,
                            bottomConnectors,
                            firstBackgroundRadiusArray, Path.Direction.CCW
                        )
                    } else {
                        backgroundPath.addRoundRect(
                            leftRect,
                            lastTop,
                            rightRect,
                            bottomRect - halfExtraSpace,
                            firstBackgroundRadiusArray,
                            Path.Direction.CCW
                        )
                    }

                }

            }
        }

        backgroundPath.op(connectorPath, Path.Op.DIFFERENCE)

        drawPath(backgroundPath, backgroundPaint)
    }

    /**
     * Sets type face of current text.
     * @param style Style of typeface, [Typeface.ITALIC],[Typeface.BOLD],[Typeface.BOLD_ITALIC],[Typeface.NORMAL]
     */
    fun setTypeface(typeface: Typeface, style: Int) {
        textPaint.typeface = Typeface.create(typeface, style)
        typefaceStyle = style
        lineSpacing = textPaint.fontMetrics.bottom
    }

    fun setTextStyle(style: Int) {
        setTypeface(typeface, style)
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
        currentTexture = bitmap
        paintShader = BitmapShader(bitmap, tileMode, tileMode).apply {
            setLocalMatrix(shaderMatrix)
        }

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
        paintShader = null
        currentTexture = null
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

        paintShader = LinearGradient(x0, y0, x1, y1, colors, position, tileMode).apply {
            setLocalMatrix(shaderMatrix)
        }

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

        gradientColors = colors
        gradientPositions = stops

        paintShader = RadialGradient(
            centerX,
            centerY,
            radius,
            colors,
            stops,
            tileMode
        ).apply {
            setLocalMatrix(shaderMatrix)
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
        gradientColors = colors
        gradientPositions = positions

        paintShader = SweepGradient(cx, cy, colors, positions).apply {
            setLocalMatrix(shaderMatrix)
        }

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

        shiftTextureWithAlignment(strokeRadiusPx)

        shadowRadius -= 0.00001f
        indicateBoundsChange()
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


    override fun toBitmap(config: Bitmap.Config): Bitmap? {
        return Bitmap.createBitmap(
            rawWidth.toInt(),
            rawHeight.toInt(),
            config
        ).also { bitmap ->
            draw(Canvas(bitmap))
        }
    }

    override fun toBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap? {
        val scale = min(width.toFloat() / rawWidth, height.toFloat() / rawHeight)

        val ws = (rawWidth * scale)
        val hs = (rawHeight * scale)

        val outputBitmap = Bitmap.createBitmap(width, height, config)

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

    override fun getBounds(bounds: RectF) {

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

            var finalExtra = 0f

            if (!isOneLine && !isFirstPass) {
                finalExtra = lineSpacing
            }

            if (underlineSize > 0f && underlineSize > textBoundsRect.bottom) {
                finalYBaseLine[finalYBaseLine.lastIndex] = underlineSize.toInt()
                finalExtra += (underlineSize - textBoundsRect.bottom)
            }

            finalHeights.add(h + finalExtra)

            isFirstPass = false
        }

        extraSpace = textStrokeWidth + if (isTextBackgroundEnabled) backgroundPaddingSize else 0f

        rawWidth = maxWidth + extraSpace

        rawHeight = finalHeights.sum() + extraSpace

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

            if (isTextBackgroundEnabled) {
                drawTextBackground()
            }

            if (shadowRadius > 0) {
                val currentColor = textColor
                val currentStyle = textPaint.style
                val currentShader = textPaint.shader
                textPaint.shader = null
                textPaint.style = Paint.Style.FILL_AND_STROKE
                textPaint.strokeWidth = textStrokeWidth

                textPaint.setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowLColor)

                drawTexts()

                textPaint.shader = currentShader
                textPaint.style = currentStyle
                textPaint.strokeWidth = 0f
                textPaint.color = currentColor

                textPaint.clearShadowLayer()
            }

            if (textStrokeWidth > 0f) {
                saveLayer(-finalTranslateX, -finalTranslateY, rawWidth, rawHeight, textPaint)
            }

            val currentMode = textPaint.xfermode

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
                textPaint.xfermode = null

                drawTexts()

                textPaint.xfermode = dstOutMode
                textPaint.color = Color.BLACK
                textPaint.style = Paint.Style.FILL

                drawTexts()

                textPaint.xfermode = null
                textPaint.shader = currentShader
                textPaint.style = currentStyle
                textPaint.strokeWidth = 0f
                textPaint.pathEffect = currentPath
                textPaint.color = currentColor
            }

            drawTexts()

            if (textStrokeWidth > 0f) {
                textPaint.xfermode = currentMode
                restore()
            }

        }
    }
}
package ir.simurgh.photolib.components.paint.painters.transform.transformables

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
import ir.simurgh.photolib.properties.Bitmapable
import ir.simurgh.photolib.properties.Blendable
import ir.simurgh.photolib.properties.Colorable
import ir.simurgh.photolib.properties.Gradientable
import ir.simurgh.photolib.properties.Opacityable
import ir.simurgh.photolib.properties.Pathable
import ir.simurgh.photolib.properties.Shadowable
import ir.simurgh.photolib.properties.StrokeCapable
import ir.simurgh.photolib.properties.Texturable
import ir.simurgh.photolib.properties.UnifiedBackgroundable
import ir.simurgh.photolib.utils.matrix.SimurghMatrix
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A powerful text painter that provides comprehensive text rendering capabilities with support for
 * multiple styles, backgrounds, gradients, textures, shadows, and transformations.
 *
 * This class handles complex text rendering scenarios including multi-line text with different
 * alignments, customizable backgrounds with rounded corners, stroke effects, and various
 * visual enhancements like shadows and blend modes.
 */
open class TextTransformable : Transformable(), Pathable, Texturable, Gradientable, StrokeCapable,
    Blendable, Bitmapable,
    Colorable, Shadowable, Opacityable, UnifiedBackgroundable {

    /** Flag indicating whether to use unified background rendering for multi-line text. */
    protected var isUnified: Boolean = false

    /** Half of the extra space used for padding calculations. */
    protected var extraSpaceHalf: Float = 0f

    /** Path used for drawing individual line backgrounds. */
    protected val backgroundPath by lazy {
        Path()
    }

    /** Path used for drawing unified backgrounds across all text lines. */
    protected val unifiedBackgroundPath by lazy {
        Path()
    }

    /** Path used for connecting background elements between lines. */
    protected val connectorPath by lazy {
        Path()
    }

    /** List containing text blocks for multi-line text rendering. */
    protected val blocHolder by lazy {
        mutableListOf<TextBloc>()
    }

    // Shadow properties
    /** Radius of the text shadow effect. */
    protected var textShadowRadius = 0f

    /** Horizontal offset of the text shadow. */
    protected var textShadowDx = 0f

    /** Vertical offset of the text shadow. */
    protected var textShadowDy = 0f

    /** Color of the text shadow. */
    protected var textShadowColor = Color.argb(120, 255, 255, 0)

    /** Raw width of the text without transformations. */
    protected var rawWidth = 0f

    /** Raw height of the text without transformations. */
    protected var rawHeight = 0f

    /** Current opacity value (0-255). */
    protected var opacityHolder: Int = 255

    /** Main paint object used for text rendering with anti-aliasing enabled. */
    protected val textPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
        }

    /** Porter-Duff mode for destination out operations. */
    protected val dstOutMode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

    /** Rectangle bounds for the entire text area. */
    protected val textBounds = RectF()

    /**
     * Text content to be rendered.
     * Setting this property triggers bounds recalculation.
     */
    open var text = ""
        set(value) {
            field = value
            notifyBoundsChanged()
        }

    /**
     * Color of the text.
     * Setting this property updates the paint color and triggers invalidation.
     */
    open var textColor = Color.BLACK
        set(value) {
            textPaint.color = value
            field = value
            invalidate()
        }

    /* Allocations -------------------------------------------------------------------------------------------- */

    /** Matrix used for shader transformations. */
    protected val shaderMatrix = SimurghMatrix()

    /** Rectangle used for text bounds measurements. */
    protected val textBoundsRect = Rect()

    /** Y-coordinate of the text baseline. */
    protected var textBaseLineY = 0f

    /** X-coordinate of the text baseline. */
    protected var textBaseLineX = 0f

    /** Extra space used to expand view dimensions to prevent clipping. */
    protected var extraSpace = 0f

    /** Current rotation value for shader transformations. */
    protected var shaderRotationHolder = 0f

    /** Flag indicating whether text background is enabled. */
    protected var isTextBackgroundEnabled = false

    /** Padding size for text background. */
    protected var backgroundPaddingSize = 0f

    /** Corner radius for text background. */
    protected var textBackgroundRadius = 12f

    /** Color of the text background. */
    @ColorInt
    protected var textBackgroundColor: Int = Color.GRAY

    /** Paint object used for drawing text backgrounds. */
    protected val backgroundPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = textBackgroundColor
        }
    }

    /** Array containing radius values for background corners. */
    protected val firstBackgroundRadiusArray = FloatArray(8) {
        textBackgroundRadius
    }

    // Stroke properties
    /** Width of the text stroke outline. */
    protected var textStrokeWidth = 0f

    /** Color of the text stroke outline. */
    protected var textStrokeColor: Int = Color.BLACK

    /**
     * Letter spacing for text rendering.
     * Setting this property updates the paint and triggers bounds recalculation.
     */
    open var letterSpacing = 0f
        set(value) {
            field = value
            textPaint.letterSpacing = field
            notifyBoundsChanged()
        }

    /**
     * Line spacing between text lines.
     * Setting this property triggers bounds recalculation.
     */
    open var lineSpacing = textPaint.fontMetrics.bottom
        set(value) {
            field = value
            notifyBoundsChanged()
        }

    /**
     * Text alignment setting.
     * Controls how text is positioned within the available space.
     */
    open var alignmentText: Alignment = Alignment.CENTER
        set(value) {
            field = value
            notifyBoundsChanged()
            invalidate()
        }

    /**
     * Typeface used for text rendering.
     * Setting this property updates the paint typeface and line spacing.
     */
    open var typeface: Typeface = Typeface.DEFAULT
        set(value) {
            field = value
            typefaceStyle = value.style
            textPaint.typeface = typeface
            lineSpacing = textPaint.fontMetrics.bottom
        }

    /** Current typeface style (normal, bold, italic, etc.). */
    open var typefaceStyle = Typeface.NORMAL

    // Path effect properties
    /** On value for dashed path effects. */
    protected var pathOnValue = 0f

    /** Off value for dashed path effects. */
    protected var pathOffValue = 0f

    /** Stroke width for path effects. */
    protected var textPathStrokeWidth = 0f

    /** Corner radius for path effects. */
    protected var textPathRadius = 0f

    /** Current blend mode for text rendering. */
    protected var textBlendMode = PorterDuff.Mode.SRC

    /** Array of colors used for gradient effects. */
    protected var gradientColors: IntArray? = null

    /** Array of positions for gradient color stops. */
    protected var gradientPositions: FloatArray? = null

    /**
     * Size of underline decoration.
     * Setting this property triggers bounds recalculation.
     */
    open var underlineSize = 0f
        set(value) {
            field = value
            notifyBoundsChanged()
        }

    /**
     * Flag indicating whether strikethrough decoration is enabled.
     * Setting this property updates the paint and triggers bounds recalculation.
     */
    open var isStrikethrough = false
        set(value) {
            field = value
            textPaint.isStrikeThruText = field
            notifyBoundsChanged()
        }

    /** Current texture bitmap applied to the text. */
    protected var currentTexture: Bitmap? = null

    init {
        // Set minimum font size recommended for OpenGL rendering optimization.
        textPaint.textSize = 256f
    }

    /**
     * Creates a deep copy of this TextPainter with all properties and settings preserved.
     *
     * @return A new TextPainter instance with identical configuration.
     */
    override fun clone(): TextTransformable {
        return TextTransformable().also { textPainter ->
            // Copy basic text properties.
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

            // Copy stroke and background settings.
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

            // Copy gradient settings based on current shader type.
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

            // Copy texture if present.
            getTexture()?.let { t ->
                textPainter.applyTexture(t)
            }

            // Copy shader matrix and apply transformations.
            textPainter.shaderMatrix.set(shaderMatrix)

            if (textPainter.textPaint.shader != null) {
                textPainter.textPaint.shader.setLocalMatrix(shaderMatrix)
            }

            // Copy additional paint effects.
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

    /**
     * Renders all text blocks to the canvas with proper alignment and spacing.
     *
     * @param canvas The canvas to draw on.
     */
    protected open fun drawTexts(canvas: Canvas) {
        canvas.apply {
            var acc = 0f

            blocHolder.forEach { textBloc ->
                acc += textBloc.height

                // Calculate horizontal position based on alignment.
                val w =
                    ((rawWidth - textBloc.width) * Alignment.getNumber(alignmentText)) - textBloc.baselineX

                // Calculate vertical position.
                val h = acc - textBloc.baselineY

                // Draw the text block.
                drawText(
                    textBloc.text,
                    w,
                    h,
                    textPaint
                )

                // Draw underline if enabled.
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

    /**
     * Renders text backgrounds with support for multi-line text and different alignment modes.
     * Handles complex background shapes and connections between lines.
     *
     * @param canvas The canvas to draw on.
     */
    protected open fun drawTextBackground(canvas: Canvas) {
        canvas.apply {
            val maxRect = RectF(Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)
            connectorPath.rewind()
            backgroundPath.rewind()
            unifiedBackgroundPath.rewind()

            blocHolder.forEachIndexed { index, textBloc ->
                textBloc.backgroundRect.let { currentBackground ->

                    val finalLineSpacing = if (index == 0) 0f else lineSpacing

                    // Add rounded rectangle for current line background.
                    backgroundPath.addRoundRect(
                        currentBackground.left,
                        currentBackground.top + finalLineSpacing,
                        currentBackground.right,
                        currentBackground.bottom,
                        firstBackgroundRadiusArray, Path.Direction.CCW
                    )

                    // Handle connections between adjacent lines.
                    if (index > 0) {
                        blocHolder[index - 1].backgroundRect.let { lastBackground ->
                            val lineSpacingSecondary = if (index < 2) 0f else lineSpacing

                            val maxRadiusAllowed =
                                abs(currentBackground.width() - lastBackground.width()) / 4f

                            when {
                                // Use unified background for small width differences.
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

                                // Handle equal width lines.
                                currentBackground.width() == lastBackground.width() -> {
                                    backgroundPath.addRoundRect(
                                        lastBackground.left,
                                        lastBackground.top,
                                        currentBackground.right,
                                        currentBackground.bottom,
                                        firstBackgroundRadiusArray, Path.Direction.CCW
                                    )
                                }

                                // Handle current line wider than previous.
                                currentBackground.width() > lastBackground.width() -> {

                                    val finalBottom = currentBackground.top + lineSpacing

                                    val finalTop = if (blocHolder[index - 1].isSandwich) {
                                        blocHolder[index - 2].backgroundRect.bottom
                                    } else {
                                        lastBackground.top
                                    }

                                    // Add connecting background section.
                                    backgroundPath.addRoundRect(
                                        (currentBackground.left + lastBackground.left) * 0.5f,
                                        (lastBackground.top + lineSpacingSecondary + currentBackground.top) * 0.5f,
                                        (currentBackground.right + lastBackground.right) * 0.5f,
                                        finalBottom,
                                        0f, 0f, Path.Direction.CCW
                                    )

                                    // Add side connectors based on alignment.
                                    if (alignmentText != Alignment.LEFT) {
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
                                    if (alignmentText != Alignment.RIGHT) {
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

                                // Handle current line narrower than previous.
                                else -> {

                                    val finalBottom = if (textBloc.isSandwich) {
                                        blocHolder[index + 1].backgroundRect.top
                                    } else {
                                        currentBackground.bottom
                                    }

                                    // Add connecting background section.
                                    backgroundPath.addRoundRect(
                                        (lastBackground.left + currentBackground.left) * 0.5f,
                                        currentBackground.top,
                                        (currentBackground.right + lastBackground.right) * 0.5f,
                                        (lastBackground.bottom + lineSpacing + currentBackground.bottom) * 0.5f,
                                        0f, 0f, Path.Direction.CCW
                                    )

                                    // Add side connectors based on alignment.
                                    if (alignmentText != Alignment.LEFT) {
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

                                    if (alignmentText != Alignment.RIGHT) {
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

            // Combine paths and draw the final background.
            backgroundPath.op(connectorPath, Path.Op.DIFFERENCE)
            backgroundPath.op(unifiedBackgroundPath, Path.Op.UNION)

            drawPath(backgroundPath, backgroundPaint)
        }
    }

    /**
     * Sets the typeface and style for text rendering.
     *
     * @param typeface The typeface to use.
     * @param style The style to apply (normal, bold, italic, etc.).
     */
    open fun setTypeface(typeface: Typeface, style: Int) {
        textPaint.typeface = Typeface.create(typeface, style)
        typefaceStyle = style
        lineSpacing = textPaint.fontMetrics.bottom
    }

    /**
     * Sets the text style using the current typeface.
     *
     * @param style The style to apply.
     */
    open fun setTextStyle(style: Int) {
        setTypeface(typeface, style)
    }

    /**
     * Applies a dashed path effect to the text with custom on/off values and corner radius.
     *
     * @param on Length of the dash segments.
     * @param off Length of the gap segments.
     * @param radius Corner radius for the path effect.
     * @param strokeWidth Width of the stroke for the path effect.
     */
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

            // Small increment to trigger paint update.
            textPaint.textSize += 0.0001f

            invalidate()
        }
    }

    /**
     * Applies a dashed path effect with equal on and off values.
     *
     * @param onAndOff Length of both dash and gap segments.
     * @param radius Corner radius for the path effect.
     * @param strokeWidth Width of the stroke for the path effect.
     */
    override fun applyPath(onAndOff: Float, radius: Float, strokeWidth: Float) {
        applyPath(onAndOff, onAndOff, radius, strokeWidth)
    }

    /**
     * Removes any applied path effects and resets the paint style to fill.
     */
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

    /**
     * Applies a bitmap texture to the text with default tile mode.
     *
     * @param bitmap The texture bitmap to apply.
     */
    override fun applyTexture(bitmap: Bitmap) {
        applyTexture(bitmap, Shader.TileMode.MIRROR)
    }

    /**
     * Applies a bitmap texture to the text with specified tile mode.
     *
     * @param bitmap The texture bitmap to apply.
     * @param tileMode The tiling mode for the texture.
     */
    override fun applyTexture(bitmap: Bitmap, tileMode: Shader.TileMode) {
        removeGradient()
        currentTexture = bitmap

        textPaint.shader = BitmapShader(bitmap, tileMode, tileMode).apply {
            setLocalMatrix(shaderMatrix)
        }
        invalidate()
    }

    /**
     * Gets the currently applied texture bitmap.
     *
     * @return The current texture bitmap, or null if none is applied.
     */
    override fun getTexture(): Bitmap? {
        return currentTexture
    }

    /**
     * Shifts the color/texture position by the specified offset.
     *
     * @param dx Horizontal offset.
     * @param dy Vertical offset.
     */
    override fun shiftColor(dx: Float, dy: Float) {
        textPaint.shader?.run {
            shaderMatrix.postTranslate(dx, dy)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    /**
     * Scales the color/texture by the specified factor.
     *
     * @param scaleFactor The scaling factor to apply.
     */
    override fun scaleColor(scaleFactor: Float) {
        textPaint.shader?.run {
            shaderMatrix.postScale(scaleFactor, scaleFactor, 0f, 0f)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    /**
     * Rotates the color/texture by the specified angle.
     *
     * @param rotation The rotation angle in degrees.
     */
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

    /**
     * Resets all color/texture transformations to default state.
     */
    override fun resetComplexColorMatrix() {
        textPaint.shader?.run {
            shaderMatrix.reset()
            shaderRotationHolder = 0f
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    /**
     * Concatenates the specified matrix with the current color transformation matrix.
     *
     * @param matrix The matrix to concatenate.
     */
    override fun concatColorMatrix(matrix: Matrix) {
        textPaint.shader?.run {
            shaderMatrix.postConcat(matrix)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    /**
     * Removes the currently applied texture.
     */
    override fun removeTexture() {
        currentTexture = null
        textPaint.shader = null
        invalidate()
    }

    /**
     * Removes any applied gradient effects.
     */
    override fun removeGradient() {
        textPaint.shader = null
        gradientColors = null
        gradientPositions = null
        invalidate()
    }

    /**
     * Applies a linear gradient to the text.
     *
     * @param x0 Starting x-coordinate of the gradient.
     * @param y0 Starting y-coordinate of the gradient.
     * @param x1 Ending x-coordinate of the gradient.
     * @param y1 Ending y-coordinate of the gradient.
     * @param colors Array of colors for the gradient.
     * @param position Array of color stop positions (can be null for even distribution).
     * @param tileMode Tile mode for the gradient.
     */
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

    /**
     * Applies a radial gradient to the text.
     *
     * @param centerX Center x-coordinate of the gradient.
     * @param centerY Center y-coordinate of the gradient.
     * @param radius Radius of the gradient.
     * @param colors Array of colors for the gradient.
     * @param stops Array of color stop positions (can be null for even distribution).
     * @param tileMode Tile mode for the gradient.
     */
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

    /**
     * Applies a sweep gradient to the text.
     *
     * @param cx Center x-coordinate of the gradient.
     * @param cy Center y-coordinate of the gradient.
     * @param colors Array of colors for the gradient.
     * @param positions Array of color stop positions (can be null for even distribution).
     */
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

    /**
     * Sets stroke properties for the text outline.
     *
     * @param strokeRadiusPx Width of the stroke in pixels.
     * @param strokeColor Color of the stroke.
     * @throws IllegalStateException if stroke width is negative.
     */
    override fun setStroke(strokeRadiusPx: Float, strokeColor: Int) {
        if (strokeRadiusPx < 0f) throw IllegalStateException("Stroke width should be a positive number")
        textStrokeWidth = strokeRadiusPx
        this.textStrokeColor = strokeColor
        notifyBoundsChanged()
    }

    /**
     * Gets the current stroke color.
     *
     * @return The stroke color as an integer.
     */
    override fun getStrokeColor(): Int {
        return textStrokeColor
    }

    /**
     * Gets the current stroke width.
     *
     * @return The stroke width in pixels.
     */
    override fun getStrokeWidth(): Float {
        return textStrokeWidth
    }

    /**
     * Shifts texture position based on alignment when stroke changes occur.
     *
     * @param currentStroke The new stroke width.
     */
    private fun shiftTextureWithAlignment(currentStroke: Float) {
        val diffCurrentStrokeWithLast = (currentStroke - extraSpace)

        var finalShiftValueX = 0f

        when (alignmentText) {
            Alignment.LEFT -> {
                // No horizontal shift needed for left alignment.
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

    /**
     * Gets the horizontal offset of the shadow.
     *
     * @return The shadow's horizontal offset.
     */
    override fun getShadowDx(): Float {
        return textShadowDx
    }

    /**
     * Gets the vertical offset of the shadow.
     *
     * @return The shadow's vertical offset.
     */
    override fun getShadowDy(): Float {
        return textShadowDy
    }

    /**
     * Gets the blur radius of the shadow.
     *
     * @return The shadow's blur radius.
     */
    override fun getShadowRadius(): Float {
        return textShadowRadius
    }

    /**
     * Gets the color of the shadow.
     *
     * @return The shadow color as an integer.
     */
    override fun getShadowColor(): Int {
        return textShadowColor
    }

    /**
     * Sets shadow properties for the text.
     *
     * @param radius Blur radius of the shadow.
     * @param dx Horizontal offset of the shadow.
     * @param dy Vertical offset of the shadow.
     * @param shadowColor Color of the shadow.
     */
    override fun setShadow(radius: Float, dx: Float, dy: Float, shadowColor: Int) {
        textShadowRadius = radius
        textShadowDx = dx
        textShadowDy = dy
        this.textShadowColor = shadowColor
        invalidate()
    }

    /**
     * Removes the shadow effect from the text.
     */
    override fun clearShadow() {
        textPaint.clearShadowLayer()
        textShadowRadius = 0f
        textShadowDx = 0f
        textShadowDy = 0f
        textShadowColor = Color.YELLOW
        invalidate()
    }


    /**
     * Converts the text to a bitmap with the specified configuration.
     *
     * @param config The bitmap configuration to use.
     * @return A bitmap representation of the text, or null if creation fails.
     */
    override fun toBitmap(config: Bitmap.Config): Bitmap? {
        return createBitmap(rawWidth.toInt(), rawHeight.toInt(), config).also { bitmap ->
            draw(Canvas(bitmap))
        }
    }

    /**
     * Converts the text to a bitmap with specified dimensions and configuration.
     * The text is scaled to fit within the specified dimensions while maintaining aspect ratio.
     *
     * @param width The desired width of the output bitmap.
     * @param height The desired height of the output bitmap.
     * @param config The bitmap configuration to use.
     * @return A bitmap representation of the text, or null if creation fails.
     */
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

    /**
     * Checks if a gradient effect is currently applied to the text.
     *
     * @return True if a gradient is applied, false otherwise.
     */
    override fun isGradientApplied(): Boolean {
        return (textPaint.shader != null && (textPaint.shader is LinearGradient || textPaint.shader is RadialGradient || textPaint.shader is SweepGradient))
    }

    /**
     * Changes the text color.
     *
     * @param color The new color to apply.
     */
    override fun changeColor(color: Int) {
        textColor = color
    }

    /**
     * Gets the current text color.
     *
     * @return The current text color as an integer.
     */
    override fun getColor(): Int {
        return textColor
    }

    /**
     * Gets the "on" value for path effects.
     *
     * @return The dash segment length.
     */
    override fun getOnValue(): Float {
        return pathOnValue
    }

    /**
     * Gets the "off" value for path effects.
     *
     * @return The gap segment length.
     */
    override fun getOffValue(): Float {
        return pathOffValue
    }

    /**
     * Gets the corner radius for path effects.
     *
     * @return The path corner radius.
     */
    override fun getPathRadius(): Float {
        return textPathRadius
    }

    /**
     * Gets the stroke width for path effects.
     *
     * @return The path stroke width.
     */
    override fun getPathStrokeWidth(): Float {
        return textPathStrokeWidth
    }

    /**
     * Sets the blend mode for text rendering.
     *
     * @param blendMode The Porter-Duff blend mode to apply.
     */
    override fun setBlendMode(blendMode: PorterDuff.Mode) {
        textPaint.xfermode = PorterDuffXfermode(blendMode)
        backgroundPaint.xfermode = textPaint.xfermode
        this.textBlendMode = blendMode
        invalidate()
    }

    /**
     * Clears the current blend mode and resets to default.
     */
    override fun clearBlend() {
        textPaint.xfermode = null
        backgroundPaint.xfermode = null
        textBlendMode = PorterDuff.Mode.SRC
        invalidate()
    }

    /**
     * Gets the current blend mode.
     *
     * @return The current Porter-Duff blend mode.
     */
    override fun getBlendMode(): PorterDuff.Mode {
        return textBlendMode
    }

    /**
     * Reports the gradient color stop positions.
     *
     * @return Array of gradient positions, or null if no gradient is applied.
     */
    override fun reportPositions(): FloatArray? {
        return gradientPositions
    }

    /**
     * Reports the gradient colors.
     *
     * @return Array of gradient colors, or null if no gradient is applied.
     */
    override fun reportColors(): IntArray? {
        return gradientColors
    }

    /**
     * Determines alignment of drawn text in [MananTextView].
     */
    /**
     * Enumeration defining text alignment options.
     */
    enum class Alignment {
        /** Aligns text to the left edge. */
        LEFT,

        /** Aligns text to the right edge. */
        RIGHT,

        /** Centers text horizontally. */
        CENTER;

        companion object {
            /**
             * Converts alignment enum to a numeric factor for calculations.
             *
             * @param alignment The alignment to convert.
             * @return Numeric factor: 0.0 for LEFT, 0.5 for CENTER, 1.0 for RIGHT.
             */
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

    /**
     * Sets the background properties for the text element.
     * Updates padding, radius, and color while optimizing for performance by only changing bounds when necessary.
     *
     * @param padding The padding size around the text background.
     * @param radius The corner radius for the background shape.
     * @param color The background color as a ColorInt.
     */
    override fun setBackground(padding: Float, radius: Float, @ColorInt color: Int) {
        // Check if bounds need to change to avoid unnecessary operations.
        val shouldChangeBounds = (backgroundPaddingSize != padding || textBackgroundRadius != radius)
        backgroundPaddingSize = padding
        textBackgroundColor = color
        textBackgroundRadius = radius
        // Apply radius to all corners of the background shape.
        firstBackgroundRadiusArray.fill(textBackgroundRadius)
        if (shouldChangeBounds) {
            // Notify that bounds have changed for layout recalculation.
            notifyBoundsChanged()
        } else {
            // Only invalidate for visual updates without layout changes.
            invalidate()
        }
    }

    /**
     * Gets the current background padding value.
     *
     * @return The padding size around the text background.
     */
    override fun getBackgroundPadding(): Float {
        return backgroundPaddingSize
    }

    /**
     * Gets the current background corner radius.
     *
     * @return The corner radius of the background shape.
     */
    override fun getBackgroundRadius(): Float {
        return textBackgroundRadius
    }

    /**
     * Gets the current background color.
     *
     * @return The background color as a ColorInt value.
     */
    override fun getBackgroundColor(): Int {
        return textBackgroundColor
    }

    /**
     * Gets the current background enabled state.
     *
     * @return True if the text background is enabled, false otherwise.
     */
    override fun getBackgroundState(): Boolean {
        return isTextBackgroundEnabled
    }

    /**
     * Sets whether the text background is enabled or disabled.
     * Notifies bounds changed to trigger layout recalculation.
     *
     * @param isEnabled True to enable the background, false to disable it.
     */
    override fun setBackgroundState(isEnabled: Boolean) {
        isTextBackgroundEnabled = isEnabled
        // Bounds change because background affects the overall element size.
        notifyBoundsChanged()
    }

    /**
     * Sets the unified state for the background rendering.
     * Unified state affects how multiple background elements are rendered together.
     *
     * @param isUnified True for unified rendering, false for separate rendering.
     */
    override fun setBackgroundUnifiedState(isUnified: Boolean) {
        this.isUnified = isUnified
        // Only visual update needed, no layout changes.
        invalidate()
    }

    /**
     * Checks if the background is in unified rendering mode.
     *
     * @return True if background uses unified rendering, false otherwise.
     */
    override fun isBackgroundUnified(): Boolean {
        return isUnified
    }

    /**
     * Calculates color with opacity factor applied.
     * Adjusts the alpha channel of a color based on the provided opacity factor.
     *
     * @param factor The opacity factor (0.0 to 1.0).
     * @return The color with adjusted alpha channel.
     */
    protected fun @receiver: ColorInt Int.calculateColorAlphaWithOpacityFactor(
        factor: Float
    ): Int =
        if (factor == 1f) this else Color.argb(
            (this.alpha * factor).roundToInt(),
            this.red,
            this.green,
            this.blue,
        )

    /**
     * Data class representing a single block of text with positioning and background information.
     *
     * @param text The text content of this block.
     * @param width The width of the text block.
     * @param height The height of the text block including spacing.
     * @param baselineX The x-offset of the text baseline.
     * @param baselineY The y-offset of the text baseline.
     * @param isSandwich Whether this block is sandwiched between wider blocks (affects background rendering).
     * @param backgroundRect The rectangle defining the background area for this text block.
     */
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

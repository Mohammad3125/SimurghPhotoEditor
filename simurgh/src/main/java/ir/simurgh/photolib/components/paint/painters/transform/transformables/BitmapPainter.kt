package ir.simurgh.photolib.components.paint.painters.transform.transformables

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import ir.simurgh.photolib.properties.Backgroundable
import ir.simurgh.photolib.properties.Blendable
import ir.simurgh.photolib.properties.CornerRounder
import ir.simurgh.photolib.properties.Opacityable
import ir.simurgh.photolib.properties.Shadowable
import ir.simurgh.photolib.properties.StrokeCapable
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A comprehensive bitmap painter that provides advanced image rendering capabilities with support for
 * various visual effects including shadows, strokes, backgrounds, corner rounding, and image adjustments.
 *
 * This class handles complex bitmap rendering scenarios with customizable backgrounds, blend modes,
 * opacity controls, and a full suite of image adjustment features like brightness, contrast,
 * saturation, hue, tint, and temperature controls.
 *
 * @param bitmap The source bitmap to be painted.
 * @param complexPath Optional custom path for non-rectangular bitmap shapes.
 */
open class BitmapPainter(bitmap: Bitmap, complexPath: Path? = null) : Transformable(), Blendable,
    Opacityable, Backgroundable,
    CornerRounder, Shadowable, StrokeCapable {

    /** Width of the stroke outline around the bitmap. */
    protected var bitmapStrokeWidth: Float = 0f

    /** Color of the stroke outline around the bitmap. */
    @ColorInt
    protected var bitmapStrokeColor: Int = Color.BLACK

    /** Corner radius for rounded corners on the bitmap. */
    protected var cornerRadius: Float = 0f

    /** Paint object used for drawing stroke outlines. */
    protected val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bitmapStrokeColor
        strokeWidth = this@BitmapPainter.bitmapStrokeWidth
        style = Paint.Style.FILL_AND_STROKE
    }

    /** Paint object configured for destination-out blending operations. */
    protected val dstOutPaint by lazy {
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            style = Paint.Style.FILL_AND_STROKE
        }
    }

    /** Primary color matrix for image adjustments. */
    protected val colorMatrix = ColorMatrix()

    /** Temporary color matrix used for intermediate calculations. */
    protected val colorMatrixTemp = ColorMatrix()

    /** Color filter based on the current color matrix settings. */
    protected open var colorMatrixFilter = ColorMatrixColorFilter(colorMatrix)
        set(value) {
            field = value
            bitmapPaint.colorFilter = value
        }

    /** Main paint object used for bitmap rendering with filtering enabled. */
    protected val bitmapPaint = Paint().apply {
        isFilterBitmap = true
        shader = BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        colorFilter = colorMatrixFilter
    }

    /** Container for image adjustment values including brightness, contrast, etc. */
    open val imageAdjustments = ImageAdjustment(0f, 1f, 1f, 0f, 0f, 0f)

    /**
     * The source bitmap being painted.
     * Setting this property updates the shader and triggers bounds recalculation.
     */
    open var bitmap: Bitmap = bitmap
        set(value) {
            field = value
            bitmapPaint.shader = BitmapShader(value, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            notifyBoundsChanged()
        }

    // Shadow properties
    /** Blur radius of the drop shadow effect. */
    protected var bitmapShadowRadius = 0f

    /** Horizontal offset of the drop shadow. */
    protected var bitmapShadowDx = 0f

    /** Vertical offset of the drop shadow. */
    protected var bitmapShadowDy = 0f

    /** Color of the drop shadow. */
    protected var bitmapShadowColor = Color.YELLOW

    /** Current blend mode applied to the bitmap. */
    protected var bitmapBlendMode: PorterDuff.Mode = PorterDuff.Mode.SRC

    /** Flag indicating whether background rendering is enabled. */
    protected var isTextBackgroundEnabled = false

    /** Padding size around the bitmap for background rendering. */
    protected var backgroundPaddingSize = 0f

    /** Corner radius for the background rectangle. */
    protected var bitmapBackgroundRadius = 12f

    /** Color of the background behind the bitmap. */
    @ColorInt
    protected var bitmapBackgroundColor: Int = Color.GRAY

    /** Paint object used for drawing backgrounds. */
    protected val backgroundPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = bitmapBackgroundColor
        }
    }

    /** Half of the padding value, cached for performance. */
    protected var halfPadding = 0f

    /** Final calculated width including padding and stroke. */
    protected var finalWidth = 0f

    /** Final calculated height including padding and stroke. */
    protected var finalHeight = 0f

    /** Rectangle bounds for the entire painter area. */
    protected val finalBounds by lazy {
        RectF()
    }

    /**
     * Optional custom path for non-rectangular bitmap shapes.
     * Setting this property triggers invalidation for re-rendering.
     */
    open var customBackgroundPath: Path? = complexPath
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Calculates and sets the bounds of the bitmap painter including any padding or stroke.
     *
     * @param bounds The RectF to store the calculated bounds.
     */
    override fun getBounds(bounds: RectF) {
        val extraSpace =
            max(bitmapStrokeWidth, if (isTextBackgroundEnabled) backgroundPaddingSize else 0f)

        halfPadding = extraSpace * 0.5f
        finalWidth = bitmap.width + extraSpace
        finalHeight = bitmap.height + extraSpace

        bounds.set(
            0f,
            0f,
            finalWidth,
            finalHeight
        )

        finalBounds.set(bounds)
    }

    /**
     * Renders the bitmap with all applied effects to the specified canvas.
     * Handles background rendering, shadows, strokes, and the main bitmap drawing in correct order.
     *
     * @param canvas The canvas to draw on.
     */
    override fun draw(canvas: Canvas) {
        canvas.apply {
            val opacityFactor = getOpacity() / 255f

            // Draw background if enabled.
            if (isTextBackgroundEnabled) {
                withSave {
                    backgroundPaint.color =
                        bitmapBackgroundColor.calculateColorAlphaWithOpacityFactor(opacityFactor)

                    saveLayer(finalBounds, null)

                    // Draw background rectangle.
                    drawRoundRect(
                        0f,
                        0f,
                        finalWidth,
                        finalHeight,
                        bitmapBackgroundRadius,
                        bitmapBackgroundRadius,
                        backgroundPaint
                    )

                    backgroundPaint.color = bitmapBackgroundColor

                    translate(halfPadding, halfPadding)

                    // Cut out the bitmap shape from the background.
                    if (customBackgroundPath != null) {
                        drawPath(customBackgroundPath!!, dstOutPaint)
                    } else {
                        drawRoundRect(
                            0f,
                            0f,
                            bitmap.width.toFloat(),
                            bitmap.height.toFloat(),
                            cornerRadius,
                            cornerRadius,
                            dstOutPaint
                        )
                    }

                    restore()
                }
            }

            // Draw shadow if enabled.
            if (bitmapShadowRadius > 0) {
                val currentPorterDuffMode = dstOutPaint.xfermode

                dstOutPaint.xfermode = null

                val transformedColor =
                    bitmapShadowColor.calculateColorAlphaWithOpacityFactor(opacityFactor)

                dstOutPaint.color = transformedColor
                dstOutPaint.strokeWidth = bitmapStrokeWidth

                dstOutPaint.setShadowLayer(
                    bitmapShadowRadius,
                    bitmapShadowDx,
                    bitmapShadowDy,
                    transformedColor
                )

                val maxRoundness = max(bitmapBackgroundRadius, cornerRadius)

                withTranslation(halfPadding, halfPadding) {
                    if (customBackgroundPath != null) {
                        drawPath(customBackgroundPath!!, dstOutPaint)
                    } else {
                        drawRoundRect(
                            0f,
                            0f,
                            bitmap.width.toFloat(),
                            bitmap.height.toFloat(),
                            maxRoundness,
                            maxRoundness,
                            dstOutPaint
                        )
                    }
                }

                // Restore paint properties after shadow drawing.
                dstOutPaint.xfermode = currentPorterDuffMode
                dstOutPaint.clearShadowLayer()
                dstOutPaint.strokeWidth = 0f
                dstOutPaint.color = Color.BLACK
            }

            // Draw stroke and main bitmap.
            withTranslation(halfPadding, halfPadding) {
                // Draw stroke outline if enabled.
                if (bitmapStrokeWidth > 0f) {

                    strokePaint.color =
                        bitmapStrokeColor.calculateColorAlphaWithOpacityFactor(opacityFactor)

                    if (customBackgroundPath != null) {
                        drawPath(customBackgroundPath!!, strokePaint)
                    } else {
                        drawRoundRect(
                            0f,
                            0f,
                            bitmap.width.toFloat(),
                            bitmap.height.toFloat(),
                            cornerRadius,
                            cornerRadius,
                            strokePaint
                        )
                    }
                }

                // Draw the main bitmap.
                drawRoundRect(
                    0f,
                    0f,
                    bitmap.width.toFloat(),
                    bitmap.height.toFloat(),
                    cornerRadius,
                    cornerRadius,
                    bitmapPaint
                )
            }
        }
    }

    /**
     * Gets the horizontal offset of the shadow.
     *
     * @return The shadow's horizontal offset in pixels.
     */
    override fun getShadowDx(): Float {
        return bitmapShadowDx
    }

    /**
     * Gets the vertical offset of the shadow.
     *
     * @return The shadow's vertical offset in pixels.
     */
    override fun getShadowDy(): Float {
        return bitmapShadowDy
    }

    /**
     * Gets the blur radius of the shadow.
     *
     * @return The shadow's blur radius in pixels.
     */
    override fun getShadowRadius(): Float {
        return bitmapShadowRadius
    }

    /**
     * Gets the color of the shadow.
     *
     * @return The shadow color as an integer.
     */
    override fun getShadowColor(): Int {
        return bitmapShadowColor
    }

    /**
     * Sets shadow properties for the bitmap.
     *
     * @param radius Blur radius of the shadow in pixels.
     * @param dx Horizontal offset of the shadow in pixels.
     * @param dy Vertical offset of the shadow in pixels.
     * @param shadowColor Color of the shadow.
     */
    override fun setShadow(radius: Float, dx: Float, dy: Float, shadowColor: Int) {
        bitmapShadowRadius = radius
        bitmapShadowDx = dx
        bitmapShadowDy = dy
        this.bitmapShadowColor = shadowColor
        invalidate()
    }

    /**
     * Removes the shadow effect from the bitmap.
     */
    override fun clearShadow() {
        dstOutPaint.clearShadowLayer()
        bitmapShadowRadius = 0f
        bitmapShadowDx = 0f
        bitmapShadowDy = 0f
        bitmapShadowColor = Color.YELLOW
        invalidate()
    }

    /**
     * Sets the blend mode for bitmap rendering.
     *
     * @param blendMode The Porter-Duff blend mode to apply.
     */
    override fun setBlendMode(blendMode: PorterDuff.Mode) {
        bitmapPaint.xfermode = PorterDuffXfermode(blendMode)
        this.bitmapBlendMode = blendMode
        invalidate()
    }

    /**
     * Clears the current blend mode and resets to default source mode.
     */
    override fun clearBlend() {
        bitmapPaint.xfermode = null
        bitmapBlendMode = PorterDuff.Mode.SRC
        invalidate()
    }

    /**
     * Creates a copy of this BitmapPainter instance.
     *
     * @return A new BitmapPainter instance with identical configuration.
     */
    override fun clone(): Transformable {
        return clone(true)
    }

    /**
     * Creates a copy of this BitmapPainter with an option to clone the underlying bitmap.
     *
     * @param cloneBitmap Whether to create a copy of the bitmap or reuse the existing one.
     * @return A new BitmapPainter instance with identical configuration.
     */
    open fun clone(cloneBitmap: Boolean = false): BitmapPainter {
        val clonedBitmap =
            if (cloneBitmap) bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true) else bitmap

        return BitmapPainter(clonedBitmap).also {
            // Copy blend mode if not default.
            if (bitmapBlendMode != PorterDuff.Mode.SRC) {
                it.setBlendMode(bitmapBlendMode)
            }

            // Copy all visual properties.
            it.setOpacity(getOpacity())
            it.setBackground(
                getBackgroundPadding(),
                getBackgroundRadius(),
                getBackgroundColor()
            )
            it.setBackgroundState(getBackgroundState())
            it.setCornerRoundness(getCornerRoundness())
            it.setShadow(getShadowRadius(), getShadowDx(), getShadowDy(), getShadowColor())

            // Copy custom path if present.
            customBackgroundPath?.let { path ->
                it.customBackgroundPath = Path(path)
            }

            // Copy stroke and image adjustment settings.
            it.setStroke(bitmapStrokeWidth, bitmapStrokeColor)
            it.imageAdjustments.set(imageAdjustments)
            it.adjustImageValues()
        }
    }

    /**
     * Gets the current blend mode.
     *
     * @return The current Porter-Duff blend mode.
     */
    override fun getBlendMode(): PorterDuff.Mode {
        return bitmapBlendMode
    }

    /**
     * Gets the current opacity value.
     *
     * @return The opacity value (0-255).
     */
    override fun getOpacity(): Int {
        return bitmapPaint.alpha
    }

    /**
     * Sets the opacity for the bitmap.
     *
     * @param opacity The opacity value (0-255).
     */
    override fun setOpacity(opacity: Int) {
        bitmapPaint.alpha = opacity
        invalidate()
    }

    /**
     * Sets background properties for the bitmap.
     *
     * @param padding Padding around the bitmap background.
     * @param radius Corner radius for the background.
     * @param color Color of the background.
     */
    override fun setBackground(padding: Float, radius: Float, @ColorInt color: Int) {
        val shouldChangeBounds =
            (backgroundPaddingSize != padding || bitmapBackgroundRadius != radius || backgroundPaddingSize < bitmapStrokeWidth)
        backgroundPaddingSize = padding
        bitmapBackgroundColor = color
        bitmapBackgroundRadius = radius

        if (shouldChangeBounds) {
            notifyBoundsChanged()
        } else {
            invalidate()
        }
    }

    /**
     * Gets the current background padding.
     *
     * @return The background padding value.
     */
    override fun getBackgroundPadding(): Float {
        return backgroundPaddingSize
    }

    /**
     * Gets the current background corner radius.
     *
     * @return The background corner radius.
     */
    override fun getBackgroundRadius(): Float {
        return bitmapBackgroundRadius
    }

    /**
     * Gets the current background color.
     *
     * @return The background color as an integer.
     */
    override fun getBackgroundColor(): Int {
        return bitmapBackgroundColor
    }

    /**
     * Gets the current background enabled state.
     *
     * @return True if background is enabled, false otherwise.
     */
    override fun getBackgroundState(): Boolean {
        return isTextBackgroundEnabled
    }

    /**
     * Enables or disables the bitmap background.
     *
     * @param isEnabled True to enable background, false to disable.
     */
    override fun setBackgroundState(isEnabled: Boolean) {
        isTextBackgroundEnabled = isEnabled
        notifyBoundsChanged()
    }

    /**
     * Gets the current corner roundness value.
     *
     * @return The corner radius for the bitmap.
     */
    override fun getCornerRoundness(): Float {
        return cornerRadius
    }

    /**
     * Sets the corner roundness for the bitmap.
     *
     * @param roundness The corner radius to apply.
     */
    override fun setCornerRoundness(roundness: Float) {
        cornerRadius = roundness
        invalidate()
    }

    /**
     * Sets stroke properties for the bitmap outline.
     *
     * @param strokeRadiusPx Width of the stroke in pixels.
     * @param strokeColor Color of the stroke.
     */
    override fun setStroke(strokeRadiusPx: Float, strokeColor: Int) {
        bitmapStrokeWidth = strokeRadiusPx
        this.bitmapStrokeColor = strokeColor
        strokePaint.color = strokeColor
        strokePaint.strokeWidth = bitmapStrokeWidth
        notifyBoundsChanged()
    }

    /**
     * Gets the current stroke color.
     *
     * @return The stroke color as an integer.
     */
    override fun getStrokeColor(): Int {
        return bitmapStrokeColor
    }

    /**
     * Gets the current stroke width.
     *
     * @return The stroke width in pixels.
     */
    override fun getStrokeWidth(): Float {
        return bitmapStrokeWidth
    }

    /**
     * Sets the hue adjustment for the bitmap.
     *
     * @param degree The hue rotation in degrees (0.0 to 360.0).
     */
    open fun setHue(@FloatRange(0.0, 360.0) degree: Float) {
        imageAdjustments.hue = degree
        adjustImageValues()
    }

    /**
     * Sets the contrast adjustment for the bitmap.
     *
     * @param contrast The contrast multiplier (0.0 to 2.0, where 1.0 is normal).
     */
    open fun setContrast(@FloatRange(0.0, 2.0) contrast: Float) {
        imageAdjustments.contrast = contrast
        adjustImageValues()
    }

    /**
     * Sets the saturation adjustment for the bitmap.
     *
     * @param saturation The saturation multiplier (0.0 to 2.0, where 1.0 is normal).
     */
    open fun setSaturation(@FloatRange(0.0, 2.0) saturation: Float) {
        imageAdjustments.saturation = saturation
        adjustImageValues()
    }

    /**
     * Sets the brightness adjustment for the bitmap.
     *
     * @param brightness The brightness offset (-1.0 to 1.0, where 0.0 is normal).
     */
    open fun setBrightness(@FloatRange(-1.0, 1.0) brightness: Float) {
        imageAdjustments.brightness = brightness
        adjustImageValues()
    }

    /**
     * Sets the tint adjustment for the bitmap.
     *
     * @param tint The tint adjustment (-1.0 to 1.0, where 0.0 is normal).
     */
    open fun setTint(@FloatRange(-1.0, 1.0) tint: Float) {
        imageAdjustments.tint = tint
        adjustImageValues()
    }

    /**
     * Sets the temperature adjustment for the bitmap.
     *
     * @param warmth The temperature adjustment (-1.0 to 1.0, where 0.0 is normal).
     */
    open fun setTemperature(@FloatRange(-1.0, 1.0) warmth: Float) {
        imageAdjustments.temperature = warmth
        adjustImageValues()
    }

    /**
     * Resets all image adjustments to their default values.
     */
    open fun resetAdjustments() {
        colorMatrixFilter = ColorMatrixColorFilter(colorMatrix.apply {
            reset()
        })
        invalidate()
    }

    /**
     * Applies all current image adjustment values to the color matrix.
     * This method combines hue, saturation, contrast, brightness, tint, and temperature
     * adjustments into a single color matrix for efficient rendering.
     */
    protected open fun adjustImageValues() {
        colorMatrix.reset()

        imageAdjustments.apply {
            // Apply hue rotation if not zero.
            if (hue != 0f) {
                colorMatrix.postConcat(colorMatrixTemp.apply {
                    reset()
                    setRotate(0, imageAdjustments.hue)
                    setRotate(1, imageAdjustments.hue)
                    setRotate(2, imageAdjustments.hue)
                })
            }

            // Apply saturation adjustment if not 1.0.
            if (saturation != 1f) {
                colorMatrix.postConcat(colorMatrixTemp.apply {
                    reset()
                    setSaturation(saturation)
                })
            }

            // Apply contrast adjustment if not 1.0.
            if (contrast != 1f) {
                val translate = (-0.5f * contrast + 0.5f) * 255f

                colorMatrix.postConcat(colorMatrixTemp.apply {
                    set(
                        floatArrayOf(
                            contrast, 0f, 0f, 0f, translate,
                            0f, contrast, 0f, 0f, translate,
                            0f, 0f, contrast, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                })
            }

            // Apply brightness adjustment if not zero.
            if (brightness != 0f) {
                val translate = brightness * 255f

                colorMatrix.postConcat(colorMatrixTemp.apply {
                    set(
                        floatArrayOf(
                            1f, 0f, 0f, 0f, translate,
                            0f, 1f, 0f, 0f, translate,
                            0f, 0f, 1f, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                })
            }

            // Apply tint and temperature adjustments if not zero.
            if (tint != 0f || temperature != 0f) {
                colorMatrix.postConcat(colorMatrixTemp.apply {
                    set(
                        when {
                            tint > 0f -> {
                                floatArrayOf(
                                    1f + temperature * 0.5f, 0f, 0f, 0f, 0f,
                                    0f, 1f - tint, 0f, 0f, 0f,
                                    0f, 0f, 1f - temperature * 0.5f, 0f, 0f,
                                    0f, 0f, 0f, 1f, 0f
                                )
                            }

                            else -> {
                                floatArrayOf(
                                    (1f + temperature * 0.5f) + tint, 0f, 0f, 0f, 0f,
                                    0f, 1f, 0f, 0f, 0f,
                                    0f, 0f, (1f - temperature * 0.5f) + tint, 0f, 0f,
                                    0f, 0f, 0f, 1f, 0f
                                )
                            }
                        }
                    )
                })
            }
        }

        colorMatrixFilter = ColorMatrixColorFilter(colorMatrix)

        invalidate()
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
        Color.argb(
            (this.alpha * factor).roundToInt(),
            this.red,
            this.green,
            this.blue,
        )

    /**
     * Data class containing image adjustment parameters for color manipulation.
     *
     * @param brightness Brightness adjustment (-1.0 to 1.0, where 0.0 is normal).
     * @param contrast Contrast multiplier (0.0 to 2.0, where 1.0 is normal).
     * @param saturation Saturation multiplier (0.0 to 2.0, where 1.0 is normal).
     * @param hue Hue rotation in degrees (0.0 to 360.0).
     * @param tint Tint adjustment (-1.0 to 1.0, where 0.0 is normal).
     * @param temperature Temperature adjustment (-1.0 to 1.0, where 0.0 is normal).
     */
    data class ImageAdjustment(
        var brightness: Float,
        var contrast: Float,
        var saturation: Float,
        var hue: Float,
        var tint: Float,
        var temperature: Float
    ) {
        /**
         * Creates a copy of this ImageAdjustment instance.
         *
         * @return A new ImageAdjustment with identical values.
         */
        fun clone(): ImageAdjustment =
            ImageAdjustment(brightness, contrast, saturation, hue, tint, temperature)

        /**
         * Sets all adjustment values from another ImageAdjustment instance.
         *
         * @param adjustment The source ImageAdjustment to copy values from.
         */
        fun set(adjustment: ImageAdjustment) {
            brightness = adjustment.brightness
            contrast = adjustment.contrast
            saturation = adjustment.saturation
            hue = adjustment.hue
            tint = adjustment.tint
            temperature = adjustment.temperature
        }
    }

}

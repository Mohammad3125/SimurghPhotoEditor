package ir.baboomeh.photolib.components.paint.painters.transform

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
import ir.baboomeh.photolib.properties.Backgroundable
import ir.baboomeh.photolib.properties.Blendable
import ir.baboomeh.photolib.properties.CornerRounder
import ir.baboomeh.photolib.properties.Opacityable
import ir.baboomeh.photolib.properties.Shadowable
import ir.baboomeh.photolib.properties.StrokeCapable
import kotlin.math.max
import kotlin.math.roundToInt

open class BitmapPainter(bitmap: Bitmap, complexPath: Path? = null) : Transformable(), Blendable,
    Opacityable, Backgroundable,
    CornerRounder, Shadowable, StrokeCapable {

    protected var strokeWidth: Float = 0f

    @ColorInt
    protected  var strokeColor: Int = Color.BLACK
    protected  var cornerRadius: Float = 0f

    protected val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        strokeWidth = this@BitmapPainter.strokeWidth
        style = Paint.Style.FILL_AND_STROKE
    }

    protected val dstOutPaint by lazy {
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            style = Paint.Style.FILL_AND_STROKE
        }
    }

    protected val colorMatrix = ColorMatrix()
    protected val colorMatrixTemp = ColorMatrix()
    protected open var colorMatrixFilter = ColorMatrixColorFilter(colorMatrix)
        set(value) {
            field = value
            bitmapPaint.colorFilter = value
        }

    protected val bitmapPaint = Paint().apply {
        isFilterBitmap = true
        shader = BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
        colorFilter = colorMatrixFilter
    }

    open val imageAdjustments = ImageAdjustment(0f, 1f, 1f, 0f, 0f, 0f)

    open var bitmap: Bitmap = bitmap
        set(value) {
            field = value
            bitmapPaint.shader = BitmapShader(value, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            notifyBoundsChanged()
        }

    protected var shadowRadius = 0f
    protected  var shadowDx = 0f
    protected  var shadowDy = 0f
    protected  var shadowColor = Color.YELLOW

    protected  var blendMode: PorterDuff.Mode = PorterDuff.Mode.SRC

    protected  var isTextBackgroundEnabled = false

    protected  var backgroundPaddingSize = 0f

    protected  var backgroundRadius = 12f

    @ColorInt
    protected  var backgroundColor: Int = Color.GRAY

    protected  val backgroundPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = backgroundColor
        }
    }

    protected  var halfPadding = 0f

    protected  var finalWidth = 0f

    protected  var finalHeight = 0f

    protected  val finalBounds by lazy {
        RectF()
    }

    open var customBackgroundPath: Path? = complexPath
        set(value) {
            field = value
            invalidate()
        }

    override fun getBounds(bounds: RectF) {
        val extraSpace =
            max(strokeWidth, if (isTextBackgroundEnabled) backgroundPaddingSize else 0f)

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

    override fun draw(canvas: Canvas) {
        canvas.apply {
            val opacityFactor = getOpacity() / 255f
            if (isTextBackgroundEnabled) {
                withSave {
                    backgroundPaint.color =
                        backgroundColor.calculateColorAlphaWithOpacityFactor(opacityFactor)

                    saveLayer(finalBounds, null)

                    drawRoundRect(
                        0f,
                        0f,
                        finalWidth,
                        finalHeight,
                        backgroundRadius,
                        backgroundRadius,
                        backgroundPaint
                    )

                    backgroundPaint.color = backgroundColor

                    translate(halfPadding, halfPadding)

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
            if (shadowRadius > 0) {
                val currentPorterDuffMode = dstOutPaint.xfermode

                dstOutPaint.xfermode = null

                val transformedColor =
                    shadowColor.calculateColorAlphaWithOpacityFactor(opacityFactor)

                dstOutPaint.color = transformedColor

                dstOutPaint.strokeWidth = strokeWidth

                dstOutPaint.setShadowLayer(
                    shadowRadius,
                    shadowDx,
                    shadowDy,
                    transformedColor
                )

                val maxRoundness = max(backgroundRadius, cornerRadius)

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

                dstOutPaint.xfermode = currentPorterDuffMode
                dstOutPaint.clearShadowLayer()
                dstOutPaint.strokeWidth = 0f
                dstOutPaint.color = Color.BLACK
            }


            withTranslation(halfPadding, halfPadding) {
                if (strokeWidth > 0f) {

                    strokePaint.color =
                        strokeColor.calculateColorAlphaWithOpacityFactor(opacityFactor)

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

    override fun getShadowDx(): Float {
        return shadowDx
    }

    override fun getShadowDy(): Float {
        return shadowDy
    }

    override fun getShadowRadius(): Float {
        return shadowRadius
    }

    override fun getShadowColor(): Int {
        return shadowColor
    }

    override fun setShadow(radius: Float, dx: Float, dy: Float, shadowColor: Int) {
        shadowRadius = radius
        shadowDx = dx
        shadowDy = dy
        this.shadowColor = shadowColor
        invalidate()
    }

    override fun clearShadow() {
        dstOutPaint.clearShadowLayer()
        shadowRadius = 0f
        shadowDx = 0f
        shadowDy = 0f
        shadowColor = Color.YELLOW
        invalidate()
    }

    override fun setBlendMode(blendMode: PorterDuff.Mode) {
        bitmapPaint.xfermode = PorterDuffXfermode(blendMode)
        this.blendMode = blendMode
        invalidate()
    }

    override fun clearBlend() {
        bitmapPaint.xfermode = null
        blendMode = PorterDuff.Mode.SRC
        invalidate()
    }

    override fun clone(): Transformable {
        return clone(true)
    }

    open fun clone(cloneBitmap: Boolean = false): BitmapPainter {
        val clonedBitmap =
            if (cloneBitmap) bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true) else bitmap

        return BitmapPainter(clonedBitmap).also {
            if (blendMode != PorterDuff.Mode.SRC) {
                it.setBlendMode(blendMode)
            }
            it.setOpacity(getOpacity())
            it.setBackground(
                getBackgroundPadding(),
                getBackgroundRadius(),
                getBackgroundColor()
            )
            it.setBackgroundState(getBackgroundState())
            it.setCornerRoundness(getCornerRoundness())
            it.setShadow(getShadowRadius(), getShadowDx(), getShadowDy(), getShadowColor())
            customBackgroundPath?.let { path ->
                it.customBackgroundPath = Path(path)
            }
            it.setStroke(strokeWidth, strokeColor)
            it.imageAdjustments.set(imageAdjustments)
            it.adjustImageValues()
        }
    }

    override fun getBlendMode(): PorterDuff.Mode {
        return blendMode
    }

    override fun getOpacity(): Int {
        return bitmapPaint.alpha
    }

    override fun setOpacity(opacity: Int) {
        bitmapPaint.alpha = opacity
        invalidate()
    }

    override fun setBackground(padding: Float, radius: Float, @ColorInt color: Int) {
        val shouldChangeBounds =
            (backgroundPaddingSize != padding || backgroundRadius != radius || backgroundPaddingSize < strokeWidth)
        backgroundPaddingSize = padding
        backgroundColor = color
        backgroundRadius = radius

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
        return backgroundRadius
    }

    override fun getBackgroundColor(): Int {
        return backgroundColor
    }

    override fun getBackgroundState(): Boolean {
        return isTextBackgroundEnabled
    }

    override fun setBackgroundState(isEnabled: Boolean) {
        isTextBackgroundEnabled = isEnabled
        notifyBoundsChanged()
    }

    override fun getCornerRoundness(): Float {
        return cornerRadius
    }

    override fun setCornerRoundness(roundness: Float) {
        cornerRadius = roundness
        invalidate()
    }

    override fun setStroke(strokeRadiusPx: Float, strokeColor: Int) {
        strokeWidth = strokeRadiusPx
        this.strokeColor = strokeColor
        strokePaint.color = strokeColor
        strokePaint.strokeWidth = strokeWidth
        notifyBoundsChanged()
    }

    override fun getStrokeColor(): Int {
        return strokeColor
    }

    override fun getStrokeWidth(): Float {
        return strokeWidth
    }

    open fun setHue(@FloatRange(0.0, 360.0) degree: Float) {
        imageAdjustments.hue = degree
        adjustImageValues()
    }

    open fun setContrast(@FloatRange(0.0, 2.0) contrast: Float) {
        imageAdjustments.contrast = contrast
        adjustImageValues()
    }

    open fun setSaturation(@FloatRange(0.0, 2.0) saturation: Float) {
        imageAdjustments.saturation = saturation
        adjustImageValues()
    }

    open fun setBrightness(@FloatRange(-1.0, 1.0) brightness: Float) {
        imageAdjustments.brightness = brightness
        adjustImageValues()
    }

    open fun setTint(@FloatRange(-1.0, 1.0) tint: Float) {
        imageAdjustments.tint = tint
        adjustImageValues()
    }

    open fun setTemperature(@FloatRange(-1.0, 1.0) warmth: Float) {
        imageAdjustments.temperature = warmth
        adjustImageValues()
    }

    open fun resetAdjustments() {
        colorMatrixFilter = ColorMatrixColorFilter(colorMatrix.apply {
            reset()
        })
        invalidate()
    }

    protected open fun adjustImageValues() {
        colorMatrix.reset()

        imageAdjustments.apply {
            if (hue != 0f) {
                colorMatrix.postConcat(colorMatrixTemp.apply {
                    reset()
                    setRotate(0, imageAdjustments.hue)
                    setRotate(1, imageAdjustments.hue)
                    setRotate(2, imageAdjustments.hue)
                })
            }

            if (saturation != 1f) {
                colorMatrix.postConcat(colorMatrixTemp.apply {
                    reset()
                    setSaturation(saturation)
                })
            }

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

    protected fun @receiver: ColorInt Int.calculateColorAlphaWithOpacityFactor(
        factor: Float
    ): Int =
        Color.argb(
            (this.alpha * factor).roundToInt(),
            this.red,
            this.green,
            this.blue,
        )

    data class ImageAdjustment(
        var brightness: Float,
        var contrast: Float,
        var saturation: Float,
        var hue: Float,
        var tint: Float,
        var temperature: Float
    ) {
        fun clone(): ImageAdjustment =
            ImageAdjustment(brightness, contrast, saturation, hue, tint, temperature)

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
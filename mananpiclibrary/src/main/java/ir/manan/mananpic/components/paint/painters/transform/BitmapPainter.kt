package ir.manan.mananpic.components.paint.painters.transform

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.ColorInt
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.graphics.withSave
import ir.manan.mananpic.properties.Backgroundable
import ir.manan.mananpic.properties.Blendable
import ir.manan.mananpic.properties.CornerRounder
import ir.manan.mananpic.properties.Opacityable
import ir.manan.mananpic.properties.Shadowable
import kotlin.math.max
import kotlin.math.roundToInt

class BitmapPainter(bitmap: Bitmap) : Transformable(), Blendable, Opacityable, Backgroundable,
    CornerRounder, Shadowable {

    private var cornerRadius: Float = 0f
    private val bitmapPaint = Paint().apply {
        isFilterBitmap = true
        shader = BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
    }

    private val dstOutPaint by lazy {
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
    }

    var bitmap: Bitmap = bitmap
        set(value) {
            field = value
            bitmapPaint.shader = BitmapShader(value, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            indicateBoundsChange()
        }

    private var shadowRadius = 0f
    private var shadowDx = 0f
    private var shadowDy = 0f
    private var shadowColor = Color.YELLOW

    private var blendMode: PorterDuff.Mode = PorterDuff.Mode.SRC

    private var isTextBackgroundEnabled = false

    private var backgroundPaddingSize = 50f

    private var backgroundRadius = 12f

    @ColorInt
    private var backgroundColor: Int = Color.GRAY

    private val backgroundPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = backgroundColor
        }
    }

    private var halfPadding = 0f

    private var finalWidth = 0f

    private var finalHeight = 0f

    private val finalBounds by lazy {
        RectF()
    }

    override fun getBounds(bounds: RectF) {
        val extraSpace = if (isTextBackgroundEnabled) backgroundPaddingSize else 0f

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
            if (shadowRadius > 0) {
                val currentPorterDuffMode = dstOutPaint.xfermode

                dstOutPaint.xfermode = null

                val transformedColor =
                    shadowColor.calculateColorAlphaWithOpacityFactor(opacityFactor)

                dstOutPaint.color = transformedColor

                dstOutPaint.setShadowLayer(
                    shadowRadius,
                    shadowDx,
                    shadowDy,
                    transformedColor
                )

                val maxRoundness = max(backgroundRadius, cornerRadius)

                drawRoundRect(
                    finalBounds,
                    maxRoundness,
                    maxRoundness,
                    dstOutPaint
                )

                dstOutPaint.xfermode = currentPorterDuffMode
                dstOutPaint.clearShadowLayer()
                dstOutPaint.color = Color.BLACK
            }
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

                    drawRoundRect(
                        0f,
                        0f,
                        bitmap.width.toFloat(),
                        bitmap.height.toFloat(),
                        cornerRadius,
                        cornerRadius,
                        dstOutPaint
                    )

                    drawRoundRect(
                        0f,
                        0f,
                        bitmap.width.toFloat(),
                        bitmap.height.toFloat(),
                        cornerRadius,
                        cornerRadius,
                        bitmapPaint
                    )

                    restore()

                }
            } else {
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
        val clonedBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
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
        val shouldChangeBounds = (backgroundPaddingSize != padding || backgroundRadius != radius)
        backgroundPaddingSize = padding
        backgroundColor = color
        backgroundRadius = radius

        if (shouldChangeBounds) {
            indicateBoundsChange()
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
        indicateBoundsChange()
    }

    override fun getCornerRoundness(): Float {
        return cornerRadius
    }

    override fun setCornerRoundness(roundness: Float) {
        cornerRadius = roundness
        invalidate()
    }

    private fun @receiver: ColorInt Int.calculateColorAlphaWithOpacityFactor(
        factor: Float
    ): Int =
        Color.argb(
            (this.alpha * factor).roundToInt(),
            this.red,
            this.green,
            this.blue,
        )

}
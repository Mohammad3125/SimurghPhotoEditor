package ir.manan.mananpic.components.paint.painters.transform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.annotation.ColorInt
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.graphics.withSave
import ir.manan.mananpic.properties.Backgroundable
import ir.manan.mananpic.properties.Blendable
import ir.manan.mananpic.properties.Opacityable
import kotlin.math.roundToInt

class BitmapPainter(var bitmap: Bitmap) : Transformable(), Blendable, Opacityable, Backgroundable {

    private val bitmapPaint = Paint().apply {
        isFilterBitmap = true
    }

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
    }

    override fun draw(canvas: Canvas) {
        if (isTextBackgroundEnabled) {
            canvas.withSave {
                val opacityFactor = getOpacity() / 255f

                backgroundPaint.color =
                    backgroundColor.calculateColorAlphaWithOpacityFactor(opacityFactor)

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
                drawBitmap(bitmap, 0f, 0f, bitmapPaint)
            }
        } else {
            canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)
        }
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
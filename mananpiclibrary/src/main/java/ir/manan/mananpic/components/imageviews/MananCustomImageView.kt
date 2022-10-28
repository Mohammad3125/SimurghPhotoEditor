package ir.manan.mananpic.components.imageviews

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.core.view.doOnPreDraw
import ir.manan.mananpic.properties.Bitmapable
import ir.manan.mananpic.properties.Blendable
import ir.manan.mananpic.properties.MananComponent
import ir.manan.mananpic.utils.MananFactory
import kotlin.math.max
import kotlin.math.min

class MananCustomImageView(context: Context) : View(context), MananComponent,
    java.io.Serializable, Bitmapable, Blendable {
    @Transient
    var bitmap: Bitmap? = null

    @Transient
    private val bitmapPaint = Paint().apply {
        isFilterBitmap = true
    }

    private var bitmapWidth = 0
    private var bitmapHeight = 0

    @Transient
    private val bounds = RectF()

    @Transient
    private val mappingMatrix = Matrix()

    private var colorFilter: ColorFilter? = null
        set(value) {
            bitmapPaint.colorFilter = value
            field = value
            invalidate()
        }

    private var blendMode : PorterDuff.Mode = PorterDuff.Mode.SRC

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        bitmap?.let {
            bitmapWidth = max(it.width, suggestedMinimumWidth)
            bitmapHeight = max(it.height, suggestedMinimumHeight)

            setMeasuredDimension(bitmapWidth, bitmapHeight)
            return
        }
        setMeasuredDimension(suggestedMinimumWidth, suggestedMinimumHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        bounds.set(
            x,
            y,
            width + x,
            height + y
        )

        pivotX = width * 0.5f
        pivotY = height * 0.5f


    }


    override fun onDraw(canvas: Canvas?) {
        canvas?.run {
            bitmap?.let {
                drawBitmap(it, 0f, 0f, bitmapPaint)
            }
        }
    }

    override fun reportBound(): RectF {
        bounds.set(
            x,
            y,
            width + x,
            height + y
        )
        mappingMatrix.run {
            setScale(scaleX, scaleY, bounds.centerX(), bounds.centerY())
            mapRect(bounds)
        }
        return bounds
    }

    override fun reportRotation(): Float {
        return rotation
    }

    override fun reportScaleX(): Float {
        return scaleX
    }

    override fun reportScaleY(): Float {
        return scaleY
    }

    override fun reportBoundPivotX(): Float {
        return bounds.centerX()
    }

    override fun reportBoundPivotY(): Float {
        return bounds.centerY()
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

    override fun applyScale(xFactor: Float, yFactor: Float) {
        scaleX *= xFactor
        scaleY *= yFactor
    }

    override fun applyMovement(dx: Float, dy: Float) {
        x += dx
        y += dy
    }

    override fun clone(): View {
        return MananFactory.createImageView(context, bitmap!!).also {
            it.colorFilter = colorFilter
            it.scaleX = scaleX
            it.scaleY = scaleY
            if (blendMode != PorterDuff.Mode.SRC) {
                it.setBlendMode(blendMode)
            }
            doOnPreDraw {
                it.rotation = rotation
            }
        }
    }

    override fun toBitmap(config: Bitmap.Config, ignoreAxisScale: Boolean): Bitmap {

        if (bitmap == null) throw IllegalStateException("bitmap of current image view is null")

        return bitmap!!
    }

    override fun toBitmap(
        width: Int,
        height: Int,
        config: Bitmap.Config,
        ignoreAxisScale: Boolean
    ): Bitmap {

        if (bitmap == null) throw IllegalStateException("bitmap of current image view is null")

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

    fun replaceBitmap(bitmap: Bitmap) {
        this.bitmap = bitmap
        requestLayout()
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

    override fun getBlendMode(): PorterDuff.Mode {
        return blendMode
    }
}
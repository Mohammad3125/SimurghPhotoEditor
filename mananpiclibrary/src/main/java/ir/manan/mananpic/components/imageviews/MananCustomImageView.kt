package ir.manan.mananpic.components.imageviews

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.core.view.doOnPreDraw
import ir.manan.mananpic.properties.Bitmapable
import ir.manan.mananpic.properties.Filterable
import ir.manan.mananpic.properties.MananComponent
import ir.manan.mananpic.utils.MananFactory
import kotlin.math.max

class MananCustomImageView(context: Context) : View(context), MananComponent, Filterable,
    java.io.Serializable, Bitmapable {
    @Transient
    var bitmap: Bitmap? = null

    @Transient
    private val bitmapPaint = Paint()

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
            doOnPreDraw {
                it.rotation = rotation
            }
        }
    }

    override fun applyFilter(colorFilter: ColorFilter) {
        this.colorFilter = colorFilter
    }

    override fun removeFilter() {
        this.colorFilter = null
    }

    override fun toBitmap(config: Bitmap.Config): Bitmap {

        if (bitmap == null) throw IllegalStateException("bitmap of current image view is null")

        bitmap!!.let {
            // Create a bitmap and invert it vertically and or horizontally if it is inverted.
            val finalBitmap =
                Bitmap.createBitmap(it, 0, 0, it.width, it.height, Matrix().apply {
                    postScale(
                        if (scaleX < 0f) -1f else 1f,
                        if (scaleY < 0f) -1f else 1f
                    )
                }, false)

            // Return the mutable copy.
            return finalBitmap.copy(finalBitmap.config, true)
        }
    }

    override fun toBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {

        if (bitmap == null) throw IllegalStateException("bitmap of current image view is null")

        // Determine how much the desired width and height is scaled base on
        // smallest desired dimension divided by maximum image dimension.
        var totalScaled = width / bounds.width()

        if (bounds.height() * totalScaled > height) {
            totalScaled = height / bounds.height()
        }

        // Create output bitmap matching desired width,height and config.
        val outputBitmap = Bitmap.createBitmap(width, height, config)

        // Calculate extra width and height remaining to later use to center the image inside bitmap.
        val extraWidth = (width / totalScaled) - bounds.width()
        val extraHeight = (height / totalScaled) - bounds.height()

        Canvas(outputBitmap).run {
            scale(totalScaled, totalScaled)
            // Finally translate to center the content.
            translate(-x + extraWidth * 0.5f, -y + extraHeight * 0.5f)
            draw(this)
        }

        return outputBitmap
    }

    fun replaceBitmap(bitmap: Bitmap) {
        this.bitmap = bitmap
        requestLayout()
    }
}
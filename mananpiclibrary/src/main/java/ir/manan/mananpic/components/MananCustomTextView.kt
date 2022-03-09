package ir.manan.mananpic.components

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import ir.manan.mananpic.properties.MananComponent
import ir.manan.mananpic.utils.sp
import kotlin.math.abs

/**
 * A custom textview created because of clipping issues with current [android.widget.TextView] in android framework.
 *
 * This class does not clip the text in any case. This way all fonts, especially the non-standard one do not get clipped.
 *
 */
class MananCustomTextView(context: Context, attr: AttributeSet?) : View(context, attr),
    MananComponent {
    constructor(context: Context) : this(context, null)

    private val textPaint by lazy {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    /**
     * Size of current text. default if 8sp.
     * Values will be interpreted as pixels.
     * Use [android.util.TypedValue] or [ir.manan.mananpic.utils.sp] to convert a sp number to pixels.
     */
    var textSize = sp(8)
        set(value) {
            textPaint.textSize = value
            field = value
            requestLayout()
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

    private val finalBounds by lazy {
        RectF()
    }

    /**
     * Baseline of text to be drawn.
     */
    private var textBaseLine = 0f

    override fun reportBound(): RectF {
        return finalBounds.apply {
            set(x, y, width + x, height + y)
        }
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
        return pivotX
    }

    override fun applyRotation(degree: Float) {
        rotation = degree
    }

    override fun applyScale(scaleFactor: Float) {
        textSize *= scaleFactor
    }

    override fun applyMovement(dx: Float, dy: Float) {
        x += dx
        y += dy
    }

    override fun clone(): View {
        // Not yet implemented.
        return this
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fontMetrics = textPaint.fontMetrics
        val textWidth = textPaint.measureText(text)
        val textHeight = abs(fontMetrics.ascent) + fontMetrics.descent + fontMetrics.leading

        pivotX = textWidth * 0.5f
        pivotY = textHeight * 0.5f

        setMeasuredDimension(
            if (suggestedMinimumWidth > textWidth) suggestedMinimumWidth else textWidth.toInt(),
            if (suggestedMinimumHeight > textHeight) suggestedMinimumWidth else textHeight.toInt()
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        finalBounds.set(x, y, width + x, height + y)

        textBaseLine = height - textPaint.fontMetrics.descent
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.run {
            super.onDraw(this)
            drawText(text, 0f, textBaseLine, textPaint)
        }
    }

    /**
     * Sets type face of current text.
     */
    fun setTypeFace(typeFace: Typeface) {
        textPaint.typeface = typeFace
        requestLayout()
    }

}
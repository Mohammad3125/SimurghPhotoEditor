package ir.manan.mananpic.components.paint.painters.brushpaint

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.view.doOnLayout
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.dp

class BrushPreview(context: Context, attributeSet: AttributeSet?) : View(context, attributeSet) {
    constructor(context: Context) : this(context, null)

    var brushPainter = BrushPaint()
        private set

    private val bitmapPaint = Paint().apply {
        isFilterBitmap = true
    }

    private val path = Path()

    private lateinit var layer: PaintLayer

    private val pathMeasure = PathMeasure()

    private lateinit var destBitmap: Bitmap

    private val points = FloatArray(80)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val measureWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measureHeight = MeasureSpec.getSize(heightMeasureSpec)

        val finalWidth = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> {
                measureWidth
            }
            MeasureSpec.AT_MOST, MeasureSpec.UNSPECIFIED -> {
                val w = dp(120).toInt()
                if (w > measureWidth) measureWidth else w
            }
            else -> {
                suggestedMinimumWidth
            }
        }

        val finalHeight = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> {
                measureHeight
            }
            MeasureSpec.AT_MOST, MeasureSpec.UNSPECIFIED -> {
                val h = dp(40).toInt()
                if (h > measureHeight) measureHeight else h
            }
            else -> {
                suggestedMinimumHeight
            }
        }

        setMeasuredDimension(finalWidth, finalHeight)

    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (changed) {
            calculatePoints()

            createLayer()

            drawPoints()
        }
    }


    private fun createLayer() {
        destBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        layer = PaintLayer(destBitmap, Matrix(), false, 1f, PorterDuff.Mode.SRC)

        brushPainter.initialize(
            context,
            this,
            MananMatrix(),
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            width,
            height
        )

        brushPainter.onLayerChanged(layer)

    }

    private fun calculatePoints() {

        val widthF = width.toFloat() - paddingRight
        val heightF = height.toFloat() - paddingBottom

        path.rewind()
        path.moveTo(paddingLeft.toFloat(), heightF * 0.5f + paddingTop)
        path.cubicTo(widthF * 0.25f, heightF, widthF * 0.75f, 0f, widthF, heightF * 0.5f)

        pathMeasure.setPath(path, false)
        val length = pathMeasure.length

        val speed = length / 40
        var distance = 0f

        val p = floatArrayOf(0f, 0f)

        repeat(40) {
            pathMeasure.getPosTan(distance, p, null)

            val ind = it * 2

            distance += speed

            points[ind] = p[0]
            points[ind + 1] = p[1]
        }
    }

    private fun drawPoints() {

        brushPainter.resetPaint()

        var lastX = points[0]
        var lastY = points[1]

        brushPainter.onMoveBegin(lastX, lastY)

        for (i in 2..points.size - 2 step 2) {

            val currentX = points[i]
            val currentY = points[i + 1]

            brushPainter.onMove(currentX - lastX, currentY - lastY, currentX, currentY)

            lastX = currentX
            lastY = currentY
        }

        brushPainter.onMoveEnded(points[points.lastIndex - 1], points[points.lastIndex])
    }


    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.run {
            drawBitmap(layer.bitmap, 0f, 0f, bitmapPaint)
        }
    }

    fun requestRender() {
        doOnLayout {
            drawPoints()
            invalidate()
        }
    }
}
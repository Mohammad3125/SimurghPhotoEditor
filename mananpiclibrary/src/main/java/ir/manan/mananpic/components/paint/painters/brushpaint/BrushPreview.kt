package ir.manan.mananpic.components.paint.painters.brushpaint

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.view.doOnLayout
import ir.manan.mananpic.components.paint.engines.CanvasDrawingEngine
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.dp

class BrushPreview(context: Context, attributeSet: AttributeSet?) : View(context, attributeSet) {
    constructor(context: Context) : this(context, null)

    var brush: Brush? = null
        set(value) {
            field = value

            doOnLayout {
                initialize(
                    width,
                    height,
                    paddingLeft.toFloat(),
                    paddingRight.toFloat(),
                    paddingTop.toFloat(),
                    paddingBottom.toFloat()
                )
            }

            requestRender()
        }

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
            initialize(
                width,
                height,
                paddingLeft.toFloat(),
                paddingRight.toFloat(),
                paddingTop.toFloat(),
                paddingBottom.toFloat()
            )
        }
    }

    private fun initialize(
        width: Int,
        height: Int,
        paddingLeft: Float,
        paddingRight: Float,
        paddingTop: Float,
        paddingBottom: Float
    ) {

        if (brush == null) {
            return
        }

        calculatePoints(
            width.toFloat(),
            height.toFloat(),
            paddingLeft,
            paddingRight,
            paddingTop,
            paddingBottom
        )

        brushPainter.initialize(
            MananMatrix(),
            RectF(
                0f, 0f,
                width.toFloat(),
                height.toFloat()
            ),
        )


        drawPoints(brush!!)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.let {
            brushPainter.draw(it)
        }
    }

    fun requestRender() {
        doOnLayout {
            drawPoints(brush!!)
            invalidate()
        }
    }

    companion object {

        private val path = Path()
        private val canvasBitmap by lazy {
            Canvas()
        }
        private val pathMeasure = PathMeasure()
        private val brushPainter = BrushPaint(CanvasDrawingEngine()).apply {
            shouldUseCacheDrawing = true
        }
        private val points = FloatArray(80)
        fun createBrushSnapshot(
            targetWidth: Int,
            targetHeight: Int,
            paddingHorizontal: Float,
            paddingVertical: Float,
            brush: Brush
        ): Bitmap {

            calculatePoints(
                targetWidth.toFloat(),
                targetHeight.toFloat(),
                paddingHorizontal,
                paddingHorizontal,
                paddingVertical,
                paddingVertical
            )

            brushPainter.initialize(
                MananMatrix(),
                RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat()),
            )

            drawPoints(brush)

            val snapshot = Bitmap.createBitmap(
                targetWidth,
                targetHeight,
                Bitmap.Config.ARGB_8888
            )

            canvasBitmap.setBitmap(snapshot)

            brushPainter.draw(canvasBitmap)

            return snapshot
        }

        private fun calculatePoints(
            targetWidth: Float,
            targetHeight: Float,
            paddingLeft: Float,
            paddingRight: Float,
            paddingTop: Float,
            paddingBottom: Float
        ) {

            val widthF = targetWidth - paddingRight
            val heightF = targetHeight - paddingBottom

            path.rewind()
            path.moveTo(paddingLeft, heightF * 0.5f + paddingTop)
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

        private fun drawPoints(brush: Brush) {

            brushPainter.brush = brush

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

    }
}
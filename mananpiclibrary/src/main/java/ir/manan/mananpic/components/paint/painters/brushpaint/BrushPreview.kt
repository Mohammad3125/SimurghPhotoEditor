package ir.manan.mananpic.components.paint.painters.brushpaint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PathMeasure
import android.util.AttributeSet
import android.view.View
import androidx.core.view.doOnLayout
import ir.manan.mananpic.components.paint.engines.CachedCanvasEngine
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.components.paint.smoothers.BezierLineSmoother
import ir.manan.mananpic.components.paint.smoothers.LineSmoother
import ir.manan.mananpic.utils.dp
import kotlin.random.Random

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

        brush?.let { finalBrush ->
            calculatePoints(
                width.toFloat(),
                height.toFloat(),
                paddingLeft,
                paddingRight,
                paddingTop,
                paddingBottom
            )

            initializeCachedProperties()

            callPoints(finalBrush)
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        if (canvas == null) return

        brush?.let {
            drawPoints(canvas, it)
        }
    }

    fun requestRender() {
        doOnLayout {
            callPoints(brush!!)
            invalidate()
        }
    }

    companion object {

        private val path = Path()
        private val canvasBitmap by lazy {
            Canvas()
        }
        private val pathMeasure = PathMeasure()
        private val engine by lazy { CachedCanvasEngine() }
        private val lineSmoother by lazy {
            BezierLineSmoother().apply {
                onDrawPoint = object : LineSmoother.OnDrawPoint {
                    override fun onDrawPoint(ex: Float, ey: Float) {
                        cachePointHolder.add(ex)
                        cachePointHolder.add(ey)
                    }
                }
            }
        }

        private val rotationCache = mutableListOf<Float>()
        private val scaleCache = mutableListOf<Float>()
        private val scatterXCache = mutableListOf<Float>()
        private val scatterYCache = mutableListOf<Float>()

        private var cacheCounter = 0
        private var cacheSizeInByte = 2000

        private var cachePointHolder = mutableListOf<Float>()

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

            initializeCachedProperties()

            callPoints(brush)

            val snapshot = Bitmap.createBitmap(
                targetWidth,
                targetHeight,
                Bitmap.Config.ARGB_8888
            )

            canvasBitmap.setBitmap(snapshot)

            drawPoints(canvasBitmap, brush)

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

        private fun callPoints(brush: Brush) {

            cacheCounter = 0
            cachePointHolder.clear()

            var ex = points[0]
            var ey = points[1]

            lineSmoother.setFirstPoint(
                ex,
                ey,
                1f,
                brush.spacedWidth
            )

            engine.onMoveBegin(
                ex,
                ey, brush
            )

            for (i in 2..points.size - 2 step 2) {
                ex = points[i]
                ey = points[i + 1]

                lineSmoother.addPoints(
                    ex,
                    ey,
                    1f,
                    brush.spacedWidth
                )

                engine.onMove(
                    ex,
                    ey, brush
                )
            }

            ex = points[points.lastIndex - 1]
            ey = points[points.lastIndex]

            lineSmoother.setLastPoint(
                ex,
                ey,
                1f,
                brush.spacedWidth
            )

            engine.onMoveEnded(
                ex, ey, brush
            )
        }

        private fun drawPoints(canvas: Canvas, brush: Brush) {
            cacheCounter = 0

            for (i in cachePointHolder.indices step 2) {

                engine.cachedScatterX = scatterXCache[cacheCounter]
                engine.cachedScatterY = scatterYCache[cacheCounter]
                engine.cachedScale = scaleCache[cacheCounter]
                engine.cachedRotation = rotationCache[cacheCounter]

                engine.draw(cachePointHolder[i], cachePointHolder[i + 1], canvas, brush)

                if (++cacheCounter > cacheSizeInByte - 1) {
                    cacheCounter = 0
                }

            }
        }

        private fun initializeCachedProperties() {
            if (rotationCache.size == 0) {
                rotationCache.clear()
                scaleCache.clear()
                scatterXCache.clear()
                scatterYCache.clear()

                repeat(cacheSizeInByte) {
                    rotationCache.add(Random.nextInt(0, 100) / 100f)
                    scaleCache.add(Random.nextInt(0, 100) / 100f)
                    scatterXCache.add(Random.nextInt(-100, 100) / 100f)
                    scatterYCache.add(Random.nextInt(-100, 100) / 100f)
                }
            }
        }
    }
}
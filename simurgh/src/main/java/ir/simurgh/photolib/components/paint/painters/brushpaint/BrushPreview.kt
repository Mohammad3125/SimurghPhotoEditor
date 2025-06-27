package ir.simurgh.photolib.components.paint.painters.brushpaint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.core.view.doOnLayout
import ir.simurgh.photolib.R
import ir.simurgh.photolib.components.paint.engines.CachedCanvasEngine
import ir.simurgh.photolib.components.paint.painters.brushpaint.brushes.Brush
import ir.simurgh.photolib.components.paint.smoothers.BezierLineSmoother
import ir.simurgh.photolib.components.paint.smoothers.LineSmoother
import ir.simurgh.photolib.utils.extensions.dp
import ir.simurgh.photolib.utils.gesture.TouchData
import kotlin.random.Random

open class BrushPreview(context: Context, attributeSet: AttributeSet?) :
    View(context, attributeSet) {
    constructor(context: Context) : this(context, null)

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    open var isCheckerBoardEnabled = true
        set(value) {
            field = value
            invalidate()
        }

    open var brush: Brush? = null
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

    protected val checkerPatternPaint by lazy {
        Paint().apply {
            shader = BitmapShader(
                BitmapFactory.decodeResource(resources, R.drawable.checker),
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT
            )
        }
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

    protected open fun initialize(
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

    override fun onDraw(canvas: Canvas) {
        if (isCheckerBoardEnabled) {
            canvas.drawPaint(checkerPatternPaint)
        }
        brush?.let {
            drawPoints(canvas, it)
        }
    }

    open fun requestRender() {
        brush?.let { b ->
            doOnLayout {
                callPoints(b)
                invalidate()
            }
        }
    }

    companion object {

        protected val path = Path()
        protected val canvasBitmap by lazy {
            Canvas()
        }
        protected val pathMeasure by lazy {
            PathMeasure()
        }
        protected val engine by lazy { CachedCanvasEngine() }
        protected var lineSmoother: LineSmoother = BezierLineSmoother().apply {
            onDrawPoint = object : LineSmoother.OnDrawPoint {
                override fun onDrawPoint(
                    ex: Float,
                    ey: Float,
                    angleDirection: Float,
                    totalDrawCount: Int,
                    isLastPoint: Boolean
                ) {
                    cachePointHolder.add(ex)
                    cachePointHolder.add(ey)
                    cacheDirectionAngleHolder.add(angleDirection)
                }
            }
        }

        protected val rotationCache = mutableListOf<Float>()
        protected val scaleCache = mutableListOf<Float>()
        protected val scatterXCache = mutableListOf<Float>()
        protected val scatterYCache = mutableListOf<Float>()

        protected var cacheCounter = 0
        protected var cacheSizeInByte = 2000

        protected var cachePointHolder = mutableListOf<Float>()
        protected var cacheDirectionAngleHolder = mutableListOf<Float>()

        protected var points = FloatArray(320)

        protected val pathPointHolder = FloatArray(2)

        protected val touchData = TouchData()

        fun createBrushSnapshot(
            targetWidth: Int,
            targetHeight: Int,
            paddingHorizontal: Float = 0f,
            paddingVertical: Float = 0f,
            brush: Brush,
            resolution: Int = 320,
            lineSmoother: LineSmoother = BezierLineSmoother(),
            customPath: Path? = null
        ): Bitmap {

            if (resolution % 2 != 0) {
                throw IllegalArgumentException("resolution should be divisible by 2")
            }

            if (resolution < 2) {
                throw IllegalArgumentException("resolution should be more than 2")
            }

            if (points.size != resolution) {
                points = FloatArray(resolution)
            }

            if (this.lineSmoother != lineSmoother) {
                this.lineSmoother = lineSmoother

                this.lineSmoother.apply {
                    onDrawPoint = object : LineSmoother.OnDrawPoint {
                        override fun onDrawPoint(
                            ex: Float,
                            ey: Float,
                            angleDirection: Float,
                            totalDrawCount: Int,
                            isLastPoint: Boolean
                        ) {
                            cachePointHolder.add(ex)
                            cachePointHolder.add(ey)
                            cacheDirectionAngleHolder.add(angleDirection)
                        }
                    }
                }
            }

            calculatePoints(
                targetWidth.toFloat(),
                targetHeight.toFloat(),
                paddingHorizontal,
                paddingHorizontal,
                paddingVertical,
                paddingVertical,
                customPath
            )

            initializeCachedProperties()

            callPoints(brush)

            val snapshot = createBitmap(targetWidth, targetHeight)

            canvasBitmap.setBitmap(snapshot)

            drawPoints(canvasBitmap, brush)

            return snapshot
        }

        protected fun calculatePoints(
            targetWidth: Float,
            targetHeight: Float,
            paddingLeft: Float,
            paddingRight: Float,
            paddingTop: Float,
            paddingBottom: Float,
            customPath: Path? = null,
        ) {

            val widthF = targetWidth - paddingRight
            val heightF = targetHeight - paddingBottom

            if (customPath != null) {
                path.set(customPath)
            } else {
                path.rewind()
                path.moveTo(paddingLeft, heightF * 0.5f + paddingTop)
                path.cubicTo(widthF * 0.25f, heightF, widthF * 0.75f, 0f, widthF, heightF * 0.5f)
            }

            pathMeasure.setPath(path, false)
            val length = pathMeasure.length

            val pointsHalf = (points.size / 2)
            val speed = length / pointsHalf
            var distance = 0f


            repeat(pointsHalf) {
                pathMeasure.getPosTan(distance, pathPointHolder, null)

                val ind = it * 2

                distance += speed

                points[ind] = pathPointHolder[0]
                points[ind + 1] = pathPointHolder[1]
            }

            pathMeasure.getPosTan(length, pathPointHolder, null)

            points[points.lastIndex - 1] = pathPointHolder[0]
            points[points.lastIndex] = pathPointHolder[1]
        }

        protected fun callPoints(brush: Brush) {

            cacheCounter = 0
            cachePointHolder.clear()
            cacheDirectionAngleHolder.clear()

            val ex = points[0]
            val ey = points[1]

            val brushSmoothness = brush.smoothness
            brush.smoothness = 0f

            touchData.ex = ex
            touchData.ey = ey


            lineSmoother.setFirstPoint(
                touchData,
                brush
            )

            engine.onMoveBegin(
                touchData, brush
            )

            for (i in 2..points.size - 2 step 2) {
                touchData.ex = points[i]
                touchData.ey = points[i + 1]

                lineSmoother.addPoints(
                    touchData,
                    brush
                )

                engine.onMove(
                    touchData,
                    brush
                )
            }

            touchData.ex = points[points.lastIndex - 1]
            touchData.ey = points[points.lastIndex]

            lineSmoother.setLastPoint(
                touchData,
                brush
            )

            engine.onMoveEnded(
                touchData, brush
            )

            brush.smoothness = brushSmoothness
        }

        protected fun drawPoints(canvas: Canvas, brush: Brush) {
            cacheCounter = 0

            for (i in cachePointHolder.indices step 2) {

                engine.cachedScatterX = scatterXCache[cacheCounter]
                engine.cachedScatterY = scatterYCache[cacheCounter]
                engine.cachedScale = scaleCache[cacheCounter]
                engine.cachedRotation = rotationCache[cacheCounter]

                engine.draw(
                    cachePointHolder[i],
                    cachePointHolder[i + 1],
                    cacheDirectionAngleHolder[i / 2],
                    canvas,
                    brush,
                    1
                )

                if (++cacheCounter > cacheSizeInByte - 1) {
                    cacheCounter = 0
                }

            }
        }

        protected fun initializeCachedProperties() {
            if (rotationCache.isEmpty()) {

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

package ir.manan.mananpic.components.paint.painters.masking

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.animation.LinearInterpolator
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.components.shapes.MananShape
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.dp

class MaskShapeTool(shape: MananShape?) : Painter() {

    constructor() : this(null)


    private val shapePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }

    var shape: MananShape? = shape
        set(value) {
            field = value
            resetPaint()
        }

    var strokeWidth = 0f
        set(value) {
            field = value
            shapePaint.strokeWidth = field
            sendMessage(PainterMessage.INVALIDATE)
        }

    private val shapeBounds by lazy {
        RectF()
    }

    private var selectedLayer: PaintLayer? = null

    private val canvasApply by lazy {
        Canvas()
    }

    var isEraser = false
        set(value) {
            field = value
            if (isEraser) {
                shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            } else {
                shapePaint.xfermode = null
            }
        }

    private lateinit var cornerPathEffect: CornerPathEffect

    private val pathEffectAnimator = ValueAnimator().apply {
        duration = 500
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        setFloatValues(0f, 20f)
        addUpdateListener {
            shapePaint.pathEffect =
                ComposePathEffect(
                    DashPathEffect(floatArrayOf(10f, 10f), it.animatedValue as Float),
                    cornerPathEffect
                )

            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    private lateinit var transformationMatrix: MananMatrix


    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        bounds: RectF
    ) {
        context.apply {
            if (strokeWidth == 0f) {
                strokeWidth = dp(3)
            }
            cornerPathEffect = CornerPathEffect(dp(2))
        }

        this.transformationMatrix = transformationMatrix

        pathEffectAnimator.start()
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {
        shapeBounds.left = initialX
        shapeBounds.top = initialY
    }

    override fun onMove(ex: Float, ey: Float, dx: Float, dy: Float) {
        resizeShape(ex, ey)
        sendMessage(PainterMessage.INVALIDATE)
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        resizeShape(lastX, lastY)
        shapePaint.style = Paint.Style.FILL
        drawOnLayer()
        shapePaint.style = Paint.Style.STROKE
        sendMessage(PainterMessage.INVALIDATE)
    }

    private fun resizeShape(right: Float, bottom: Float) {
        shape?.apply {
            shapeBounds.right = right
            shapeBounds.bottom = bottom
            resize(shapeBounds.width(), shapeBounds.height())
        }
    }

    private fun drawOnLayer() {
        selectedLayer?.bitmap?.let { layer ->
            canvasApply.setBitmap(layer)
            drawShape(canvasApply)
            resetPaint()
        }
    }

    override fun draw(canvas: Canvas) {
        shapePaint.strokeWidth = strokeWidth / transformationMatrix.getRealScaleX()
        drawShape(canvas)
    }

    private fun drawShape(canvas: Canvas) {
        canvas.apply {
            save()
            translate(shapeBounds.left, shapeBounds.top)
            shape?.draw(canvas, shapePaint)
            restore()
        }
    }

    override fun onLayerChanged(layer: PaintLayer?) {
        selectedLayer = layer
    }

    override fun resetPaint() {
        shapeBounds.setEmpty()
        resizeShape(0f, 0f)
    }
}
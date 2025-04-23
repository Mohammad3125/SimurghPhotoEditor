package ir.manan.mananpic.components.paint.painters.masking

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ComposePathEffect
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.animation.LinearInterpolator
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.components.paint.paintview.PaintLayer
import ir.manan.mananpic.components.shapes.MananShape
import ir.manan.mananpic.properties.MaskTool
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.TouchData

class MaskShapeTool(shape: MananShape?) : Painter(), MaskTool {

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

    private var isEraser = false
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

    private var isMoveBeginCalled = false


    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)
        context.apply {
            if (strokeWidth == 0f) {
                strokeWidth = dp(3)
            }
            cornerPathEffect = CornerPathEffect(dp(2))
        }

        this.transformationMatrix = transformationMatrix

        pathEffectAnimator.start()
    }

    override fun onMoveBegin(touchData: TouchData) {
        shapeBounds.left = touchData.ex
        shapeBounds.top = touchData.ey
        isMoveBeginCalled = true
    }

    override fun onMove(touchData: TouchData) {
        resizeShape(touchData.ex, touchData.ey)
        sendMessage(PainterMessage.INVALIDATE)
    }

    override fun onMoveEnded(touchData: TouchData) {
        if (!isMoveBeginCalled) {
            return
        }
        isMoveBeginCalled = false
        resizeShape(touchData.ex, touchData.ey)
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

    override fun setEraserMode(isEnabled: Boolean) {
        isEraser = isEnabled
    }
}
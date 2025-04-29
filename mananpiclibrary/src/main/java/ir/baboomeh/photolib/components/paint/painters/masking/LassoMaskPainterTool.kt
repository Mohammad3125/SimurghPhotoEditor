package ir.baboomeh.photolib.components.paint.painters.masking

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ComposePathEffect
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.view.animation.LinearInterpolator
import ir.baboomeh.photolib.components.paint.Painter
import ir.baboomeh.photolib.components.paint.painters.brushpaint.brushes.NativeBrush
import ir.baboomeh.photolib.components.paint.paintview.PaintLayer
import ir.baboomeh.photolib.components.paint.smoothers.BezierLineSmoother
import ir.baboomeh.photolib.components.paint.smoothers.LineSmoother
import ir.baboomeh.photolib.properties.MaskTool
import ir.baboomeh.photolib.utils.MananMatrix
import ir.baboomeh.photolib.utils.dp
import ir.baboomeh.photolib.utils.gesture.TouchData

open class LassoMaskPainterTool : Painter(), LineSmoother.OnDrawPoint, MaskTool {

    protected val lassoPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }

    protected val lassoPath by lazy {
        Path().apply {
            fillType = Path.FillType.WINDING
        }
    }

    protected val canvasColorApply by lazy {
        Canvas()
    }

    var lassoStrokeWidth = 0f
        set(value) {
            field = value
            lassoPaint.strokeWidth = field
            sendMessage(PainterMessage.INVALIDATE)
        }

    private var isEraseMode = false
        set(value) {
            field = value
            if (isEraseMode) {
                lassoPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            } else {
                lassoPaint.xfermode = null
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
            lassoPaint.pathEffect =
                ComposePathEffect(
                    DashPathEffect(floatArrayOf(10f, 10f), it.animatedValue as Float),
                    cornerPathEffect
                )

            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    protected var selectedLayer: PaintLayer? = null

    protected var isFirstPoint = true

    protected lateinit var transformationMatrix: MananMatrix

    protected val touchSmoother = BezierLineSmoother().apply {
        onDrawPoint = this@LassoMaskPainterTool
    }

    protected val smoothnessBrush = NativeBrush().apply {
        smoothness = 0.5f
        size = 10
    }

    var lassoSmoothness = 0.5f
        set(value) {
            field = value
            smoothnessBrush.smoothness = field
        }

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)
        context.apply {
            cornerPathEffect = CornerPathEffect(dp(2))
        }

        this.transformationMatrix = transformationMatrix

        pathEffectAnimator.start()
    }

    override fun onMoveBegin(touchData: TouchData) {
        touchSmoother.setFirstPoint(touchData, smoothnessBrush)
    }

    override fun onMove(touchData: TouchData) {
        touchSmoother.addPoints(touchData, smoothnessBrush)
    }

    override fun onMoveEnded(touchData: TouchData) {
        touchSmoother.setLastPoint(touchData, smoothnessBrush)
        applyOnLayer()
    }

    override fun onDrawPoint(
        ex: Float,
        ey: Float,
        angleDirection: Float,
        totalDrawCount: Int,
        isLastPoint: Boolean
    ) {
        drawLine(ex, ey)
    }

    protected open fun applyOnLayer() {
        selectedLayer?.bitmap?.let { layer ->
            canvasColorApply.setBitmap(layer)
            lassoPaint.style = Paint.Style.FILL
            canvasColorApply.drawPath(lassoPath, lassoPaint)
            lassoPaint.style = Paint.Style.STROKE
            resetPaint()
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    open fun drawLine(ex: Float, ey: Float) {
        if (isFirstPoint) {
            lassoPath.moveTo(ex, ey)
            isFirstPoint = false
        } else {
            lassoPath.lineTo(ex, ey)
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    override fun draw(canvas: Canvas) {
        lassoPaint.strokeWidth = lassoStrokeWidth / transformationMatrix.getRealScaleX()
        canvas.drawPath(lassoPath, lassoPaint)
    }

    override fun resetPaint() {
        lassoPath.rewind()
        isFirstPoint = true
        sendMessage(PainterMessage.INVALIDATE)
    }

    override fun onLayerChanged(layer: PaintLayer?) {
        selectedLayer = layer
    }

    override fun setEraserMode(isEnabled: Boolean) {
        isEraseMode = isEnabled
    }
}
package ir.baboomeh.photolib.components.paint.painters.masking

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
import androidx.core.graphics.withSave
import ir.baboomeh.photolib.components.paint.Painter
import ir.baboomeh.photolib.components.paint.paintview.PaintLayer
import ir.baboomeh.photolib.components.shapes.MananShape
import ir.baboomeh.photolib.properties.MaskTool
import ir.baboomeh.photolib.utils.MananMatrix
import ir.baboomeh.photolib.utils.dp
import ir.baboomeh.photolib.utils.gesture.TouchData

open class MaskShapeTool(context: Context, shape: MananShape?) : Painter(), MaskTool {

    constructor(context: Context) : this(context, null)


    protected val shapePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }

    open var shape: MananShape? = shape
        set(value) {
            field = value
            resetPaint()
        }

    open var strokeWidth = context.dp(3)
        set(value) {
            field = value
            shapePaint.strokeWidth = field
            sendMessage(PainterMessage.INVALIDATE)
        }

    protected val shapeBounds by lazy {
        RectF()
    }

    protected var selectedLayer: PaintLayer? = null

    protected val canvasApply by lazy {
        Canvas()
    }

    protected open var isEraser = false
        set(value) {
            field = value
            if (isEraser) {
                shapePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            } else {
                shapePaint.xfermode = null
            }
        }

    protected val cornerPathEffect by lazy {
        CornerPathEffect(context.dp(2))
    }

    protected val pathEffectAnimator = ValueAnimator().apply {
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

    protected lateinit var transformationMatrix: MananMatrix

    protected var isMoveBeginCalled = false

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)
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

    protected open fun resizeShape(right: Float, bottom: Float) {
        shape?.apply {
            shapeBounds.right = right
            shapeBounds.bottom = bottom
            resize(shapeBounds.width(), shapeBounds.height())
        }
    }

    protected open fun drawOnLayer() {
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

    protected open fun drawShape(canvas: Canvas) {
        canvas.withSave {
            translate(shapeBounds.left, shapeBounds.top)
            shape?.draw(canvas, shapePaint)
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
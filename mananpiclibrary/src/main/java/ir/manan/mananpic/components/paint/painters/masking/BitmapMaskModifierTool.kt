package ir.manan.mananpic.components.paint.painters.masking

import android.content.Context
import android.graphics.*
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.components.paint.engines.DrawingEngine
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.components.paint.smoothers.BezierLineSmoother
import ir.manan.mananpic.components.paint.smoothers.LineSmoother
import ir.manan.mananpic.utils.MananMatrix

class BitmapMaskModifierTool(bitmap: Bitmap, maskBitmap: Bitmap, var engine: DrawingEngine) :
    Painter(),
    LineSmoother.OnDrawPoint {

    var bitmap: Bitmap = bitmap
        set(value) {
            field = value
        }

    var maskBitmap: Bitmap = maskBitmap
        set(value) {
            field = value
            paintCanvas.setBitmap(field)
        }

    var brush: Brush? = null
        set(value) {
            field = value
            if (value != null) {
                finalBrush = value
            }
        }

    private lateinit var finalBrush: Brush

    var lineSmoother: LineSmoother = BezierLineSmoother()
        set(value) {
            field = value
            field.onDrawPoint = this
        }

    private val paintCanvas by lazy {
        Canvas()
    }

    private val dstOutBitmapPaint by lazy {
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
    }
    private val bitmapPaint by lazy {
        Paint()
    }

    private val layerBound = RectF()

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        bounds: RectF
    ) {
        paintCanvas.setBitmap(maskBitmap)
        lineSmoother.onDrawPoint = this
        layerBound.set(bounds)
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {
        if (shouldDraw()) {
            engine.onMoveBegin(initialX, initialY, finalBrush)

            lineSmoother.setFirstPoint(
                initialX,
                initialY,
                finalBrush
            )
        }
    }

    override fun onMove(ex: Float, ey: Float, dx: Float, dy: Float) {
        if (shouldDraw()) {

            engine.onMove(ex, ey, dx, dy, finalBrush)

            lineSmoother.addPoints(ex, ey, finalBrush)

        }
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        if (shouldDraw()) {

            engine.onMoveEnded(lastX, lastX, finalBrush)

            lineSmoother.setLastPoint(
                lastX,
                lastY,
                finalBrush
            )

            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    private fun shouldDraw(): Boolean {
        return this::finalBrush.isInitialized
    }

    override fun draw(canvas: Canvas) {
        canvas.apply {
            saveLayer(layerBound, bitmapPaint)
            drawBitmap(bitmap, 0f, 0f, bitmapPaint)
            drawBitmap(maskBitmap, 0f, 0f, dstOutBitmapPaint)
            restore()
        }
    }

    override fun resetPaint() {
    }

    override fun onDrawPoint(ex: Float, ey: Float, angleDirection: Float, isLastPoint: Boolean) {
        engine.draw(
            ex,
            ey,
            angleDirection,
            paintCanvas,
            finalBrush
        )
        sendMessage(PainterMessage.INVALIDATE)
    }
}
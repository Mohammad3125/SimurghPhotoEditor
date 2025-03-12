package ir.manan.mananpic.components.paint.painters.masking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.components.paint.engines.DrawingEngine
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.components.paint.paintview.MananPaintView
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
        super.initialize(context, transformationMatrix, fitInsideMatrix, bounds)
        paintCanvas.setBitmap(maskBitmap)
        lineSmoother.onDrawPoint = this
        layerBound.set(bounds)
    }

    override fun onMoveBegin(touchData: MananPaintView.TouchData) {
        if (shouldDraw()) {
            touchData.run {
                engine.onMoveBegin(touchData, finalBrush)

                lineSmoother.setFirstPoint(
                    touchData,
                    finalBrush
                )
            }
        }
    }

    override fun onMove(touchData: MananPaintView.TouchData) {
        if (shouldDraw()) {

            engine.onMove(touchData, finalBrush)

            lineSmoother.addPoints(touchData, finalBrush)

        }
    }

    override fun onMoveEnded(touchData: MananPaintView.TouchData) {
        if (shouldDraw()) {

            engine.onMoveEnded(touchData, finalBrush)

            lineSmoother.setLastPoint(
                touchData,
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

    override fun onDrawPoint(
        ex: Float,
        ey: Float,
        angleDirection: Float,
        totalDrawCount: Int,
        isLastPoint: Boolean
    ) {
        engine.draw(
            ex,
            ey,
            angleDirection,
            paintCanvas,
            finalBrush,
            totalDrawCount
        )
        sendMessage(PainterMessage.INVALIDATE)
    }
}
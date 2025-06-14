package ir.baboomeh.photolib.components.paint.painters.masking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import ir.baboomeh.photolib.components.paint.Painter
import ir.baboomeh.photolib.components.paint.engines.DrawingEngine
import ir.baboomeh.photolib.components.paint.painters.brushpaint.brushes.Brush
import ir.baboomeh.photolib.components.paint.smoothers.BezierLineSmoother
import ir.baboomeh.photolib.components.paint.smoothers.LineSmoother
import ir.baboomeh.photolib.utils.gesture.TouchData
import ir.baboomeh.photolib.utils.matrix.MananMatrix

open class BitmapMaskModifierTool(open var bitmap: Bitmap, maskBitmap: Bitmap, var engine: DrawingEngine) :
    Painter(),
    LineSmoother.OnDrawPoint {

    open var maskBitmap: Bitmap = maskBitmap
        set(value) {
            field = value
            paintCanvas.setBitmap(field)
        }

    open var brush: Brush? = null
        set(value) {
            field = value
            if (value != null) {
                finalBrush = value
            }
        }

    protected lateinit var finalBrush: Brush

    open var lineSmoother: LineSmoother = BezierLineSmoother()
        set(value) {
            field = value
            field.onDrawPoint = this
        }

    protected val paintCanvas by lazy {
        Canvas()
    }

    protected val dstOutBitmapPaint by lazy {
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
    }
    protected val bitmapPaint by lazy {
        Paint()
    }

    protected val layerBound = RectF()

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)
        paintCanvas.setBitmap(maskBitmap)
        lineSmoother.onDrawPoint = this
        layerBound.set(layerBounds)
    }

    override fun onMoveBegin(touchData: TouchData) {
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

    override fun onMove(touchData: TouchData) {
        if (shouldDraw()) {

            engine.onMove(touchData, finalBrush)

            lineSmoother.addPoints(touchData, finalBrush)

        }
    }

    override fun onMoveEnded(touchData: TouchData) {
        if (shouldDraw()) {

            engine.onMoveEnded(touchData, finalBrush)

            lineSmoother.setLastPoint(
                touchData,
                finalBrush
            )

            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    protected open fun shouldDraw(): Boolean {
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
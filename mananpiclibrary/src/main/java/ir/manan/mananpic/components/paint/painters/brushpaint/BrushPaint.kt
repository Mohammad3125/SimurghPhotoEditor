package ir.manan.mananpic.components.paint.painters.brushpaint

import android.graphics.*
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.components.paint.engines.DrawingEngine
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.components.paint.smoothers.BezierLineSmoother
import ir.manan.mananpic.components.paint.smoothers.LineSmoother
import ir.manan.mananpic.utils.MananMatrix

class BrushPaint(var engine: DrawingEngine) : Painter(), LineSmoother.OnDrawPoint {

    private var alphaBlendPaint = Paint().apply {
        isFilterBitmap = true
    }

    var brush: Brush? = null
        set(value) {
            field = value
            if (value != null) {
                finalBrush = value
            }
        }
    private lateinit var finalBrush: Brush

    private lateinit var ccBitmap: Bitmap
    private val paintCanvas by lazy {
        Canvas()
    }
    private lateinit var alphaBlendBitmap: Bitmap

    private val alphaBlendCanvas by lazy {
        Canvas()
    }
    private var shouldBlendAlpha = false

    private var viewBounds = RectF()

    private var isLayerNull = true
    private var isBrushNull = false

    var lineSmoother: LineSmoother = BezierLineSmoother()
        set(value) {
            field = value
            field.onDrawPoint = this
        }

    override fun initialize(
        matrix: MananMatrix,
        bounds: RectF,
    ) {

        viewBounds.set(bounds)

        lineSmoother.onDrawPoint = this
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {
        if (!this::finalBrush.isInitialized || isLayerNull) {
            isBrushNull = true
            return
        }

        isBrushNull = false

        engine.onMoveBegin(initialX, initialY, finalBrush)

        lineSmoother.setFirstPoint(
            initialX,
            initialY,
            1f - finalBrush.smoothness,
            finalBrush.spacedWidth
        )

        alphaBlendPaint.alpha = (finalBrush.opacity * 255f).toInt()

        shouldBlendAlpha = finalBrush.alphaBlend

        if (shouldCreateAlphaBitmap()) {
            alphaBlendBitmap = ccBitmap.copy(Bitmap.Config.ARGB_8888, true)
            alphaBlendCanvas.setBitmap(alphaBlendBitmap)
        }

    }

    private fun shouldCreateAlphaBitmap(): Boolean {
        return shouldBlendAlpha && (!this::alphaBlendBitmap.isInitialized || (alphaBlendBitmap.width != ccBitmap.width || alphaBlendBitmap.height != ccBitmap.height))
    }

    override fun onMove(ex: Float, ey: Float, dx: Float, dy: Float) {

        if (shouldDraw()) {

            engine.onMove(ex, ey, dx, dy, finalBrush)

            lineSmoother.addPoints(ex, ey, 1f - finalBrush.smoothness, finalBrush.spacedWidth)

        }
    }

    override fun onDrawPoint(ex: Float, ey: Float) {
        engine.draw(ex, ey, if (shouldBlendAlpha) alphaBlendCanvas else paintCanvas, finalBrush)
        sendMessage(PainterMessage.INVALIDATE)
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {

        if (shouldDraw()) {

            engine.onMoveEnded(lastX, lastX, finalBrush)

            lineSmoother.setLastPoint(
                lastX,
                lastY,
                1f - finalBrush.smoothness,
                finalBrush.spacedWidth
            )

            if (shouldBlendAlpha) {
                paintCanvas.drawBitmap(alphaBlendBitmap, 0f, 0f, alphaBlendPaint)
                alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
            }

            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    override fun onLayerChanged(layer: PaintLayer?) {

        if (layer == null) {
            isLayerNull = true
            return
        }

        isLayerNull = false

        ccBitmap = layer.bitmap
        paintCanvas.setBitmap(ccBitmap)
    }

    override fun draw(canvas: Canvas) {
        if (!isBrushNull && shouldBlendAlpha) {
            canvas.drawBitmap(alphaBlendBitmap, 0f, 0f, alphaBlendPaint)
        }
    }

    private fun shouldDraw(): Boolean =
        !isBrushNull && !isLayerNull


    override fun resetPaint() {
        ccBitmap.eraseColor(Color.TRANSPARENT)
        alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
        sendMessage(PainterMessage.INVALIDATE)
    }

    override fun undo() {
    }

    override fun redo() {
    }

    override fun onSizeChanged(newBounds: RectF, changeMatrix: Matrix) {
        viewBounds.set(newBounds)
    }
}
package ir.manan.mananpic.components.paint.painters.coloring.flood

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.components.paint.paintview.MananPaintView
import ir.manan.mananpic.components.paint.paintview.PaintLayer
import ir.manan.mananpic.utils.MananMatrix

class FloodFillPainter(var floodFiller: FloodFill) : Painter() {

    private var layerBitmap: Bitmap? = null

    var fillColor = Color.BLACK

    var threshold = 0.6f

    var thresholdStep = 0.1f

    private var currentThreshold = 0f

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        bounds: RectF
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, bounds)
        currentThreshold = threshold
    }

    override fun onMoveBegin(touchData: MananPaintView.TouchData) {

    }

    override fun onMove(touchData: MananPaintView.TouchData) {

    }

    override fun onMoveEnded(touchData: MananPaintView.TouchData) {
        layerBitmap?.let { bitmap ->
            val xI = touchData.ex.toInt()
            val xY = touchData.ey.toInt()

            if (!isValid(bitmap, xI, xY)) {
                return
            }

            if (bitmap.getPixel(xI, xY) == fillColor) {
                currentThreshold += thresholdStep
                currentThreshold = currentThreshold.coerceIn(0f, 1f)
            } else {
                currentThreshold = threshold
            }

            floodFiller.fill(bitmap, xI, xY, fillColor, currentThreshold)
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    private fun isValid(bitmap: Bitmap, ex: Int, ey: Int): Boolean {
        return (ex.coerceIn(0, bitmap.width - 1) == ex) && (ey.coerceIn(0, bitmap.height - 1) == ey)
    }

    override fun draw(canvas: Canvas) {

    }

    override fun resetPaint() {

    }

    override fun onSizeChanged(newBounds: RectF, changeMatrix: Matrix) {

    }

    override fun onLayerChanged(layer: PaintLayer?) {
        if (layer == null) {
            return
        }

        layerBitmap = layer.bitmap
    }

}
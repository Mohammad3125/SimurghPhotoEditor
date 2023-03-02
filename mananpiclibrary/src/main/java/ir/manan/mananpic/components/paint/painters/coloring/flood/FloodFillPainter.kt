package ir.manan.mananpic.components.paint.painters.coloring.flood

import android.graphics.*
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.utils.MananMatrix

class FloodFillPainter(var floodFiller: FloodFill) : Painter() {

    private var layerBitmap: Bitmap? = null

    var fillColor = Color.BLACK

    var threshold = 0.6f

    var thresholdStep = 0.1f

    private var currentThreshold = 0f

    override fun initialize(matrix: MananMatrix, bounds: RectF) {
        currentThreshold = threshold
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {

    }

    override fun onMove(ex: Float, ey: Float, dx: Float, dy: Float) {

    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        layerBitmap?.let { bitmap ->
            val xI = lastX.toInt()
            val xY = lastY.toInt()

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
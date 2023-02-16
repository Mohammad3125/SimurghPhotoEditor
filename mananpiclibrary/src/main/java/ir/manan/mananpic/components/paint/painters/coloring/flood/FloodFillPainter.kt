package ir.manan.mananpic.components.paint.painters.coloring.flood

import android.graphics.*
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.utils.MananMatrix

class FloodFillPainter(var floodFiller: FloodFill) : Painter() {

    private var layerBitmap: Bitmap? = null

    var fillColor = Color.BLACK


    override fun initialize(matrix: MananMatrix, bounds: RectF) {

    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {

    }

    override fun onMove(ex: Float, ey: Float, dx: Float, dy: Float) {

    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        layerBitmap?.let { bitmap ->
            floodFiller.fill(bitmap, lastX.toInt(), lastY.toInt(), fillColor, 0.5f)
            sendMessage(PainterMessage.INVALIDATE)
        }
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
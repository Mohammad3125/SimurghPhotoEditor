package ir.baboomeh.photolib.components.paint.painters.coloring.flood

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.baboomeh.photolib.components.paint.Painter
import ir.baboomeh.photolib.components.paint.paintview.PaintLayer
import ir.baboomeh.photolib.utils.gesture.TouchData

class FloodFillPainter : Painter() {

    private var layerBitmap: Bitmap? = null

    private var onFloodFillRequest: ((bitmap: Bitmap, ex: Int, ey: Int) -> Unit)? = null

    private var preventHistorySave = true

    override fun onMoveBegin(touchData: TouchData) {

    }

    override fun onMove(touchData: TouchData) {

    }

    override fun onMoveEnded(touchData: TouchData) {
        layerBitmap?.let { bitmap ->
            val ex = touchData.ex.toInt()
            val ey = touchData.ey.toInt()

            if (!isValid(bitmap, ex, ey)) {
                return
            }

            onFloodFillRequest?.invoke(bitmap, ex, ey)
        }

        preventHistorySave = true
    }

    private fun isValid(bitmap: Bitmap, ex: Int, ey: Int): Boolean {
        return (ex.coerceIn(0, bitmap.width - 1) == ex) && (ey.coerceIn(0, bitmap.height - 1) == ey)
    }

    override fun draw(canvas: Canvas) {

    }

    override fun resetPaint() {

    }

    fun setOnFloodFillRequest(func: (bitmap: Bitmap, ex: Int, ey: Int) -> Unit) {
        onFloodFillRequest = func
    }

    override fun onLayerChanged(layer: PaintLayer?) {
        if (layer == null) {
            return
        }

        layerBitmap = layer.bitmap
    }

    override fun doesHandleHistory(): Boolean {
        val toReturn = preventHistorySave
        preventHistorySave = false
        return toReturn
    }

}
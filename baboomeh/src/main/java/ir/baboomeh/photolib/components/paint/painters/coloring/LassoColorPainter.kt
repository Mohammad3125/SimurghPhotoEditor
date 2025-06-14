package ir.baboomeh.photolib.components.paint.painters.coloring

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import androidx.annotation.ColorInt
import ir.baboomeh.photolib.components.paint.painters.masking.LassoMaskPainterTool
import ir.baboomeh.photolib.utils.gesture.TouchData

open class LassoColorPainter(context: Context) : LassoMaskPainterTool(context) {

    @ColorInt
    open var fillingColor = Color.BLACK
        set(value) {
            field = value
            lassoPaint.color = field
            sendMessage(PainterMessage.INVALIDATE)
        }

    init {
        lassoPaint.style = Paint.Style.FILL
    }

    override fun onMoveEnded(touchData: TouchData) {
        super.onMoveEnded(touchData)
        applyOnLayer()
    }

    override fun applyOnLayer() {
        selectedLayer?.bitmap.let { layerBitmap ->
            canvasColorApply.setBitmap(layerBitmap)
            canvasColorApply.drawPath(lassoPath, lassoPaint)
            resetPaint()
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

}
package ir.manan.mananpic.components.paint.painters.coloring

import android.graphics.Color
import android.graphics.Paint
import androidx.annotation.ColorInt
import ir.manan.mananpic.components.paint.painters.masking.LassoMaskPainterTool
import ir.manan.mananpic.utils.gesture.TouchData

open class LassoColorPainter : LassoMaskPainterTool() {

    @ColorInt
    var fillingColor = Color.BLACK
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
package ir.baboomeh.photolib.components.paint.painters.coloring

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.annotation.ColorInt
import ir.baboomeh.photolib.components.paint.painters.selection.PenToolBase

class PenToolColorPainter(context: Context) : PenToolBase(context) {
    private val canvasApply by lazy {
        Canvas()
    }

    @ColorInt
    var fillingColor = Color.BLACK


    private lateinit var coloringBitmap: Bitmap

    fun applyColoring(isInverse: Boolean) {

        selectedLayer?.let { layer ->
            if (!this::coloringBitmap.isInitialized && layer.bitmap.config != null) {
                coloringBitmap = layer.bitmap.copy(layer.bitmap.config ?: Bitmap.Config.ARGB_8888, true)
            }

            drawLinesIntoPath(path)
            if (isInverse) {
                coloringBitmap.eraseColor(fillingColor)
                canvasApply.setBitmap(coloringBitmap)
                linesPaint.style = Paint.Style.FILL
                linesPaint.color = Color.BLACK
                linesPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                canvasApply.drawPath(path, linesPaint)
                linesPaint.xfermode = null
                linesPaint.style = Paint.Style.STROKE
                linesPaint.color = Color.BLACK
                canvasApply.setBitmap(layer.bitmap)
                canvasApply.drawBitmap(coloringBitmap, 0f, 0f, linesPaint)

            } else {
                canvasApply.setBitmap(layer.bitmap)
                linesPaint.style = Paint.Style.FILL
                linesPaint.color = fillingColor
                canvasApply.drawPath(path, linesPaint)
                linesPaint.style = Paint.Style.STROKE
                linesPaint.color = Color.BLACK
            }
        }
    }
}
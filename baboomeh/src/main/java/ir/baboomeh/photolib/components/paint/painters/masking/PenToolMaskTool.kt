package ir.baboomeh.photolib.components.paint.painters.masking

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import ir.baboomeh.photolib.components.paint.painters.selection.PenToolBase

open class PenToolMaskTool(context: Context) : PenToolBase(context) {

    protected val canvasApply by lazy {
        Canvas()
    }

    open fun applyOnMaskLayer() {
        drawLinesIntoPath(path)
        selectedLayer?.let { maskLayer ->
            canvasApply.setBitmap(maskLayer.bitmap)
            linesPaint.style = Paint.Style.FILL
            canvasApply.drawPath(path, linesPaint)
            linesPaint.style = Paint.Style.STROKE
        }
    }

    open fun cutFromMaskLayer() {
        linesPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        applyOnMaskLayer()
        linesPaint.xfermode = null
    }

    open fun removeLastLine() {
        lines.run {
            if (isNotEmpty()) {
                // Remove last line in stack.
                pop()

                // If it's not empty...
                if (isNotEmpty()) {
                    // Then get the previous line and select it and restore its state.
                    val currentLine = peek()

                    selectedLine = currentLine

                    setLineRelatedVariables(currentLine)

                    isNewLineDrawn = true
                } else {
                    isNewLineDrawn = false
                    selectedLine = null
                }

                // If path is close and user undoes the operation,
                // then open the path and reset its offset and cancel path animation.
                if (isPathClose) {
                    isPathClose = false
                    cancelAnimation()
                }
            }

            // Decrement the counter.
            if (pointCounter > 0) {
                --pointCounter
            }

            sendMessage(PainterMessage.INVALIDATE)
        }
    }
}

package ir.manan.mananpic.components.paint.painters.coloring

import android.graphics.*
import androidx.annotation.ColorInt
import ir.manan.mananpic.components.paint.painters.masking.LassoMaskPainterTool
import java.util.*

open class LassoColorPainter : LassoMaskPainterTool() {


    protected val undoStack = Stack<State>()

    protected val redoStack = Stack<State>()

    protected val pathMeasure = PathMeasure()

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

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        super.onMoveEnded(lastX, lastY)
        saveState()
    }

    override fun drawLine(ex: Float, ey: Float) {
        if (isFirstPoint || lassoPath.isEmpty) {
            lassoPath.moveTo(ex, ey)
            saveState()
            isFirstPoint = false
        } else {
            lassoPath.lineTo(ex, ey)
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    private fun saveState() {
        redoStack.clear()
        undoStack.push(State(Path(lassoPath), isFirstPoint))
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(lassoPath, lassoPaint)
    }

    override fun resetPaint() {
        lassoPath.rewind()
        isFirstPoint = true
        sendMessage(PainterMessage.INVALIDATE)
    }

    fun applyColoring() {
        selectedLayer?.bitmap.let { layerBitmap ->
            canvasColorApply.setBitmap(layerBitmap)
            canvasColorApply.drawPath(lassoPath, lassoPaint)
            resetPaint()

            sendMessage(PainterMessage.SAVE_HISTORY)
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    override fun undo() {
        swapStacks(undoStack, redoStack)
    }

    override fun redo() {
        swapStacks(redoStack, undoStack)
    }

    private fun swapStacks(
        popStack: Stack<State>,
        pushStack: Stack<State>
    ) {
        if (popStack.isNotEmpty()) {
            val poppedState = popStack.pop()

            pathMeasure.setPath(poppedState.pathCopy, false)
            val poppedPathLength = pathMeasure.length
            pathMeasure.setPath(lassoPath, false)
            val currentPathLength = pathMeasure.length

            if (popStack.isNotEmpty() && (pushStack.isEmpty() || currentPathLength == poppedPathLength)) {
                val newPopped = popStack.pop()
                lassoPath.set(newPopped.pathCopy)
                isFirstPoint = newPopped.isFirstPoint
                if (isFirstPoint) {
                    lassoPath.rewind()
                }
                pushStack.push(poppedState)
                pushStack.push(newPopped)
            } else {
                pushStack.push(poppedState)
                lassoPath.set(poppedState.pathCopy)
                isFirstPoint = poppedState.isFirstPoint
                if (isFirstPoint) {
                    lassoPath.rewind()
                }
            }

            // State of layer cache only changes after gestures get applied, since this method isn't
            // called after any gestures then it is needed to re-cache layers in order for changes to be visible.
            sendMessage(PainterMessage.CACHE_LAYERS)
            sendMessage(PainterMessage.INVALIDATE)
        }
    }
    override fun doesHandleHistory(): Boolean {
        return true
    }

    protected data class State(val pathCopy: Path, val isFirstPoint: Boolean)
}
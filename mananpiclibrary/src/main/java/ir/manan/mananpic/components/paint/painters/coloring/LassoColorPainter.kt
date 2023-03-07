package ir.manan.mananpic.components.paint.painters.coloring

import android.content.Context
import android.graphics.*
import androidx.annotation.ColorInt
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.utils.MananMatrix
import java.util.*

class LassoColorPainter : Painter() {

    private val lassoPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    private val lassoPath by lazy {
        Path().apply {
            fillType = Path.FillType.WINDING
        }
    }

    private val undoStack = Stack<Pair<Path, Boolean>>()

    private val redoStack = Stack<Pair<Path, Boolean>>()

    private val pathMeasure = PathMeasure()

    private var selectedLayer: PaintLayer? = null

    @ColorInt
    var fillingColor = Color.BLACK
        set(value) {
            field = value
            lassoPaint.color = field
            sendMessage(PainterMessage.INVALIDATE)
        }

    private var isFirstPoint = true

    private val canvasColorApply by lazy {
        Canvas()
    }

    override fun initialize(context: Context, matrix: MananMatrix, bounds: RectF) {

    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {
    }

    override fun onMove(ex: Float, ey: Float, dx: Float, dy: Float) {
        drawLine(ex, ey)
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        drawLine(lastX, lastY)
        saveState()
    }

    private fun drawLine(ex: Float, ey: Float) {
        if (isFirstPoint) {
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
        undoStack.push(Path(lassoPath) to isFirstPoint)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(lassoPath, lassoPaint)
    }

    override fun resetPaint() {
        lassoPath.rewind()
        isFirstPoint = true
        sendMessage(PainterMessage.INVALIDATE)
    }

    override fun onSizeChanged(newBounds: RectF, changeMatrix: Matrix) {

    }

    override fun onLayerChanged(layer: PaintLayer?) {
        selectedLayer = layer
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
        popStack: Stack<Pair<Path, Boolean>>,
        pushStack: Stack<Pair<Path, Boolean>>
    ) {
        if (popStack.isNotEmpty()) {
            val poppedState = popStack.pop()

            pathMeasure.setPath(poppedState.first, false)
            val poppedPathLength = pathMeasure.length
            pathMeasure.setPath(lassoPath, false)
            val currentPathLength = pathMeasure.length

            if (popStack.isNotEmpty() && (pushStack.isEmpty() || currentPathLength == poppedPathLength)) {
                val newPopped = popStack.pop()
                lassoPath.set(newPopped.first)
                isFirstPoint = newPopped.second
                pushStack.push(poppedState)
                pushStack.push(newPopped)
            } else {
                pushStack.push(poppedState)
                lassoPath.set(poppedState.first)
                isFirstPoint = poppedState.second
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
}
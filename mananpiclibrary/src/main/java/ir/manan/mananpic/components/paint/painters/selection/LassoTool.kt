package ir.manan.mananpic.components.paint.painters.selection

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.animation.LinearInterpolator
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.dp
import java.util.*

class LassoTool : Painter() {

    private val lassoPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
    }

    private val bitmapPaint by lazy {
        Paint()
    }

    var lassoStrokeWidth = 0f
        set(value) {
            field = value
            lassoPaint.strokeWidth = field
            sendMessage(PainterMessage.INVALIDATE)
        }


    private val lassoPath by lazy {
        Path().apply {
            fillType = Path.FillType.WINDING
        }
    }

    private val lassoCopy by lazy {
        Path()
    }

    private val canvas by lazy {
        Canvas()
    }

    private val bitmapRectangle by lazy {
        RectF()
    }

    private val viewBounds = RectF()

    private lateinit var cornerPathEffect: CornerPathEffect

    private val pathEffectAnimator = ValueAnimator().apply {
        duration = 500
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        setFloatValues(0f, 20f)
        addUpdateListener {
            lassoPaint.pathEffect =
                ComposePathEffect(
                    DashPathEffect(floatArrayOf(10f, 10f), it.animatedValue as Float),
                    cornerPathEffect
                )

            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    private val undoStack = Stack<State>()

    private val redoStack = Stack<State>()

    private val pathMeasure = PathMeasure()

    private var selectedLayer: PaintLayer? = null

    private var isFirstPoint = true

    private var copiedBitmap: Bitmap? = null

    var isInverse = false
        set(value) {
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    private lateinit var canvasMatrix: MananMatrix

    override fun initialize(context: Context, matrix: MananMatrix, bounds: RectF) {
        canvasMatrix = matrix
        context.apply {
            cornerPathEffect = CornerPathEffect(dp(2))
            lassoStrokeWidth = dp(4)
        }
        pathEffectAnimator.start()
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
        lassoPaint.strokeWidth = lassoStrokeWidth / canvasMatrix.getRealScaleX()

        lassoCopy.set(lassoPath)

        lassoCopy.close()

        if (isInverse && !lassoPath.isEmpty) {
            lassoCopy.addRect(
                viewBounds, Path.Direction.CW
            )
        }

        canvas.drawPath(lassoCopy, lassoPaint)
    }

    override fun resetPaint() {
        lassoPath.rewind()
        lassoCopy.rewind()
        isFirstPoint = true
        sendMessage(PainterMessage.CACHE_LAYERS)
        sendMessage(PainterMessage.INVALIDATE)
    }

    fun copy(): Bitmap? {
        doIfLayerNotNullAndPathIsNotEmpty { layer ->

            copiedBitmap =
                createBitmap(layer)

            drawBitmapOnPath(layer.bitmap)

            return copiedBitmap
        }

        return null
    }

    fun cut(): Bitmap? {
        doIfLayerNotNullAndPathIsNotEmpty { layer ->
            copiedBitmap = createBitmap(layer)

            drawBitmapOnPath(layer.bitmap)
            clip()

            return copiedBitmap
        }

        return null
    }

    private fun drawBitmapOnPath(layerBitmap: Bitmap) {
        val fillType = lassoPath.fillType

        lassoPath.fillType =
            if (isInverse) Path.FillType.INVERSE_WINDING else Path.FillType.WINDING

        canvas.apply {
            setBitmap(copiedBitmap)
            save()
            translate(-bitmapRectangle.left, -bitmapRectangle.top)
            clip(canvas, layerBitmap)
            restore()
        }

        lassoPath.fillType = fillType
    }

    private fun createBitmap(layer: PaintLayer): Bitmap {
        if (isInverse) {
            bitmapRectangle.set(
                0f,
                0f,
                layer.bitmap.width.toFloat(),
                layer.bitmap.height.toFloat()
            )
        } else {
            lassoPath.computeBounds(bitmapRectangle, true)
        }
        return Bitmap.createBitmap(
            bitmapRectangle.width().toInt(),
            bitmapRectangle.height().toInt(),
            Bitmap.Config.ARGB_8888
        )
    }

    fun clip() {
        doIfLayerNotNullAndPathIsNotEmpty { layer ->
            val layerCopy = layer.bitmap.copy(Bitmap.Config.ARGB_8888, true)

            layer.bitmap.eraseColor(Color.TRANSPARENT)

            canvas.setBitmap(layer.bitmap)

            val fillType = lassoPath.fillType

            lassoPath.fillType =
                if (isInverse) Path.FillType.WINDING else Path.FillType.INVERSE_WINDING

            clip(canvas, layerCopy)

            lassoPath.fillType = fillType

            sendMessage(PainterMessage.SAVE_HISTORY)
        }
    }

    private inline fun doIfLayerNotNullAndPathIsNotEmpty(function: (layer: PaintLayer) -> Unit) {
        if (!isFirstPoint && !lassoPath.isEmpty) {
            selectedLayer?.let { layer ->
                function(layer)
                resetPaint()
            }
        }
    }

    private fun clip(canvas: Canvas, bitmap: Bitmap) {
        canvas.apply {
            save()
            clipPath(lassoPath)
            drawBitmap(bitmap, 0f, 0f, bitmapPaint)
            restore()
        }
    }

    override fun onSizeChanged(newBounds: RectF, changeMatrix: Matrix) {
        sendMessage(PainterMessage.INVALIDATE)
    }

    override fun onLayerChanged(layer: PaintLayer?) {
        selectedLayer = layer

        layer?.let {
            viewBounds.set(0f, 0f, layer.bitmap.width.toFloat(), layer.bitmap.height.toFloat())
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

    private data class State(val pathCopy: Path, val isFirstPoint: Boolean)
}
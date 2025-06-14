package ir.baboomeh.photolib.components.paint.painters.selection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import ir.baboomeh.photolib.components.paint.painters.coloring.LassoColorPainter
import ir.baboomeh.photolib.components.paint.painters.selection.clippers.PathBitmapClipper
import ir.baboomeh.photolib.components.paint.paintview.PaintLayer
import ir.baboomeh.photolib.utils.gesture.TouchData
import ir.baboomeh.photolib.utils.matrix.MananMatrix

open class LassoTool(context:Context, var clipper: PathBitmapClipper) : LassoColorPainter(context) {

    protected val lassoCopy by lazy {
        Path()
    }

    protected val viewBounds = RectF()

    open var isInverse = false
        set(value) {
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    protected lateinit var canvasMatrix: MananMatrix


    init {
        lassoPaint.style = Paint.Style.STROKE
    }

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)
        canvasMatrix = transformationMatrix
        pathEffectAnimator.start()
        viewBounds.set(layerBounds)
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
        sendMessage(PainterMessage.INVALIDATE)
    }

    open fun copy(): Bitmap? {
        doIfLayerNotNullAndPathIsNotEmpty { layer ->
            val finalBitmap = clipper.copy()
            return finalBitmap
        }

        return null
    }

    override fun onMoveEnded(touchData: TouchData) {
        touchSmoother.setLastPoint(touchData, smoothnessBrush)
    }

    protected fun setClipper(layer: PaintLayer) {
        clipper.path = lassoPath
        clipper.bitmap = layer.bitmap
        clipper.isInverse = isInverse
    }

    open fun cut(): Bitmap? {
        doIfLayerNotNullAndPathIsNotEmpty { layer ->
            val finalBitmap = clipper.cut()
            sendMessage(PainterMessage.SAVE_HISTORY)
            return finalBitmap
        }

        return null
    }

    open fun clip() {
        doIfLayerNotNullAndPathIsNotEmpty { layer ->
            clipper.clip()
            sendMessage(PainterMessage.SAVE_HISTORY)
        }
    }

    open fun getClippingBounds(rect: RectF) {
        doIfLayerNotNullAndPathIsNotEmpty {
            clipper.getClippingBounds(rect)
        }
    }

    override fun release() {
        super.release()
        pathEffectAnimator.cancel()
    }

    protected inline fun doIfLayerNotNullAndPathIsNotEmpty(function: (layer: PaintLayer) -> Unit) {
        if (!isFirstPoint && !lassoPath.isEmpty) {
            selectedLayer?.let { layer ->
                setClipper(layer)
                function(layer)
            }
        }
    }
}
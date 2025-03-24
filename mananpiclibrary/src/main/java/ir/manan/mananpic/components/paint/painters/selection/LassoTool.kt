package ir.manan.mananpic.components.paint.painters.selection

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ComposePathEffect
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.animation.LinearInterpolator
import ir.manan.mananpic.components.paint.painters.coloring.LassoColorPainter
import ir.manan.mananpic.components.paint.painters.selection.clippers.PathBitmapClipper
import ir.manan.mananpic.components.paint.paintview.PaintLayer
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.TouchData

class LassoTool(var clipper: PathBitmapClipper) : LassoColorPainter() {

    private val lassoCopy by lazy {
        Path()
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

    var isInverse = false
        set(value) {
            field = value
            sendMessage(PainterMessage.INVALIDATE)
        }

    private lateinit var canvasMatrix: MananMatrix


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
        context.apply {
            cornerPathEffect = CornerPathEffect(dp(2))
            lassoStrokeWidth = dp(4)
        }
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

    fun copy(): Bitmap? {
        doIfLayerNotNullAndPathIsNotEmpty { layer ->
            val finalBitmap = clipper.copy()
            return finalBitmap
        }

        return null
    }

    override fun onMoveEnded(touchData: TouchData) {
        touchSmoother.setLastPoint(touchData, smoothnessBrush)
    }

    private fun setClipper(layer: PaintLayer) {
        clipper.path = lassoPath
        clipper.bitmap = layer.bitmap
        clipper.isInverse = isInverse
    }

    fun cut(): Bitmap? {
        doIfLayerNotNullAndPathIsNotEmpty { layer ->
            val finalBitmap = clipper.cut()
            sendMessage(PainterMessage.SAVE_HISTORY)
            return finalBitmap
        }

        return null
    }

    fun clip() {
        doIfLayerNotNullAndPathIsNotEmpty { layer ->
            clipper.clip()
            sendMessage(PainterMessage.SAVE_HISTORY)
        }
    }

    fun getClippingBounds(rect: RectF) {
        doIfLayerNotNullAndPathIsNotEmpty {
            clipper.getClippingBounds(rect)
        }
    }

    override fun release() {
        super.release()
        pathEffectAnimator.cancel()
    }

    private inline fun doIfLayerNotNullAndPathIsNotEmpty(function: (layer: PaintLayer) -> Unit) {
        if (!isFirstPoint && !lassoPath.isEmpty) {
            selectedLayer?.let { layer ->
                setClipper(layer)
                function(layer)
            }
        }
    }
}
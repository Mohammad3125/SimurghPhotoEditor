package ir.manan.mananpic.components.paint.painters.brushpaint

import android.content.Context
import android.graphics.*
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.components.paint.engines.DrawingEngine
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.components.paint.smoothers.BezierLineSmoother
import ir.manan.mananpic.components.paint.smoothers.LineSmoother
import ir.manan.mananpic.utils.MananMatrix

class BrushPaint(var engine: DrawingEngine) : Painter(), LineSmoother.OnDrawPoint {

    private var layerPaint = Paint().apply {
        isFilterBitmap = true
    }

    private val blendPaint = Paint().apply {
        isFilterBitmap = true
    }

    private var texturePaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        isFilterBitmap = true
    }


    var brush: Brush? = null
        set(value) {
            field = value
            if (value != null) {
                finalBrush = value
            }
        }
    private lateinit var finalBrush: Brush

    private lateinit var ccBitmap: Bitmap
    private val paintCanvas by lazy {
        Canvas()
    }
    private lateinit var alphaBlendBitmap: Bitmap

    private val alphaBlendCanvas by lazy {
        Canvas()
    }
    private var shouldBlend = false

    private var viewBounds = RectF()

    private var isLayerNull = true
    private var isBrushNull = false
    private var shouldBlendTexture = false

    private lateinit var finalCanvasToDraw: Canvas

    var lineSmoother: LineSmoother = BezierLineSmoother()
        set(value) {
            field = value
            field.onDrawPoint = this
        }

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        bounds: RectF
    ) {

        viewBounds.set(bounds)

        lineSmoother.onDrawPoint = this
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {
        if (!this::finalBrush.isInitialized || isLayerNull) {
            isBrushNull = true
            return
        }

        isBrushNull = false

        engine.onMoveBegin(initialX, initialY, finalBrush)

        lineSmoother.setFirstPoint(
            initialX,
            initialY,
            finalBrush
        )

        shouldBlend = finalBrush.alphaBlend

        shouldBlendTexture = finalBrush.texture != null

        finalCanvasToDraw =
            if (shouldBlend || shouldBlendTexture) alphaBlendCanvas else paintCanvas

        createAlphaBitmapIfNeeded()

        if (shouldBlendTexture) {
            texturePaint.shader =
                BitmapShader(
                    finalBrush.texture!!,
                    Shader.TileMode.MIRROR,
                    Shader.TileMode.MIRROR
                ).apply {
                    setLocalMatrix(finalBrush.textureTransformation)
                }
        }

    }

    private fun createAlphaBitmapIfNeeded() {
        blendPaint.alpha = if (shouldBlend) (finalBrush.opacity * 255f).toInt() else 255

        if (shouldCreateAlphaBitmap()) {
            alphaBlendBitmap = ccBitmap.copy(Bitmap.Config.ARGB_8888, true)
            alphaBlendCanvas.setBitmap(alphaBlendBitmap)
        }
    }

    private fun shouldCreateAlphaBitmap(): Boolean {
        return (shouldBlend || shouldBlendTexture) && (!this::alphaBlendBitmap.isInitialized || (alphaBlendBitmap.width != ccBitmap.width || alphaBlendBitmap.height != ccBitmap.height))
    }

    override fun onMove(ex: Float, ey: Float, dx: Float, dy: Float) {

        if (shouldDraw()) {

            engine.onMove(ex, ey, dx, dy, finalBrush)

            lineSmoother.addPoints(ex, ey, finalBrush)

        }
    }

    override fun onDrawPoint(ex: Float, ey: Float, angleDirection: Float) {
        engine.draw(
            ex,
            ey,
            angleDirection,
            finalCanvasToDraw,
            finalBrush
        )
        sendMessage(PainterMessage.INVALIDATE)
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {

        if (shouldDraw()) {

            engine.onMoveEnded(lastX, lastX, finalBrush)

            lineSmoother.setLastPoint(
                lastX,
                lastY,
                finalBrush
            )

            if (shouldBlendTexture) {
                drawTextureOnBrush(paintCanvas)
                alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
            } else if (shouldBlend) {
                paintCanvas.drawBitmap(alphaBlendBitmap, 0f, 0f, blendPaint)
                alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
            }

            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    override fun onLayerChanged(layer: PaintLayer?) {

        if (layer == null) {
            isLayerNull = true
            return
        }

        isLayerNull = false

        ccBitmap = layer.bitmap
        paintCanvas.setBitmap(ccBitmap)
    }

    override fun draw(canvas: Canvas) {
        if (isBrushNull) {
            return
        }

        if (shouldBlendTexture) {
            drawTextureOnBrush(canvas)
        } else if (shouldBlend) {
            canvas.drawBitmap(alphaBlendBitmap, 0f, 0f, blendPaint)
        }
    }

    private fun drawTextureOnBrush(canvas: Canvas) {
        canvas.saveLayer(viewBounds, layerPaint)
        canvas.drawBitmap(alphaBlendBitmap, 0f, 0f, blendPaint)
        canvas.drawRect(viewBounds, texturePaint)
        canvas.restore()
    }

    private fun shouldDraw(): Boolean =
        !isBrushNull && !isLayerNull


    override fun resetPaint() {
        ccBitmap.eraseColor(Color.TRANSPARENT)
        alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
        sendMessage(PainterMessage.INVALIDATE)
    }

    fun changeBrushTextureBlending(xfermode: PorterDuff.Mode) {
        texturePaint.xfermode = PorterDuffXfermode(xfermode)
        sendMessage(PainterMessage.INVALIDATE)
    }
}
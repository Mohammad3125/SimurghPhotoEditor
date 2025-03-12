package ir.manan.mananpic.components.paint.painters.brushpaint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.components.paint.engines.DrawingEngine
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.components.paint.painters.masking.MaskTool
import ir.manan.mananpic.components.paint.paintview.MananPaintView
import ir.manan.mananpic.components.paint.paintview.PaintLayer
import ir.manan.mananpic.components.paint.smoothers.BezierLineSmoother
import ir.manan.mananpic.components.paint.smoothers.LineSmoother
import ir.manan.mananpic.utils.MananMatrix

@MaskTool
class BrushPaint(var engine: DrawingEngine) : Painter(), LineSmoother.OnDrawPoint {

    private var layerPaint = Paint().apply {
        isFilterBitmap = true
    }

    private val blendPaint = Paint().apply {
        isFilterBitmap = true
    }

    private var texturePaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
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
        super.initialize(context, transformationMatrix, fitInsideMatrix, bounds)
        viewBounds.set(bounds)
        lineSmoother.onDrawPoint = this
    }

    override fun onMoveBegin(touchData: MananPaintView.TouchData) {
        if (!this::finalBrush.isInitialized || isLayerNull) {
            isBrushNull = true
            return
        }

        isBrushNull = false

        chooseCanvasToDraw()

        engine.onMoveBegin(touchData, finalBrush)

        lineSmoother.setFirstPoint(
            touchData,
            finalBrush
        )

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
            alphaBlendBitmap =
                Bitmap.createBitmap(ccBitmap.width, ccBitmap.height, Bitmap.Config.ARGB_8888)
            alphaBlendCanvas.setBitmap(alphaBlendBitmap)
        }
    }

    private fun shouldCreateAlphaBitmap(): Boolean {
        return (shouldBlend || shouldBlendTexture) && (!this::alphaBlendBitmap.isInitialized || (alphaBlendBitmap.width != ccBitmap.width || alphaBlendBitmap.height != ccBitmap.height))
    }

    override fun onMove(touchData: MananPaintView.TouchData) {

        if (shouldDraw()) {

            engine.onMove(touchData, finalBrush)

            lineSmoother.addPoints(touchData, finalBrush)

        }
    }

    override fun onDrawPoint(
        ex: Float,
        ey: Float,
        angleDirection: Float,
        totalDrawCount: Int,
        isLastPoint: Boolean
    ) {
        engine.draw(
            ex,
            ey,
            angleDirection,
            finalCanvasToDraw,
            finalBrush,
            totalDrawCount
        )
        sendMessage(PainterMessage.INVALIDATE)
    }

    override fun onMoveEnded(touchData: MananPaintView.TouchData) {

        chooseCanvasToDraw()

        if (shouldDraw()) {

            engine.onMoveEnded(touchData, finalBrush)

            lineSmoother.setLastPoint(
                touchData,
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

    private fun chooseCanvasToDraw() {
        shouldBlend = finalBrush.alphaBlend

        shouldBlendTexture = finalBrush.texture != null

        finalCanvasToDraw =
            if (shouldBlend || shouldBlendTexture) alphaBlendCanvas else paintCanvas

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
        if (this::ccBitmap.isInitialized) {
            ccBitmap.eraseColor(Color.TRANSPARENT)
        }

        if (this::alphaBlendBitmap.isInitialized) {
            alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
        }

        sendMessage(PainterMessage.INVALIDATE)
    }

    fun changeBrushTextureBlending(blendMode: PorterDuff.Mode) {
        texturePaint.xfermode = PorterDuffXfermode(blendMode)
        sendMessage(PainterMessage.INVALIDATE)
    }
}
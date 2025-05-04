package ir.baboomeh.photolib.components.paint.painters.brushpaint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import ir.baboomeh.photolib.components.paint.Painter
import ir.baboomeh.photolib.components.paint.engines.DrawingEngine
import ir.baboomeh.photolib.components.paint.painters.brushpaint.brushes.Brush
import ir.baboomeh.photolib.components.paint.paintview.PaintLayer
import ir.baboomeh.photolib.components.paint.smoothers.BezierLineSmoother
import ir.baboomeh.photolib.components.paint.smoothers.LineSmoother
import ir.baboomeh.photolib.properties.MaskTool
import ir.baboomeh.photolib.utils.MananMatrix
import ir.baboomeh.photolib.utils.gesture.TouchData

class BrushPainter(var engine: DrawingEngine) : Painter(), LineSmoother.OnDrawPoint, MaskTool {

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
    private var isAlphaBlending = false

    private var viewBounds = RectF()

    private var isLayerNull = true
    private var isBrushNull = false
    private var isBlendingTexture = false

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
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)
        viewBounds.set(layerBounds)
        lineSmoother.onDrawPoint = this
    }

    override fun onMoveBegin(touchData: TouchData) {
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

        if (isBlendingTexture) {
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
        blendPaint.alpha = if (isAlphaBlending) (finalBrush.opacity * 255f).toInt() else 255

        if (shouldCreateAlphaBitmap()) {
            alphaBlendBitmap =
                createBitmap(ccBitmap.width, ccBitmap.height)
            alphaBlendCanvas.setBitmap(alphaBlendBitmap)
        }
    }

    private fun shouldCreateAlphaBitmap(): Boolean {
        return (isAlphaBlending || isBlendingTexture) && (!this::alphaBlendBitmap.isInitialized || (alphaBlendBitmap.width != ccBitmap.width || alphaBlendBitmap.height != ccBitmap.height))
    }

    override fun onMove(touchData: TouchData) {

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

    override fun onMoveEnded(touchData: TouchData) {

        chooseCanvasToDraw()

        if (shouldDraw()) {

            engine.onMoveEnded(touchData, finalBrush)

            lineSmoother.setLastPoint(
                touchData,
                finalBrush
            )

            if (isBlendingTexture) {
                alphaBlendCanvas.drawRect(viewBounds, texturePaint)
                paintCanvas.drawBitmap(alphaBlendBitmap, 0f, 0f, blendPaint)
                alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
            } else if (isAlphaBlending) {
                paintCanvas.drawBitmap(alphaBlendBitmap, 0f, 0f, blendPaint)
                alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
            }

            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    private fun chooseCanvasToDraw() {
        isAlphaBlending = finalBrush.alphaBlend

        isBlendingTexture = finalBrush.texture != null

        finalCanvasToDraw =
            if (isAlphaBlending || isBlendingTexture) alphaBlendCanvas else paintCanvas
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

        if (isBlendingTexture) {
            canvas.drawTextureOnBrush()
        } else if (isAlphaBlending) {
            canvas.drawBitmap(alphaBlendBitmap, 0f, 0f, blendPaint)
        }
    }

    private fun Canvas.drawTextureOnBrush() {
        saveLayer(viewBounds, layerPaint)
        drawBitmap(alphaBlendBitmap, 0f, 0f, blendPaint)
        drawRect(viewBounds, texturePaint)
        restore()
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

    override fun setEraserMode(isEnabled: Boolean) {
        engine.setEraserMode(isEnabled)
    }
}
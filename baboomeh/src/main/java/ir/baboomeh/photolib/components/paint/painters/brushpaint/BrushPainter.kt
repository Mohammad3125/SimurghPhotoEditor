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
import ir.baboomeh.photolib.utils.gesture.TouchData
import ir.baboomeh.photolib.utils.matrix.MananMatrix

open class BrushPainter(
    var engine: DrawingEngine,
    lineSmoother: LineSmoother = BezierLineSmoother()
) : Painter(), LineSmoother.OnDrawPoint, MaskTool {

    protected var layerPaint = Paint().apply {
        isFilterBitmap = true
    }

    protected val blendPaint = Paint().apply {
        isFilterBitmap = true
    }

    protected var texturePaint = Paint().apply {
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
    protected lateinit var finalBrush: Brush

    protected lateinit var ccBitmap: Bitmap
    protected val paintCanvas by lazy {
        Canvas()
    }
    protected lateinit var alphaBlendBitmap: Bitmap

    protected val alphaBlendCanvas by lazy {
        Canvas()
    }
    protected var isAlphaBlending = false

    protected var viewBounds = RectF()

    protected var isLayerNull = true
    protected var isBrushNull = false
    protected var isBlendingTexture = false

    protected lateinit var finalCanvasToDraw: Canvas

    open var lineSmoother: LineSmoother = lineSmoother
        set(value) {
            field = value
            field.onDrawPoint = this
        }

    init {
        lineSmoother.onDrawPoint = this
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

    protected open fun createAlphaBitmapIfNeeded() {
        blendPaint.alpha = if (isAlphaBlending) (finalBrush.opacity * 255f).toInt() else 255

        if (shouldCreateAlphaBitmap()) {
            alphaBlendBitmap =
                createBitmap(ccBitmap.width, ccBitmap.height)
            alphaBlendCanvas.setBitmap(alphaBlendBitmap)
        }
    }

    protected open fun shouldCreateAlphaBitmap(): Boolean {
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

    protected open fun chooseCanvasToDraw() {
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
            drawTextureOnBrush(canvas)
        } else if (isAlphaBlending) {
            canvas.drawBitmap(alphaBlendBitmap, 0f, 0f, blendPaint)
        }
    }

    protected open fun drawTextureOnBrush(canvas : Canvas) {
        canvas.apply {
            saveLayer(viewBounds, layerPaint)
            drawBitmap(alphaBlendBitmap, 0f, 0f, blendPaint)
            drawRect(viewBounds, texturePaint)
            restore()
        }
    }
    protected open fun shouldDraw(): Boolean =
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

    open fun changeBrushTextureBlending(blendMode: PorterDuff.Mode) {
        texturePaint.xfermode = PorterDuffXfermode(blendMode)
        sendMessage(PainterMessage.INVALIDATE)
    }

    override fun setEraserMode(isEnabled: Boolean) {
        engine.setEraserMode(isEnabled)
    }
}
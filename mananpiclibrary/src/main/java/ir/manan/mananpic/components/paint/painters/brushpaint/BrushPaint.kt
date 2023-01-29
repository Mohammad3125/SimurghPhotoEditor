package ir.manan.mananpic.components.paint.painters.brushpaint

import android.graphics.*
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.components.paint.engines.DrawingEngine
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.components.paint.smoothers.BezierLineSmoother
import ir.manan.mananpic.components.paint.smoothers.LineSmoother
import ir.manan.mananpic.utils.MananMatrix

class BrushPaint(var engine: DrawingEngine) : Painter(), LineSmoother.OnDrawPoint {

    //    private var bitmapPaint = Paint().apply {
//        isFilterBitmap = true
//    }
    private var texturePaint = Paint().apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private var alphaBlendPaint = Paint().apply {
        isFilterBitmap = true
    }

    var brush: Brush? = null
    private lateinit var finalBrush: Brush

    private lateinit var ccBitmap: Bitmap
    private lateinit var paintCanvas: Canvas
    private lateinit var bufferCanvas: Canvas
    private lateinit var bufferBitmap: Bitmap
    private lateinit var alphaBlendBitmap: Bitmap
    private lateinit var alphaBlendCanvas: Canvas
    private var shouldBlendAlpha = false

    private var viewBounds = RectF()

    private val textureRect by lazy { RectF() }
    private var textureBitmap: Bitmap? = null
    private var textureShader: BitmapShader? = null
    private val textureMat by lazy { Matrix() }

    private var areCanvasesInitialized = false
    private var isLayerNull = true
    private var isBrushNull = false

    var lineSmoother: LineSmoother = BezierLineSmoother()
        set(value) {
            field = value
            field.onDrawPoint = this
        }

    override fun initialize(
        matrix: MananMatrix,
        bounds: RectF,
    ) {

        viewBounds.set(bounds)

        textureRect.set(viewBounds)

        lineSmoother.onDrawPoint = this
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {

        if (isLayerNull) {
            return
        }

        areCanvasesInitialized = (this::paintCanvas.isInitialized)

        brush?.let { b ->

            isBrushNull = false

            engine.onMoveBegin(initialX, initialY, b)

            lineSmoother.setFirstPoint(initialX, initialY, 1f - b.smoothness, b.spacedWidth)

            alphaBlendPaint.alpha = (b.opacity * 255f).toInt()

            shouldBlendAlpha = b.alphaBlend

            val ts = b.textureScale

            textureMat.setScale(ts, ts)

            if (b.texture != null) {

                textureBitmap = b.texture

                textureShader =
                    BitmapShader(textureBitmap!!, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)

                textureShader!!.setLocalMatrix(textureMat)

                texturePaint.shader = textureShader
            } else if (textureBitmap != null) {
                textureBitmap = null
                textureShader = null
                texturePaint.shader = null
            }

            finalBrush = b

            return
        }
        isBrushNull = true
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float) {

        if (shouldDraw()) {

            engine.onMove(ex, ey, finalBrush)

            lineSmoother.addPoints(ex, ey, 1f - finalBrush.smoothness, finalBrush.spacedWidth)

//        paintCanvas.save()
//
//        paintCanvas.translate(-viewBounds.left, -viewBounds.top)
//
//        paintCanvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
//            color = Color.RED
//            style = Paint.Style.STROKE
//            strokeWidth = 4f
//        })
//
//        paintCanvas.drawPoint(mid1x, mid1y, Paint().apply {
//            strokeWidth = 5f
//            color = Color.BLUE
//        })
//
//        paintCanvas.drawPoint(mid2x, mid2y, Paint().apply {
//            strokeWidth = 5f
//            color = Color.BLUE
//        })
//
//        paintCanvas.restore()

        }
    }

    override fun onDrawPoint(ex: Float, ey: Float) {
        engine.draw(ex, ey, if (shouldBlendAlpha) alphaBlendCanvas else paintCanvas, finalBrush)
        invalidate()
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {

        if (shouldDraw()) {

            engine.onMoveEnded(lastX, lastX, finalBrush)

            lineSmoother.setLastPoint(lastX, lastY, 1f - finalBrush.smoothness, finalBrush.spacedWidth)
//
//            if (textureBitmap != null) {
//
//                bufferCanvas.save()
//
//                bufferCanvas.translate(-viewBounds.left, -viewBounds.top)
//
//                bufferCanvas.drawRect(
//                    0f, 0f, textureRect.right, textureRect.bottom,
//                    texturePaint.apply {
//                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
//                    }
//                )
//
//                texturePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
//
//                bufferCanvas.restore()
//
//                paintCanvas.drawBitmap(bufferBitmap, 0f, 0f, bitmapPaint)
//
//                bufferBitmap.eraseColor(Color.TRANSPARENT)
//            }
//
            if (shouldBlendAlpha) {
                paintCanvas.drawBitmap(alphaBlendBitmap, 0f, 0f, alphaBlendPaint)
                alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
            }
//
            invalidate()
        }
    }

    override fun onLayerChanged(layer: PaintLayer?) {

        if (layer == null) {
            isLayerNull = true
            return
        }

        isLayerNull = false

        val layerBitmap = layer.bitmap


        if (!this::ccBitmap.isInitialized || (this::ccBitmap.isInitialized && (layerBitmap.width != ccBitmap.width || layerBitmap.height != ccBitmap.height))) {

            ccBitmap = layer.bitmap

            alphaBlendBitmap = ccBitmap.copy(Bitmap.Config.ARGB_8888, true)

            bufferBitmap = ccBitmap.copy(Bitmap.Config.ARGB_8888, true)

            if (this::paintCanvas.isInitialized) {
                paintCanvas.setBitmap(ccBitmap)
                bufferCanvas.setBitmap(bufferBitmap)
                alphaBlendCanvas.setBitmap(alphaBlendBitmap)
            } else {
                paintCanvas = Canvas(ccBitmap)

                bufferCanvas = Canvas(bufferBitmap)

                alphaBlendCanvas = Canvas(alphaBlendBitmap)
            }
        } else {
            ccBitmap = layer.bitmap
            paintCanvas.setBitmap(ccBitmap)
        }
    }

    override fun draw(canvas: Canvas) {

//        if(shouldBlendAlpha && !isBrushNull) {
//            texturePaint.alpha = (brush!!.opacity * 255f).toInt()
//        }
//
//        texturePaint.alpha = 255
//

//        if (textureBitmap != null) {
//
//            canvas.drawBitmap(bufferBitmap, textureRect.left, textureRect.top, bitmapPaint)
//
//            canvas.drawRect(textureRect, texturePaint)
//
//            canvas.drawBitmap(ccBitmap, textureRect.left, textureRect.top, bitmapPaint)
//        }

        if (!isBrushNull && shouldBlendAlpha) {
            canvas.drawBitmap(alphaBlendBitmap, 0f, 0f, alphaBlendPaint)
        }
    }

    private fun shouldDraw(): Boolean =
        (!isBrushNull && areCanvasesInitialized && !isLayerNull)


    override fun resetPaint() {
        ccBitmap.eraseColor(Color.TRANSPARENT)
        alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
        invalidate()
    }

    override fun onSizeChanged(newBounds: RectF, changeMatrix: Matrix) {
        viewBounds.set(newBounds)
        textureRect.set(viewBounds)
    }
}
package ir.manan.mananpic.components.paint.painters.brushpaint

import android.graphics.*
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.components.paint.engines.DrawingEngine
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.components.paint.smoothers.BezierLineSmoother
import ir.manan.mananpic.components.paint.smoothers.LineSmoother
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.gesture.GestureUtils
import kotlin.random.Random

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


    private var spacedWidth = 0f

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

    private val rotationCache = mutableListOf<Float>()
    private val scaleCache = mutableListOf<Float>()
    private val scatterXCache = mutableListOf<Float>()
    private val scatterYCache = mutableListOf<Float>()

    private var cacheCounter = 0
    private var cacheSizeInByte = 2000
    private var cachePointHolder = mutableListOf<Float>()

    var shouldUseCacheDrawing = false

    var isInEraserMode = false

    var lineSmoother: LineSmoother = BezierLineSmoother()
        set(value) {
            field = value
            field.onDrawPoint = this
        }

    private var taperSizeHolder = 0

    override fun initialize(
        matrix: MananMatrix,
        bounds: RectF,
    ) {

        viewBounds.set(bounds)

        textureRect.set(viewBounds)

        initializeCachedProperties()

        lineSmoother.onDrawPoint = this
    }

    private fun initializeCachedProperties() {
        rotationCache.clear()
        scaleCache.clear()
        scatterXCache.clear()
        scatterYCache.clear()

        repeat(cacheSizeInByte) {
            rotationCache.add(Random.nextInt(0, 100) / 100f)
            scaleCache.add(Random.nextInt(0, 100) / 100f)
            scatterXCache.add(Random.nextInt(-100, 100) / 100f)
            scatterYCache.add(Random.nextInt(-100, 100) / 100f)
        }
    }


    override fun onMoveBegin(initialX: Float, initialY: Float) {

        if (isLayerNull && !shouldUseCacheDrawing) {
            return
        }

        cacheCounter = 0

        areCanvasesInitialized = (this::paintCanvas.isInitialized)

        brush?.let { b ->

            cachePointHolder.clear()

            isBrushNull = false

            spacedWidth = (b.size * b.spacing)

            lineSmoother.setFirstPoint(initialX, initialY, 1f - b.smoothness, spacedWidth)

            taperSizeHolder = if (b.startTaperSize == 0) b.size else b.startTaperSize

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

            if (isInEraserMode) {
                if (b.brushBlending != PorterDuff.Mode.DST_OUT) {
                    b.brushBlending = PorterDuff.Mode.DST_OUT
                }
            } else {
                b.brushBlending = PorterDuff.Mode.SRC_OVER
            }

            finalBrush = b

            return
        }
        isBrushNull = true
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float) {

        if (shouldDraw()) {

            lineSmoother.addPoints(ex, ey, 1f - finalBrush.smoothness, spacedWidth)

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

        if (finalBrush.startTaperSpeed > 0 && finalBrush.startTaperSize > 0) {
            spacedWidth = (taperSizeHolder * finalBrush.spacing)
        }

        val lastSize = finalBrush.size

        if (finalBrush.startTaperSpeed > 0 && finalBrush.startTaperSize != finalBrush.size) {
            taperSizeHolder += finalBrush.startTaperSpeed
            taperSizeHolder = taperSizeHolder.coerceAtMost(finalBrush.size)
            finalBrush.size = taperSizeHolder
        }

        if (shouldUseCacheDrawing) {
            cachePointHolder.add(ex)
            cachePointHolder.add(ey)
        } else {
            engine.draw(ex, ey, if (shouldBlendAlpha) alphaBlendCanvas else paintCanvas, finalBrush)
            invalidate()
        }

        finalBrush.size = lastSize
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        lineSmoother.setLastPoint(lastX, lastY, 1f - finalBrush.smoothness, spacedWidth)
        if (!isBrushNull && !isLayerNull) {
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

    private fun drawCachedCircles(
        ex: Float,
        ey: Float,
        scatterX: Float,
        scatterY: Float,
        scale: Float,
        angle: Float,
        canvas: Canvas
    ) {
        canvas.save()

        val scatterSize = brush!!.scatter

        if (scatterSize > 0f) {
            val brushSize = brush!!.size

            val rx = (brushSize * (scatterSize * scatterX)).toInt()
            val ry = (brushSize * (scatterSize * scatterY)).toInt()

            canvas.translate(
                ex - viewBounds.left + rx,
                ey - viewBounds.top + ry
            )
        } else {
            canvas.translate(ex - viewBounds.left, ey - viewBounds.top)
        }

        val angleJitter = brush!!.angleJitter

        val fixedAngle = brush!!.angle


        if (angleJitter > 0f && fixedAngle > 0f || angleJitter > 0f && fixedAngle == 0f) {

            val rot = GestureUtils.mapTo360(
                fixedAngle + (360f * (angleJitter * angle))
            )

            canvas.rotate(rot)
        } else if (angleJitter == 0f && fixedAngle > 0f) {
            canvas.rotate(fixedAngle)
        }

        val squish = 1f - (brush!!.squish)

        val sizeJitter = brush!!.sizeJitter

        val jitterNumber = sizeJitter * scale
        val finalScale = (1f + jitterNumber)
        canvas.scale(finalScale * squish, finalScale)

        val brushOpacity = if (brush!!.opacityJitter > 0f) {
            Random.nextInt(0, (255f * brush!!.opacityJitter).toInt())
        } else if (brush!!.alphaBlend) {
            255
        } else {
            (brush!!.opacity * 255f).toInt()
        }

        brush!!.draw(canvas, brushOpacity)

        canvas.restore()
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

        if (shouldUseCacheDrawing) {

            cacheCounter = 0

            for (i in cachePointHolder.indices step 2) {

                drawCachedCircles(
                    cachePointHolder[i],
                    cachePointHolder[i + 1],
                    scatterXCache[cacheCounter],
                    scatterYCache[cacheCounter],
                    scaleCache[cacheCounter],
                    rotationCache[cacheCounter],
                    canvas
                )

                if (++cacheCounter > cacheSizeInByte - 1) {
                    cacheCounter = 0
                }
            }
        }

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

        if (!isBrushNull && shouldBlendAlpha && !shouldUseCacheDrawing) {
            canvas.drawBitmap(alphaBlendBitmap, 0f, 0f, alphaBlendPaint)
        }
    }

    private fun shouldDraw(): Boolean =
        (!isBrushNull && areCanvasesInitialized && !isLayerNull) || shouldUseCacheDrawing


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
package ir.manan.mananpic.components.paint.painters.brushpaint

import android.graphics.*
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.gesture.GestureUtils
import kotlin.math.floor
import kotlin.random.Random

class BrushPaint : Painter() {

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

    private var spacedWidth = 0f

    private val path = Path()

    private val pathMeasure = PathMeasure()

    private lateinit var ccBitmap: Bitmap

    private lateinit var paintCanvas: Canvas

    private lateinit var bufferCanvas: Canvas

    private lateinit var bufferBitmap: Bitmap

    private lateinit var alphaBlendBitmap: Bitmap

    private lateinit var alphaBlendCanvas: Canvas

    private var shouldBlendAlpha = false

    private var viewBounds = RectF()

    private var sizeVariance = 1f

    private var isBrushNull = false

    private var lastDegree = 0f

    private val textureRect by lazy {
        RectF()
    }
    private var textureBitmap: Bitmap? = null

    private var textureShader: BitmapShader? = null

    private val textureMat by lazy {
        Matrix()
    }

    private val hsvHolder = FloatArray(3)

    private var hueDegreeHolder = 0f

    private var hueFlip = true

    private var areCanvasesInitialized = false

    private var isLayerNull = true

    private val rotationCache = mutableListOf<Float>()
    private val scaleCache = mutableListOf<Float>()
    private val scatterXCache = mutableListOf<Float>()
    private val scatterYCache = mutableListOf<Float>()

    private var cacheCounter = 0

    private var cacheSizeInByte = 2000

    var shouldUseCacheDrawing = false

    private var perv1x = 0f
    private var perv1y = 0f

    private var mid1x = 0f
    private var mid1y = 0f

    private var mid2x = 0f
    private var mid2y = 0f

    private var perv2x = 0f
    private var perv2y = 0f

    private var curX = 0f
    private var curY = 0f

    private var distance = 0f

    private var extra = 0f

    private var isFirstThreeCreated = false

    private var counter = 0

    private val pointHolder = floatArrayOf(0f, 0f)

    var isInEraserMode = false

    private var cachePointHolder = mutableListOf<Float>()


    override fun initialize(
        matrix: MananMatrix,
        bounds: RectF,
    ) {

        viewBounds.set(bounds)

        textureRect.set(viewBounds)

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

        brush?.let {

            cachePointHolder.clear()

            isFirstThreeCreated = false
            counter = 0

            perv2x = initialX
            perv2y = initialY

            counter++

            isBrushNull = false

            spacedWidth = (it.size * it.spacing)

            extra = 0f

            alphaBlendPaint.alpha = (it.opacity * 255f).toInt()

            shouldBlendAlpha = it.alphaBlend

            sizeVariance = 1f

            val ts = brush!!.textureScale

            textureMat.setScale(ts, ts)

            if (brush!!.texture != null) {

                textureBitmap = brush!!.texture

                textureShader =
                    BitmapShader(textureBitmap!!, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)

                textureShader!!.setLocalMatrix(textureMat)

                texturePaint.shader = textureShader
            } else if (textureBitmap != null && brush!!.texture == null) {
                textureBitmap = null
                textureShader = null
                texturePaint.shader = null
            }

            lastDegree = 0f

            if (isInEraserMode) {
                brush!!.brushBlending = PorterDuff.Mode.DST_OUT
            } else {
                brush!!.brushBlending = PorterDuff.Mode.SRC
            }

            return
        }
        isBrushNull = true
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float) {

        if ((!isBrushNull && areCanvasesInitialized && !isLayerNull) || shouldUseCacheDrawing) {

            if (!isFirstThreeCreated) {

                when (counter) {
                    0 -> {
                        perv2x = ex
                        perv2y = ey
                    }
                    1 -> {
                        perv1x = ex
                        perv1y = ey
                    }
                    2 -> {
                        curX = ex
                        curY = ey

                        calculateQuadAndDraw()

                        counter = 0

                        isFirstThreeCreated = true

                        return
                    }
                }

                counter++
            } else {
                perv2x = perv1x
                perv2y = perv1y

                perv1x = curX
                perv1y = curY

                curX = ex
                curY = ey

                calculateQuadAndDraw()

            }
        }
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {

        distance = 0f
//        if (!isBrushNull && !isLayerNull) {
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
//            if (shouldBlendAlpha) {
//                paintCanvas.drawBitmap(alphaBlendBitmap, 0f, 0f, alphaBlendPaint)
//                alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
//            }
//
//            invalidate()
//        }
    }

    private fun calculateQuadAndDraw() {
        path.rewind()

        mid1x = (perv1x + perv2x) * 0.5f
        mid1y = (perv1y + perv2y) * 0.5f

        mid2x = (curX + perv1x) * 0.5f
        mid2y = (curY + perv1y) * 0.5f

        path.moveTo(mid1x, mid1y)

        path.quadTo(perv1x, perv1y, mid2x, mid2y)

        pathMeasure.setPath(path, false)

        distance += (pathMeasure.length)

        val total = floor(distance / spacedWidth).toInt()

        val totalToShiftBack = (distance - (spacedWidth * total)) + extra

        repeat(total) {

            pathMeasure.getPosTan(
                distance - totalToShiftBack,
                pointHolder,
                null
            )

            distance -= spacedWidth

            if (shouldUseCacheDrawing) {
                cachePointHolder.add(pointHolder[0])
                cachePointHolder.add(pointHolder[1])
            } else {
                drawCircles(
                    pointHolder[0],
                    pointHolder[1],
                    1f,
                    (it + 1) == total,
                    if (shouldBlendAlpha && textureBitmap == null) alphaBlendCanvas else if (textureBitmap != null) bufferCanvas else paintCanvas
                )
            }
        }

        extra = distance

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

    private fun drawCircles(
        touchX: Float,
        touchY: Float,
        sv: Float,
        shouldInvalidate: Boolean = true,
        canvas: Canvas = paintCanvas,
    ) {

        canvas.save()

        val scatterSize = brush!!.scatter

        if (scatterSize > 0f) {

            val r = ((brush!!.size) * scatterSize).toInt()

            if (r != 0) {
                val randomScatterX =
                    Random.nextInt(-r, r).toFloat()

                val randomScatterY =
                    Random.nextInt(-r, r).toFloat()

                canvas.translate(
                    touchX - viewBounds.left + randomScatterX,
                    touchY - viewBounds.top + randomScatterY
                )
            }
        } else {
            canvas.translate(touchX - viewBounds.left, touchY - viewBounds.top)
        }
        val angleJitter = brush!!.angleJitter

        val fixedAngle = brush!!.angle

        if (angleJitter > 0f && fixedAngle > 0f || angleJitter > 0f && fixedAngle == 0f) {
            val rot = GestureUtils.mapTo360(
                fixedAngle + Random.nextInt(
                    0,
                    (360f * angleJitter).toInt()
                ).toFloat()
            )
            canvas.rotate(
                rot
            )
        } else if (angleJitter == 0f && fixedAngle > 0f) {
            canvas.rotate(fixedAngle)
        }

        val sizeJitter = brush!!.sizeJitter

        val squish = 1f - (brush!!.squish)

        if (sizeJitter > 0f) {
            val randomJitterNumber = Random.nextInt(0, (100f * sizeJitter).toInt()) / 100f
            val finalScale = (1f + randomJitterNumber) * sv
            canvas.scale(finalScale * squish, finalScale)
        } else if (sv != 0f) {
            canvas.scale(sv * squish, sv)
        }

        brush!!.apply {
            val lastColor = color
            if (textureBitmap == null) {
                if (hueJitter > 0) {
                    Color.colorToHSV(color, hsvHolder)
                    var hue = hsvHolder[0]
                    hue += Random.nextInt(0, hueJitter)
                    hue = GestureUtils.mapTo360(hue)
                    hsvHolder[0] = hue
                    color = Color.HSVToColor(hsvHolder)
                } else if (hueFlow > 0f && hueDistance > 0f) {
                    Color.colorToHSV(color, hsvHolder)

                    var hue = hsvHolder[0]

                    if (hueFlip) {
                        hueDegreeHolder += (1f / hueFlow)
                    } else {
                        hueDegreeHolder -= (1f / hueFlow)
                    }

                    if (hueDegreeHolder >= hueDistance) {
                        hueDegreeHolder = hueDistance.toFloat()
                        hueFlip = false
                    }
                    if (hueDegreeHolder <= 0f) {
                        hueDegreeHolder = 0f
                        hueFlip = true
                    }

                    hue += hueDegreeHolder

                    hue = GestureUtils.mapTo360(hue)

                    hsvHolder[0] = hue

                    color = Color.HSVToColor(hsvHolder)
                }
            }

            val brushOpacity = if (opacityJitter > 0f) {
                Random.nextInt(0, (255f * opacityJitter).toInt())
            } else if (alphaBlend) {
                255
            } else {
                (opacity * 255f).toInt()
            }

            draw(canvas, brushOpacity)

            if (color != lastColor) {
                color = lastColor
            }
        }

        canvas.restore()

        if (shouldInvalidate) {
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

            for(i in cachePointHolder.indices step 2) {

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

        if (!isBrushNull && shouldBlendAlpha) {
            canvas.drawBitmap(alphaBlendBitmap, viewBounds.left, viewBounds.top, alphaBlendPaint)
        }
    }


    override fun resetPaint() {
        ccBitmap.eraseColor(Color.TRANSPARENT)
        alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
        invalidate()
    }

    override fun onSizeChanged(newBounds: RectF, changeMatrix: Matrix) {
        viewBounds.set(newBounds)
        textureRect.set(viewBounds)
    }

    override fun undo() {
    }
}
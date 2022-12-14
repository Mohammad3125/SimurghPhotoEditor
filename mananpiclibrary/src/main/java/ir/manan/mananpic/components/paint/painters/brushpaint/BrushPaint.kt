package ir.manan.mananpic.components.paint.painters.brushpaint

import android.content.Context
import android.graphics.*
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.gesture.GestureUtils
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class BrushPaint : Painter() {

    private var bitmapPaint = Paint().apply {
        isFilterBitmap = true
    }
    private var texturePaint = Paint()
    private var alphaBlendPaint = Paint().apply {
        isFilterBitmap = true
    }

    var brush: Brush? = null

    private var spacedWidth = 0f


    private lateinit var ccBitmap: Bitmap

    private lateinit var paintCanvas: Canvas

    private lateinit var bufferCanvas: Canvas

    private lateinit var bufferBitmap: Bitmap

    private lateinit var alphaBlendBitmap: Bitmap

    private lateinit var alphaBlendCanvas: Canvas

    private var shouldBlendAlpha = false

    private var viewBounds = RectF()

    private var lastDrawnEx = 0f
    private var lastDrawnEy = 0f

    private var sizeVariance = 1f

    private var isBrushNull = false

    private var lastVtr = 0f

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

    override fun initialize(
        context: Context,
        matrix: MananMatrix,
        bounds: RectF,
    ) {

        viewBounds.set(bounds)

        val options = BitmapFactory.Options()
        options.inScaled = false

//        textureRect.set(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
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

        if (isLayerNull) {
            return
        }

        cacheCounter = 0

        areCanvasesInitialized = (this::paintCanvas.isInitialized)

        brush?.let {

            lastDrawnEx = initialX
            lastDrawnEy = initialY

            isBrushNull = false

            spacedWidth = (it.size * it.spacing) / it.smoothness
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

            return
        }
        isBrushNull = true
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float) {

        if (!isBrushNull && areCanvasesInitialized && !isLayerNull) {

            // TODO: soft clipping:  applying texture to the paint and using the drawn image and removing background

            var diffX = (ex - lastDrawnEx)
            var diffY = (ey - lastDrawnEy)

            var vtr = sqrt(diffX.pow(2) + diffY.pow(2))

            val repI = (vtr / spacedWidth).toInt()

            val brushVariance = brush!!.sizeVariance

            if (brushVariance > 0f) {

                sizeVariance *= (1f - (1f - (vtr / lastVtr) / (repI.toFloat() / brush!!.spacing)))

                lastVtr = vtr

                if (sizeVariance > 1f) {
                    sizeVariance = 1f
                } else if (sizeVariance < brushVariance) {
                    sizeVariance = brushVariance
                }

                spacedWidth = (brush!!.size * brush!!.spacing) * sizeVariance
            }


            val smoothness = brush!!.smoothness
            val aR = 1f - smoothness

            repeat(repI) { i ->
                val diffFac = ((vtr - spacedWidth) / vtr)

                val currentX = (ex - (diffX * diffFac))
                val currentY = (ey - (diffY * diffFac))

//                val angleBetween = GestureUtils.calculateAngle(
//                    (currentX - lastDrawnEx).toDouble(),
//                    (lastDrawnEy - currentY).toDouble()
//
//                )
//
//                val diffAngle = angleBetween - lastDegree


                val finalX = currentX * smoothness + (lastDrawnEx * aR)
                val finalY = currentY * smoothness + (lastDrawnEy * aR)

//                if (abs(diffAngle) > 1f) {
//
//                    val matrix = Matrix()
//
//                    if (diffAngle > 0f) {
//                        matrix.setRotate(diffAngle - 1f, lastDrawnEx, lastDrawnEy)
//                    } else {
//                        matrix.setRotate(diffAngle + 1f, lastDrawnEx, lastDrawnEy)
//                    }
//
//                    val points = floatArrayOf(currentX, currentY)
//
//                    matrix.mapPoints(points)
//
//                    println("angle between $diffAngle")
//                    println("x bf $finalX   x af ${points[0]}     y bf $finalY   y af ${points[1]}")
//
//                    finalX = points[0]
//                    finalY = points[1]
//                }


//                lastDegree = angleBetween

                if (shouldUseCacheDrawing) {

                    drawCachedCircles(
                        finalX,
                        finalY,
                        scatterXCache[cacheCounter],
                        scatterYCache[cacheCounter],
                        scaleCache[cacheCounter],
                        rotationCache[cacheCounter],
                        if (shouldBlendAlpha && textureBitmap == null) alphaBlendCanvas else paintCanvas
                    )

                    if (++cacheCounter > cacheSizeInByte - 1) {
                        cacheCounter = 0
                    }

                } else {
                    drawCircles(
                        finalX,
                        finalY,
                        sizeVariance,
                        i == (repI - 1),
                        if (shouldBlendAlpha && textureBitmap == null) alphaBlendCanvas else paintCanvas
                    )
                }


                lastDrawnEx = finalX
                lastDrawnEy = finalY

                if (repI > 1) {
                    diffX = (ex - lastDrawnEx)
                    diffY = (ey - lastDrawnEy)

                    vtr = sqrt(diffX.pow(2) + diffY.pow(2))
                }
            }

        }
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

    override fun onMoveEnded(lastX: Float, lastY: Float) {

//        bufferCanvas.drawColor(Color.WHITE)

        if (!isBrushNull && !isLayerNull) {

//            if (textureBitmap != null) {
//
//                bufferCanvas.save()
//
//                bufferCanvas.translate(-viewBounds.left, -viewBounds.top)
//
//                bufferCanvas.drawRect(
//                    0f, 0f, textureRect.right, textureRect.bottom,
//                    texturePaint
//                )
//
//                bufferCanvas.restore()
//
//                bufferCanvas.drawBitmap(ccBitmap, 0f, 0f, bitmapPaint)
//
//                paintCanvas.drawBitmap(bufferBitmap, 0f, 0f, bitmapPaint)
//
//                bufferCanvas.drawColor(Color.TRANSPARENT)
//            }

            if (shouldBlendAlpha) {
                paintCanvas.drawBitmap(alphaBlendBitmap, 0f, 0f, alphaBlendPaint)
                alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
            }

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

        if (sizeJitter > 0f) {

            val jitterNumber = sizeJitter * scale
            val finalScale = (1f + jitterNumber)
            canvas.scale(finalScale * squish, finalScale)
        }

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


//        if(shouldBlendAlpha && !isBrushNull) {
//            texturePaint.alpha = (brush!!.opacity * 255f).toInt()
//        }
//
//        texturePaint.alpha = 255
//
//        if (textureBitmap != null) {
//            canvas.drawColor(Color.WHITE)
//            canvas.drawRect(textureRect, texturePaint)
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

    }

    override fun undo() {
    }
}
package ir.manan.mananpic.components.paint.engines

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.utils.gesture.GestureUtils
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class CanvasDrawingEngine : DrawingEngine {

    private val hsvHolder = FloatArray(3)
    private var hueDegreeHolder = 0f
    private var hueFlip = true

    var isInEraserMode = false
    private var taperSizeHolder = 0f

    private var sizeVarianceEasingStep = 0.005f
    private var targetSizeVarianceHolder = 0f
    private var sizeVarianceHolder = 0f

    private var initialX = 0f
    private var initialY = 0f

    private var lastVtr = 0f

    private var currentDiff = 0f

    private var lastSizeVariance = 1f

    private var currentSpacing = 0f
    override fun onMoveBegin(ex: Float, ey: Float, brush: Brush) {
        taperSizeHolder = brush.startTaperSize

        sizeVarianceHolder = brush.sizeVariance
        targetSizeVarianceHolder = sizeVarianceHolder

        currentSpacing = brush.spacing

        if (isInEraserMode) {
            if (brush.brushBlending != PorterDuff.Mode.DST_OUT) {
                brush.brushBlending = PorterDuff.Mode.DST_OUT
            }
        } else {
            brush.brushBlending = PorterDuff.Mode.SRC_OVER
        }

        initialX = ex
        initialY = ey


    }

    override fun onMove(ex: Float, ey: Float, dx: Float, dy: Float, brush: Brush) {
        val vtr =
            sqrt(
                (dx).pow(2) + (dy).pow(2)
            ) * 0.1f + lastVtr * 0.9f

        initialX = ex
        initialY = ey

        currentDiff = vtr - lastVtr

        lastVtr = vtr

        if (brush.sizeVariance != 1f) {
            val diff = (currentDiff * brush.sizeVarianceSpeed)

            if (brush.sizeVariance < 1f) {
                sizeVarianceHolder = brush.sizeVariance + diff
                sizeVarianceHolder = sizeVarianceHolder.coerceIn(brush.sizeVariance, 1f)
            } else {
                sizeVarianceHolder = brush.sizeVariance - diff
                sizeVarianceHolder = sizeVarianceHolder.coerceIn(1f, brush.sizeVariance)
            }
        }

        brush.spacing = currentSpacing
        brush.spacing *= targetSizeVarianceHolder

        sizeVarianceEasingStep = 0.1f * brush.spacing

        lastSizeVariance = sizeVarianceHolder
    }

    override fun onMoveEnded(ex: Float, ey: Float, brush: Brush) {
        lastVtr = 0f
        currentDiff = 0f
        brush.spacing = currentSpacing
    }

    override fun draw(ex: Float, ey: Float, canvas: Canvas, brush: Brush) {
        brush.apply {
            canvas.save()

            if (scatter > 0f) {

                val r = (size * scatter).toInt()

                if (r != 0) {
                    val randomScatterX =
                        Random.nextInt(-r, r).toFloat()

                    val randomScatterY =
                        Random.nextInt(-r, r).toFloat()

                    canvas.translate(
                        ex + randomScatterX,
                        ey + randomScatterY
                    )
                }
            } else {
                canvas.translate(ex, ey)
            }

            if (angleJitter > 0f && angle > 0f || angleJitter > 0f && angle == 0f) {
                val rot = GestureUtils.mapTo360(
                    angle + Random.nextInt(
                        0,
                        (360f * angleJitter).toInt()
                    ).toFloat()
                )
                canvas.rotate(rot)
            } else if (angleJitter == 0f && angle > 0f) {
                canvas.rotate(angle)
            }

            if (startTaperSpeed > 0 && startTaperSize != 1f && taperSizeHolder != 1f) {
                if (startTaperSize < 1f) {
                    taperSizeHolder += startTaperSpeed
                    taperSizeHolder = taperSizeHolder.coerceAtMost(1f)
                } else {
                    taperSizeHolder -= startTaperSpeed
                    taperSizeHolder = taperSizeHolder.coerceAtLeast(1f)
                }
            }

            if (targetSizeVarianceHolder < sizeVarianceHolder) {
                targetSizeVarianceHolder += sizeVarianceEasingStep
            } else {
                targetSizeVarianceHolder -= sizeVarianceEasingStep
            }

            val squish = 1f - squish

            val finalTaperSize =
                if (taperSizeHolder != 1f && startTaperSpeed > 0) taperSizeHolder else 1f

            val finalSizeVariance =
                if (sizeVariance != 1f) targetSizeVarianceHolder else 1f

            if (sizeJitter > 0f) {
                val randomJitterNumber = Random.nextInt(0, (100f * sizeJitter).toInt()) / 100f
                val finalScale = (1f + randomJitterNumber) * finalTaperSize * finalSizeVariance
                canvas.scale(finalScale * squish, finalScale)
            } else if (squish != 1f) {
                canvas.scale(
                    squish * finalTaperSize * finalSizeVariance,
                    finalTaperSize * finalSizeVariance
                )
            } else if (taperSizeHolder != 1f && startTaperSpeed > 0) {
                canvas.scale(finalTaperSize * finalSizeVariance, finalTaperSize * finalSizeVariance)
            } else if (sizeVariance != 1f) {
                canvas.scale(finalSizeVariance, finalSizeVariance)
            }

            val lastColor = color

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

            canvas.restore()
        }
    }
}
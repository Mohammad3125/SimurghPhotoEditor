package ir.manan.mananpic.components.paint.engines

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import ir.manan.mananpic.components.paint.painters.brushpaint.brushes.Brush
import ir.manan.mananpic.utils.gesture.GestureUtils
import kotlin.math.abs
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


    private var opacityVarianceEasingStep = 0.1f
    private var targetOpacityVariance = 0f
    private var opacityVarianceHolder = 0

    private var lastVtrSizeVariance = 0f
    private var lastVtrOpacityVariance = 0f

    private var lastSizeVariance = 1f

    private var currentSpacing = 0f
    override fun onMoveBegin(ex: Float, ey: Float, brush: Brush) {
        taperSizeHolder = brush.startTaperSize

        sizeVarianceHolder = brush.sizeVariance
        targetSizeVarianceHolder = sizeVarianceHolder

        currentSpacing = brush.spacing

        targetOpacityVariance =
            abs(brush.opacityVariance * 255f)

        if (isInEraserMode) {
            if (brush.brushBlending != PorterDuff.Mode.DST_OUT) {
                brush.brushBlending = PorterDuff.Mode.DST_OUT
            }
        } else {
            brush.brushBlending = PorterDuff.Mode.SRC_OVER
        }

    }

    override fun onMove(ex: Float, ey: Float, dx: Float, dy: Float, brush: Brush) {

        calculateSizeVariance(dx, dy, brush)

        calculateOpacityVariance(dx, dy, brush)


    }

    override fun onMoveEnded(ex: Float, ey: Float, brush: Brush) {
        lastVtrSizeVariance = 0f
        brush.spacing = currentSpacing
    }


    private fun calculateSizeVariance(dx: Float, dy: Float, brush: Brush) {
        if (brush.sizeVariance == 1f) {
            return
        }

        val speed = calculateSpeed(dx, dy, lastSizeVariance, brush.sizeVarianceSensitivity)

        lastVtrSizeVariance = speed

        val diff = speed * 0.1f

        if (brush.sizeVariance < 1f) {
            sizeVarianceHolder = brush.sizeVariance + diff
            sizeVarianceHolder = sizeVarianceHolder.coerceIn(brush.sizeVariance, 1f)
        } else {
            sizeVarianceHolder = brush.sizeVariance - diff
            sizeVarianceHolder = sizeVarianceHolder.coerceIn(1f, brush.sizeVariance)
        }

        lastSizeVariance = sizeVarianceHolder

        sizeVarianceEasingStep = brush.sizeVarianceEasing * brush.spacing

    }

    private fun calculateSpeed(dx: Float, dy: Float, lastSpeed: Float, sensitivity: Float): Float {
        val sensitivityInverse = 1f - sensitivity

        val vtr =
            sqrt(
                (dx).pow(2) + (dy).pow(2)
            ) * sensitivity + lastSpeed * sensitivityInverse

        return abs(vtr - lastSpeed)
    }

    private fun calculateOpacityVariance(dx: Float, dy: Float, brush: Brush) {
        if (brush.opacityVariance == 0f) {
            return
        }

        val speed = calculateSpeed(dx, dy, lastVtrOpacityVariance, brush.opacityVarianceSpeed)

        val base = abs(brush.opacityVariance * 255f).toInt()

        if (brush.opacityVariance > 0f) {
            opacityVarianceHolder = (base + (speed * brush.opacityVarianceSpeed)).toInt()
            opacityVarianceHolder = opacityVarianceHolder.coerceIn(base, 255)
        } else {
            opacityVarianceHolder = (base - (speed * brush.opacityVarianceSpeed)).toInt()
            opacityVarianceHolder = opacityVarianceHolder.coerceIn(0, base)
        }

        opacityVarianceEasingStep = brush.opacityVarianceEasing / brush.spacing

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

            if (targetOpacityVariance < opacityVarianceHolder) {
                targetOpacityVariance += opacityVarianceEasingStep
            } else {
                targetOpacityVariance -= opacityVarianceEasingStep
            }

            val brushOpacity = if (opacityVariance != 0f) {
                targetOpacityVariance.toInt()
            } else if (opacityJitter > 0f) {
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
package ir.baboomeh.photolib.components.paint.engines

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import ir.baboomeh.photolib.components.paint.painters.brushpaint.brushes.Brush
import ir.baboomeh.photolib.utils.MathUtils
import ir.baboomeh.photolib.utils.gesture.GestureUtils
import ir.baboomeh.photolib.utils.gesture.TouchData
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

    private var lastSizePressure = 0f
    private var currentSizePressure = 0f

    private var lastOpacityPressure = 0f
    private var currentOpacityPressure = 0f

    private var currentSpacing = 0f
    override fun onMoveBegin(touchData: TouchData, brush: Brush) {
        taperSizeHolder = brush.startTaperSize

        sizeVarianceHolder = brush.sizeVariance
        targetSizeVarianceHolder = sizeVarianceHolder

        lastSizePressure =
            mapPressure(touchData.pressure, brush.minimumPressureSize, brush.maximumPressureSize)

        lastOpacityPressure = mapPressure(
            touchData.pressure, brush.minimumPressureOpacity, brush.maximumPressureOpacity
        )

        currentSizePressure = lastSizePressure

        currentOpacityPressure = lastOpacityPressure

        currentSpacing = brush.spacing

        targetOpacityVariance = abs(brush.opacityVariance * 255f)

        if (isInEraserMode) {
            if (brush.brushBlending != PorterDuff.Mode.DST_OUT) {
                brush.brushBlending = PorterDuff.Mode.DST_OUT
            }
        } else {
            brush.brushBlending = PorterDuff.Mode.SRC_OVER
        }

    }

    override fun onMove(touchData: TouchData, brush: Brush) {

        calculateSizeVariance(touchData, brush)

        calculateOpacityVariance(touchData, brush)

        currentSizePressure = calculatePressureSensitivity(
            touchData.pressure,
            brush.isSizePressureSensitive,
            brush.sizePressureSensitivity,
            brush.minimumPressureSize,
            brush.maximumPressureSize,
            lastSizePressure
        )

        currentOpacityPressure = calculatePressureSensitivity(
            touchData.pressure,
            brush.isOpacityPressureSensitive,
            brush.opacityPressureSensitivity,
            brush.minimumPressureOpacity,
            brush.maximumPressureOpacity,
            lastOpacityPressure
        )
    }


    override fun onMoveEnded(touchData: TouchData, brush: Brush) {
        currentSizePressure =
            mapPressure(touchData.pressure, brush.minimumPressureSize, brush.maximumPressureSize)

        lastVtrSizeVariance = 0f
        hueDegreeHolder = 0f
        brush.spacing = currentSpacing
    }

    private fun calculatePressureSensitivity(
        pressure: Float,
        isSensitive: Boolean,
        sensitivity: Float,
        minimumPressure: Float,
        maximumPressure: Float,
        lastPressure: Float
    ): Float {
        return if (!isSensitive || pressure == 1f) {
            1f
        } else {
            val inversePressureSensitivity = 1f - sensitivity
            mapPressure(
                pressure, minimumPressure, maximumPressure
            ) * sensitivity + (lastPressure * inversePressureSensitivity)
        }
    }


    private fun mapPressure(
        pressure: Float, minimumPressure: Float, maximumPressure: Float
    ): Float = MathUtils.convertFloatRange(
        0f, 1f, minimumPressure, maximumPressure, pressure
    )


    private fun calculateSizeVariance(touchData: TouchData, brush: Brush) {
        if (brush.sizeVariance == 1f) {
            return
        }

        val speed = calculateSpeed(
            touchData.dx, touchData.dy, lastSizeVariance, brush.sizeVarianceSensitivity
        )

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

        val vtr = sqrt(
            (dx).pow(2) + (dy).pow(2)
        ) * sensitivity + lastSpeed * sensitivityInverse

        return abs(vtr - lastSpeed)
    }

    private fun calculateOpacityVariance(touchData: TouchData, brush: Brush) {
        if (brush.opacityVariance == 0f) {
            return
        }

        val speed = calculateSpeed(
            touchData.dx, touchData.dy, lastVtrOpacityVariance, brush.opacityVarianceSpeed
        )

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

    override fun draw(
        ex: Float, ey: Float, directionalAngle: Float, canvas: Canvas, brush: Brush, drawCount: Int
    ) {
        brush.apply {
            canvas.save()

            calculateAndTranslate(canvas, ex, ey)

            calculateAndRotate(directionalAngle, canvas)

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

            val finalTaperSize =
                if (taperSizeHolder != 1f && startTaperSpeed > 0) taperSizeHolder else 1f

            val finalSizeVariance = if (sizeVariance != 1f) targetSizeVarianceHolder else 1f

            if (isSizePressureSensitive) {
                lastSizePressure += ((currentSizePressure - lastSizePressure) / drawCount)
            }

            if (isOpacityPressureSensitive) {
                lastOpacityPressure += ((currentOpacityPressure - lastOpacityPressure) / drawCount)
            }

            calculateAndScale(finalTaperSize, finalSizeVariance, canvas, 1f - squish)

            val lastColor = color

            calculateColorHue()

            if (targetOpacityVariance < opacityVarianceHolder) {
                targetOpacityVariance += opacityVarianceEasingStep
            } else {
                targetOpacityVariance -= opacityVarianceEasingStep
            }

            draw(canvas, calculateBrushOpacity())

            if (color != lastColor) {
                color = lastColor
            }

            canvas.restore()
        }
    }

    private fun Brush.calculateAndRotate(
        directionalAngle: Float,
        canvas: Canvas
    ) {
        if (angleJitter > 0f && (angle > 0f || directionalAngle > 0f) || angleJitter > 0f && angle == 0f) {
            val rot = GestureUtils.mapTo360(
                angle + Random.nextInt(
                    0,
                    (360f * angleJitter).toInt()
                ).toFloat() + directionalAngle
            )
            canvas.rotate(rot)
        } else if (angleJitter == 0f && (angle > 0f || directionalAngle > 0f)) {
            canvas.rotate(angle + directionalAngle)
        }
    }

    private fun Brush.calculateAndTranslate(
        canvas: Canvas,
        ex: Float,
        ey: Float
    ) {
        when {
            scatter > 0f -> {

                val r = (size * scatter).toInt()

                if (r != 0) {
                    val randomScatterX = Random.nextInt(-r, r).toFloat()

                    val randomScatterY = Random.nextInt(-r, r).toFloat()

                    canvas.translate(
                        ex + randomScatterX, ey + randomScatterY
                    )
                }
            }

            else -> {
                canvas.translate(ex, ey)
            }
        }
    }

    private fun Brush.calculateColorHue() {
        when {
            hueJitter > 0 -> {
                Color.colorToHSV(color, hsvHolder)
                hsvHolder[0] += Random.nextInt(0, hueJitter).toFloat()
                hsvHolder[0] = GestureUtils.mapTo360(hsvHolder[0])
                color = Color.HSVToColor(hsvHolder)
            }

            hueFlow > 0f && hueDistance > 0f -> {
                Color.colorToHSV(color, hsvHolder)

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

                hsvHolder[0] += hueDegreeHolder

                hsvHolder[0] = GestureUtils.mapTo360(hsvHolder[0])

                color = Color.HSVToColor(hsvHolder)
            }
        }
    }

    private fun Brush.calculateAndScale(
        finalTaperSize: Float,
        finalSizeVariance: Float,
        canvas: Canvas,
        squish: Float
    ) {
        when {
            sizeJitter > 0f || squish != 1f -> {
                val randomJitterNumber = if (sizeJitter == 0f) {
                    0f
                } else {
                    Random.nextInt(
                        0,
                        (100f * sizeJitter).toInt()
                    ) / 100f
                }

                val finalScale =
                    (1f + randomJitterNumber) * finalTaperSize * finalSizeVariance * lastSizePressure

                canvas.scale(finalScale * squish, finalScale)
            }

            isSizePressureSensitive -> {
                canvas.scale(lastSizePressure, lastSizePressure)
            }

            taperSizeHolder != 1f && startTaperSpeed > 0 -> {
                val s = finalTaperSize * finalSizeVariance
                canvas.scale(s, s)
            }

            sizeVariance != 1f -> {
                canvas.scale(finalSizeVariance, finalSizeVariance)
            }

        }
    }


    private fun Brush.calculateBrushOpacity() = when {
        isOpacityPressureSensitive -> {
            ((opacity * 255f) * lastOpacityPressure).toInt()
        }

        opacityVariance != 0f -> {
            targetOpacityVariance.toInt()
        }

        opacityJitter > 0f -> {
            Random.nextInt(0, (255f * opacityJitter).toInt())
        }

        alphaBlend -> {
            255
        }

        else -> {
            (opacity * 255f).toInt()
        }
    }

    override fun isEraserModeEnabled(): Boolean {
        return isInEraserMode
    }

    override fun setEraserMode(isEnabled: Boolean) {
        isInEraserMode = isEnabled
    }
}
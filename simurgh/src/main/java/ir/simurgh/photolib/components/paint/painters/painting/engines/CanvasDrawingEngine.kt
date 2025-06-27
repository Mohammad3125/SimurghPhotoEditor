package ir.simurgh.photolib.components.paint.painters.painting.engines

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.annotation.ColorInt
import androidx.core.graphics.withSave
import ir.simurgh.photolib.components.paint.painters.painting.brushes.Brush
import ir.simurgh.photolib.utils.MathUtils
import ir.simurgh.photolib.utils.gesture.GestureUtils
import ir.simurgh.photolib.utils.gesture.TouchData
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Concrete implementation of DrawingEngine that handles brush stroke rendering with various effects.
 * Manages pressure sensitivity, opacity, rotation, size variation, and other brush effects.
 */
open class CanvasDrawingEngine : DrawingEngine {

    /** Temporary HSV color array for color transformations */
    protected val hsvHolder = FloatArray(3)
    /** Current hue shift value used for color flow effects */
    protected var hueDegreeHolder = 0f
    /** Controls direction of hue flow animation */
    protected var hueFlip = true

    /** Controls whether eraser mode is active (uses DST_OUT blending) */
    open var isInEraserMode = false
    /** Tracks the current taper size during stroke rendering */
    protected var taperSizeHolder = 0f

    /** Step size for interpolating size variance */
    protected var sizeVarianceEasingStep = 0.005f
    /** Target size variance value */
    protected var targetSizeVarianceHolder = 0f
    /** Current size variance value */
    protected var sizeVarianceHolder = 0f

    /** Step size for interpolating opacity variance */
    protected var opacityVarianceEasingStep = 0.1f
    /** Target opacity variance value */
    protected var targetOpacityVariance = 0f
    /** Current opacity variance value */
    protected var opacityVarianceHolder = 0

    /** Previous speed measurement for motion smoothing */
    protected var lastVtrSizeVariance = 0f
    protected var lastVtrOpacityVariance = 0f

    /** Previous size variance state for interpolation */
    protected var lastSizeVariance = 1f

    /** Pressure sensitivity tracking variables */
    protected var lastSizePressure = 0f
    protected var currentSizePressure = 0f

    protected var lastOpacityPressure = 0f
    protected var currentOpacityPressure = 0f

    /** Current spacing value for brush stamps */
    protected var currentSpacing = 0f

    /**
     * Initializes brush parameters at the start of a touch gesture.
     *
     * @param touchData Touch event data containing pressure information
     * @param brush The brush being used for rendering
     */
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

        // Set appropriate blending mode for eraser/normal operation
        if (isInEraserMode) {
            if (brush.brushBlending != PorterDuff.Mode.DST_OUT) {
                brush.brushBlending = PorterDuff.Mode.DST_OUT
            }
        } else {
            brush.brushBlending = PorterDuff.Mode.SRC_OVER
        }
    }

    /**
     * Updates brush parameters during ongoing touch movement.
     *
     * @param touchData Current touch event data
     * @param brush The brush being used for rendering
     */
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

    /**
     * Finalizes touch gesture parameters at the end of a stroke.
     *
     * @param touchData Final touch event data
     * @param brush The brush being used for rendering
     */
    override fun onMoveEnded(touchData: TouchData, brush: Brush) {
        currentSizePressure =
            mapPressure(touchData.pressure, brush.minimumPressureSize, brush.maximumPressureSize)

        lastVtrSizeVariance = 0f
        hueDegreeHolder = 0f
        brush.spacing = currentSpacing
    }

    /**
     * Calculates pressure sensitivity based on current pressure and sensitivity settings.
     *
     * @param pressure Current pressure value from input device
     * @param isSensitive Whether pressure sensitivity is enabled
     * @param sensitivity Sensitivity factor (0.0-1.0)
     * @param minimum Pressure minimum value
     * @param maximum Pressure maximum value
     * @param lastPressure Previous pressure value for interpolation
     * @return Calculated pressure-sensitive value
     */
    protected open fun calculatePressureSensitivity(
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

    /**
     * Maps pressure value from [0.0, 1.0] range to custom [min, max] range.
     *
     * @param pressure Input pressure (0.0-1.0)
     * @param min Minimum output value
     * @param max Maximum output value
     * @return Mapped pressure value
     */
    protected open fun mapPressure(
        pressure: Float, minimumPressure: Float, maximumPressure: Float
    ): Float = MathUtils.convertFloatRange(
        0f, 1f, minimumPressure, maximumPressure, pressure
    )

    /**
     * Updates size variance based on stroke speed and brush settings.
     *
     * @param touchData Touch event data with motion vectors
     * @param brush The brush being used for rendering
     */
    protected open fun calculateSizeVariance(touchData: TouchData, brush: Brush) {
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

    /**
     * Calculates motion speed for variance and sensitivity calculations.
     *
     * @param dx X axis delta
     * @param dy Y axis delta
     * @param lastSpeed Previous speed value for smoothing
     * @param sensitivity Sensitivity factor affecting speed calculation
     * @return Calculated speed value
     */
    protected open fun calculateSpeed(
        dx: Float,
        dy: Float,
        lastSpeed: Float,
        sensitivity: Float
    ): Float {
        val sensitivityInverse = 1f - sensitivity

        val vtr = sqrt(
            (dx).pow(2) + (dy).pow(2)
        ) * sensitivity + lastSpeed * sensitivityInverse

        return abs(vtr - lastSpeed)
    }

    /**
     * Updates opacity variance based on stroke speed and brush settings.
     *
     * @param touchData Touch event data with motion vectors
     * @param brush The brush being used for rendering
     */
    protected open fun calculateOpacityVariance(touchData: TouchData, brush: Brush) {
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

    /**
     * Draws a single brush stamp at the specified location.
     *
     * Applies all brush effects including:
     * - Position translation with scatter
     * - Rotation with jitter
     * - Size scaling with pressure and variant effects
     * - Color modification with hue effects
     * - Opacity calculations
     *
     * @param ex Exact X coordinate for drawing
     * @param ey Exact Y coordinate for drawing
     * @param directionalAngle Rotational angle to apply
     * @param canvas Canvas to draw on
     * @param brush The brush to draw
     * @param drawCount Number of draw calls in this stroke
     */
    override fun draw(
        ex: Float, ey: Float, directionalAngle: Float, canvas: Canvas, brush: Brush, drawCount: Int
    ) {
        brush.apply {
            canvas.withSave {
                // Apply position with scatter effect
                translateCanvasByBrush(brush, canvas, ex, ey)

                // Apply rotation with angle jitter
                rotateCanvasByBrush(brush, canvas, directionalAngle)

                // Apply tapper size interpolation
                if (startTaperSpeed > 0 && startTaperSize != 1f && taperSizeHolder != 1f) {
                    if (startTaperSize < 1f) {
                        taperSizeHolder += startTaperSpeed
                        taperSizeHolder = taperSizeHolder.coerceAtMost(1f)
                    } else {
                        taperSizeHolder -= startTaperSpeed
                        taperSizeHolder = taperSizeHolder.coerceAtLeast(1f)
                    }
                }

                // Interpolate size variance
                if (targetSizeVarianceHolder < sizeVarianceHolder) {
                    targetSizeVarianceHolder += sizeVarianceEasingStep
                } else {
                    targetSizeVarianceHolder -= sizeVarianceEasingStep
                }

                val finalTaperSize =
                    if (taperSizeHolder != 1f && startTaperSpeed > 0) taperSizeHolder else 1f

                val finalSizeVariance = if (sizeVariance != 1f) targetSizeVarianceHolder else 1f

                // Update pressure-interpolated values
                if (isSizePressureSensitive) {
                    lastSizePressure += ((currentSizePressure - lastSizePressure) / drawCount)
                }

                if (isOpacityPressureSensitive) {
                    lastOpacityPressure += ((currentOpacityPressure - lastOpacityPressure) / drawCount)
                }

                // Apply all scale transformations and squish factor
                scaleCanvasByBrush(brush, finalTaperSize, finalSizeVariance, canvas, 1f - squish)

                val lastColor = color

                // Apply hue effects
                color = calculateColorHue(brush)

                // Interpolate opacity variance
                if (targetOpacityVariance < opacityVarianceHolder) {
                    targetOpacityVariance += opacityVarianceEasingStep
                } else {
                    targetOpacityVariance -= opacityVarianceEasingStep
                }

                // Finalize and draw the brush stamp
                draw(this, calculateBrushOpacity(brush))

                if (color != lastColor) {
                    color = lastColor
                }
            }
        }
    }

    /**
     * Rotates the canvas according to brush rotation settings.
     *
     * Adds random angle jitter if enabled, and can rotate based on stroke direction.
     *
     * @param brush The brush providing rotation parameters
     * @param canvas The canvas to rotate
     * @param directionalAngle Directional angle to apply when auto-rotating
     */
    protected open fun rotateCanvasByBrush(
        brush: Brush,
        canvas: Canvas,
        directionalAngle: Float
    ) {
        brush.run {
            if (angleJitter > 0f && (angle > 0f || directionalAngle > 0f) || angleJitter > 0f && angle == 0f) {
                canvas.rotate(
                    GestureUtils.mapTo360(
                        angle + Random.nextInt(
                            0,
                            (360f * angleJitter).toInt()
                        ).toFloat() + directionalAngle
                    )
                )
            } else if (angleJitter == 0f && (angle > 0f || directionalAngle > 0f)) {
                canvas.rotate(angle + directionalAngle)
            }
        }
    }

    /**
     * Translates the canvas to the brush position with optional scatter effects.
     *
     * When scatter is enabled, adds random position offsets to create texture.
     *
     * @param brush The brush providing position parameters
     * @param canvas The canvas to translate
     * @param ex Exact X position
     * @param ey Exact Y position
     */
    protected open fun translateCanvasByBrush(
        brush: Brush,
        canvas: Canvas,
        ex: Float,
        ey: Float
    ) {
        when {
            brush.scatter > 0f -> {
                val r = (brush.size * brush.scatter).toInt()
                if (r != 0) {
                    val randomScatterX = Random.nextInt(-r, r).toFloat()
                    val randomScatterY = Random.nextInt(-r, r).toFloat()
                    canvas.translate(ex + randomScatterX, ey + randomScatterY)
                }
            }
            else -> {
                canvas.translate(ex, ey)
            }
        }
    }

    /**
     * Calculates a new color with hue modifications based on brush settings.
     *
     * Handles random hue jitter, hue flow animation, and other color effects.
     *
     * @param brush The brush providing color parameters
     * @return Modified color with applied effects
     */
    @ColorInt
    protected open fun calculateColorHue(brush: Brush): Int =
        brush.run {
            when {
                hueJitter > 0 -> {
                    Color.colorToHSV(color, hsvHolder)
                    hsvHolder[0] += Random.nextInt(0, hueJitter).toFloat()
                    hsvHolder[0] = GestureUtils.mapTo360(hsvHolder[0])
                    Color.HSVToColor(hsvHolder)
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

                    Color.HSVToColor(hsvHolder)
                }

                else -> {
                    color
                }
            }
        }

    /**
     * Scales the canvas according to brush size parameters.
     *
     * Handles size jitter, pressure sensitivity, taper effects and variance scaling.
     *
     * @param brush The brush providing size parameters
     * @param finalTaperSize Taper scaling factor
     * @param finalSizeVariance Variance scaling factor
     * @param canvas The canvas to scale
     * @param squish Vertical compression factor
     */
    protected open fun scaleCanvasByBrush(
        brush: Brush,
        finalTaperSize: Float,
        finalSizeVariance: Float,
        canvas: Canvas,
        squish: Float
    ) {
        brush.apply {
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
    }

    /**
     * Calculates the final opacity value with various effects applied.
     *
     * @param brush The brush providing opacity parameters
     * @return Calculated opacity value (0-255)
     */
    protected open fun calculateBrushOpacity(brush: Brush): Int {
        return brush.run {
            when {
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
        }
    }

    /**
     * Returns whether eraser mode is currently active.
     *
     * @return True if in eraser mode, false otherwise
     */
    override fun isEraserModeEnabled(): Boolean {
        return isInEraserMode
    }

    /**
     * Sets the eraser mode state and updates blending mode accordingly.
     *
     * @param isEnabled True to enable eraser mode, false to disable
     */
    override fun setEraserMode(isEnabled: Boolean) {
        isInEraserMode = isEnabled
    }
}

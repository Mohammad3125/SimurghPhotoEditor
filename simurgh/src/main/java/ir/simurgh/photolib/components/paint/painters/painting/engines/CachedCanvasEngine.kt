package ir.simurgh.photolib.components.paint.painters.painting.engines

import android.graphics.Canvas
import androidx.core.graphics.withSave
import ir.simurgh.photolib.components.paint.painters.painting.brushes.Brush
import ir.simurgh.photolib.utils.gesture.GestureUtils
import ir.simurgh.photolib.utils.gesture.TouchData
import kotlin.random.Random

/**
 * Performance-optimized drawing engine that uses pre-calculated random values for consistent brush effects.
 *
 * This engine is designed for scenarios where performance is critical, such as:
 * - Brush preview generation
 * - High-speed stroke rendering
 * - Consistent brush appearance across multiple draws
 *
 * **Key Differences from CanvasDrawingEngine:**
 * - Uses cached random values instead of generating new ones each draw
 * - Simpler effect calculations for better performance
 * - No pressure sensitivity or variance effects
 * - No eraser mode support (always returns false)
 * - Minimal state tracking between draws
 *
 * **Cached Values:**
 * The engine expects external components (like BrushPreview) to provide
 * pre-calculated random values through the cached properties:
 * - [cachedScatterX], [cachedScatterY]: For scatter effects
 * - [cachedScale]: For size jitter effects
 * - [cachedRotation]: For angle jitter effects
 *
 * This approach ensures consistent appearance when the same brush stroke
 * is rendered multiple times, which is important for preview generation
 * and performance-critical scenarios.
 *
 * Example usage:
 * ```kotlin
 * val engine = CachedCanvasEngine()
 *
 * // Set cached values (typically from a pre-generated array)
 * engine.cachedScatterX = preGeneratedScatterX[index]
 * engine.cachedScatterY = preGeneratedScatterY[index]
 * engine.cachedScale = preGeneratedScale[index]
 * engine.cachedRotation = preGeneratedRotation[index]
 * ```
 *
 * @see CanvasDrawingEngine for full-featured real-time rendering
 * @see ir.simurgh.photolib.components.paint.painters.painting.BrushPreview for usage example
 */
open class CachedCanvasEngine : DrawingEngine {

    /**
     * Cached X scatter value.
     * Used for consistent horizontal scatter effects.
     */
    open var cachedScatterX = 0f

    /**
     * Cached Y scatter value.
     * Used for consistent vertical scatter effects.
     */
    open var cachedScatterY = 0f

    /**
     * Cached scale jitter value.
     * Used for consistent size variation effects.
     */
    open var cachedScale = 0f

    /**
     * Cached rotation value.
     * Used for consistent angle jitter effects.
     */
    open var cachedRotation = 0f


    open var cachedColor : Int? = null

    /** Current taper size value for progressive size changes */
    private var taperSizeHolder = 0f

    override fun onMoveBegin(touchData: TouchData, brush: Brush) {
        // Initialize taper effect at stroke start
        taperSizeHolder = brush.startTaperSize
    }

    override fun onMove(touchData: TouchData, brush: Brush) {
        // No dynamic calculations needed - using cached values only
    }

    override fun onMoveEnded(touchData: TouchData, brush: Brush) {
        // No cleanup needed for cached approach
    }

    override fun draw(
        ex: Float,
        ey: Float,
        directionalAngle: Float,
        canvas: Canvas,
        brush: Brush,
        drawCount: Int
    ) {
        brush.apply {
            canvas.withSave {
                // Apply scatter effect using cached values
                val scatterSize = scatter

                if (scatterSize > 0f) {
                    val brushSize = size

                    // Calculate scatter offsets using cached random values
                    val rx = (brushSize * (scatterSize * cachedScatterX)).toInt()
                    val ry = (brushSize * (scatterSize * cachedScatterY)).toInt()

                    translate(ex + rx, ey + ry)
                } else {
                    // No scatter: use exact position
                    translate(ex, ey)
                }

                // Apply rotation effects using cached values
                val angleJitter = angleJitter
                val fixedAngle = angle

                if (angleJitter > 0f && (fixedAngle > 0f || directionalAngle > 0f) || angleJitter > 0f && fixedAngle == 0f) {
                    // Apply fixed angle + cached jitter + directional angle
                    val rot = GestureUtils.mapTo360(
                        fixedAngle + (360f * (angleJitter * cachedRotation)) + directionalAngle
                    )

                    rotate(rot)
                } else if (angleJitter == 0f && (fixedAngle > 0f || directionalAngle > 0f)) {
                    // Apply fixed angle + directional angle (no jitter)
                    rotate(fixedAngle + directionalAngle)
                }

                // Apply taper effect (gradual size change)
                if (startTaperSpeed > 0 && startTaperSize != 1f && taperSizeHolder != 1f) {
                    if (startTaperSize < 1f) {
                        // Taper up: gradually increase size
                        taperSizeHolder += startTaperSpeed
                        taperSizeHolder = taperSizeHolder.coerceAtMost(1f)
                    } else {
                        // Taper down: gradually decrease size
                        taperSizeHolder -= startTaperSpeed
                        taperSizeHolder = taperSizeHolder.coerceAtLeast(1f)
                    }
                }

                // Apply size effects using cached values
                val squish = 1f - squish // Convert squish to scaling factor
                val sizeJitter = sizeJitter
                val jitterNumber = sizeJitter * cachedScale // Use cached scale value

                val finalTaperSize =
                    if (taperSizeHolder != 1f && startTaperSpeed > 0) taperSizeHolder else 1f

                val finalScale = (1f + jitterNumber) * finalTaperSize

                // Apply scaling with squish effect
                scale(finalScale * squish, finalScale)

                // Calculate brush opacity (simplified compared to CanvasDrawingEngine)
                val brushOpacity = if (opacityJitter > 0f) {
                    // Use random opacity jitter (note: this still uses Random for simplicity)
                    Random.nextInt(0, (255f * opacityJitter).toInt())
                } else {
                    // Standard opacity
                    (opacity * 255).toInt()
                }

                val lastColor = color

                cachedColor?.let {
                    color = it
                }

                // Draw the brush stamp
                draw(this, brushOpacity)

                if(lastColor != color) {
                    color = lastColor
                }

            }
        }
    }

    override fun isEraserModeEnabled(): Boolean {
        // Cached engine doesn't support eraser mode for performance reasons
        return false
    }

    override fun setEraserMode(isEnabled: Boolean) {
        // Cached engine doesn't support eraser mode for performance reasons
        // This is a no-op to maintain interface compatibility
    }
}

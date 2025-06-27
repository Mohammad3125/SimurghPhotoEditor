package ir.simurgh.photolib.components.paint.smoothers

import android.graphics.Path
import android.graphics.PathMeasure
import ir.simurgh.photolib.components.paint.painters.painting.brushes.Brush
import ir.simurgh.photolib.utils.gesture.GestureUtils
import ir.simurgh.photolib.utils.gesture.TouchData
import kotlin.math.atan2
import kotlin.math.floor

/**
 * A line smoother implementation that uses Bezier curves to create smooth drawing paths.
 * This smoother processes touch input points and generates quadratic Bezier curves
 * to create fluid and natural-looking brush strokes with proper spacing and rotation.
 *
 * This class is inspired by [krzysztofzablocki's LineDrawing](https://github.com/krzysztofzablocki/LineDrawing?tab=readme-ov-file) project.
 */
class BezierLineSmoother : LineSmoother() {

    // Previous point coordinates for curve calculation.
    private var perv1x = 0f
    private var perv1y = 0f

    // First midpoint coordinates for Bezier curve construction.
    private var mid1x = 0f
    private var mid1y = 0f

    // Second midpoint coordinates for Bezier curve construction.
    private var mid2x = 0f
    private var mid2y = 0f

    // Second previous point coordinates for curve calculation.
    private var perv2x = 0f
    private var perv2y = 0f

    // Current touch point coordinates.
    private var curX = 0f
    private var curY = 0f

    // Accumulated distance along the path for spacing calculations.
    private var distance = 0f

    // Flag to track if the initial three points required for Bezier curves have been established.
    private var isFirstThreeCreated = false

    // Counter to track the number of points received during initialization.
    private var counter = 0

    // Reusable array to hold position coordinates from PathMeasure.
    private val pointHolder = floatArrayOf(0f, 0f)

    // Reusable array to hold tangent coordinates from PathMeasure.
    private val tanHolder = floatArrayOf(0f, 0f)

    // Counter for tracking draw operations within a single curve.
    private var drawCounter = 0

    // Path object for constructing the Bezier curve.
    private val path = Path()

    // PathMeasure for calculating positions and tangents along the path.
    private val pathMeasure = PathMeasure()

    /**
     * Sets the first point of a new drawing stroke.
     * Initializes the smoother state and prepares for curve generation.
     *
     * @param touchData The touch input data containing position information.
     * @param brush The brush configuration for this stroke.
     */
    override fun setFirstPoint(touchData: TouchData, brush: Brush) {
        // Reset the initialization state for a new stroke.
        isFirstThreeCreated = false

        // Store the first touch point.
        perv2x = touchData.ex
        perv2y = touchData.ey

        // Initialize the point counter.
        counter = 0
        counter++
    }

    /**
     * Adds intermediate points to build the smooth curve.
     * Collects the first three points to establish the initial Bezier curve,
     * then continuously updates the curve with subsequent points.
     *
     * @param touchData The touch input data containing position information.
     * @param brush The brush configuration for this stroke.
     */
    override fun addPoints(touchData: TouchData, brush: Brush) {
        touchData.run {
            if (!isFirstThreeCreated) {
                // Collect the first three points needed to create initial Bezier curve.
                when (counter) {
                    0 -> {
                        // Store the first point.
                        perv2x = ex
                        perv2y = ey
                    }

                    1 -> {
                        // Store the second point.
                        perv1x = ex
                        perv1y = ey
                    }

                    2 -> {
                        // Store the third point and create the first curve.
                        curX = ex
                        curY = ey

                        // Generate and draw the initial quadratic Bezier curve.
                        calculateQuadAndDraw(brush)

                        // Reset counter and mark initialization as complete.
                        counter = 0
                        isFirstThreeCreated = true

                        return
                    }
                }

                counter++
            } else {
                // Shift points for continuous curve generation.
                perv2x = perv1x
                perv2y = perv1y

                perv1x = curX
                perv1y = curY

                curX = ex
                curY = ey

                // Calculate and draw the next curve segment.
                calculateQuadAndDraw(brush)
            }
        }
    }

    /**
     * Finalizes the drawing stroke with the last point.
     * Completes the final curve segment and resets the smoother state.
     *
     * @param touchData The final touch input data.
     * @param brush The brush configuration for this stroke.
     */
    override fun setLastPoint(touchData: TouchData, brush: Brush) {
        touchData.run {
            if (isFirstThreeCreated) {
                // Complete the final curve segment with the last point.
                perv2x = perv1x
                perv2y = perv1y

                perv1x = curX
                perv1y = curY

                curX = ex
                curY = ey

                // Generate the final curve segment.
                calculateQuadAndDraw(brush)

                // Reset initialization state.
                isFirstThreeCreated = false
            } else {
                // If no curves were created, draw a single point.
                onDrawPoint?.onDrawPoint(ex, ey, 0f, 1, true)
            }

            // Reset distance tracking and clear the path.
            distance = 0f
            path.rewind()
        }
    }

    /**
     * Calculates quadratic Bezier curve parameters and generates evenly spaced drawing points.
     * This method applies smoothing, creates the curve path, and calculates positions
     * and rotations for brush strokes along the path.
     *
     * @param brush The brush configuration containing spacing and smoothing parameters.
     */
    private fun calculateQuadAndDraw(brush: Brush) {
        val spacedWidth = brush.spacedWidth
        val smoothness = brush.smoothness

        // Cache null check for performance.
        val isListenerNull = onDrawPoint == null

        // Calculate the first midpoint between previous points.
        mid1x = (perv1x + perv2x) * 0.5f
        mid1y = (perv1y + perv2y) * 0.5f

        // Apply smoothing to the current point by blending with previous point.
        val smoothnessInverse = 1f - smoothness
        curX = curX * smoothnessInverse + (perv1x * smoothness)
        curY = curY * smoothnessInverse + (perv1y * smoothness)

        // Calculate the second midpoint for the control point.
        mid2x = (curX + perv1x) * 0.5f
        mid2y = (curY + perv1y) * 0.5f

        // Initialize path with the first midpoint if empty.
        if (path.isEmpty) {
            path.moveTo(mid1x, mid1y)
        }

        // Create quadratic Bezier curve from mid1 to mid2 with perv1 as control point.
        path.quadTo(perv1x, perv1y, mid2x, mid2y)

        // Measure the path to calculate positions along the curve.
        pathMeasure.setPath(path, false)

        val width = (pathMeasure.length)

        // Calculate the number of evenly spaced points to draw along the curve.
        val total = floor((width - distance) / spacedWidth).toInt()

        drawCounter = 0

        // Generate evenly spaced drawing points along the curve.
        repeat(total) {
            // Advance the distance by the spacing interval.
            distance += spacedWidth

            // Get position and tangent at the current distance along the path.
            pathMeasure.getPosTan(
                distance,
                pointHolder,
                tanHolder
            )

            // Calculate brush rotation angle if auto-rotation is enabled.
            val degree = if (brush.autoRotate) {
                // Convert tangent vector to rotation angle and normalize to 0-360 range.
                GestureUtils.mapTo360(
                    -(Math.toDegrees(
                        (atan2(
                            tanHolder[0].toDouble(),
                            tanHolder[1].toDouble()
                        ))
                    ).toFloat() - 180f) - 90f
                )
            } else {
                0f
            }

            // Notify the listener to draw a point at the calculated position.
            if (!isListenerNull) {
                onDrawPoint!!.onDrawPoint(
                    pointHolder[0],
                    pointHolder[1],
                    degree,
                    total - drawCounter,
                    it == (total - 1)
                )
            }

            drawCounter++
        }
    }
}

package ir.simurgh.photolib.components.paint.smoothers

import android.graphics.Path
import android.graphics.PathMeasure
import ir.simurgh.photolib.components.paint.painters.painting.brushes.Brush
import ir.simurgh.photolib.utils.gesture.TouchData
import kotlin.math.floor

/**
 * Basic line smoother that provides simple linear interpolation between touch points.
 *
 * This smoother creates smooth strokes by connecting touch points with straight lines
 * and then generating evenly spaced points along those lines. It's the simplest
 * smoothing algorithm, providing good performance while maintaining stroke accuracy.
 *
 * Key features:
 * - Linear interpolation between consecutive touch points.
 * - Consistent spacing based on brush properties.
 * - Minimal smoothing overhead for real-time performance.
 * - Preserves the exact path traced by user input.
 *
 * Use this smoother when you need:
 * - Fast, responsive drawing.
 * - Precise control over stroke paths.
 * - Minimal processing overhead.
 *
 * @see BezierLineSmoother for more advanced curve smoothing.
 */
class BasicSmoother : LineSmoother() {

    /**
     * Path object used to construct the stroke geometry.
     * Accumulates line segments as touch points are added.
     */
    private val path by lazy {
        Path()
    }

    /**
     * PathMeasure for calculating distances and extracting points along the path.
     * Used to generate evenly spaced points for brush stamp placement.
     */
    private val pathMeasure by lazy {
        PathMeasure()
    }

    /**
     * Current distance traveled along the path.
     * Used to maintain consistent spacing between brush stamps.
     */
    private var distance = 0f

    /**
     * Reusable array to hold point coordinates extracted from the path.
     * Reduces memory allocations during drawing operations.
     */
    private val pointHolder = FloatArray(2)

    /**
     * Initializes the smoother with the first touch point.
     *
     * Resets the path and moves to the starting position without drawing.
     * This establishes the beginning of a new stroke.
     *
     * @param touchData Touch information containing the starting coordinates.
     * @param brush Brush properties (not used in basic smoothing).
     */
    override fun setFirstPoint(touchData: TouchData, brush: Brush) {
        // Clear any previous path data.
        path.rewind()
        // Move to the starting point without drawing a line.
        path.moveTo(touchData.ex, touchData.ey)
    }

    /**
     * Adds a new touch point and generates interpolated drawing points.
     *
     * Creates a line segment from the previous position to the new touch point,
     * then extracts evenly spaced points along the entire path for drawing.
     *
     * @param touchData Current touch information with new coordinates.
     * @param brush Brush properties used for spacing calculations.
     */
    override fun addPoints(touchData: TouchData, brush: Brush) {
        // Add a line segment to the new touch point.
        path.lineTo(touchData.ex, touchData.ey)
        // Generate and draw evenly spaced points along the path.
        drawPoints(brush)
    }

    /**
     * Finalizes the stroke with the last touch point.
     *
     * Adds the final line segment and generates any remaining drawing points.
     * Resets the distance counter for the next stroke.
     *
     * @param touchData Final touch information.
     * @param brush Brush properties for final point generation.
     */
    override fun setLastPoint(touchData: TouchData, brush: Brush) {
        // Add the final line segment.
        path.lineTo(touchData.ex, touchData.ey)
        // Generate remaining drawing points.
        drawPoints(brush)
        // Reset distance for the next stroke.
        distance = 0f
    }

    /**
     * Generates evenly spaced drawing points along the current path.
     *
     * This method measures the path length and extracts points at regular
     * intervals based on the brush's spacing requirements. Each point is
     * passed to the drawing callback for rendering.
     *
     * @param brush Brush containing spacing and other properties.
     */
    private fun drawPoints(brush: Brush) {
        // Get the desired spacing between brush stamps.
        val spacedWidth = brush.spacedWidth

        // Check if we have a drawing callback to avoid unnecessary work.
        val isListenerNull = onDrawPoint == null

        // Set up path measurement for the current path.
        pathMeasure.setPath(path, false)

        // Get the total length of the path.
        val width = (pathMeasure.length)

        // Calculate how many evenly spaced points we can fit from our current distance.
        val total = floor((width - distance) / spacedWidth).toInt()

        // Generate each evenly spaced point.
        repeat(total) {
            // Move to the next point position.
            distance += spacedWidth

            // Extract the coordinates at this distance along the path.
            pathMeasure.getPosTan(
                distance,
                pointHolder,
                null
            )

            // Send the point to the drawing callback if available.
            if (!isListenerNull) {
                onDrawPoint!!.onDrawPoint(pointHolder[0], pointHolder[1], 0f, 1, it == (total - 1))
            }
        }
    }
}

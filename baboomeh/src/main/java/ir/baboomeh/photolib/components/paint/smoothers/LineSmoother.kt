package ir.baboomeh.photolib.components.paint.smoothers

import ir.baboomeh.photolib.components.paint.painters.brushpaint.brushes.Brush
import ir.baboomeh.photolib.utils.gesture.TouchData

/**
 * Abstract base class for stroke smoothing and interpolation algorithms.
 *
 * Line smoothers process raw touch input and generate smooth, interpolated points
 * along the stroke path. This is essential for creating natural-looking brush strokes
 * from discrete touch events.
 *
 * Key responsibilities:
 * - **Smoothing**: Reduce jitter and noise from touch input
 * - **Interpolation**: Generate additional points between touch samples
 * - **Spacing**: Ensure consistent spacing between brush stamps
 * - **Direction calculation**: Compute stroke direction for brush rotation
 *
 * Different implementations provide various smoothing algorithms:
 * - [BasicSmoother]: Simple linear interpolation with fixed spacing
 * - [BezierLineSmoother]: Smooth curves using quadratic Bézier interpolation
 *
 * Workflow:
 * 1. [setFirstPoint] - Initialize with the first touch point
 * 2. [addPoints] - Process additional touch points as they arrive
 * 3. [setLastPoint] - Finalize the stroke with the last touch point
 *
 * The smoother calls [OnDrawPoint] for each generated point, which is typically
 * handled by a drawing engine to render brush stamps.
 *
 * @see BezierLineSmoother
 * @see ir.baboomeh.photolib.components.paint.smoothers.BasicSmoother
 */
abstract class LineSmoother {

    /**
     * Callback interface for receiving generated smooth points.
     *
     * Implementations (typically drawing engines) use this to render
     * brush stamps at each calculated point along the stroke.
     */
    var onDrawPoint: OnDrawPoint? = null

    /**
     * Initializes the smoother with the first point of a stroke.
     *
     * This method should:
     * - Store the initial position
     * - Reset any internal state from previous strokes
     * - Prepare for subsequent point processing
     *
     * @param touchData Touch information for the first point
     * @param brush Brush properties that may affect smoothing (spacing, smoothness, etc.)
     */
    abstract fun setFirstPoint(touchData: TouchData, brush: Brush)

    /**
     * Processes additional touch points during stroke creation.
     *
     * This is called for each new touch point as the user moves their finger.
     * The smoother should:
     * - Apply smoothing algorithms
     * - Generate interpolated points
     * - Call [OnDrawPoint] for each point to be rendered
     * - Maintain proper spacing based on brush properties
     *
     * @param touchData Current touch information
     * @param brush Brush properties affecting spacing and smoothing
     */
    abstract fun addPoints(touchData: TouchData, brush: Brush)

    /**
     * Finalizes the stroke with the last touch point.
     *
     * This method should:
     * - Process the final point
     * - Generate any remaining interpolated points
     * - Ensure the stroke ends at the correct position
     * - Clean up internal state
     *
     * @param touchData Final touch information
     * @param brush Brush properties for final processing
     */
    abstract fun setLastPoint(touchData: TouchData, brush: Brush)

    /**
     * Interface for receiving smoothed and interpolated points.
     *
     * This callback is invoked for each point that should be rendered
     * along the smoothed stroke path.
     */
    interface OnDrawPoint {
        /**
         * Called for each point to be drawn along the stroke.
         *
         * @param ex X coordinate of the point to draw
         * @param ey Y coordinate of the point to draw
         * @param angleDirection Direction angle of the stroke at this point (in degrees)
         *                      Used for brush rotation when auto-rotate is enabled
         * @param totalDrawCount Total number of draw calls for this stroke segment
         *                      Can be used for effects that depend on draw count
         * @param isLastPoint true if this is the last point in the current stroke segment
         */
        fun onDrawPoint(
            ex: Float,
            ey: Float,
            angleDirection: Float,
            totalDrawCount: Int,
            isLastPoint: Boolean
        )
    }
}

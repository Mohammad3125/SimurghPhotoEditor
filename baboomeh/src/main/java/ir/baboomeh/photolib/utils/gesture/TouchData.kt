package ir.baboomeh.photolib.utils.gesture

/**
 * Data class that encapsulates comprehensive touch input information.
 *
 * This class is used throughout the gesture detection and painting system
 * to pass touch event data between components. It includes not only the
 * current position but also movement deltas, cumulative movement, timing,
 * and pressure information.
 *
 * Key information provided:
 * - **Current position**: Exact touch coordinates
 * - **Movement deltas**: Change since last touch event
 * - **Cumulative movement**: Total movement since touch began
 * - **Timing**: When the touch event occurred
 * - **Pressure**: How hard the user is pressing (if supported)
 *
 * This data is essential for:
 * - Smooth stroke rendering
 * - Pressure-sensitive effects
 * - Gesture recognition
 * - Touch slope detection
 * - Performance optimization
 *
 * Example usage:
 * ```kotlin
 * fun onMove(touchData: TouchData) {
 *     // Use current position for drawing
 *     drawAt(touchData.ex, touchData.ey)
 *
 *     // Use pressure for brush size
 *     val brushSize = baseBrushSize * touchData.pressure
 *
 *     // Check if significant movement occurred
 *     if (touchData.dxSum > threshold || touchData.dySum > threshold) {
 *         // User has moved significantly
 *         processSignificantMovement()
 *     }
 * }
 * ```
 * @param ex Current X coordinate of the touch point
 * @param ey Current Y coordinate of the touch point
 * @param dx Delta X (change in X since last touch event)
 * @param dy Delta Y (change in Y since last touch event)
 * @param dxSum Cumulative absolute X movement since touch began
 * @param dySum Cumulative absolute Y movement since touch began
 * @param time Timestamp when this touch event occurred (in milliseconds)
 * @param pressure Touch pressure (0.0 to 1.0, where 1.0 is maximum pressure)
 */
data class TouchData(
    var ex: Float = 0f,
    var ey: Float = 0f,
    var dx: Float = 0f,
    var dy: Float = 0f,
    var dxSum: Float = 0f,
    var dySum: Float = 0f,
    var time: Long = 0,
    var pressure: Float = 1f
) {

    /**
     * Copy constructor that creates a new TouchData instance with the same values.
     *
     * @param touchData The TouchData instance to copy from
     */
    constructor(touchData: TouchData) : this() {
        set(touchData)
    }

    /**
     * Returns a human-readable string representation of the touch data.
     *
     * This is useful for debugging and logging touch events.
     * The format includes all key properties in a compact format.
     *
     * @return Formatted string with all touch properties
     */
    override fun toString(): String {
        return buildString {
            append("  ex ")
            append(ex)
            append("  ey ")
            append(ey)
            append("  dx ")
            append(dx)
            append("  dy ")
            append(dy)
            append("  time ")
            append(time)
            append("  pressure ")
            append(pressure)
        }
    }

    /**
     * Copies all values from another TouchData instance to this one.
     *
     * This is more efficient than creating a new TouchData instance
     * when you need to update an existing one.
     *
     * @param touchData The TouchData instance to copy values from
     */
    fun set(touchData: TouchData) {
        this.ex = touchData.ex
        this.ey = touchData.ey
        this.dx = touchData.dx
        this.dy = touchData.dy
        this.dxSum = touchData.dxSum
        this.dySum = touchData.dySum
        this.time = touchData.time
        this.pressure = touchData.pressure
    }
}

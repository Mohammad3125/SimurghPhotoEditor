package ir.simurgh.photolib.utils.gesture.gestures

/**
 * Interface definition for a finger move listener.
 * This listener gets invoked by classes that implement move gesture.
 */
interface OnMoveListener {
    /**
     * First point on the screen that user touches.
     * @param initialX First x point that user touches.
     * @param initialY First y point that user touches.
     * @return Return true to show interest in consuming the event.
     */
    fun onMoveBegin(initialX: Float, initialY: Float): Boolean

    /**
     * This method gets invoked as long as user is moving his/her finger across screen.
     * @param dx Difference between initial x point and current point.
     * @param dy Difference between initial y point and current point.
     * @return Return true to show interest in consuming the event.
     */
    fun onMove(dx: Float, dy: Float): Boolean


    /**
     * This method gets invoked as long as user is moving his/her finger across screen.
     * @param dx Difference between initial x point and current point.
     * @param dy Difference between initial y point and current point.
     * @param ex Current x coordinate of event.
     * @param ey Current y coordinate of event.
     * @return Return true to show interest in consuming the event.
     */
    fun onMove(dx: Float, dy: Float, ex: Float, ey: Float): Boolean

    /**
     * This method gets invoked when user lifts his/her finger up from screen.
     * @param lastX Last x point on the screen that user touched.
     * @param lastY Last y point on the screen that user touched.
     */
    fun onMoveEnded(lastX: Float, lastY: Float)
}
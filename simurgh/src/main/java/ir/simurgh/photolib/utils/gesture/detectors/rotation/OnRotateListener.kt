package ir.simurgh.photolib.utils.gesture.detectors.rotation

/**
 * Interface to be used as callback to detect rotation via a detector.
 */
interface OnRotateListener {
    /**
     * This method gets invoked when first rotation gesture is detected.
     * @param initialDegree The initial rotation angle in degrees.
     * @param px The x-coordinate of the rotation pivot point.
     * @param py The y-coordinate of the rotation pivot point.
     * @return Return true to show interest in consuming the event.
     */
    fun onRotateBegin(initialDegree: Float, px: Float, py: Float): Boolean

    /**
     * This method gets invoked every time a rotation is get detected by detector.
     * @param degree The current rotation angle in degrees.
     * @param px The x-coordinate of the rotation pivot point.
     * @param py The y-coordinate of the rotation pivot point.
     * @return Return true to show interest in consuming the event.
     */
    fun onRotate(degree: Float, px: Float, py: Float): Boolean

    /**
     * Called when the rotation gesture has ended.
     * This is invoked when the user lifts their fingers from the screen.
     */
    fun onRotateEnded()
}

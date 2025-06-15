package ir.baboomeh.photolib.utils.gesture.detectors.rotation

/**
 * A class to simplify [OnRotateListener] to be cleaner.
 * This class provides default implementations for all methods in OnRotateListener,
 * allowing subclasses to override only the methods they need.
 */
open class SimpleOnRotateListener : OnRotateListener {

    /**
     * Default implementation that returns false, indicating no interest in consuming the event.
     * Override this method to handle rotation begin events.
     * @param initialDegree The initial rotation angle in degrees.
     * @param px The x-coordinate of the rotation pivot point.
     * @param py The y-coordinate of the rotation pivot point.
     * @return False by default. Override to return true if you want to consume the event.
     */
    override fun onRotateBegin(initialDegree: Float, px: Float, py: Float): Boolean {
        return false
    }

    /**
     * Default implementation that returns true, indicating interest in consuming the event.
     * Override this method to handle ongoing rotation events.
     * @param degree The current rotation angle in degrees.
     * @param px The x-coordinate of the rotation pivot point.
     * @param py The y-coordinate of the rotation pivot point.
     * @return True by default. Override to provide custom behavior.
     */
    override fun onRotate(degree: Float, px: Float, py: Float): Boolean {
        return true
    }

    /**
     * Default implementation that provides no action.
     * Override this method to handle rotation end events.
     */
    override fun onRotateEnded() {
        // Default implementation does nothing.
    }
}
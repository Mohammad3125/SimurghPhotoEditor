package ir.manan.mananpic.utils.gesture.detectors.rotation

/**
 * Interface to be used as callback to detect rotation via a detector.
 */
interface OnRotateListener {
    /**
     * This method gets invoked when first rotation gesture is detected.
     * @return Return true to show interest in consuming the event.
     */
    fun onRotateBegin(initialDegree: Float, px: Float, py: Float): Boolean

    /**
     * This method gets invoked every time a rotation is get detected by detector.
     * @return Return true to show interest in consuming the event.
     */
    fun onRotate(degree: Float, px: Float, py: Float): Boolean
    fun onRotateEnded()
}
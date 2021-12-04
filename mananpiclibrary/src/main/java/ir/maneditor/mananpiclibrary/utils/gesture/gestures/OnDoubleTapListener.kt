package ir.maneditor.mananpiclibrary.utils.gesture.gestures

/**
 * Interface to be used as a callback when a double tap gets detected.
 */
interface OnDoubleTapListener {
    /**
     * This method gets called when a double tap gets detected by a detector.
     * @param x X position of current double tap.
     * @param y Y position of current double tap.
     */
    fun onDoubleTap(x: Float, y: Float) : Boolean

}
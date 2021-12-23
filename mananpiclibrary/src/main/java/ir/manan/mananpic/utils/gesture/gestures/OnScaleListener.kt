package ir.manan.mananpic.utils.gesture.gestures

/**
 * Interface definition for a listener that gets invoked when any scaling event happen in a view's motion event.
 */
interface OnScaleListener {
    /**
     * When first time user touches the screen.
     * @return Return true to show interest in consuming the event.
     */
    fun onScaleBegin(): Boolean

    /**
     * This method gets invoked as long as user is gesturing a scale gesture.
     * @param scaleFactor A factor that indicates how much user scaled from initial contact point.
     * @return Return true to show interest in consuming the event.
     */
    fun onScale(scaleFactor: Float): Boolean

    /**
     * This method gets invoked when scaling gesture ends.
     */
    fun onScaleEnded()
}
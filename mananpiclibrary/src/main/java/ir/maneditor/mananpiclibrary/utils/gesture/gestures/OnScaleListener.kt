package ir.maneditor.mananpiclibrary.utils.gesture.gestures

/**
 * Interface definition for a listener that gets invoked when any scaling event happen in a view's motion event.
 */
interface OnScaleListener {
    /**
     * When first time user touches the screen
     */
    fun onScaleBegin()

    /**
     * This method gets invoked as long as user is gesturing a scale gesture.
     * @param scaleFactor A factor that indicates how much user scaled from initial contact point.
     */
    fun onScale(scaleFactor: Float)

    /**
     * This method gets invoked when scaling gesture ends.
     */
    fun onScaleEnded()
}
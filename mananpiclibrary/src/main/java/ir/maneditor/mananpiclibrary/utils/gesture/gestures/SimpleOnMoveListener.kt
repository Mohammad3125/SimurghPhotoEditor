package ir.maneditor.mananpiclibrary.utils.gesture.gestures

/**
 * A class for simplifying [OnMoveListener] to be cleaner.
 */
open class SimpleOnMoveListener : OnMoveListener {

    override fun onMoveBegin(initialX: Float, initialY: Float) {}

    override fun onMove(dx: Float, dy: Float) {}

    override fun onMoveEnded(lastX: Float, lastY: Float) {}
}
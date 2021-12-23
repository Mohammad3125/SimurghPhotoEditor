package ir.manan.mananpic.utils.gesture.gestures

/**
 * A class for simplifying [OnMoveListener] to be cleaner.
 */
open class SimpleOnMoveListener : OnMoveListener {

    override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
        return false
    }

    override fun onMove(dx: Float, dy: Float): Boolean {
        return true
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float): Boolean {
        return true
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {}
}
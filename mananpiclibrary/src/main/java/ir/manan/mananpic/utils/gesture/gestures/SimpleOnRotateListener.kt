package ir.manan.mananpic.utils.gesture.gestures

/**
 * A class to simplify [OnRotateListener] to be cleaner.
 */
open class SimpleOnRotateListener : OnRotateListener {
    override fun onRotateBegin(initialDegree: Float): Boolean {
        return false
    }

    override fun onRotate(degree: Float): Boolean {
        return true
    }

    override fun onRotateEnded() {

    }

}
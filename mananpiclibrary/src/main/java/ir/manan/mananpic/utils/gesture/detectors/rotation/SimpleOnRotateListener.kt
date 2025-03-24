package ir.manan.mananpic.utils.gesture.detectors.rotation

/**
 * A class to simplify [OnRotateListener] to be cleaner.
 */
open class SimpleOnRotateListener : OnRotateListener {
    override fun onRotateBegin(initialDegree: Float, px: Float, py: Float): Boolean {
        return false
    }

    override fun onRotate(degree: Float, px: Float, py: Float): Boolean {
        return true
    }

    override fun onRotateEnded() {

    }

}
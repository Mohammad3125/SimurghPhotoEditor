package ir.maneditor.mananpiclibrary.utils.gesture.detectors

import android.view.MotionEvent
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.Gesture
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.OnDoubleTapListener

/**
 * A gesture detector that detects double-tap gesture.
 * @param timeBetweenTaps Determines the acceptable time between each tap to be accepted as a double-tap.
 * @param listener Listener that gets invoked when detector detects a double-tap.
 */
class DoubleTapDetector(var timeBetweenTaps: Long = 500L, var listener: OnDoubleTapListener) :
    Gesture {

    private var timeKeeper = 0L
    private var firstTap = true

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount == 1) {
                    if (firstTap) {
                        timeKeeper = System.currentTimeMillis()
                        firstTap = false
                    } else {
                        if (System.currentTimeMillis() - timeKeeper < timeBetweenTaps) {
                            firstTap = true
                            timeKeeper = 0L
                            listener.onDoubleTap(event.x, event.y)
                        }
                    }
                }
                true
            }
            else -> {
                false
            }
        }
    }
}
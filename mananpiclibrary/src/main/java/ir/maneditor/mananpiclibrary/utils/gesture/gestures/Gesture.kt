package ir.maneditor.mananpiclibrary.utils.gesture.gestures

import android.view.MotionEvent

/**
 * A base interface for classes that implement a gesture.
 */
interface Gesture {
    /**
     * Binds a event to the gesture to consume it.
     * @param event Event that is going to be consumed by gesture detectors (they may invoke listeners based on user actions.)
     */
    fun onTouchEvent(event: MotionEvent?) : Boolean
}
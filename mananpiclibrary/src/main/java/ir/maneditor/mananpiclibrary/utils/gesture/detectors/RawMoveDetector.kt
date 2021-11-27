package ir.maneditor.mananpiclibrary.utils.gesture.detectors

import android.view.MotionEvent
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.Gesture
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.OnMoveListener

/**
 * Implementation of a finger move gesture.
 * @param listener a [OnMoveListener] that gets called in appropriate situations.
 */
class RawMoveDetector(var listener: OnMoveListener) : Gesture {

    private var initialX = 0f
    private var initialY = 0f

    // It will notify the motion event that user is gesturing a new gesture on the screen.
    private var newGesture = true

    override fun onTouchEvent(event: MotionEvent?) {
        when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y

                listener.onMoveBegin(initialX, initialY)
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && newGesture) {

                    listener.onMove(event.x - initialX, event.y - initialY)

                    initialX = event.x
                    initialY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Do not let the moving gesture continue it's work, because it
                // shifts the view while rotating or scaling.
                newGesture = false
            }

            MotionEvent.ACTION_UP -> {
                // After all of the fingers were lifted from screen, then we can make a move gesture.
                newGesture = true
                listener.onMoveEnded(event.x, event.y)
            }
        }
    }
}
package ir.maneditor.mananpiclibrary.utils.gesture.detectors

import android.view.MotionEvent
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.Gesture
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.OnMoveListener

/**
 * Implementation of a finger move gesture.
 * @param pointerCount Determines number of fingers that make gesture.
 * @param listener a [OnMoveListener] that gets called in appropriate situations.
 */
class MoveDetector(var pointerCount: Int, var listener: OnMoveListener) : Gesture {

    private var initialX = 0f
    private var initialY = 0f

    // It will notify the motion event that user is gesturing a new gesture on the screen.
    private var newGesture = true

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y

                listener.onMoveBegin(initialX, initialY)
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == pointerCount && newGesture) {
                    val currentX = event.x
                    val currentY = event.y

                    val bool = listener.onMove(currentX - initialX, currentY - initialY)
                    listener.onMove(currentX - initialX, currentY - initialY, currentX, currentY)

                    initialX = currentX
                    initialY = currentY

                    bool
                } else
                    false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Do not let the moving gesture continue it's work, because it
                // shifts the view while rotating or scaling.
                newGesture = false
                false
            }

            MotionEvent.ACTION_UP -> {
                // After all of the fingers were lifted from screen, then we can make a move gesture.
                newGesture = true
                listener.onMoveEnded(event.x, event.y)
                false
            }
            else -> {
                false
            }
        }
    }
}
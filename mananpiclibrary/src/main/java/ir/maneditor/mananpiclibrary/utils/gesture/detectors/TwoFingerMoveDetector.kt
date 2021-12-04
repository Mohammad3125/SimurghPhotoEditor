package ir.maneditor.mananpiclibrary.utils.gesture.detectors

import android.view.MotionEvent
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.Gesture
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.OnMoveListener

/**
 * A class for detecting move gesture performed by two fingers.
 * @param listener Listener that gets invoked when a move gesture gets detected by detector.
 */
class TwoFingerMoveDetector(var listener: OnMoveListener) : Gesture {
    private var initialX = 0f
    private var initialY = 0f

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return when (event?.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                initialX = event.x
                initialY = event.y

                listener.onMoveBegin(initialX, initialY)

                true
            }

            MotionEvent.ACTION_MOVE -> {
                // Continue if there are only two pointers(fingers) on screen.
                if (event.pointerCount == 2) {
                    val currentX = event.x
                    val currentY = event.y

                    listener.onMove(currentX - initialX, currentY - initialY)

                    initialX = currentX
                    initialY = currentY

                    true
                } else
                    false
            }

            MotionEvent.ACTION_UP -> {
                listener.onMoveEnded(event.x, event.y)
                false
            }

            else -> {
                false
            }
        }
    }
}
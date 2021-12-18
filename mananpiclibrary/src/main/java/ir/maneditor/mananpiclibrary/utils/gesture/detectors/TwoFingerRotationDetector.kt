package ir.maneditor.mananpiclibrary.utils.gesture.detectors

import android.view.MotionEvent
import ir.maneditor.mananpiclibrary.utils.gesture.GestureUtils
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.Gesture
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.OnRotateListener

/**
 * A gesture class for rotation gesture with two fingers.
 * @param listener A [OnRotateListener] that gets called in appropriate situations.
 */
class TwoFingerRotationDetector(var listener: OnRotateListener) : Gesture {

    // Later will be used for calculations.
    private var initialRotation = 0f

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return when (event?.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                event.run {
                    initialRotation -= calculateAngle(event)
                    listener.onRotateBegin(initialRotation)
                }
                true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 2) {
                    val calculatedRotation = calculateAngle(event) + initialRotation
                    listener.onRotate(calculatedRotation)
                    true
                } else false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                initialRotation += calculateAngle(event)
                false
            }

            MotionEvent.ACTION_UP -> {
                listener.onRotateEnded()
                false
            }

            else -> {
                false
            }

        }
    }

    /**
     * Resets rotation of detector to a degree (default is 0).
     */
    fun resetRotation(toDegree: Float = 0f) {
        initialRotation = toDegree
    }

    private fun calculateAngle(event: MotionEvent): Float {
        event.run {
            return GestureUtils.calculateAngle(
                (getX(0) - getX(1)).toDouble(),
                (getY(1) - getY(0)).toDouble()
            )
        }
    }
}
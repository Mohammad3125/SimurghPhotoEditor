package ir.manan.mananpic.utils.gesture.detectors

import android.view.MotionEvent
import ir.manan.mananpic.utils.gesture.GestureUtils
import ir.manan.mananpic.utils.gesture.GestureUtils.Companion.mapTo360
import ir.manan.mananpic.utils.gesture.gestures.OnRotateListener
import ir.manan.mananpic.utils.gesture.gestures.RotationDetectorGesture
import kotlin.math.round

/**
 * A gesture class for rotation gesture with two fingers.
 * @param listener A [OnRotateListener] that gets called in appropriate situations.
 */
class TwoFingerRotationDetector(private var listener: OnRotateListener) : RotationDetectorGesture {

    // Later will be used for calculations.
    private var initialRotation = 0f

    /**
     * If greater than 0 then rotation snaps to steps of current number for example
     * if step was 8.5f then we would have 8.5f then 17f then 25.5f as rotation and so on.
     * Default value is 0f meaning no stepping is applied on rotation.
     * @throws IllegalStateException if step is less than 0.
     */
    private var step = 0f

    private var wasTouchedWithTwoPointers = false

    override fun setRotationStep(rotationStep: Float) {
        if (rotationStep < 0) throw IllegalStateException("step value should be equal or greater than 0")
        step = rotationStep
    }

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
                    val rawRotation = calculateAngle(event) + initialRotation

                    val validatedRotation = mapTo360(rawRotation)

                    if (step > 0f) {
                        // Calculate the nearest step point by rounding the result of dividing current rotation by step.
                        // For example if we had a step of 8.5 and we have been rotated 3 degrees so far then result would be:
                        // round(3 / 8.5f ~= 0.3529) -> 0f = 8.5 * 0 = 0.
                        // Now imagine we had rotation of 8 and step of 8.5f, the result would be:
                        // round(8 / 8.5f ~= 0.9411) -> 1f = 8.5 * 1f = 8.5.
                        listener.onRotate(mapTo360(step * (round(validatedRotation / step))))
                    } else {
                        listener.onRotate(validatedRotation)
                    }

                    wasTouchedWithTwoPointers = true

                    true
                } else false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                initialRotation += calculateAngle(event)
                false
            }

            MotionEvent.ACTION_UP -> {
                // Only call 'onRotateEnded' method when we was rotating with two pointers.
                if (wasTouchedWithTwoPointers) {
                    listener.onRotateEnded()
                }

                wasTouchedWithTwoPointers = false
                false
            }

            else -> {
                false
            }

        }
    }

    override fun resetRotation(resetTo: Float) {
        initialRotation = resetTo
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
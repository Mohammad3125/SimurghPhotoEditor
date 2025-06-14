package ir.baboomeh.photolib.utils.gesture.detectors.rotation

import android.view.MotionEvent
import ir.baboomeh.photolib.utils.gesture.GestureUtils
import ir.baboomeh.photolib.utils.gesture.GestureUtils.Companion.mapTo360
import kotlin.math.round

/**
 * A gesture class for rotation gesture with two fingers.
 * @param listener A [OnRotateListener] that gets called in appropriate situations.
 */
open class TwoFingerRotationDetector(protected var listener: OnRotateListener) : RotationDetectorGesture {

    // Later will be used for calculations.
    protected var initialRotation = 0f

    /**
     * If greater than 0 then rotation snaps to steps of current number for example
     * if step was 8.5f then we would have 8.5f then 17f then 25.5f as rotation and so on.
     * Default value is 0f meaning no stepping is applied on rotation.
     * @throws IllegalStateException if step is less than 0.
     */
    protected var step = 0f

    protected var x0 = 0f
    protected var y0 = 0f

    protected var x1 = 0f
    protected var y1 = 0f

    protected var dx = 0f
    protected var dy = 0f

    protected var shouldProgress = false

    protected var wasTouchedWithTwoPointers = false

    override fun setRotationStep(rotationStep: Float) {
        if (rotationStep < 0) throw IllegalStateException("step value should be equal or greater than 0")
        step = rotationStep
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return when (event?.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                event.run {

                    calculatePoints(event)

                    initialRotation -= calculateAngle(dx.toDouble(), dy.toDouble())

                    shouldProgress = listener.onRotateBegin(
                        initialRotation,
                        x0 + ((x1 - x0) * 0.5f),
                        y0 + ((y1 - y0) * 0.5f)
                    )
                    shouldProgress
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 2 && shouldProgress) {

                    calculatePoints(event)

                    val px = x0 + ((x1 - x0) * 0.5f)
                    val py = y0 + ((y1 - y0) * 0.5f)

                    val rawRotation = calculateAngle(dx.toDouble(), dy.toDouble()) + initialRotation

                    val validatedRotation = mapTo360(rawRotation)

                    shouldProgress = if (step > 0f) {
                        // Calculate the nearest step point by rounding the result of dividing current rotation by step.
                        // For example if we had a step of 8.5 and we have been rotated 3 degrees so far then result would be:
                        // round(3 / 8.5f ~= 0.3529) -> 0f = 8.5 * 0 = 0.
                        // Now imagine we had rotation of 8 and step of 8.5f, the result would be:
                        // round(8 / 8.5f ~= 0.9411) -> 1f = 8.5 * 1f = 8.5.
                        listener.onRotate(
                            mapTo360(step * (round(validatedRotation / step))),
                            px,
                            py
                        )
                    } else {
                        listener.onRotate(validatedRotation, px, py)
                    }

                    wasTouchedWithTwoPointers = true

                    true
                } else false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                calculatePoints(event)

                initialRotation += calculateAngle(dx.toDouble(), dy.toDouble())
                false
            }

            MotionEvent.ACTION_UP -> {
                // Only call 'onRotateEnded' method when we was rotating with two pointers.
                if (wasTouchedWithTwoPointers && shouldProgress) {
                    listener.onRotateEnded()
                }

                wasTouchedWithTwoPointers = false
                shouldProgress = false
                false
            }

            else -> {
                false
            }

        }
    }

    protected open fun calculatePoints(event: MotionEvent) {
        x0 = event.getX(0)
        y0 = event.getY(0)

        x1 = event.getX(1)
        y1 = event.getY(1)

        dx = x0 - x1
        dy = y1 - y0
    }

    override fun resetRotation(resetTo: Float) {
        initialRotation = resetTo
    }


    protected open fun calculateAngle(dx: Double, dy: Double): Float {
        return GestureUtils.calculateAngle(
            dx,
            dy
        )
    }
}
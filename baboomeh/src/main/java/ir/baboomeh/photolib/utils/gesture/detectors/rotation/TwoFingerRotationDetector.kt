package ir.baboomeh.photolib.utils.gesture.detectors.rotation

import android.view.MotionEvent
import ir.baboomeh.photolib.utils.gesture.GestureUtils
import ir.baboomeh.photolib.utils.gesture.GestureUtils.Companion.mapTo360
import kotlin.math.round

/**
 * A gesture class for rotation gesture with two fingers.
 * This detector calculates rotation angle based on the position changes of two touch points.
 * It supports optional step-based rotation for quantized rotation values.
 * @param listener A [OnRotateListener] that gets called in appropriate situations.
 */
open class TwoFingerRotationDetector(protected var listener: OnRotateListener) : RotationDetectorGesture {

    /** Used for calculating rotation relative to the initial touch position. */
    protected var initialRotation = 0f

    /**
     * If greater than 0 then rotation snaps to steps of current number for example.
     * If step was 8.5f then we would have 8.5f then 17f then 25.5f as rotation and so on.
     * Default value is 0f meaning no stepping is applied on rotation.
     * @throws IllegalStateException if step is less than 0.
     */
    protected var step = 0f

    /** X coordinate of the first touch point. */
    protected var x0 = 0f

    /** Y coordinate of the first touch point. */
    protected var y0 = 0f

    /** X coordinate of the second touch point. */
    protected var x1 = 0f

    /** Y coordinate of the second touch point. */
    protected var y1 = 0f

    /** Difference in X coordinates between the two touch points. */
    protected var dx = 0f

    /** Difference in Y coordinates between the two touch points. */
    protected var dy = 0f

    /** Flag indicating whether the rotation gesture should continue processing. */
    protected var shouldProgress = false

    /** Flag tracking whether the user has touched with two pointers during this gesture. */
    protected var wasTouchedWithTwoPointers = false

    /**
     * Sets the rotation step size for quantized rotation.
     * @param rotationStep The step size in degrees. Must be >= 0.
     * @throws IllegalStateException if step is less than 0.
     */
    override fun setRotationStep(rotationStep: Float) {
        if (rotationStep < 0) throw IllegalStateException("step value should be equal or greater than 0.")
        step = rotationStep
    }

    /**
     * Handles touch events to detect rotation gestures.
     * @param event The motion event to process.
     * @return True if the event was consumed, false otherwise.
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return when (event?.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                event.run {
                    // Calculate initial touch points for rotation baseline.
                    calculatePoints(event)

                    // Adjust initial rotation to account for current finger positions.
                    initialRotation -= calculateAngle(dx.toDouble(), dy.toDouble())

                    // Notify listener and check if gesture should be processed.
                    shouldProgress = listener.onRotateBegin(
                        initialRotation,
                        x0 + ((x1 - x0) * 0.5f), // Center point X.
                        y0 + ((y1 - y0) * 0.5f)  // Center point Y.
                    )
                    shouldProgress
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Process rotation only if we have exactly 2 pointers and should continue.
                if (event.pointerCount == 2 && shouldProgress) {
                    // Update touch point positions.
                    calculatePoints(event)

                    // Calculate the center point between the two touch points.
                    val px = x0 + ((x1 - x0) * 0.5f)
                    val py = y0 + ((y1 - y0) * 0.5f)

                    // Calculate the raw rotation angle.
                    val rawRotation = calculateAngle(dx.toDouble(), dy.toDouble()) + initialRotation

                    // Normalize rotation to 0-360 degree range.
                    val validatedRotation = mapTo360(rawRotation)

                    // Apply stepping if configured, otherwise use continuous rotation.
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
                        // Use continuous rotation without stepping.
                        listener.onRotate(validatedRotation, px, py)
                    }

                    // Mark that we have successfully detected two-finger rotation.
                    wasTouchedWithTwoPointers = true

                    true
                } else false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Update touch points and adjust baseline rotation.
                calculatePoints(event)

                // Update initial rotation for potential continued gestures.
                initialRotation += calculateAngle(dx.toDouble(), dy.toDouble())
                false
            }

            MotionEvent.ACTION_UP -> {
                // Only call 'onRotateEnded' method when we was rotating with two pointers.
                if (wasTouchedWithTwoPointers && shouldProgress) {
                    listener.onRotateEnded()
                }

                // Reset gesture state.
                wasTouchedWithTwoPointers = false
                shouldProgress = false
                false
            }

            else -> {
                false
            }
        }
    }

    /**
     * Calculates and updates the positions of both touch points and their differences.
     * @param event The motion event containing touch point data.
     */
    protected open fun calculatePoints(event: MotionEvent) {
        // Get coordinates of both touch points.
        x0 = event.getX(0)
        y0 = event.getY(0)

        x1 = event.getX(1)
        y1 = event.getY(1)

        // Calculate differences for angle calculation.
        dx = x0 - x1
        dy = y1 - y0
    }

    /**
     * Resets the rotation baseline to a specific angle.
     * @param resetTo The angle in degrees to reset the rotation to.
     */
    override fun resetRotation(resetTo: Float) {
        initialRotation = resetTo
    }

    /**
     * Calculates the rotation angle based on coordinate differences.
     * @param dx Difference in X coordinates.
     * @param dy Difference in Y coordinates.
     * @return The calculated angle in degrees.
     */
    protected open fun calculateAngle(dx: Double, dy: Double): Float {
        return GestureUtils.calculateAngle(dx, dy)
    }
}

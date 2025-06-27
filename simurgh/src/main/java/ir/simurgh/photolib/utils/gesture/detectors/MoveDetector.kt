package ir.simurgh.photolib.utils.gesture.detectors

import android.view.MotionEvent
import ir.simurgh.photolib.utils.gesture.gestures.Gesture
import ir.simurgh.photolib.utils.gesture.gestures.OnMoveListener

/**
 * Implementation of a finger move gesture.
 * This detector handles move gestures with a configurable number of fingers and provides
 * callbacks through an OnMoveListener for gesture events.
 * @param pointerCount Determines number of fingers that make gesture.
 * @param listener a [OnMoveListener] that gets called in appropriate situations.
 * @throws IllegalStateException if pointer count <= 0.
 */
open class MoveDetector(var pointerCount: Int, var listener: OnMoveListener) : Gesture {

    /** Initial X coordinate of the first touch point. */
    protected var initialX = 0f

    /** Initial Y coordinate of the first touch point. */
    protected var initialY = 0f

    /** Initial X coordinate of the second touch point (for multi-finger gestures). */
    protected var secondPointerInitialX = 0f

    /** Initial Y coordinate of the second touch point (for multi-finger gestures). */
    protected var secondPointerInitialY = 0f

    /** Flag indicating if this is a new gesture (not a continuation of previous gesture). */
    protected var newGesture = true

    /** Flag indicating whether the gesture should continue processing. */
    protected var shouldContinue = false

    init {
        if (pointerCount <= 0) throw IllegalStateException("pointer count should be more than 0.")
    }

    /**
     * Handles touch events to detect move gestures.
     * @param event The motion event to process.
     * @return True if the event was consumed, false otherwise.
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Store initial touch position.
                initialX = event.x
                initialY = event.y

                // Notify listener and check if gesture should continue.
                shouldContinue = listener.onMoveBegin(initialX, initialY)
                return shouldContinue
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Handle second finger down for multi-finger gestures.
                if (pointerCount >= 2 && shouldContinue) {
                    secondPointerInitialX = event.getX(1)
                    secondPointerInitialY = event.getY(1)
                }
                true
            }

            MotionEvent.ACTION_MOVE -> {
                // Process move events only if we have the correct number of pointers.
                if (event.pointerCount == pointerCount && newGesture && shouldContinue) {

                    // Calculate movement delta for the first finger.
                    var currentX = event.x - initialX
                    var currentY = event.y - initialY

                    // For multi-finger gestures, average the movement of both fingers.
                    if (pointerCount >= 2) {

                        val secondPointerX = event.getX(1)
                        val secondPointerY = event.getY(1)

                        // Calculate average movement between both fingers.
                        currentX =
                            ((secondPointerX - secondPointerInitialX) + (event.x - initialX)) / 2
                        currentY =
                            ((secondPointerY - secondPointerInitialY) + (event.y - initialY)) / 2

                        // Update second pointer initial position for next calculation.
                        secondPointerInitialX = secondPointerX
                        secondPointerInitialY = secondPointerY
                    }

                    // Notify listener with movement delta and absolute position.
                    val bool = listener.onMove(currentX, currentY)
                    bool.and(listener.onMove(currentX, currentY, event.x, event.y))

                    // Update initial position for next movement calculation.
                    initialX = event.x
                    initialY = event.y

                    bool
                } else
                    false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Prevent move gesture from continuing when fingers are lifted during multi-touch.
                // This prevents the view from shifting while rotating or scaling.
                newGesture = false
                false
            }

            MotionEvent.ACTION_UP -> {
                // Re-enable move gestures after all fingers are lifted from screen.
                newGesture = true
                if (shouldContinue) {
                    // Notify listener that the move gesture has ended.
                    listener.onMoveEnded(event.x, event.y)
                }
                false
            }
            else -> {
                false
            }
        }
    }
}

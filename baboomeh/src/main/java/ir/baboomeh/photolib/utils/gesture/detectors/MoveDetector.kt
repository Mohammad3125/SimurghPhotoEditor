package ir.baboomeh.photolib.utils.gesture.detectors

import android.view.MotionEvent
import ir.baboomeh.photolib.utils.gesture.gestures.Gesture
import ir.baboomeh.photolib.utils.gesture.gestures.OnMoveListener

/**
 * Implementation of a finger move gesture.
 * @param pointerCount Determines number of fingers that make gesture.
 * @param listener a [OnMoveListener] that gets called in appropriate situations.
 * @throws IllegalStateException if pointer count <= 0.
 */
class MoveDetector(var pointerCount: Int, var listener: OnMoveListener) : Gesture {

    private var initialX = 0f
    private var initialY = 0f

    private var secondPointerInitialX = 0f
    private var secondPointerInitialY = 0f

    // It will notify the motion event that user is gesturing a new gesture on the screen.
    private var newGesture = true

    private var shouldContinue = false


    init {
        if (pointerCount <= 0) throw IllegalStateException("pointer count should be more than 0")
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y

                shouldContinue = listener.onMoveBegin(initialX, initialY)
                return shouldContinue
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerCount >= 2 && shouldContinue) {
                    secondPointerInitialX = event.getX(1)
                    secondPointerInitialY = event.getY(1)
                }
                true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == pointerCount && newGesture && shouldContinue) {

                    var currentX = event.x - initialX
                    var currentY = event.y - initialY

                    if (pointerCount >= 2) {

                        val secondPointerX = event.getX(1)
                        val secondPointerY = event.getY(1)

                        currentX =
                            ((secondPointerX - secondPointerInitialX) + (event.x - initialX)) / 2
                        currentY =
                            ((secondPointerY - secondPointerInitialY) + (event.y - initialY)) / 2

                        secondPointerInitialX = secondPointerX
                        secondPointerInitialY = secondPointerY
                    }

                    val bool = listener.onMove(currentX, currentY)
                    bool.and(listener.onMove(currentX, currentY, event.x, event.y))

                    initialX = event.x
                    initialY = event.y

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
                if (shouldContinue) {
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
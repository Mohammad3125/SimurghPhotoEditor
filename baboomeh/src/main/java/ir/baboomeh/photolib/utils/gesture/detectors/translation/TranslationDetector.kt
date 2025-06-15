package ir.baboomeh.photolib.utils.gesture.detectors.translation

import android.view.MotionEvent
import ir.baboomeh.photolib.utils.gesture.TouchData
import ir.baboomeh.photolib.utils.gesture.gestures.Gesture
import kotlin.math.abs

/**
 * A gesture detector for translation (pan/move) operations with support for single and multi-finger gestures.
 * This detector tracks touch movements and provides detailed touch data including position,
 * delta movement, cumulative movement, pressure, and timing information.
 * @param listener The callback interface to receive translation events.
 */
open class TranslationDetector(var listener: OnTranslationDetector) : Gesture {

    /** Initial X coordinate of the first touch point. */
    protected var initialX = 0f

    /** Initial Y coordinate of the first touch point. */
    protected var initialY = 0f

    /** Initial X coordinate of the second touch point. */
    protected var secondPointerInitialX = 0f

    /** Initial Y coordinate of the second touch point. */
    protected var secondPointerInitialY = 0f

    /** Cumulative absolute X movement for the second pointer. */
    protected var secondDxSum = 0f

    /** Cumulative absolute Y movement for the second pointer. */
    protected var secondDySum = 0f

    /** Cumulative absolute X movement for the first pointer. */
    protected var firstDxSum = 0f

    /** Cumulative absolute Y movement for the first pointer. */
    protected var firstDySum = 0f

    /** Collection storing touch data for all active pointers. */
    protected open val touchHolder by lazy {
        mutableListOf<TouchData>()
    }

    /** The current number of active touch pointers. */
    open val pointerCount: Int
        get() = touchHolder.size

    /** Flag to enable/disable processing of touch event history for smoother gestures. */
    var isTouchEventHistoryEnabled = false

    /** Flag indicating whether the gesture should continue processing. */
    protected var shouldProgress = false

    /**
     * Handles touch events to detect translation gestures.
     * @param event The motion event to process.
     * @return True if the event was consumed, false otherwise.
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return event?.run {
            when (actionMasked) {
                MotionEvent.ACTION_DOWN -> {

                    // Save the initial points to later determine how much user has moved
                    // his/her finger across screen.
                    initialX = x
                    initialY = y

                    // Initialize touch data for the first pointer.
                    touchHolder.add(TouchData())

                    touchHolder[0].ex = initialX
                    touchHolder[0].ey = initialY
                    touchHolder[0].pressure = pressure
                    touchHolder[0].time = eventTime

                    // Notify listener and check if gesture should continue.
                    shouldProgress = listener.onMoveBegin(this@TranslationDetector)
                    shouldProgress
                }

                MotionEvent.ACTION_POINTER_DOWN -> {

                    // Handle second pointer for multi-finger gestures.
                    if (pointerCount == 2) {
                        secondPointerInitialX = getX(1)
                        secondPointerInitialY = getY(1)

                        // Add touch data for the second pointer.
                        touchHolder.add(TouchData())

                        touchHolder[0].ex = initialX
                        touchHolder[0].ey = initialY

                        touchHolder[1].ex = secondPointerInitialX
                        touchHolder[1].ey = secondPointerInitialY
                    }

                    // Re-notify listener for multi-pointer gesture begin.
                    shouldProgress = listener.onMoveBegin(this@TranslationDetector)
                    shouldProgress
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!shouldProgress) {
                        return false
                    }
                    // Handle two-finger translation gestures.
                    if (pointerCount == 2) {
                        // Process historical touch events for smoother gesture tracking.
                        if (isTouchEventHistoryEnabled) {
                            repeat(historySize) { pos ->

                                calculateFirstPointer(
                                    getHistoricalX(0, pos),
                                    getHistoricalY(0, pos),
                                    getHistoricalEventTime(pos),
                                    getHistoricalPressure(0, pos)
                                )

                                calculateSecondPointer(
                                    getHistoricalX(1, pos),
                                    getHistoricalY(1, pos),
                                    getHistoricalEventTime(0),
                                    getHistoricalPressure(1, pos)
                                )

                                listener.onMove(this@TranslationDetector)
                            }
                        }

                        // Process current touch positions.
                        calculateFirstPointer(x, y, eventTime, getPressure(0))
                        calculateSecondPointer(getX(1), getY(1), eventTime, getPressure(1))
                        listener.onMove(this@TranslationDetector)

                    } else if (pointerCount == 1) {
                        // Handle single-finger translation gestures.

                        // Process historical touch events for smoother gesture tracking.
                        if (isTouchEventHistoryEnabled) {
                            repeat(historySize) { pos ->
                                calculateFirstPointer(
                                    getHistoricalX(0, pos),
                                    getHistoricalY(0, pos),
                                    getHistoricalEventTime(pos),
                                    getHistoricalPressure(0, pos)
                                )

                                listener.onMove(this@TranslationDetector)
                            }
                        }

                        // Process current touch position.
                        calculateFirstPointer(x, y, eventTime, getPressure(0))
                        listener.onMove(this@TranslationDetector)
                    }
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Reset cumulative movement tracking.
                    secondDxSum = 0f
                    secondDySum = 0f

                    firstDxSum = 0f
                    firstDySum = 0f

                    // Final position calculation.
                    calculateFirstPointer(x, y, eventTime, pressure)

                    // Notify listener that the gesture has ended.
                    if (shouldProgress) {
                        listener.onMoveEnded(this@TranslationDetector)
                    }

                    shouldProgress = false

                    // Clear all touch data.
                    touchHolder.clear()

                    false
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    // Remove data for the lifted pointer.
                    touchHolder.removeAt(1)
                    true
                }

                else -> {
                    // Clean up resources for unhandled events.
                    recycle()
                    false
                }
            }
        } ?: true
    }


    /**
     * Calculates and updates touch data for the first pointer.
     * @param ex Current X coordinate.
     * @param ey Current Y coordinate.
     * @param eventTime Timestamp of the touch event.
     * @param pressure Touch pressure value.
     */
    protected open fun calculateFirstPointer(ex: Float, ey: Float, eventTime: Long, pressure: Float) {
        // Calculate movement delta from initial position.
        val dx = ex - initialX
        val dy = ey - initialY

        // Update cumulative absolute movement.
        firstDxSum += abs(dx)
        firstDySum += abs(dy)

        // Update touch data with current values.
        touchHolder[0].setTouchData(
            ex,
            ey,
            dx,
            dy,
            firstDxSum,
            firstDySum,
            eventTime,
            pressure,
        )

        // Update initial position for next calculation.
        initialX = ex
        initialY = ey
    }

    /**
     * Calculates and updates touch data for the second pointer.
     * @param ex Current X coordinate.
     * @param ey Current Y coordinate.
     * @param eventTime Timestamp of the touch event.
     * @param pressure Touch pressure value.
     */
    protected open fun calculateSecondPointer(ex: Float, ey: Float, eventTime: Long, pressure: Float) {
        // Calculate movement delta from initial position.
        val dx = ex - secondPointerInitialX
        val dy = ey - secondPointerInitialY

        // Update cumulative absolute movement.
        secondDxSum += abs(dx)
        secondDySum += abs(dy)

        // Update touch data with current values.
        touchHolder[1].setTouchData(
            ex,
            ey,
            dx,
            dy,
            secondDxSum,
            secondDySum,
            eventTime,
            pressure,
        )

        // Update initial position for next calculation.
        secondPointerInitialX = ex
        secondPointerInitialY = ey
    }

    /**
     * Retrieves touch data for a specific pointer.
     * @param pointerIndex The index of the pointer (0 for first, 1 for second, etc.).
     * @return TouchData object containing information about the specified pointer.
     * @throws IllegalArgumentException if pointer index is invalid.
     */
    open fun getTouchData(pointerIndex: Int): TouchData {
        if (pointerIndex < 0 || pointerIndex >= touchHolder.size) {
            throw IllegalArgumentException("pointer index is not valid.")
        }
        return touchHolder[pointerIndex]
    }

    /**
     * Extension function to update all touch data properties in a single call.
     * @param ex Current X coordinate.
     * @param ey Current Y coordinate.
     * @param dx Delta X movement since last update.
     * @param dy Delta Y movement since last update.
     * @param dxSum Cumulative absolute X movement.
     * @param dySum Cumulative absolute Y movement.
     * @param time Timestamp of the touch event.
     * @param pressure Touch pressure value.
     */
    protected fun TouchData.setTouchData(
        ex: Float,
        ey: Float,
        dx: Float,
        dy: Float,
        dxSum: Float,
        dySum: Float,
        time: Long,
        pressure: Float
    ) {
        this.ex = ex
        this.ey = ey
        this.dx = dx
        this.dy = dy
        this.dxSum = dxSum
        this.dySum = dySum
        this.time = time
        this.pressure = pressure
    }

}

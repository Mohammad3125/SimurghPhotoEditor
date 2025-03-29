package ir.manan.mananpic.utils.gesture.detectors.translation

import android.view.MotionEvent
import ir.manan.mananpic.utils.gesture.TouchData
import ir.manan.mananpic.utils.gesture.gestures.Gesture
import kotlin.math.abs

open class TranslationDetector(var listener: OnTranslationDetector) : Gesture {

    protected var initialX = 0f
    protected var initialY = 0f

    protected var secondPointerInitialX = 0f
    protected var secondPointerInitialY = 0f

    protected var secondDxSum = 0f
    protected var secondDySum = 0f

    protected var firstDxSum = 0f
    protected var firstDySum = 0f

    protected open val touchHolder by lazy {
        mutableListOf<TouchData>()
    }

    open val pointerCount: Int
        get() = touchHolder.size

    var isTouchEventHistoryEnabled = false

    private var shouldProgress = false

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return event?.run {
            when (actionMasked) {
                MotionEvent.ACTION_DOWN -> {

                    // Save the initial points to later determine how much user has moved
                    // His/her finger across screen.
                    initialX = x
                    initialY = y

                    touchHolder.add(TouchData())

                    touchHolder[0].ex = initialX
                    touchHolder[0].ey = initialY
                    touchHolder[0].pressure = pressure
                    touchHolder[0].time = eventTime

                    shouldProgress = listener.onMoveBegin(this@TranslationDetector)
                    shouldProgress
                }

                MotionEvent.ACTION_POINTER_DOWN -> {

                    if (pointerCount == 2) {
                        secondPointerInitialX = getX(1)
                        secondPointerInitialY = getY(1)

                        touchHolder.add(TouchData())

                        touchHolder[0].ex = initialX
                        touchHolder[0].ey = initialY

                        touchHolder[1].ex = secondPointerInitialX
                        touchHolder[1].ey = secondPointerInitialY
                    }

                    shouldProgress = listener.onMoveBegin(this@TranslationDetector)
                    shouldProgress
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!shouldProgress) {
                        return false
                    }
                    // If there are currently 2 pointers on screen and user is not scaling then
                    // translate the canvas matrix.
                    if (pointerCount == 2) {
                        if (isTouchEventHistoryEnabled) {
                            repeat(historySize) { pos ->

                                calculateFirstPointer(
                                    getHistoricalX(0, pos),
                                    getHistoricalY(0, pos),
                                    getHistoricalEventTime(pos),
                                    getHistoricalPressure(
                                        0,
                                        pos
                                    )
                                )

                                calculateSecondPointer(
                                    getHistoricalX(1, pos),
                                    getHistoricalY(1, pos),
                                    getHistoricalEventTime(0),
                                    getHistoricalPressure(
                                        1, pos
                                    )
                                )

                                listener.onMove(this@TranslationDetector)
                            }
                        }

                        calculateFirstPointer(x, y, eventTime, getPressure(0))
                        calculateSecondPointer(getX(1), getY(1), eventTime, getPressure(1))
                        listener.onMove(this@TranslationDetector)

                    } else if (pointerCount == 1) {

                        if (isTouchEventHistoryEnabled) {
                            repeat(historySize) { pos ->
                                calculateFirstPointer(
                                    getHistoricalX(0, pos),
                                    getHistoricalY(0, pos),
                                    getHistoricalEventTime(pos),
                                    getHistoricalPressure(
                                        0,
                                        pos
                                    )
                                )

                                listener.onMove(this@TranslationDetector)
                            }
                        }

                        calculateFirstPointer(x, y, eventTime, getPressure(0))
                        listener.onMove(this@TranslationDetector)
                    }
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    secondDxSum = 0f
                    secondDySum = 0f

                    firstDxSum = 0f
                    firstDySum = 0f

                    calculateFirstPointer(x, y, eventTime, pressure)

                    if (shouldProgress) {
                        listener.onMoveEnded(this@TranslationDetector)
                    }

                    shouldProgress = false

                    touchHolder.clear()

                    false
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    touchHolder.removeAt(1)
                    true
                }

                else -> {
                    recycle()
                    false
                }
            }
        } ?: true
    }


    private fun calculateFirstPointer(ex: Float, ey: Float, eventTime: Long, pressure: Float) {
        val dx = ex - initialX
        val dy = ey - initialY

        firstDxSum += abs(dx)
        firstDySum += abs(dy)

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

        initialX = ex
        initialY = ey
    }

    private fun calculateSecondPointer(ex: Float, ey: Float, eventTime: Long, pressure: Float) {
        val dx = ex - secondPointerInitialX
        val dy = ey - secondPointerInitialY

        secondDxSum += abs(dx)
        secondDySum += abs(dy)

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

        secondPointerInitialX = ex
        secondPointerInitialY = ey
    }

    open fun getTouchData(pointerIndex: Int): TouchData {
        if (pointerIndex < 0 || pointerIndex >= touchHolder.size) {
            throw IllegalArgumentException("pointer index is not valid")
        }
        return touchHolder[pointerIndex]
    }

    private fun TouchData.setTouchData(
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

package ir.baboomeh.photolib.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.OnGestureListener
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.view.doOnLayout
import ir.baboomeh.photolib.utils.dp
import ir.baboomeh.photolib.utils.gesture.detectors.MoveDetector
import ir.baboomeh.photolib.utils.gesture.gestures.OnMoveListener
import kotlin.math.abs
import kotlin.math.max

open class MananGradientSlider(context: Context, attributeSet: AttributeSet?) :
    View(context, attributeSet), OnMoveListener, OnGestureListener {
    constructor(context: Context) : this(context, null)

    protected var strokeWidthHalf = 0f
    protected var isWrapContent = false

    protected var isShaderDirty = true

    protected var isMoved = false
    protected var isOnMoveCalled = false

    protected var isNewCircleCreated = false

    protected var onCircleHandleClicked: ((index: Int) -> Unit)? = null
    protected var onCircleHandleClickedListener: OnCircleClickListener? = null

    protected var onColorsAndPositionsChanged: ((colors: IntArray, positions: FloatArray) -> Unit)? =
        null
    protected var colorsAndPositionsChangedCallback: OnColorsAndPositionsChanged? = null

    protected var onColorsOrPositionsChangeEnded: (() -> Unit)? = null

    open var gradientLineWidth = dp(8)
        set(value) {
            field = value
            gradientLinePaint.strokeWidth = field
            requestLayout()
            invalidate()
        }

    protected val moveDetector = MoveDetector(1, this)

    protected val gestureDetector = GestureDetector(context, this)

    protected val gradientLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = gradientLineWidth
    }

    open var colorCircleStrokeWidth = dp(1)

    protected val circlesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    protected val circleHandles = mutableListOf<CircleHandle>()

    open var colorCircleRadius = dp(8)
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    open var circlesStrokeColor = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    open var circleHandleTouchArea = dp(24)

    protected lateinit var gradientShader: Shader

    protected var selectedCircleHandleIndex = -1

    protected var isFirstCirclesAdded = false

    protected var widthF = 0f
    protected var heightF = 0f
    protected var drawingStart = 0f
    protected var drawingTop = 0f
    protected var heightHalf = 0f

    protected var defaultPaddingVertical = dp(12)
    protected var wrapContentSize = dp(48).toInt()

    override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
        if (initialX.coerceIn(-circleHandleTouchArea, width + circleHandleTouchArea) == initialX &&
            initialY.coerceIn(
                heightHalf - circleHandleTouchArea,
                heightHalf + circleHandleTouchArea
            ) == initialY
        ) {
            selectedCircleHandleIndex = findNearestCircleIndex(initialX)


            if (selectedCircleHandleIndex == -1) {
                circleHandles.add(CircleHandle(initialX.coerceIn(drawingStart, widthF), Color.RED))
                selectedCircleHandleIndex = circleHandles.lastIndex
                isShaderDirty = true
                isNewCircleCreated = true
                isMoved = true
                invalidate()
            }

            return true
        }
        return false
    }

    protected open fun findNearestCircleIndex(initialX: Float): Int {
        return circleHandles.filter {
            (initialX.coerceIn(
                it.x - circleHandleTouchArea,
                it.x + circleHandleTouchArea
            ) == initialX)
        }.sortedBy {
            abs(initialX - it.x)
        }.let { sortedList ->
            if (sortedList.isEmpty()) -1 else circleHandles.indexOf(sortedList[0])
        }
    }

    override fun onMove(dx: Float, dy: Float): Boolean {
        return true
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float): Boolean {
        if (selectedCircleHandleIndex != -1) {
            val circleX = circleHandles[selectedCircleHandleIndex].x

            if (ex != circleX) {
                circleHandles[selectedCircleHandleIndex].x = ex.coerceIn(drawingStart, widthF)

                isShaderDirty = true
                isOnMoveCalled = true

                invalidate()
            }
        }
        return true
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        if (isMoved || isOnMoveCalled) {
            onColorsOrPositionsChangeEnded?.invoke()
            isMoved = false
            isOnMoveCalled = false
        }
    }

    override fun onDown(p0: MotionEvent): Boolean {
        return false
    }

    override fun onShowPress(p0: MotionEvent) {
    }

    override fun onSingleTapUp(p0: MotionEvent): Boolean {
        if (selectedCircleHandleIndex != -1 && !isNewCircleCreated) {
            callCircleListeners(selectedCircleHandleIndex)
            return true
        }
        isNewCircleCreated = false
        return false
    }

    override fun onScroll(
        e1: MotionEvent?,
        p0: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return false
    }

    override fun onLongPress(p0: MotionEvent) {
        if (circleHandles.size > 2) {
            circleHandles.removeAt(selectedCircleHandleIndex)
            isShaderDirty = true
            selectedCircleHandleIndex = -1
            isMoved = true
            invalidate()
        }
    }

    override fun onFling(
        e1: MotionEvent?,
        p0: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return if (event != null) {
            moveDetector.onTouchEvent(event).or(gestureDetector.onTouchEvent(event))
        } else {
            super.onTouchEvent(null)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measureWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measureHeight = MeasureSpec.getSize(heightMeasureSpec)

        isWrapContent = false

        val finalHeight = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> {
                measureHeight
            }

            MeasureSpec.AT_MOST, MeasureSpec.UNSPECIFIED -> {
                isWrapContent = true
                wrapContentSize
            }

            else -> {
                suggestedMinimumHeight
            }
        }

        setMeasuredDimension(
            max(measureWidth, suggestedMinimumWidth),
            max(finalHeight, suggestedMinimumHeight)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        calculateBounds(width.toFloat(), height.toFloat())
    }

    protected open fun calculateBounds(targetWidth: Float, targetHeight: Float) {
        heightF = targetHeight - paddingBottom - paddingTop

        if (isWrapContent) {
            heightF -= (defaultPaddingVertical * 2f)
        }

        heightHalf = heightF * 0.5f
        gradientLinePaint.strokeWidth = heightHalf

        widthF = targetWidth - paddingEnd - heightHalf

        drawingStart = heightHalf + paddingStart
        drawingTop = heightHalf + paddingTop

        if (isWrapContent) {
            drawingTop += defaultPaddingVertical
        }
        strokeWidthHalf = if (gradientLinePaint.strokeCap != Paint.Cap.BUTT) {
            // If paint cap is not BUTT then add half the width of stroke at start and end of line.
            gradientLinePaint.strokeWidth * 0.5f
        } else {
            0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.also { c ->

            if (!isFirstCirclesAdded && circleHandles.isEmpty()) {
                circleHandles.add(CircleHandle(drawingStart, Color.WHITE))
                circleHandles.add(CircleHandle(widthF, Color.BLACK))
                isFirstCirclesAdded = true
            }

            if (isShaderDirty) {

                // Used to prevent listener invoking on the first time we're initializing shader.
                val wasInitialized = this::gradientShader.isInitialized

                val sortedList = circleHandles.sortedBy { it.x }

                val colors = sortedList.map { it.color }.toIntArray()

                val positions =
                    sortedList.map { it.calculateRelativePosition(widthF) }.toFloatArray()

                gradientShader = LinearGradient(
                    drawingStart,
                    drawingTop,
                    widthF,
                    drawingTop,
                    colors,
                    positions,
                    Shader.TileMode.MIRROR
                )

                gradientLinePaint.shader = gradientShader

                isShaderDirty = false

                if (wasInitialized) {
                    callColorAndPositionListeners(colors, positions)

                    if (isMoved) {
                        onColorsOrPositionsChangeEnded?.invoke()
                        isMoved = false
                    }
                }
            }

            c.drawLine(
                drawingStart,
                drawingTop,
                widthF,
                drawingTop, gradientLinePaint
            )

            val finalLowerCircleRadius = colorCircleStrokeWidth + colorCircleRadius

            circleHandles.forEach {
                circlesPaint.color = circlesStrokeColor

                c.drawCircle(
                    it.x,
                    drawingTop,
                    finalLowerCircleRadius,
                    circlesPaint
                )

                circlesPaint.color = it.color

                c.drawCircle(
                    it.x,
                    drawingTop,
                    colorCircleRadius,
                    circlesPaint
                )
            }
        }
    }

    open fun setOnCircleClickedListener(listener: (index: Int) -> Unit) {
        onCircleHandleClicked = listener
    }

    open fun setOnCircleClickedListener(listener: OnCircleClickListener) {
        onCircleHandleClickedListener = listener
    }

    protected open fun callCircleListeners(index: Int) {
        onCircleHandleClicked?.invoke(index)
        onCircleHandleClickedListener?.onClicked(index)
    }

    open fun setOnColorsAndPositionsChangedListener(listener: OnColorsAndPositionsChanged) {
        colorsAndPositionsChangedCallback = listener
    }

    open fun setOnColorsAndPositionsChangedListener(listener: (colors: IntArray, positions: FloatArray) -> Unit) {
        onColorsAndPositionsChanged = listener
    }

    open fun setOnColorOrPositionsChangeEnded(listener: () -> Unit) {
        onColorsOrPositionsChangeEnded = listener
    }

    protected open fun callColorAndPositionListeners(colors: IntArray, positions: FloatArray) {
        colorsAndPositionsChangedCallback?.onChanged(colors, positions)
        onColorsAndPositionsChanged?.invoke(colors, positions)
    }

    open fun changeColorOfCircleAt(@ColorInt color: Int, index: Int) {
        if (index < 0 || index > circleHandles.size) throw IllegalStateException("current index is out of bounds of circle handles")
        changeColorAt(color, index)

    }

    open fun changeColorOfSelectedCircle(@ColorInt color: Int) {
        changeColorAt(color, selectedCircleHandleIndex)
    }

    protected open fun changeColorAt(@ColorInt color: Int, index: Int) {
        circleHandles[index].color = color
        isShaderDirty = true
        isMoved = true
        invalidate()
    }

    open fun getCurrentColors(): IntArray {
        return if (isFirstCirclesAdded) {
            circleHandles.sortedBy { it.x }.map { it.color }.toIntArray()
        } else {
            intArrayOf(Color.WHITE, Color.BLACK)
        }
    }

    open fun getPositions(): FloatArray {
        return if (isFirstCirclesAdded) {
            circleHandles.sortedBy { it.x }.map { it.calculateRelativePosition(widthF) }
                .toFloatArray()
        } else {
            floatArrayOf(0f, 1f)
        }
    }

    open fun setColorsAndPositions(colors: IntArray, positions: FloatArray) {
        if (colors.size < 2) {
            throw IllegalStateException("colors array size should be equal or more than 2")
        }
        if (positions.size < 2) {
            throw IllegalStateException("positions array size should be equal or more than 2")
        }

        if (colors.size != positions.size) {
            throw IllegalStateException("colors array and position array do not have same length")
        }

        circleHandles.clear()

        doOnLayout {
            colors.forEachIndexed { index, color ->
                circleHandles.add(
                    CircleHandle(
                        widthF * positions[index],
                        color
                    )
                )
            }
        }

        isShaderDirty = true

        invalidate()
    }

    open fun resetSlider() {
        circleHandles.clear()
        isFirstCirclesAdded = false
        isShaderDirty = true
        invalidate()
    }

    protected data class CircleHandle(var x: Float, @ColorInt var color: Int) {
        fun calculateRelativePosition(fromPosition: Float): Float = x / fromPosition
    }

    interface OnCircleClickListener {
        fun onClicked(index: Int)
    }

    interface OnColorsAndPositionsChanged {
        fun onChanged(colors: IntArray, positions: FloatArray)
    }
}

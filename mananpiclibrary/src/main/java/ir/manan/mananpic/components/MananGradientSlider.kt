package ir.manan.mananpic.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector.OnGestureListener
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.doOnLayout
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.detectors.MoveDetector
import ir.manan.mananpic.utils.gesture.gestures.OnMoveListener
import kotlin.math.abs

class MananGradientSlider(context: Context, attributeSet: AttributeSet?) :
    View(context, attributeSet), OnMoveListener, OnGestureListener {
    constructor(context: Context) : this(context, null)

    private var drawingBaseline = 0f

    private var shouldChangeShader = true

    private var isNewCircleCreated = false

    private var onCircleHandleClicked: ((index: Int) -> Unit)? = null
    private var onCircleHandleClickedListener: OnCircleClickListener? = null

    private var onColorsAndPositionsChanged: ((colors: IntArray, positions: FloatArray) -> Unit)? =
        null
    private var onColorsAndPositionsChangedListener: OnColorsAndPositionsChanged? = null

    var gradientLineWidth = dp(8)
        set(value) {
            field = value
            gradientLinePaint.strokeWidth = field
            requestLayout()
            invalidate()
        }

    private val moveDetector = MoveDetector(1, this)

    private val gestureDetector = GestureDetectorCompat(context, this)

    private val gradientLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = gradientLineWidth
    }

    var colorCircleStrokeWidth = dp(1)

    private val circlesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val circleHandles = mutableListOf<CircleHandle>()

    var colorCircleRadius = dp(8)
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var circlesStrokeColor = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    var circleHandleTouchArea = dp(24)

    private lateinit var gradientShader: Shader

    private var selectedCircleHandleIndex = -1

    private var isFirstCirclesAdded = false

    override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
        val halfHeight = height * 0.5f
        if (initialX.coerceIn(-circleHandleTouchArea, width + circleHandleTouchArea) == initialX &&
            initialY.coerceIn(
                halfHeight - circleHandleTouchArea,
                halfHeight + circleHandleTouchArea
            ) == initialY
        ) {
            selectedCircleHandleIndex = findNearestCircleIndex(initialX)

            if (selectedCircleHandleIndex == -1) {
                circleHandles.add(CircleHandle(initialX, Color.RED))
                selectedCircleHandleIndex = circleHandles.lastIndex
                shouldChangeShader = true
                isNewCircleCreated = true
                invalidate()
            }

            return true
        }
        return false
    }

    private fun findNearestCircleIndex(initialX: Float): Int {
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
                val fc = (colorCircleStrokeWidth + colorCircleRadius)
                val widthLimit = width - fc
                circleHandles[selectedCircleHandleIndex].x =
                    (if (ex < fc) fc else if (ex > widthLimit) widthLimit else ex)

                shouldChangeShader = true

                invalidate()
            }
        }
        return true
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {

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
        p0: MotionEvent,
        p1: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return false
    }

    override fun onLongPress(p0: MotionEvent) {
        if (circleHandles.size > 2) {
            circleHandles.removeAt(selectedCircleHandleIndex)
            shouldChangeShader = true
            selectedCircleHandleIndex = -1
            invalidate()
        }
    }

    override fun onFling(
        p0: MotionEvent,
        p1: MotionEvent,
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
            super.onTouchEvent(event)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val measureWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measureHeight = MeasureSpec.getSize(heightMeasureSpec)

        val finalWidth = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> {
                measureWidth
            }
            MeasureSpec.AT_MOST, MeasureSpec.UNSPECIFIED -> {
                val w = dp(96).toInt()
                if (w > measureWidth) measureWidth else w
            }
            else -> {
                suggestedMinimumWidth
            }
        }

        val finalHeight = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> {
                measureHeight
            }
            MeasureSpec.AT_MOST, MeasureSpec.UNSPECIFIED -> {
                val h = ((colorCircleRadius * 2) + gradientLineWidth).toInt()
                if (h > measureHeight) measureHeight else h
            }
            else -> {
                suggestedMinimumHeight
            }
        }

        setMeasuredDimension(finalWidth, finalHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        drawingBaseline = height * 0.5f
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.also { c ->

            if(!isFirstCirclesAdded && circleHandles.isEmpty()) {
                circleHandles.add(CircleHandle(colorCircleStrokeWidth + colorCircleRadius, Color.WHITE))
                circleHandles.add(
                    CircleHandle(width - (colorCircleStrokeWidth + colorCircleRadius), Color.BLACK)
                )
                isFirstCirclesAdded = true
            }

            if (shouldChangeShader) {


                // Used to prevent listener invoking on the first time we're initializing shader.
                val wasInitialized = this::gradientShader.isInitialized

                val widthF = width.toFloat()

                val sortedList = circleHandles.sortedBy { it.x }

                val colors = sortedList.map { it.color }.toIntArray()

                val positions =
                    sortedList.map { it.calculateRelativePosition(widthF) }.toFloatArray()


                gradientShader = LinearGradient(
                    0f,
                    drawingBaseline,
                    width.toFloat(),
                    drawingBaseline,
                    colors,
                    positions,
                    Shader.TileMode.MIRROR
                )


                gradientLinePaint.shader = gradientShader

                shouldChangeShader = false

                if (wasInitialized) {
                    callColorAndPositionListeners(colors, positions)
                }
            }

            c.drawLine(0f, drawingBaseline, width.toFloat(), drawingBaseline, gradientLinePaint)

            val finalLowerCircleRadius = colorCircleStrokeWidth + colorCircleRadius

            circleHandles.forEach {
                circlesPaint.color = circlesStrokeColor

                c.drawCircle(
                    it.x,
                    drawingBaseline,
                    finalLowerCircleRadius,
                    circlesPaint
                )

                circlesPaint.color = it.color

                c.drawCircle(
                    it.x,
                    drawingBaseline,
                    colorCircleRadius,
                    circlesPaint
                )
            }
        }
    }

    fun setOnCircleClickedListener(listener: (index: Int) -> Unit) {
        onCircleHandleClicked = listener
    }

    fun setOnCircleClickedListener(listener: OnCircleClickListener) {
        onCircleHandleClickedListener = listener
    }

    private fun callCircleListeners(index: Int) {
        onCircleHandleClicked?.invoke(index)
        onCircleHandleClickedListener?.onClicked(index)
    }

    fun setOnColorsAndPositionsChangedListener(listener: OnColorsAndPositionsChanged) {
        onColorsAndPositionsChangedListener = listener
    }

    fun setOnColorsAndPositionsChangedListener(listener: (colors: IntArray, positions: FloatArray) -> Unit) {
        onColorsAndPositionsChanged = listener
    }

    private fun callColorAndPositionListeners(colors: IntArray, positions: FloatArray) {
        onColorsAndPositionsChangedListener?.onChanged(colors, positions)
        onColorsAndPositionsChanged?.invoke(colors, positions)
    }

    fun changeColorOfCircleAt(@ColorInt color: Int, index: Int) {
        if (index < 0 || index > circleHandles.size) throw IllegalStateException("current index is out of bounds of circle handles")
        changeColorAt(color, index)

    }

    fun changeColorOfSelectedCircle(@ColorInt color: Int) {
        changeColorAt(color, selectedCircleHandleIndex)
    }

    private fun changeColorAt(@ColorInt color: Int, index: Int) {
        circleHandles[index].color = color
        shouldChangeShader = true
        invalidate()
    }

    fun getCurrentColors(): IntArray {
        return if (isFirstCirclesAdded) {
            circleHandles.sortedBy { it.x }.map { it.color }.toIntArray()
        } else {
            intArrayOf(Color.WHITE, Color.BLACK)
        }
    }

    fun getPositions(): FloatArray {
        return if (isFirstCirclesAdded) {
            circleHandles.sortedBy { it.x }.map { it.calculateRelativePosition(width.toFloat()) }
                .toFloatArray()
        } else {
            floatArrayOf(0f, 1f)
        }
    }

    fun setColorsAndPositions(colors: IntArray, positions: FloatArray) {
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
                        width.toFloat() * positions[index],
                        color
                    )
                )
            }
        }

        invalidate()
    }

    private data class CircleHandle(var x: Float, @ColorInt var color: Int) {
        fun calculateRelativePosition(fromPosition: Float): Float = x / fromPosition
    }

    interface OnCircleClickListener {
        fun onClicked(index: Int)
    }

    interface OnColorsAndPositionsChanged {
        fun onChanged(colors: IntArray, positions: FloatArray)
    }
}
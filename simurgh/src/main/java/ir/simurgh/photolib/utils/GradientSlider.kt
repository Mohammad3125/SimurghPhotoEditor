package ir.simurgh.photolib.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.view.doOnLayout
import ir.simurgh.photolib.utils.extensions.dp
import ir.simurgh.photolib.utils.gesture.detectors.MoveDetector
import ir.simurgh.photolib.utils.gesture.gestures.OnMoveListener
import kotlin.math.abs
import kotlin.math.max

/**
 * A custom view that displays an interactive gradient slider with draggable color handles.
 * Users can tap to add new color stops, drag existing handles to reposition them,
 * and long-press to remove handles (minimum 2 handles must remain).
 *
 * Features:
 * - Interactive gradient editing with touch gestures.
 * - Customizable appearance (colors, sizes, stroke widths).
 * - Listeners for color changes and user interactions.
 * - Support for programmatic gradient setup and modification.
 */
open class GradientSlider(context: Context, attributeSet: AttributeSet?) :
    View(context, attributeSet), OnMoveListener, GestureDetector.OnGestureListener {

    constructor(context: Context) : this(context, null)

    /** Half of the stroke width, used for positioning calculations. */
    protected var strokeWidthHalf = 0f

    /** Flag indicating if the view height is set to wrap_content. */
    protected var isWrapContent = false

    /** Flag indicating if the gradient shader needs to be recreated. */
    protected var isShaderDirty = true

    /** Flag indicating if a move operation has occurred. */
    protected var isMoved = false

    /** Flag indicating if the onMove callback has been called. */
    protected var isOnMoveCalled = false

    /** Flag indicating if a new circle handle was just created. */
    protected var isNewCircleCreated = false

    /** Lambda callback for circle handle click events. */
    protected var onCircleHandleClicked: ((index: Int) -> Unit)? = null

    /** Interface-based callback for circle handle click events. */
    protected var onCircleHandleClickedListener: OnCircleClickListener? = null

    /** Lambda callback for colors and positions change events. */
    protected var onColorsAndPositionsChanged: ((colors: IntArray, positions: FloatArray) -> Unit)? =
        null

    /** Interface-based callback for colors and positions change events. */
    protected var colorsAndPositionsChangedCallback: OnColorsAndPositionsChanged? = null

    /** Callback invoked when color or position changes have ended. */
    protected var onColorsOrPositionsChangeEnded: (() -> Unit)? = null

    /**
     * Width of the gradient line in pixels.
     * Setting this property triggers layout and redraw.
     */
    open var gradientLineWidth = dp(8)
        set(value) {
            field = value
            gradientLinePaint.strokeWidth = field
            requestLayout()
            invalidate()
        }

    /** Detector for handling move gestures. */
    protected val moveDetector = MoveDetector(1, this)

    /** Detector for handling standard gestures like tap and long press. */
    protected val gestureDetector = GestureDetector(context, this)

    /** Paint object for drawing the gradient line. */
    protected val gradientLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = gradientLineWidth
    }

    /** Stroke width for the color circle handles in pixels. */
    open var colorCircleStrokeWidth = dp(1)

    /** Paint object for drawing the color circle handles. */
    protected val circlesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** List of all circle handles representing color stops on the gradient. */
    protected val circleHandles = mutableListOf<CircleHandle>()

    /**
     * Radius of the color circle handles in pixels.
     * Setting this property triggers layout and redraw.
     */
    open var colorCircleRadius = dp(8)
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    /**
     * Color of the stroke around circle handles.
     * Setting this property triggers a redraw.
     */
    open var circlesStrokeColor = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    /** Touch area radius for circle handle interaction in pixels. */
    open var circleHandleTouchArea = dp(24)

    /** The linear gradient shader used for drawing the gradient line. */
    protected lateinit var gradientShader: Shader

    /** Index of the currently selected circle handle (-1 if none selected). */
    protected var selectedCircleHandleIndex = -1

    /** Flag indicating if the initial default circles have been added. */
    protected var isFirstCirclesAdded = false

    /** Cached width of the view as float. */
    protected var widthF = 0f

    /** Cached height of the view as float. */
    protected var heightF = 0f

    /** X coordinate where drawing starts. */
    protected var drawingStart = 0f

    /** Y coordinate where drawing occurs. */
    protected var drawingTop = 0f

    /** Half of the view height. */
    protected var heightHalf = 0f

    /** Default vertical padding when using wrap_content height. */
    protected var defaultPaddingVertical = dp(12)

    /** Size used when height is wrap_content. */
    protected var wrapContentSize = dp(48).toInt()

    /**
     * Called when a move gesture begins.
     * Determines if the touch is within a valid area and selects or creates a circle handle.
     *
     * @param initialX The initial X coordinate of the touch.
     * @param initialY The initial Y coordinate of the touch.
     * @return True if the move gesture should be handled, false otherwise.
     */
    override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
        // Check if touch is within the valid interaction area.
        if (initialX.coerceIn(-circleHandleTouchArea, width + circleHandleTouchArea) == initialX &&
            initialY.coerceIn(
                heightHalf - circleHandleTouchArea,
                heightHalf + circleHandleTouchArea
            ) == initialY
        ) {
            selectedCircleHandleIndex = findNearestCircleIndex(initialX)

            // If no existing handle is found, create a new one.
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

    /**
     * Finds the nearest circle handle to the given X coordinate within the touch area.
     *
     * @param initialX The X coordinate to search near.
     * @return The index of the nearest circle handle, or -1 if none found.
     */
    protected open fun findNearestCircleIndex(initialX: Float): Int {
        return circleHandles.filter {
            // Check if the touch point is within the touch area of this handle.
            (initialX.coerceIn(
                it.x - circleHandleTouchArea,
                it.x + circleHandleTouchArea
            ) == initialX)
        }.sortedBy {
            // Sort by distance from touch point.
            abs(initialX - it.x)
        }.let { sortedList ->
            if (sortedList.isEmpty()) -1 else circleHandles.indexOf(sortedList[0])
        }
    }

    /**
     * Called during move gestures (legacy method).
     * @return Always returns true to continue handling the gesture.
     */
    override fun onMove(dx: Float, dy: Float): Boolean {
        return true
    }

    /**
     * Called during move gestures with absolute coordinates.
     * Updates the position of the selected circle handle.
     *
     * @param dx Delta X (not used).
     * @param dy Delta Y (not used).
     * @param ex Absolute X coordinate.
     * @param ey Absolute Y coordinate (not used).
     * @return True if the move was handled.
     */
    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float): Boolean {
        if (selectedCircleHandleIndex != -1) {
            val circleX = circleHandles[selectedCircleHandleIndex].x

            // Only update if the position actually changed.
            if (ex != circleX) {
                // Constrain the handle position within the drawable area.
                circleHandles[selectedCircleHandleIndex].x = ex.coerceIn(drawingStart, widthF)

                isShaderDirty = true
                isOnMoveCalled = true

                invalidate()
            }
        }
        return true
    }

    /**
     * Called when a move gesture ends.
     * Triggers the change-ended callback if any changes occurred.
     */
    override fun onMoveEnded(lastX: Float, lastY: Float) {
        if (isMoved || isOnMoveCalled) {
            onColorsOrPositionsChangeEnded?.invoke()
            isMoved = false
            isOnMoveCalled = false
        }
    }

    /**
     * Called when a finger first touches the screen.
     * @return False to allow other gesture detectors to handle the event.
     */
    override fun onDown(p0: MotionEvent): Boolean {
        return false
    }

    /**
     * Called when a finger is pressed and held (before long press timeout).
     * No action needed for this gesture.
     */
    override fun onShowPress(p0: MotionEvent) {
    }

    /**
     * Called when a single tap is confirmed.
     * Triggers circle click listeners if a handle was tapped.
     *
     * @return True if the tap was handled, false otherwise.
     */
    override fun onSingleTapUp(p0: MotionEvent): Boolean {
        if (selectedCircleHandleIndex != -1 && !isNewCircleCreated) {
            callCircleListeners(selectedCircleHandleIndex)
            return true
        }
        isNewCircleCreated = false
        return false
    }

    /**
     * Called during scroll gestures.
     * @return False as scrolling is not handled by this view.
     */
    override fun onScroll(
        e1: MotionEvent?,
        p0: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return false
    }

    /**
     * Called when a long press is detected.
     * Removes the selected circle handle if more than 2 handles exist.
     */
    override fun onLongPress(p0: MotionEvent) {
        // Maintain minimum of 2 handles for a valid gradient.
        if (circleHandles.size > 2) {
            circleHandles.removeAt(selectedCircleHandleIndex)
            isShaderDirty = true
            selectedCircleHandleIndex = -1
            isMoved = true
            invalidate()
        }
    }

    /**
     * Called during fling gestures.
     * @return False as flinging is not handled by this view.
     */
    override fun onFling(
        e1: MotionEvent?,
        p0: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return false
    }

    /**
     * Handles touch events by delegating to move and gesture detectors.
     *
     * @param event The touch event.
     * @return True if the event was handled.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return if (event != null) {
            moveDetector.onTouchEvent(event).or(gestureDetector.onTouchEvent(event))
        } else {
            super.onTouchEvent(null)
        }
    }

    /**
     * Measures the view dimensions.
     * Supports wrap_content for height with a default size.
     */
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

    /**
     * Called when the view's layout changes.
     * Recalculates drawing bounds based on the new dimensions.
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        calculateBounds(width.toFloat(), height.toFloat())
    }

    /**
     * Calculates the drawing bounds and positions based on view dimensions and padding.
     *
     * @param targetWidth The target width for calculations.
     * @param targetHeight The target height for calculations.
     */
    protected open fun calculateBounds(targetWidth: Float, targetHeight: Float) {
        heightF = targetHeight - paddingBottom - paddingTop

        // Account for additional padding when using wrap_content.
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

        // Calculate stroke width adjustment for rounded caps.
        strokeWidthHalf = if (gradientLinePaint.strokeCap != Paint.Cap.BUTT) {
            // If paint cap is not BUTT then add half the width of stroke at start and end of line.
            gradientLinePaint.strokeWidth * 0.5f
        } else {
            0f
        }
    }

    /**
     * Draws the gradient slider on the canvas.
     * This includes the gradient line and all circle handles.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.also { c ->

            // Initialize with default white-to-black gradient if no handles exist.
            if (!isFirstCirclesAdded && circleHandles.isEmpty()) {
                circleHandles.add(CircleHandle(drawingStart, Color.WHITE))
                circleHandles.add(CircleHandle(widthF, Color.BLACK))
                isFirstCirclesAdded = true
            }

            // Recreate the gradient shader if needed.
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

                // Notify listeners of the change (but not during initial setup).
                if (wasInitialized) {
                    callColorAndPositionListeners(colors, positions)

                    if (isMoved) {
                        onColorsOrPositionsChangeEnded?.invoke()
                        isMoved = false
                    }
                }
            }

            // Draw the gradient line.
            c.drawLine(
                drawingStart,
                drawingTop,
                widthF,
                drawingTop, gradientLinePaint
            )

            // Draw circle handles with stroke and fill.
            val finalLowerCircleRadius = colorCircleStrokeWidth + colorCircleRadius

            circleHandles.forEach {
                // Draw the stroke circle.
                circlesPaint.color = circlesStrokeColor

                c.drawCircle(
                    it.x,
                    drawingTop,
                    finalLowerCircleRadius,
                    circlesPaint
                )

                // Draw the colored fill circle.
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

    /**
     * Sets a lambda callback for circle click events.
     *
     * @param listener The callback to invoke when a circle is clicked.
     */
    open fun setOnCircleClickedListener(listener: (index: Int) -> Unit) {
        onCircleHandleClicked = listener
    }

    /**
     * Sets an interface-based callback for circle click events.
     *
     * @param listener The callback to invoke when a circle is clicked.
     */
    open fun setOnCircleClickedListener(listener: OnCircleClickListener) {
        onCircleHandleClickedListener = listener
    }

    /**
     * Invokes all registered circle click listeners.
     *
     * @param index The index of the clicked circle.
     */
    protected open fun callCircleListeners(index: Int) {
        onCircleHandleClicked?.invoke(index)
        onCircleHandleClickedListener?.onClicked(index)
    }

    /**
     * Sets an interface-based callback for colors and positions change events.
     *
     * @param listener The callback to invoke when colors or positions change.
     */
    open fun setOnColorsAndPositionsChangedListener(listener: OnColorsAndPositionsChanged) {
        colorsAndPositionsChangedCallback = listener
    }

    /**
     * Sets a lambda callback for colors and positions change events.
     *
     * @param listener The callback to invoke when colors or positions change.
     */
    open fun setOnColorsAndPositionsChangedListener(listener: (colors: IntArray, positions: FloatArray) -> Unit) {
        onColorsAndPositionsChanged = listener
    }

    /**
     * Sets a callback for when color or position changes have ended.
     *
     * @param listener The callback to invoke when changes end.
     */
    open fun setOnColorOrPositionsChangeEnded(listener: () -> Unit) {
        onColorsOrPositionsChangeEnded = listener
    }

    /**
     * Invokes all registered colors and positions change listeners.
     *
     * @param colors The current colors array.
     * @param positions The current positions array.
     */
    protected open fun callColorAndPositionListeners(colors: IntArray, positions: FloatArray) {
        colorsAndPositionsChangedCallback?.onChanged(colors, positions)
        onColorsAndPositionsChanged?.invoke(colors, positions)
    }

    /**
     * Changes the color of a circle handle at the specified index.
     *
     * @param color The new color for the circle.
     * @param index The index of the circle to change.
     * @throws IllegalStateException If the index is out of bounds.
     */
    open fun changeColorOfCircleAt(@ColorInt color: Int, index: Int) {
        if (index < 0 || index > circleHandles.size) throw IllegalStateException("current index is out of bounds of circle handles.")
        changeColorAt(color, index)
    }

    /**
     * Changes the color of the currently selected circle handle.
     *
     * @param color The new color for the selected circle.
     */
    open fun changeColorOfSelectedCircle(@ColorInt color: Int) {
        changeColorAt(color, selectedCircleHandleIndex)
    }

    /**
     * Internal method to change the color of a circle at the specified index.
     *
     * @param color The new color.
     * @param index The index of the circle to change.
     */
    protected open fun changeColorAt(@ColorInt color: Int, index: Int) {
        circleHandles[index].color = color
        isShaderDirty = true
        isMoved = true
        invalidate()
    }

    /**
     * Gets the current colors of all circle handles in sorted order.
     *
     * @return Array of colors from left to right.
     */
    open fun getCurrentColors(): IntArray {
        return if (isFirstCirclesAdded) {
            circleHandles.sortedBy { it.x }.map { it.color }.toIntArray()
        } else {
            intArrayOf(Color.WHITE, Color.BLACK)
        }
    }

    /**
     * Gets the current positions of all circle handles in sorted order.
     *
     * @return Array of positions from 0.0 to 1.0.
     */
    open fun getPositions(): FloatArray {
        return if (isFirstCirclesAdded) {
            circleHandles.sortedBy { it.x }.map { it.calculateRelativePosition(widthF) }
                .toFloatArray()
        } else {
            floatArrayOf(0f, 1f)
        }
    }

    /**
     * Sets the colors and positions of the gradient programmatically.
     *
     * @param colors Array of colors (minimum 2 required).
     * @param positions Array of positions from 0.0 to 1.0 (must match colors length).
     * @throws IllegalStateException If arrays are invalid.
     */
    open fun setColorsAndPositions(colors: IntArray, positions: FloatArray) {
        if (colors.size < 2) {
            throw IllegalStateException("colors array size should be equal or more than 2.")
        }
        if (positions.size < 2) {
            throw IllegalStateException("positions array size should be equal or more than 2.")
        }

        if (colors.size != positions.size) {
            throw IllegalStateException("colors array and position array do not have same length.")
        }

        circleHandles.clear()

        // Wait for layout to complete before positioning handles.
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

    /**
     * Resets the slider to its initial state with default white-to-black gradient.
     */
    open fun resetSlider() {
        circleHandles.clear()
        isFirstCirclesAdded = false
        isShaderDirty = true
        invalidate()
    }

    /**
     * Represents a color handle on the gradient slider.
     *
     * @property x The X coordinate of the handle.
     * @property color The color of the handle.
     */
    protected data class CircleHandle(var x: Float, @ColorInt var color: Int) {
        /**
         * Calculates the relative position of this handle from 0.0 to 1.0.
         *
         * @param fromPosition The total width to calculate relative to.
         * @return The relative position as a float.
         */
        fun calculateRelativePosition(fromPosition: Float): Float = x / fromPosition
    }

    /**
     * Interface for circle click event callbacks.
     */
    interface OnCircleClickListener {
        /**
         * Called when a circle handle is clicked.
         *
         * @param index The index of the clicked circle.
         */
        fun onClicked(index: Int)
    }

    /**
     * Interface for colors and positions change event callbacks.
     */
    interface OnColorsAndPositionsChanged {
        /**
         * Called when the colors or positions of the gradient change.
         *
         * @param colors The current array of colors.
         * @param positions The current array of positions.
         */
        fun onChanged(colors: IntArray, positions: FloatArray)
    }
}

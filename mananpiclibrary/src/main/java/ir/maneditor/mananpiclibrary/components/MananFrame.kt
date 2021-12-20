package ir.maneditor.mananpiclibrary.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.withRotation
import androidx.core.view.children
import ir.maneditor.mananpiclibrary.R
import ir.maneditor.mananpiclibrary.properties.Scalable
import ir.maneditor.mananpiclibrary.utils.dp
import ir.maneditor.mananpiclibrary.utils.gesture.detectors.MoveDetector
import ir.maneditor.mananpiclibrary.utils.gesture.detectors.TwoFingerRotationDetector
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.SimpleOnMoveListener
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.SimpleOnRotateListener

/**
 * A class that extends [FrameLayout] class and overrides certain functions such as
 * [onInterceptTouchEvent] and [performClick] and [onTouchEvent] to get cleaner code
 * and custom behaviour. This class also takes responsibility for editing views(scaling, rotating and moving).
 */
class MananFrame(context: Context, attr: AttributeSet?) : FrameLayout(context, attr) {
    constructor(context: Context) : this(context, null)

    private var currentEditingView: View? = null

    private val boxPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }

    /**
     * stroke width of box around current editing view (if [isDrawingBoxEnabled] is true.)
     */
    var frameBoxStrokeWidth = dp(2)
        set(value) {
            boxPaint.strokeWidth = value
            field = value
        }

    /**
     * Color of box around current editing view (if [isDrawingBoxEnabled] is true.)
     */
    var frameBoxColor = Color.BLACK
        set(value) {
            boxPaint.color = value
            field = value
        }

    /**
     * If true, this ViewGroup draws a box around the current editing view.
     */
    var isDrawingBoxEnabled = false
        set(value) {
            field = value

            // Determine if ViewGroup is going to do drawing operations or not.
            setWillNotDraw(!value)

            if (value)
                invalidate()
        }


    private val scaleGestureListener by lazy {
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector?): Boolean {
                val currentView = currentEditingView
                if (currentView is Scalable) {
                    currentView.applyScale(
                        detector!!.scaleFactor,
                        width,
                        height
                    )
                }
                return true
            }
        }
    }

    private val scaleDetector by lazy {
        ScaleGestureDetector(context, scaleGestureListener).apply {
            // This needs to be false because it will interfere with other gestures.
            isQuickScaleEnabled = false
        }
    }

    private val rotateGestureListener by lazy {
        object : SimpleOnRotateListener() {
            override fun onRotate(degree: Float): Boolean {
                currentEditingView?.rotation = degree
                return true
            }
        }
    }

    private val rotateDetector by lazy { TwoFingerRotationDetector(rotateGestureListener) }

    private val moveGestureListener by lazy {
        object : SimpleOnMoveListener() {

            override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
                performClick()

                val childAtPosition = getChildAtPoint(initialX, initialY)

                // If returned child is not null and it is not referencing the same object that
                // current editable view is referencing then change editing view.
                if (childAtPosition != null && currentEditingView !== childAtPosition) {
                    rotateDetector.resetRotation(childAtPosition.rotation)
                    currentEditingView = childAtPosition
                    invalidate()
                }

                return currentEditingView != null
            }

            override fun onMove(dx: Float, dy: Float): Boolean {
                return if (currentEditingView != null) {
                    val currentView = currentEditingView!!

                    currentView.x += dx
                    currentView.y += dy

                    // Don't let the view go beyond the phone's display and limit it's y axis.
                    if ((currentView.y + currentView.height) > height) currentView.y =
                        (height - currentView.height).toFloat()

                    if (currentView.y < y) currentView.y = y

                    if ((currentView.x + currentView.width) > width) currentView.x =
                        (width - currentView.width).toFloat()

                    if (currentView.x < x) currentView.x = x

                    true
                } else false
            }
        }
    }

    private val moveDetector by lazy {
        MoveDetector(1, moveGestureListener)
    }

    private val commonGestureDetector by lazy {
        GestureDetector(context, commonGestureListener).apply {
            setOnDoubleTapListener(doubleTapListener)
        }
    }

    private val doubleTapListener by lazy {
        object : GestureDetector.OnDoubleTapListener {
            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                return true
            }

            override fun onDoubleTap(e: MotionEvent?): Boolean {
                if (e != null) {
                    currentEditingView = null
                    return true
                }
                return false
            }

            override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
                return false
            }
        }
    }

    private val commonGestureListener by lazy {
        object : GestureDetector.OnGestureListener {
            override fun onDown(e: MotionEvent?): Boolean {
                return true
            }

            override fun onShowPress(e: MotionEvent?) {
            }

            override fun onSingleTapUp(e: MotionEvent?): Boolean {
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent?,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                return false
            }

            override fun onLongPress(e: MotionEvent?) {

            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent?,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                return false
            }
        }
    }

    init {
        context.theme.obtainStyledAttributes(attr, R.styleable.MananFrame, 0, 0).apply {
            try {
                isDrawingBoxEnabled =
                    getBoolean(R.styleable.MananFrame_isDrawingBoxEnabled, false)

                if (isDrawingBoxEnabled) {

                    frameBoxColor = getColor(R.styleable.MananFrame_frameBoxColor, Color.BLACK)

                    frameBoxStrokeWidth =
                        getDimension(
                            R.styleable.MananFrame_frameBoxStrokeWidth,
                            frameBoxStrokeWidth
                        )
                }

            } finally {
                recycle()
            }
        }
        // Let some components like TextView be able to draw things outside their bounds (shadow layer and etc...)
        clipChildren = false

    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return when (ev!!.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentEditingView != null
            }
            MotionEvent.ACTION_MOVE -> {
                // We're currently moving the child, so intercept the event.
                true
            }
            else -> {
                false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Bind appropriate gestures to the ongoing event.
        scaleDetector.onTouchEvent(event)
        rotateDetector.onTouchEvent(event)
        moveDetector.onTouchEvent(event)
        commonGestureDetector.onTouchEvent(event)
        when (event?.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                invalidate()
            }
        }
        return true
    }

    override fun draw(canvas: Canvas?) {
        super.draw(canvas)
        // Draw the box around view.
        if (currentEditingView != null && isDrawingBoxEnabled) {
            val view = currentEditingView!!
            val editingViewX = view.x
            val editingViewY = view.y

            val editingViewWidth = view.width
            val editingViewHeight = view.height

            val editingViewRotation = view.rotation

            val pivotPointX = (editingViewX + editingViewWidth * 0.5f)
            val pivotPointY = (editingViewY + editingViewHeight * 0.5f)

            canvas!!.run {
                withRotation(
                    editingViewRotation,
                    pivotPointX,
                    pivotPointY
                ) {
                    drawRect(
                        editingViewX,
                        editingViewY,
                        editingViewWidth + editingViewX,
                        editingViewHeight + editingViewY,
                        boxPaint
                    )
                }
            }
        }
    }

    /**
     * Returns the child at given coordinates.
     * If two child overlap it swaps between them on each tap.
     * @param x X coordinate of current touch.
     * @param y Y coordinate of current touch.
     */
    private fun getChildAtPoint(x: Float, y: Float): View? {
        if (childCount == 0) return null

        // Create a matrix and later apply view rotation to it
        // to apply rotation to event coordinates in case
        // view has been rotated.
        val rotationMatrix = Matrix()
        children.forEach { v ->
            if (v !== currentEditingView) {
                // Converting points to float array is required to use matrix 'mapPoints' method.
                val touchPoints = floatArrayOf(x, y)

                // Rotate matrix to amount of view's rotation with pivot points being center of
                // view in each coordinate.
                rotationMatrix.setRotate(
                    -v.rotation,
                    (v.x + v.pivotX),
                    (v.y + v.pivotY)
                )

                // Finally apply rotation to the current coordinate of touch x and y.
                rotationMatrix.mapPoints(touchPoints)

                val viewX = v.x
                val viewY = v.y

                rotationMatrix.reset()

                // TouchPoints[0] is rotation applied x and TouchPoints[1] is rotation applied y.
                if (touchPoints[0] in viewX..viewX + v.width && touchPoints[1] in viewY..viewY + v.height) {
                    return v
                }
            }
        }
        return null
    }

    /**
     * Draws content of the layout onto a bitmap.
     * This method first makes the background color white and draws the content on a bitmap then makes background transparent.
     * @param bitmap The bitmap that is going to be drawn on.
     */
    fun drawToBitmap(bitmap: Bitmap) {
        setBackgroundColor(Color.WHITE)
        val lastSelectedView = currentEditingView
        currentEditingView = null
        invalidate()
        draw(Canvas(bitmap))
        setBackgroundColor(Color.TRANSPARENT)
        currentEditingView = lastSelectedView
    }

    /**
     * Selects a view in view group to enter editing state.
     * It does not throw exception if child in given index is null.
     * @param index Index of view that is going to be selected.
     */
    fun selectView(index: Int) {
        val selectedChild = getChildAt(index)
        if (selectedChild != null) {
            currentEditingView = selectedChild
            rotateDetector.resetRotation(currentEditingView!!.rotation)
            invalidate()
        }
    }

    /**
     * Removes the view that is currently selected.
     */
    fun removeSelectedView() {
        if (currentEditingView != null) {
            removeView(currentEditingView)
            currentEditingView = null
        }
    }

    /**
     * Returns currently selected child.
     */
    fun getSelectedView(): View? {
        return currentEditingView
    }

    override fun invalidate() {
        // Only invalidate if drawing box is enabled.
        if (isDrawingBoxEnabled)
            super.invalidate()
    }

    override fun addView(child: View?, index: Int) {
        initializeChild(child)
        super.addView(child, index)
    }

    override fun addView(child: View?) {
        initializeChild(child)
        super.addView(child)
    }

    private fun initializeChild(child: View?) {
        child?.run {
            layoutParams =
                LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )

            if (this is ImageView) adjustViewBounds = true

            rotateDetector.resetRotation(child.rotation)

            currentEditingView = this
        }
    }

}
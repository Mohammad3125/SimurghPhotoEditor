package ir.manan.mananpic.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import ir.manan.mananpic.R
import ir.manan.mananpic.properties.MananComponent
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.detectors.MoveDetector
import ir.manan.mananpic.utils.gesture.detectors.TwoFingerRotationDetector
import ir.manan.mananpic.utils.gesture.gestures.SimpleOnMoveListener
import ir.manan.mananpic.utils.gesture.gestures.SimpleOnRotateListener

/**
 * A class that extends [FrameLayout] class and overrides certain functions such as
 * [onInterceptTouchEvent] and [performClick] and [onTouchEvent] to get cleaner code
 * and custom behaviour. This class also takes responsibility for editing views(scaling, rotating and moving).
 */
class MananFrame(context: Context, attr: AttributeSet?) : FrameLayout(context, attr) {
    constructor(context: Context) : this(context, null)

    private var currentEditingView: MananComponent? = null

    private val boxPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }

    private var onChildClicked: ((View, Boolean) -> Unit)? = null
    private var onChildClickedListener: OnChildClickedListener? = null

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
                currentEditingView?.applyScale(detector!!.scaleFactor)
                return true
            }
        }
    }

    private val scaleDetector by lazy {
        ScaleGestureDetector(context, scaleGestureListener).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // This needs to be false because it will interfere with other gestures.
                isQuickScaleEnabled = false
            }
        }
    }

    private val rotateGestureListener by lazy {
        object : SimpleOnRotateListener() {
            override fun onRotate(degree: Float): Boolean {
                currentEditingView?.applyRotation(degree)
                return true
            }
        }
    }

    private val rotateDetector by lazy { TwoFingerRotationDetector(rotateGestureListener) }

    private val moveGestureListener by lazy {
        object : SimpleOnMoveListener() {

            override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
                performClick()

                val childAtPosition = getChildAtPoint(initialX, initialY) as? MananComponent

                // If returned child is not null and it is not referencing the same object that
                // current editable view is referencing then change editing view.
                if (currentEditingView !== childAtPosition && childAtPosition != null) {
                    rotateDetector.resetRotation(childAtPosition.reportRotation())
                    callListeners(childAtPosition as View, true)
                    currentEditingView = childAtPosition
                    invalidate()
                }

                return currentEditingView != null
            }

            override fun onMove(dx: Float, dy: Float): Boolean {
                return if (currentEditingView != null) {
                    currentEditingView!!.applyMovement(dx, dy)

//                        if (currentView is MatrixComponent) {
//                            val bounds = currentView.reportBound()
//
//                            var totalXToMove = 0f
//                            var totalYToMove = 0f
//
//                            if (bounds.left < 0f)
//                                totalXToMove = abs(bounds.left)
//                            else if (bounds.right > width)
//                                totalXToMove = width - bounds.right
//
//                            if (bounds.top < 0f)
//                                totalYToMove = abs(bounds.top)
//                            else if (bounds.bottom > height)
//                                totalYToMove = height - bounds.bottom
//
//                            currentView.applyMovement(totalXToMove, totalYToMove)
//                        }


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
                    deselectSelectedView()
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

            // Get bounds of component to create a rectangle with it.
            val bound = view.reportBound()

            canvas!!.run {

                // Take a snapshot of current state of canvas.
                save()

                // Match the rotation of canvas to view to be able to
                // draw rotated rectangle.
                rotate(
                    view.reportRotation(),
                    view.reportBoundPivotX(),
                    view.reportBoundPivotY()
                )

                // Draw a box around component.
                drawRect(
                    bound.left,
                    bound.top,
                    bound.right,
                    bound.bottom,
                    boxPaint
                )

                // Restore the previous state of canvas which is not rotated.
                restore()

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
            v as MananComponent
            if (v !== currentEditingView) {
                // Converting points to float array is required to use matrix 'mapPoints' method.
                val touchPoints = floatArrayOf(x, y)

                // Get bounds of current component to later validate if touch is in area of
                // that component or not.
                val bounds = v.reportBound()

                // Rotate matrix to amount of view's rotation with pivot points being center of
                // view in each coordinate.
                rotationMatrix.setRotate(
                    -v.reportRotation(),
                    v.reportBoundPivotX(),
                    v.reportBoundPivotY()
                )

                // Finally apply rotation to the current coordinate of touch x and y.
                rotationMatrix.mapPoints(touchPoints)

                if (touchPoints[0] in bounds.left..bounds.right && touchPoints[1] in bounds.top..bounds.bottom)
                    return v
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
        val selectedChild = getChildAt(index) as? MananComponent
        if (selectedChild != null) {
            callListeners(selectedChild as View, true)
            currentEditingView = selectedChild
            rotateDetector.resetRotation(selectedChild.reportRotation())
            invalidate()
        }
    }

    /**
     * Deselects the current selected view.
     * This method doesn't throw exception if there isn't any child selected.
     */
    fun deselectSelectedView() {
        if (currentEditingView != null) {

            callListeners(currentEditingView as View, false)

            currentEditingView = null
            invalidate()
        }
    }

    /**
     * Removes the view that is currently selected.
     */
    fun removeSelectedView() {
        if (currentEditingView != null) {

            callListeners(currentEditingView as View, false)

            removeView(currentEditingView as View)
            currentEditingView = null
        }
    }

    private fun callListeners(view: View, isSelected: Boolean) {
        onChildClicked?.invoke(view, isSelected)
        onChildClickedListener?.onClicked(view, isSelected)

    }

    /**
     * Returns currently selected child.
     */
    fun getSelectedView(): View? {
        return currentEditingView as? View
    }

    override fun invalidate() {
        // Only invalidate if drawing box is enabled.
        if (isDrawingBoxEnabled)
            super.invalidate()
    }

    override fun onViewAdded(child: View?) {
        if (child !is MananComponent) throw IllegalStateException("only components that implement MananComponent can be added")

        initializeChild(child)
        super.onViewAdded(child)
    }

    private fun initializeChild(child: View) {
        child.run {

            updateLayoutParams<LayoutParams> {
                gravity = Gravity.CENTER
            }

            rotateDetector.resetRotation((child as MananComponent).reportRotation())

            callListeners(child, true)

            currentEditingView = this as MananComponent
        }
    }

    /**
     * Sets listener for when child get clicked.
     * This listener will not get re-invoked if user click the selected component again.
     */
    fun setOnChildClicked(listener: (View, Boolean) -> Unit) {
        onChildClicked = listener
    }

    /**
     * Sets listener for when child get clicked.
     * This listener will not get re-invoked if user click the selected component again.
     */
    fun setOnChildClicked(listener: OnChildClickedListener) {
        onChildClickedListener = listener
    }


    /**
     * Interface definition for callback that get invoked when selection state of
     * a child in [MananFrame] changes.
     */
    interface OnChildClickedListener {
        /**
         * This method get invoked when child selection state chagnes.
         * @param view Clicked view.
         * @param isSelected Determines if view is in selected state or deselected state.
         */
        fun onClicked(view: View, isSelected: Boolean)
    }

}
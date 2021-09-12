package ir.maneditor.mananpiclibrary

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import ir.maneditor.mananpiclibrary.properties.Scalable
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min


class EditableView(context: Context, attr: AttributeSet?) : ViewGroup(context, attr) {

    constructor(context: Context) : this(context, null)

    private var drawFrame: Boolean = true

    private var scaleFactor = 1f

    private val mainChild: View
        get() =
            children.first()


    private var initialX = 0f
    private var initialY = 0f

    private var initialRotation = 0f

    private var pointerCount = 0

    // It will notify the motion event that user is gesturing a new gesture on the screen
    private var newGesture = false

    private lateinit var viewParent: ViewGroup

    private val mainFrameBoundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 2.dp
        style = Paint.Style.STROKE
    }

    private val frameLayoutRectangle = RectF()

    private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {


        override fun onScale(detector: ScaleGestureDetector?): Boolean {

            scaleFactor *= detector!!.scaleFactor
            scaleFactor = max(0.1f, min(scaleFactor, 100f))

            if (mainChild is Scalable) {
                (mainChild as? Scalable)?.applyScale(scaleFactor)
                resetViewToParentBounds(mainChild)
            }

            return true
        }
    }


    private val scaleDetector = ScaleGestureDetector(context, scaleListener)


    private val motionEventHandler: (view: View, event: MotionEvent) -> Boolean = { v, event ->
        // Add motion event to the scale detector
        scaleDetector.onTouchEvent(event)

        pointerCount = event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (drawFrame) {
                    // Get the initial x and y of current view
                    initialX = v.x - event.rawX
                    initialY = v.y - event.rawY
                }
                performClick()

            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Calculate the rotating when two pointers are on the screen.
                initialRotation = event.run {
                    calculateTheRotation(
                        (getX(0) - getX(1)).toDouble(),
                        (getY(1) - getY(0)).toDouble()
                    )
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // If view is in editing state (got clicked)
                if (drawFrame) {
                    /* Moving the view by touch */

                    // and if there is only 1 pointer on the screen
                    if (pointerCount == 1 && newGesture) {

                        // Move the view
                        v.x = event.rawX + initialX
                        v.y = event.rawY + initialY

                        // Don't let the view go beyond the phone's display and limit it's x and y
                        viewParent.run {
                            if ((v.y + v.height) >= height) v.y =
                                (height - v.height).toFloat()

                            if (v.y <= y) v.y = y
                        }
                    }

                    /* Rotating the view by touch */
                    // If there are total of two pointer on the screen
                    else if (pointerCount == 2) {
                        // Rotate the ViewGroup
                        rotation += event.run {
                            calculateTheRotation(
                                (getX(0) - getX(1)).toDouble(),
                                (getY(1) - getY(0)).toDouble()
                            ) - initialRotation
                        }
                    }
                }
            }
            // Do not let the moving gesture continue it's work, because it shifts the view while rotating or scaling.
            MotionEvent.ACTION_POINTER_UP -> {
                newGesture = false
            }
            // After all of the fingers were lifted from screen, then we can make a move gesture.
            MotionEvent.ACTION_UP -> {
                newGesture = true
            }

        }
        true
    }

    init {
        setWillNotDraw(false)

        // Elevating the editable view in z direction
        // This will allow the view to be on the top if it's selected
        // Therefore it wouldn't get stuck behind a view.
        translationZ += 10f
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return when (ev!!.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                true
            }
            MotionEvent.ACTION_MOVE -> {
                // We're currently moving the child, so intercept the event
                true
            }
            else -> {
                false
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setOnTouchListener(motionEventHandler)
        viewParent = parent as ViewGroup
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (doesHaveChild()) {

            mainChild.run {

                measureChild(this, widthMeasureSpec, heightMeasureSpec)

                setMeasuredDimension(
                    resolveSize(
                        measuredWidth,
                        widthMeasureSpec
                    ),
                    resolveSize(measuredHeight, heightMeasureSpec)
                )

            }
            return
        }
        setMeasuredDimension(0, 0)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (doesHaveChild() && changed) {
            mainChild.run {
                layout(
                    0,
                    0,
                    measuredWidth,
                    measuredHeight
                )

                frameLayoutRectangle.set(
                    0f,
                    0f,
                    measuredWidth.toFloat(),
                    measuredHeight.toFloat()
                )
            }

        }
    }

    override fun dispatchDraw(canvas: Canvas?) {
        super.dispatchDraw(canvas)
        if (drawFrame)
            canvas!!.apply {
                drawRoundRect(frameLayoutRectangle, 0f, 0f, mainFrameBoundaryPaint)
            }
    }

    override fun shouldDelayChildPressedState(): Boolean {
        return false
    }

    /**
     * This function resets child's x and y to the parents (keeps children centered while scaling happens)
     * @param view the child view.
     */
    private fun resetViewToParentBounds(view: View) {
        view.x = 0f
        view.y = 0f
    }


    /**
     * This function calculates the degree of rotation with the given delta x and y.
     * The rotation is calculated by the angle of two touch points.
     * @param deltaX Difference between x in touches (two pointers)
     * @param deltaY Difference between y in touches (two pointers)
     */
    private fun calculateTheRotation(deltaX: Double, deltaY: Double): Float {
        return Math.toDegrees(
            atan2(
                deltaX,
                deltaY
            )
        ).toFloat()
    }


    fun showFrameAroundView() {
        // Show the rectangle frame around the view
        drawFrame = true
        invalidate()
    }

    private fun hideFrameAroundView() {
        // Hide the rectangle around the view (meaning it's not longer in editing state)
        drawFrame = false
        invalidate()
    }

    override fun addView(child: View?) {
        resetTheView(child)

        super.addView(child)

        resetViewToParent(child)

        drawFrame = true
    }


    /**
     * This method removes the view that is holding and returns it.
     * It also removes the frame around that view and resets the view's x and y to the parent's x and y.
     * @return Returns the view from the ViewGroup.
     */
    fun disengageView(): View {
        val viewToReturn = mainChild

        setTheView(viewToReturn)

        removeAllViews()

        hideFrameAroundView()

        return viewToReturn
    }


    fun doesHaveChild(): Boolean {
        return childCount > 0
    }

    private fun resetTheView(view: View?) {
        x = view!!.x
        y = view.y
        rotation = view.rotation
    }

    private fun setTheView(view: View?) {
        view!!.x = x
        view.y = y
        view.rotation = rotation
    }

    private fun resetViewToParent(view: View?) {
        view!!.rotation = 0f
        view.x = 0f
        view.y = 0f
    }
}
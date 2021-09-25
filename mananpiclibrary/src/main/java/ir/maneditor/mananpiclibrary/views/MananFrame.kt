package ir.maneditor.mananpiclibrary.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import ir.maneditor.mananpiclibrary.EditableView
import ir.maneditor.mananpiclibrary.properties.Scalable
import kotlin.math.abs
import kotlin.math.atan2

/**
 * A class that extends [FrameLayout] class and overrides certain functions such as
 * [onInterceptTouchEvent] and [performClick] and [onTouchEvent] to get cleaner code
 * and custom behaviour. This class works with [EditableView] to make views editable.
 */
class MananFrame(context: Context, attr: AttributeSet?) : FrameLayout(context, attr) {
    constructor(context: Context) : this(context, null)

    private val editableView by lazy {
        EditableView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private var initialX = 0f
    private var initialY = 0f

    private var scaleFactor = 0.13f

    private var totalInitialScaling = 0f

    private var initialRotation = 0f

    private var pointerCount = 0

    // It will notify the motion event that user is gesturing a new gesture on the screen.
    private var newGesture = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addView(editableView)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return when (ev!!.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                false
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

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        pointerCount = event!!.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Get the initial x and y of current view.
                initialX = editableView.x - event.rawX
                initialY = editableView.y - event.rawY
                editableView.performClick()
                performClick()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Calculate the rotating when two pointers are on the screen.
                event.run {
                    initialRotation -= calculateTheRotation(
                        (getX(0) - getX(1)).toDouble(),
                        (getY(1) - getY(0)).toDouble()
                    )

                    // Calculate the initial pointers at first to use it as a reference point.
                    totalInitialScaling = abs((getX(0) - getX(1))) + abs((getY(0) - getY(1)))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // If view is in editing state (got clicked).
                /* Moving the view by touch */

                // and if there is only 1 pointer on the screen.
                if (pointerCount == 1 && newGesture) {

                    // Move the view
                    editableView.x = event.rawX + initialX
                    editableView.y = event.rawY + initialY

                    // Don't let the view go beyond the phone's display and limit it's y axis.
                    if ((editableView.y + editableView.height) >= height) editableView.y =
                        (height - editableView.height).toFloat()

                    if (editableView.y <= y) editableView.y = y
                }

                // If there are total of two pointer on the screen.
                else if (pointerCount == 2) {
                    /* Rotating the view by touch */
                    // Rotate the ViewGroup
                    event.run {
                        editableView.rotation =
                            calculateTheRotation(
                                (getX(0) - getX(1)).toDouble(),
                                (getY(1) - getY(0)).toDouble()
                            ) + initialRotation

                        (editableView.mainChild as? Scalable)?.applyScale(
                            (((abs((getX(0) - getX(1))) + abs((getY(0) - getY(1)))) -
                                    totalInitialScaling) * scaleFactor / (editableView.width + editableView.height)) + 1f
                        )

                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Do not let the moving gesture continue it's work, because it
                // shifts the view while rotating or scaling.
                newGesture = false

            }
            // After all of the fingers were lifted from screen, then we can make a move gesture.
            MotionEvent.ACTION_UP -> {
                newGesture = true
                initialRotation = editableView.rotation
            }
        }
        return true
    }


    /**
     * This function calculates the degree of rotation with the given delta x and y.
     * The rotation is calculated by the angle of two touch points.
     * @param deltaX Difference between x in touches (two pointers).
     * @param deltaY Difference between y in touches (two pointers).
     */
    private fun calculateTheRotation(deltaX: Double, deltaY: Double): Float {
        return Math.toDegrees(
            atan2(
                deltaX,
                deltaY
            )
        ).toFloat()
    }


    /**
     * Draws content of the layout onto a bitmap.
     * This method first makes the background color white and then hides frame around
     * the view and then draws the content on a bitmap and then makes background transparent
     * and shows the frame around the current editing view.
     *
     * @param bitmap The bitmap that is going to be drawn on.
     */
    fun drawToBitmap(bitmap: Bitmap) {
        setBackgroundColor(Color.WHITE)
        editableView.hideFrameAroundView()
        draw(Canvas(bitmap))
        editableView.showFrameAroundView()
        setBackgroundColor(Color.TRANSPARENT)
    }

    /**
     * Adds the view to the [EditableView] and it becomes editable.
     * @param view The view that is going to be added to the [EditableView]
     */
    fun addViewToEdit(view: View) {
        if (editableView.doesHaveChild())
            addView(editableView.disengageView())

        removeView(view)
        editableView.addView(view)
    }
}
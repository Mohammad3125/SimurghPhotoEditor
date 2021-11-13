package ir.maneditor.mananpiclibrary.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.withRotation
import androidx.core.view.updateLayoutParams
import ir.maneditor.mananpiclibrary.R
import ir.maneditor.mananpiclibrary.properties.Scalable
import ir.maneditor.mananpiclibrary.utils.dp
import kotlin.math.abs
import kotlin.math.atan2

/**
 * A class that extends [FrameLayout] class and overrides certain functions such as
 * [onInterceptTouchEvent] and [performClick] and [onTouchEvent] to get cleaner code
 * and custom behaviour. This class also takes responsibility for editing views(scaling, rotating and moving).
 */
class MananFrame(context: Context, attr: AttributeSet?) : FrameLayout(context, attr) {
    constructor(context: Context) : this(context, null)

    private var currentEditingView: View? = null

    private var initialX = 0f
    private var initialY = 0f

    /**
     * sensitivity of scaling algorithm (default is 1f).
     */
    var scaleFactor = 1f

    private var totalInitialScaling = 0f

    private var initialRotation = 0f

    private var pointerCount = 0

    // It will notify the motion event that user is gesturing a new gesture on the screen.
    private var newGesture = false

    private val boxPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }

    /**
     * stroke width of box around current editing view (if [isDrawingBoxAroundEditingViewEnabled] is true.)
     */
    var frameBoxStrokeWidth = 2.dp
        set(value) {
            boxPaint.strokeWidth = value
            field = value
        }

    /**
     * Color of box around current editing view (if [isDrawingBoxAroundEditingViewEnabled] is true.)
     */
    var frameBoxColor = Color.BLACK
        set(value) {
            boxPaint.color = value
            field = value
        }

    /**
     * If true, this ViewGroup draws a box around the current editing view.
     */
    var isDrawingBoxAroundEditingViewEnabled = false
        set(value) {
            field = value
            invalidate()
        }

    init {
        context.theme.obtainStyledAttributes(attr, R.styleable.MananFrame, 0, 0).apply {
            try {
                isDrawingBoxAroundEditingViewEnabled =
                    getBoolean(R.styleable.MananFrame_isDrawingBoxEnabled, false)

                setWillNotDraw(!isDrawingBoxAroundEditingViewEnabled)

                if (isDrawingBoxAroundEditingViewEnabled) {

                    frameBoxColor = getColor(R.styleable.MananFrame_frameBoxColor, Color.BLACK)

                    frameBoxStrokeWidth =
                        getDimension(R.styleable.MananFrame_frameBoxStrokeWidth, 2.dp)
                }

            } finally {
                recycle()
            }
        }

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

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (currentEditingView != null) {

            pointerCount = event!!.pointerCount

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Get the initial x and y of current view.
                    initialX = currentEditingView!!.x - event.rawX
                    initialY = currentEditingView!!.y - event.rawY
                    currentEditingView!!.performClick()
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
                        currentEditingView!!.x = event.rawX + initialX
                        currentEditingView!!.y = event.rawY + initialY

                        // Don't let the view go beyond the phone's display and limit it's y axis.
                        if ((currentEditingView!!.y + currentEditingView!!.height) > height) currentEditingView!!.y =
                            (height - currentEditingView!!.height).toFloat()

                        if (currentEditingView!!.y < 0f) currentEditingView!!.y = 0f

                        if ((currentEditingView!!.x + currentEditingView!!.width) > width) currentEditingView!!.x =
                            (width - currentEditingView!!.width).toFloat()

                        if (currentEditingView!!.x < 0f) currentEditingView!!.x = 0f
                    }

                    // If there are total of two pointer on the screen.
                    else if (pointerCount == 2) {
                        /* Rotating the view by touch */
                        // Rotate the ViewGroup
                        event.run {
                            currentEditingView!!.rotation =
                                calculateTheRotation(
                                    (getX(0) - getX(1)).toDouble(),
                                    (getY(1) - getY(0)).toDouble()
                                ) + initialRotation


                            if (currentEditingView is Scalable) {
                                val scalingDifference =
                                    (abs((getX(0) - getX(1))) + abs((getY(0) - getY(1))))

                                (currentEditingView as? Scalable)?.applyScale(
                                    ((scalingDifference -
                                            totalInitialScaling) * scaleFactor / (currentEditingView!!.width + currentEditingView!!.height)) + 1f,
                                    width,
                                    height
                                )

                                totalInitialScaling = scalingDifference
                            }
                        }
                    }
                    // Only invalidate if we're drawing box around the view.
                    if (isDrawingBoxAroundEditingViewEnabled)
                        invalidate()
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    // Do not let the moving gesture continue it's work, because it
                    // shifts the view while rotating or scaling.
                    newGesture = false
                    initialRotation = currentEditingView!!.rotation
                }
                // After all of the fingers were lifted from screen, then we can make a move gesture.
                MotionEvent.ACTION_UP -> {
                    newGesture = true
                }
            }
            return true
        }
        return false
    }

    override fun draw(canvas: Canvas?) {
        super.draw(canvas)
        // Draw the box around view.
        if (currentEditingView != null && isDrawingBoxAroundEditingViewEnabled) {
            val editingViewX = currentEditingView!!.x
            val editingViewY = currentEditingView!!.y

            val editingViewWidth = currentEditingView!!.width
            val editingViewHeight = currentEditingView!!.height

            val editingViewRotation = currentEditingView!!.rotation

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
     * This method first makes the background color white and draws the content on a bitmap then makes background transparent.
     * @param bitmap The bitmap that is going to be drawn on.
     */
    fun drawToBitmap(bitmap: Bitmap) {
        setBackgroundColor(Color.WHITE)
        draw(Canvas(bitmap))
        setBackgroundColor(Color.TRANSPARENT)
    }

    /**
     * Adds the view to the editing view and it becomes editable.
     * @param view The view that is going to be added.
     */
    fun addViewToEdit(view: View) {
        currentEditingView = view
        currentEditingView!!.updateLayoutParams<LayoutParams> {
            gravity = Gravity.CENTER
        }
    }
}
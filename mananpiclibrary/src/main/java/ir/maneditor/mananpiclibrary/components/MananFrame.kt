package ir.maneditor.mananpiclibrary.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.withRotation
import androidx.core.view.updateLayoutParams
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

    private val scaleDetector by lazy { ScaleGestureDetector(context, scaleGestureListener) }

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
            override fun onMove(dx: Float, dy: Float): Boolean {
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

                return true
            }
        }
    }

    private val moveDetector by lazy {
        MoveDetector(moveGestureListener)
    }

    init {
        context.theme.obtainStyledAttributes(attr, R.styleable.MananFrame, 0, 0).apply {
            try {
                isDrawingBoxAroundEditingViewEnabled =
                    getBoolean(R.styleable.MananFrame_isDrawingBoxEnabled, false)

                if (isDrawingBoxAroundEditingViewEnabled) {

                    frameBoxColor = getColor(R.styleable.MananFrame_frameBoxColor, Color.BLACK)

                    frameBoxStrokeWidth =
                        getDimension(R.styleable.MananFrame_frameBoxStrokeWidth, 2.dp)
                }

            } finally {
                recycle()
            }
        }
        // Let some components like TextView be able to draw things outside their bounds (shadow layer and etc...)
        clipChildren = false

        // Determine if ViewGroup is going to do drawing operations or not.
        setWillNotDraw(!isDrawingBoxAroundEditingViewEnabled)
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

            // Bind appropriate gestures to the ongoing event.
            scaleDetector.onTouchEvent(event)
            rotateDetector.onTouchEvent(event)
            moveDetector.onTouchEvent(event)

            val currentView = currentEditingView!!

            when (event?.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    currentView.performClick()
                    performClick()
                }
                MotionEvent.ACTION_MOVE -> {
                    // Only invalidate if we're drawing box around the view.
                    if (isDrawingBoxAroundEditingViewEnabled)
                        invalidate()
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
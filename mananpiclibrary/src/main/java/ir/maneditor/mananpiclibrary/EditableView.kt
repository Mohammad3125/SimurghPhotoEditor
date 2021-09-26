package ir.maneditor.mananpiclibrary

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import ir.maneditor.mananpiclibrary.utils.dp


@Deprecated(
    "This class is no longer responsible for editing views until further announcement (may be deleted soon.)",
    level = DeprecationLevel.WARNING
)
class EditableView(context: Context, attr: AttributeSet?) : ViewGroup(context, attr) {

    constructor(context: Context) : this(context, null)

    private var drawFrame: Boolean = true

    val mainChild: View
        get() =
            children.first()

    private lateinit var viewParent: ViewGroup

    private val mainFrameBoundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 2.dp
        style = Paint.Style.STROKE
    }

    private val frameLayoutRectangle = RectF()

    init {
        setWillNotDraw(false)

        // Elevating the editable view in z direction
        // that will allow the view to be on the top if it's selected
        // therefore it wouldn't get stuck behind a view.
        translationZ += 10f
    }


    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return when (ev!!.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                true
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
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

    fun showFrameAroundView() {
        // Show the rectangle frame around the view.
        drawFrame = true
        invalidate()
    }

    fun hideFrameAroundView() {
        // Hide the rectangle around the view (meaning it's not longer in editing state.)
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
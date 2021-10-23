package ir.maneditor.mananpiclibrary.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import ir.maneditor.mananpiclibrary.utils.dp
import ir.maneditor.mananpiclibrary.views.MananCropper.HandleBar.*

class MananCropper(context: Context, attr: AttributeSet?) : View(context, attr) {

    private val gridLinePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            strokeWidth = 1.dp
            style = Paint.Style.STROKE
        }
    }

    private val gridLineHandleBarPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY
            strokeWidth = 3.dp
            style = Paint.Style.STROKE
        }
    }
    private var mapOfHandleBars = crateHandleBarPointMap()

    private var gridLineDimension = createDimensions()
    private var gridLineHandleBarDimension = createHandleBarsDimensions()

    private var handleBar: HandleBar? = null

    private var initialX = 0f
    private var initialY = 0f

    private var initialWidth = 0
    private var initialHeight = 0

    constructor(context: Context, width: Int, height: Int) : this(context, null) {
        setInitialSize(width, height)
    }

    /**
     * Sets the initial size for cropper.
     * @param width Width of the cropper.
     * @param height Width of the cropper.
     */
    fun setInitialSize(width: Int, height: Int) {
        initialWidth = width
        initialHeight = height
        layoutParams = FrameLayout.LayoutParams(
            width,
            height
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // If we have a change in size then we should reinitialize the dimensions.
        if (w != oldw || h != oldh) {
            gridLineDimension = createDimensions()
            gridLineHandleBarDimension = createHandleBarsDimensions()
            mapOfHandleBars = crateHandleBarPointMap()
        }
    }

    override fun onDraw(canvas: Canvas?) {
        canvas!!.run {
            // Draw grid line
            drawLines(gridLineDimension, gridLinePaint)

            // Draw handle bars
            drawLines(gridLineHandleBarDimension, gridLineHandleBarPaint)
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event!!.actionMasked) {
            MotionEvent.ACTION_DOWN -> {

                // Figure out which handle bar is in range of the event.
                handleBar = figureOutWhichHandleIsInRangeOfEvent(
                    PointF(
                        event.x,
                        event.y
                    )
                )

                // Save the initial points the user touched to later calculate the difference between
                // Initial point and ongoing event to figure out how much the view should resize.
                initialX = event.x
                initialY = event.y

                performClick()

                // Only show interest if user touched a handle bar, otherwise don't
                // request to intercept the event because the parent is going to move
                // the cropper and if we intercept it, it won't work.
                parent.requestDisallowInterceptTouchEvent(handleBar != null)
                return handleBar != null
            }

            MotionEvent.ACTION_MOVE -> {
                // Difference between initial touch point and current ongoing event.
                val differenceY = (event.y - initialY)
                val differenceX = (event.x - initialX)

                // We don't directly change the height, width, x and y because we have to validate them
                // before changing anything.
                var heightToChange = height
                var widthToChange = width

                var xToChange = x
                var yToChange = y

                when (handleBar) {
                    TOP, BOTTOM -> {
                        // Change the height based on difference between points.
                        heightToChange -= (if (handleBar == TOP) differenceY else -differenceY).toInt()

                        // We shift x or y values because we want the view to not change it's position.
                        // If we don't do that, the view expands in both sides.
                        yToChange += differenceY / 2
                        if (handleBar == BOTTOM) initialY += differenceY
                    }
                    RIGHT, LEFT -> {
                        widthToChange += (if (handleBar == RIGHT) differenceX else -differenceX).toInt()

                        xToChange += differenceX / 2
                        if (handleBar == RIGHT) initialX += differenceX
                    }
                    TOP_LEFT, TOP_RIGHT -> {
                        heightToChange -= differenceY.toInt()
                        widthToChange -= (if (handleBar == TOP_LEFT) differenceX else -differenceX).toInt()

                        xToChange += differenceX / 2
                        yToChange += differenceY / 2
                        if (handleBar == TOP_RIGHT) initialX += differenceX
                    }
                    BOTTOM_LEFT, BOTTOM_RIGHT -> {
                        heightToChange += differenceY.toInt()
                        widthToChange -= (if (handleBar == BOTTOM_LEFT) differenceX else -differenceX).toInt()

                        yToChange += differenceY / 2
                        xToChange += differenceX / 2
                        initialY += differenceY
                        if (handleBar == BOTTOM_RIGHT) initialX += differenceX
                    }
                }
                // After calculation on width,height,x and y change them if they are not larger
                // than initial values.
                updateLayoutParams {
                    height = if (heightToChange > initialHeight) initialHeight else heightToChange
                    width = if (widthToChange > initialWidth) initialWidth else widthToChange
                }
                // If the changes are more than initial width or height don't allow the y and x to
                // change because if we don't, the view shifts as user drags his/her finger on screen.
                if (heightToChange < initialHeight)
                    y = yToChange
                if (widthToChange < initialWidth)
                    x = xToChange


            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                return false
            }
        }
        return true
    }

    /**
     * Figures which handle bar is responsible for the current point in screen.
     * If there are no handle bars in area of touch this method return null.
     * @param point Represents the points that's been touched.
     * @return Returns the handle bar responsible for given point (nullable).
     */
    private fun figureOutWhichHandleIsInRangeOfEvent(point: PointF): HandleBar? {
        // Iterate over handle bar points and figure where the touch is located and which handle bar is touched.
        for (pair in mapOfHandleBars.keys)
            if ((point.x in pair.first.x..pair.second.x) && (point.y in pair.first.y..pair.second.y))
                return mapOfHandleBars[pair]
        return null
    }


    /**
     * This method creates the dimension of grid line based on current width and height of view.
     * @return Returns a [FloatArray] representing the location of lines that should be drawn on screen.
     */
    private fun createDimensions(): FloatArray {
        val mHeight = measuredHeight.toFloat()
        val mWidth = measuredWidth.toFloat()
        return floatArrayOf(

            // Grids
            mWidth * 0.33f,
            0f,
            mWidth * 0.33f,
            mHeight,

            mWidth * 0.66f,
            0f,
            mWidth * 0.66f,
            mHeight,

            0f,
            mHeight * 0.33f,
            mWidth,
            mHeight * 0.33f,

            0f,
            mHeight * 0.66f,
            mWidth,
            mHeight * 0.66f,

            // Top Line
            0f,
            0f,
            mWidth,
            0f,

            // Left line
            0f,
            0f,
            0f,
            mHeight,

            // Bottom line
            0f,
            mHeight,
            mWidth,
            mHeight,

            // Right line
            mWidth,
            mHeight,
            mWidth,
            0f


        )
    }

    /**
     * Calculates the positions that handle bars should locate.
     * @return Returns a [FloatArray] representing the location of lines that should be drawn on screen.
     */
    private fun createHandleBarsDimensions(): FloatArray {
        val mHeight = measuredHeight.toFloat()
        val mWidth = measuredWidth.toFloat()
        return floatArrayOf(
            // Left
            0f,
            mHeight * 0.55f,
            0f,
            mHeight * 0.45f,

            // Bottom
            mWidth * 0.45f,
            mHeight,
            mWidth * 0.55f,
            mHeight,

            // Right
            mWidth,
            mHeight * 0.45f,
            mWidth,
            mHeight * 0.55f,

            // Top
            mWidth * 0.45f,
            0f,
            mWidth * 0.55f,
            0f,

            // Top left corner.
            0f,
            0f,
            (mWidth + mHeight) * 0.05f,
            0f,

            0f,
            0f,
            0f,
            (mWidth + mHeight) * 0.05f,

            // Top right corner.
            mWidth - (mWidth + mHeight) * 0.05f,
            0f,
            mWidth,
            0f,

            mWidth,
            0f,
            mWidth,
            (mWidth + mHeight) * 0.05f,

            // Bottom left corner.
            0f,
            mHeight - (mWidth + mHeight) * 0.05f,
            0f,
            mHeight,

            0f,
            mHeight,
            (mWidth + mHeight) * 0.05f,
            mHeight,


            // Bottom right corner.
            mWidth - (mWidth + mHeight) * 0.05f,
            mHeight,
            mWidth,
            mHeight,


            mWidth,
            mHeight,
            mWidth,
            mHeight - (mWidth + mHeight) * 0.05f
        )
    }

    /**
     * This method figures the touch area of each handle bar.
     * @return Returns a map of handle bar area range and [HandleBar] itself.
     */
    private fun crateHandleBarPointMap(): MutableMap<Pair<PointF, PointF>, HandleBar> {
        // Save initial height and width
        val mHeight = measuredHeight.toFloat()
        val mWidth = measuredWidth.toFloat()

        // Figure out some extra touch area for better touch experience.
        val excessTouchArea = (mHeight + mWidth) / 50

        // Store areas that handle are located + excess area.
        return mutableMapOf<Pair<PointF, PointF>, HandleBar>(
            Pair(Pair(PointF(0f, 0f), PointF(12.dp, 12.dp)), TOP_LEFT),
            Pair(
                Pair(
                    PointF((mWidth * 0.5f) - excessTouchArea.dp, 0f),
                    PointF((mWidth * 0.5f) + excessTouchArea.dp, 12.dp)
                ), TOP
            ),
            Pair(
                Pair(
                    PointF(mWidth - excessTouchArea.dp, (mHeight * 0.5f) - excessTouchArea.dp),
                    PointF(mWidth, (mHeight * 0.5f) + excessTouchArea.dp)
                ), RIGHT
            ),
            Pair(
                Pair(PointF(mWidth - excessTouchArea.dp, 0f), PointF(mWidth, excessTouchArea.dp)),
                TOP_RIGHT
            ),
            Pair(
                Pair(
                    PointF(0f, (mHeight * 0.5f) - excessTouchArea.dp),
                    PointF(excessTouchArea.dp, (mHeight * 0.5f) + excessTouchArea.dp)
                ), LEFT
            ),
            Pair(
                Pair(
                    PointF(0f, mHeight - excessTouchArea.dp),
                    PointF(excessTouchArea.dp, mHeight + excessTouchArea.dp)
                ),
                BOTTOM_LEFT
            ),
            Pair(
                Pair(
                    PointF((mWidth * 0.5f) - excessTouchArea.dp, mHeight - excessTouchArea.dp),
                    PointF((mWidth * 0.5f) + excessTouchArea.dp, mHeight)
                ),
                BOTTOM
            ),
            Pair(
                Pair(
                    PointF(mWidth - excessTouchArea.dp, mHeight - excessTouchArea.dp),
                    PointF(mWidth, mHeight)
                ), BOTTOM_RIGHT
            ),
        )
    }

    /**
     * This class represents the location of bars on grid line for better code readability.
     */
    private enum class HandleBar {
        TOP_LEFT,
        TOP,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM,
        BOTTOM_RIGHT,
        LEFT,
        RIGHT
    }
}
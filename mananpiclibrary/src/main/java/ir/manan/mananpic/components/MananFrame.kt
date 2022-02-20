package ir.manan.mananpic.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import ir.manan.mananpic.R
import ir.manan.mananpic.properties.MananComponent
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.MananMatrixAnimator
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.detectors.MoveDetector
import ir.manan.mananpic.utils.gesture.detectors.TwoFingerRotationDetector
import ir.manan.mananpic.utils.gesture.gestures.SimpleOnMoveListener
import ir.manan.mananpic.utils.gesture.gestures.SimpleOnRotateListener
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A class that extends [FrameLayout] class and overrides certain functions such as
 * [onInterceptTouchEvent] and [performClick] and [onTouchEvent] to get cleaner code
 * and custom behaviour. This class also takes responsibility for editing views(scaling, rotating and moving).
 */
class MananFrame(context: Context, attr: AttributeSet?) : FrameLayout(context, attr) {
    constructor(context: Context) : this(context, null)

    companion object {
        private const val MAXIMUM_SCALE_FACTOR = 10f
    }

    // Page settings.
    var pageWidth = 0
    var pageHeight = 0
    private var pageSizeRatio: Float = 0f

    /**
     * A flag that later will be set if user moves his/her finger enough to be
     * registered as move gesture, otherwise it will be registered as single tap.
     */
    private var isMoved = false

    // Used to store total difference of touch in move gesture to then
    // compare it against a touch slope to determine if user has performed touch or move gesture.
    private var totalDx = 0f
    private var totalDy = 0f


    // Used to retrieve touch slopes.
    private val viewConfiguration by lazy {
        ViewConfiguration.get(context)
    }

    // Paint used to draw page.
    private val pagePaint by lazy {
        Paint().apply {
            style = Paint.Style.FILL
        }
    }

    /**
     * Color of page drawn.
     */
    var pageBackgroundColor = Color.WHITE
        set(value) {
            field = value
            pagePaint.color = value
            invalidate()
        }

    // Rectangle that later we create to draw an area that we consider as page.
    private var pageRect = RectF()

    private var currentEditingView: MananComponent? = null

    // Determines if child has been scaled down to fit page bounds.
    private var isChildScaleNormalized: Boolean = false

    private val boxPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }

    private var onChildClicked: ((View, Boolean) -> Unit)? = null
    private var onChildClickedListener: OnChildClickedListener? = null

    private var onChildrenChanged: ((View, Boolean) -> Unit)? = null
    private var onChildrenChangedListener: OnChildrenListChanged? = null

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
            invalidate()
        }

    private val smartGuidePaint by lazy {
        Paint()
    }

    /**
     * Holds lines for smart guidelines.
     */
    private val smartGuideLineHolder = arrayListOf<Float>()

    /**
     * Smart guideline stroke width, default value is 2 dp.
     * value will be interpreted as pixels, use [Context.dp] or [View.dp] to convert a dp value to pixels.
     */
    var smartGuideLineStrokeWidth = dp(2)
        set(value) {
            smartGuidePaint.strokeWidth = value
            field = value
            invalidate()
        }

    /**
     * Smart guideline stroke color, default is #f403fc.
     * value should be a color int.
     */
    @ColorInt
    var smartGuideLineStrokeColor = Color.parseColor("#f403fc")
        set(value) {
            smartGuidePaint.color = value
            field = value
            invalidate()
        }

    // Flag that determines if canvas should use matrix to manipulate scale and translation.
    private var isCanvasMatrixEnabled = true

    // Matrix that we later use to manipulate canvas scale and translation.
    private val canvasMatrix = MananMatrix()

    /**
     * Used for mapping operations like transforming a rectangle etc...
     */
    private val mappingMatrix by lazy {
        Matrix()
    }

    /**
     * A rectangle to hold the mapped rectangles inside it. It is
     * defined here to avoid allocations.
     */
    private val mappingRectangle by lazy {
        RectF()
    }

    private val matrixAnimator by lazy {
        MananMatrixAnimator(canvasMatrix, RectF(pageRect), 300L, FastOutSlowInInterpolator())
    }

    /**
     * Extra space that user can translate the canvas before canvas animation triggers.
     * Default is 0.
     */
    var extraSpaceToTriggerAnimation = 0f

    private val scaleGestureListener by lazy {
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
                return !matrixAnimator.isAnimationRunning()
            }

            override fun onScale(detector: ScaleGestureDetector?): Boolean {
                if (detector != null) {
                    val sf = detector.scaleFactor
                    if (currentEditingView != null) {
                        currentEditingView!!.applyScale(sf)
                        smartGuideLineHolder.clear()
                    } else {
                        // If there isn't any component selected, scale the canvas.
                        canvasMatrix.postScale(
                            sf,
                            sf,
                            detector.focusX,
                            detector.focusY
                        )
                    }
                    return true
                }
                return false
            }

            override fun onScaleEnd(detector: ScaleGestureDetector?) {
                super.onScaleEnd(detector)
                // Set 'isMoved' to true to prevent selecting the target view if it's been in user touch locations.
                isMoved = true
                animateCanvasBack()
                findSmartGuideLines()
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

    private val rotateDetector by lazy {
        TwoFingerRotationDetector(rotateGestureListener)
    }

    private val moveGestureListener by lazy {
        object : SimpleOnMoveListener() {
            override fun onMove(dx: Float, dy: Float): Boolean {
                if (currentEditingView != null) {
                    // Slow down the translation if canvas matrix is zoomed.
                    val s = canvasMatrix.getOppositeScale()
                    currentEditingView!!.applyMovement(dx * s, dy * s)
                    findSmartGuideLines()
                } else {
                    // If there isn't any component selected, translate the canvas.
                    canvasMatrix.postTranslate(dx, dy)
                }

                // Store total dx and dy to then determine if user has moved his/her finger
                // enough to be registered as moving gesture.
                totalDx += abs(dx)
                totalDy += abs(dy)

                // Compare the total difference against the touch slop.
                isMoved =
                    totalDx > viewConfiguration.scaledTouchSlop || totalDy > viewConfiguration.scaledTouchSlop

                return true
            }

            override fun onMoveEnded(lastX: Float, lastY: Float) {
                super.onMoveEnded(lastX, lastY)

                // If user hasn't moved his/her finger enough to be registered as moving gesture,
                // then select the components if they're on current location of touch.
                if (!isMoved) {
                    performClick()

                    val childAtPosition = getChildAtPoint(lastX, lastY) as? MananComponent

                    // Deselect the child if returned component is null.
                    if (childAtPosition == null) {
                        deselectSelectedView()
                    }
                    // If returned child is not null and it is not referencing the same object that
                    // current editable view is referencing then change editing view.
                    else if (currentEditingView !== childAtPosition) {
                        rotateDetector.resetRotation(childAtPosition.reportRotation())
                        callOnChildClickListeners(childAtPosition as View, true)
                        currentEditingView = childAtPosition
                        findSmartGuideLines()
                        invalidate()
                    }
                }

                // Reset for next gesture.
                totalDx = 0f
                totalDy = 0f
                isMoved = false


                animateCanvasBack()
            }
        }
    }

    private val moveDetector by lazy {
        MoveDetector(1, moveGestureListener)
    }


    /**
     * Total distance for smart guideline to trigger itself.
     * Default values is 2 dp.
     * Value will be interpreted as pixels (use [Context.dp] extension function to convert a dp value to pixel.)
     * This value will become less as user zooms in; for example if user zoom two times the current factor, then
     * current value will become half; This is done to provide better accuracy.
     */
    var acceptableDistanceForSmartGuideline = dp(2)

    init {
        context.theme.obtainStyledAttributes(attr, R.styleable.MananFrame, 0, 0).apply {
            try {
                isDrawingBoxEnabled =
                    getBoolean(R.styleable.MananFrame_isDrawingBoxEnabled, false)

                frameBoxColor = getColor(R.styleable.MananFrame_frameBoxColor, Color.BLACK)

                frameBoxStrokeWidth =
                    getDimension(
                        R.styleable.MananFrame_frameBoxStrokeWidth,
                        frameBoxStrokeWidth
                    )

                pageBackgroundColor =
                    getColor(R.styleable.MananFrame_pageBackgroundColor, Color.WHITE)

                pageWidth = getInteger(R.styleable.MananFrame_pageWidth, 0)

                pageHeight = getInteger(R.styleable.MananFrame_pageHeight, 0)

                pageSizeRatio = pageWidth.toFloat() / pageHeight.toFloat()

                smartGuideLineStrokeColor =
                    getColor(
                        R.styleable.MananFrame_smartGuidelineStrokeColor,
                        smartGuideLineStrokeColor
                    )

                smartGuideLineStrokeWidth =
                    getDimension(
                        R.styleable.MananFrame_smartGuidelineStrokeWidth,
                        smartGuideLineStrokeWidth
                    )

            } finally {
                recycle()
            }
        }
        // Let some components like TextView be able to draw things outside their bounds (shadow layer and etc...)
        clipChildren = false

        // Don't clip children to padding.
        clipToPadding = false

        // Always draws.
        setWillNotDraw(false)

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
        when (event?.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                invalidate()
            }
        }
        return true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        // Create a page rect with aspect ratio of page.
        // Note that this page rect dimensions might be different comparing to given width and height of page
        // but aspect ratio is the same, so we can later scale these to match our desired page size.
        if (pageWidth != 0 && pageHeight != 0 && pageRect.isEmpty) {

            // Get difference of paddings to then apply to rectangle.
            val diffHorizontalPadding = paddingLeft - paddingRight
            val diffVerticalPadding = paddingTop - paddingBottom

            if (pageSizeRatio > 1f) {

                var widthF =
                    width.toFloat() - paddingLeft - paddingRight - paddingTop - paddingBottom

                var bottomPage = (widthF / pageSizeRatio)

                // If after applying aspect ratio the height was greater than view's width
                // then reduce the size and set view height (max height available) as baseline
                // for width and then again calculate the height.
                val heightWithPadding = (height - paddingTop - paddingBottom)
                if (bottomPage > heightWithPadding) {
                    bottomPage = heightWithPadding.toFloat()
                    widthF = heightWithPadding * pageSizeRatio
                }

                // Calculate the extra space after finalizing the dimensions values by subtracting
                // it with view dimension and then dividing by two to find how much we should
                // shift rectangle to center it inside the view.
                val bottomHalf = (height - bottomPage) * 0.5f
                val rightHalf = (width - widthF) * 0.5f


                // Finally set the rectangle with paddings.
                pageRect.set(
                    rightHalf + diffHorizontalPadding,
                    bottomHalf + diffVerticalPadding,
                    widthF + rightHalf + diffHorizontalPadding,
                    bottomPage + bottomHalf + diffVerticalPadding
                )
            } else {
                var heightF =
                    height.toFloat() - paddingBottom - paddingTop - paddingLeft - paddingRight

                var rightPage = (heightF * pageSizeRatio)

                // If after applying aspect ratio the width was greater than view's width
                // then reduce the size and set view width (max width available) as baseline
                // for height and then again calculate the width.
                val widthWithPadding = (width - paddingLeft - paddingRight)
                if (rightPage > widthWithPadding) {
                    rightPage = widthWithPadding.toFloat()
                    heightF = widthWithPadding / pageSizeRatio
                }

                // Calculate the extra space after finalizing the dimensions values by subtracting
                // it with view dimension and then dividing by two to find how much we should
                // shift rectangle to center it inside the view.
                val rightHalf: Float = (width - rightPage) * 0.5f
                val bottomHalf: Float = (height - heightF) * 0.5f

                // Finally set the rectangle with paddings.
                pageRect.set(
                    rightHalf + diffHorizontalPadding,
                    bottomHalf + diffVerticalPadding,
                    rightPage + rightHalf + diffHorizontalPadding,
                    heightF + bottomHalf + diffVerticalPadding
                )
            }
        }

        if (!isChildScaleNormalized || changed) {
            if (currentEditingView != null) {
                fitChildInsidePage(currentEditingView!!)
                isChildScaleNormalized = true
            }
        }
    }

    /**
     * Fits child inside the page(not view bounds).
     * @param child Child that is going to be fitted inside page.
     */
    private fun fitChildInsidePage(child: MananComponent) {
        child.run {
            var bound = reportBound()

            val pageWidth = pageRect.width()
            val pageHeight = pageRect.height()

            // First scale down the component to match the page width.
            applyScale(pageWidth / bound.width())

            // Refresh the bounds to then determine if we should scale down again or not.
            bound = reportBound()
            val boundHeight = bound.height()

            // Check if after scaling the other axis exceeds page bounds.
            if (boundHeight > pageHeight) {
                applyScale(pageHeight / boundHeight)
                bound = reportBound()
            }

            // Convert to view to get offset on each axis based on width and height param
            // of view. If a parent has a padding and child has MATCH_PARENT on either width or
            // height then it's x and y would shift but it is not the case with WRAP_CONTENT.
            val v = this as View
            // Determine how much we should shift the view to center it.
            val totalXToShift = (pageRect.centerX() - bound.centerX()) - getOffsetX(v)
            val totalYToShift = (pageRect.centerY() - bound.centerY()) - getOffsetY(v)

            // Finally shift to center the component.
            applyMovement(totalXToShift, totalYToShift)

        }
    }

    override fun dispatchDraw(canvas: Canvas?) {
        // Draws page rectangle to be visible to user.
        canvas?.drawRect(pageRect, pagePaint)

        super.dispatchDraw(canvas)
    }

    override fun draw(canvas: Canvas?) {
        canvas?.run {
            // concat the canvas matrix to matrix that we manipulate.
            if (isCanvasMatrixEnabled)
                concat(canvasMatrix)

            super.draw(this)

            currentEditingView?.run {

                val oppositeScale = canvasMatrix.getOppositeScale()

                // Draw the box around view.
                if (isDrawingBoxEnabled) {
                    if (isCanvasMatrixEnabled) {
                        boxPaint.strokeWidth =
                            frameBoxStrokeWidth * oppositeScale
                    }
                    // Get bounds of component to create a rectangle with it.
                    val bound = reportBound()


                    // If  width and or height is MATCH_PARENT then add offset.
                    // This is because MATH_PARENT components shift while parent
                    // has padding.
                    val v = this as View
                    val offsetX = getOffsetX(v)
                    val offsetY = getOffsetY(v)

                    // Take a snapshot of current state of canvas.
                    save()

                    // Match the rotation of canvas to view to be able to
                    // draw rotated rectangle.
                    rotate(
                        reportRotation(),
                        reportBoundPivotX() + offsetX,
                        reportBoundPivotY() + offsetY
                    )

                    // Draw a box around component.
                    drawRect(
                        bound.left + offsetX,
                        bound.top + offsetY,
                        bound.right + offsetX,
                        bound.bottom + offsetY,
                        boxPaint
                    )

                    // Restore the previous state of canvas which is not rotated.
                    restore()
                }

                // Draw smart guidelines.
                drawLines(smartGuideLineHolder.toFloatArray(), smartGuidePaint.apply {
                    strokeWidth = smartGuideLineStrokeWidth * oppositeScale
                })
            }
        }
    }

    /**
     * Finds possible guide lines on selected component and other components and populates the line holder if there is
     * any line that could help user.
     * This method detects guide lines on sides of selected component.
     * Sides that are calculated for guide lines include:
     * - Left-Left
     * - Left,Right
     * - Right,Left
     * - Right,Right
     * - Top,Top
     * - Top,Bottom
     * - Bottom,Top
     * - Bottom,Bottom
     * - CenterX
     * - CenterY
     */
    private fun findSmartGuideLines() {
        if (currentEditingView != null) {
            smartGuideLineHolder.clear()

            val finalDistanceValue =
                acceptableDistanceForSmartGuideline * canvasMatrix.getOppositeScale()

            // Set global rectangle used for mapping to selected component's bounds.
            mappingRectangle.set(currentEditingView!!.reportBound())
            // Map it with it's rotation to get exact location of rectangle points.
            mapRectToComponentRotation(currentEditingView!!, mappingRectangle)

            // Remove selected component from list of children (because we don't need to find smart guideline for
            // selected component which is a undefined behaviour) and then map each bounds of children to get exact
            // location of points and then add page's bounds to get smart guidelines for page too.
            children.minus(currentEditingView as View).map { v ->
                (v as MananComponent).run {
                    val rotatedBound = RectF(reportBound())
                    mapRectToComponentRotation(this, rotatedBound)
                    rotatedBound
                }
            }.plus(pageRect).forEach { childBounds ->

                // Stores total value that selected component should shift in each axis
                var totalToShiftX = 0f
                var totalToShiftY = 0f

                // Calculate distance between two centers in x axis.
                val centerXDiff = childBounds.centerX() - mappingRectangle.centerX()
                val centerXDiffAbs = abs(centerXDiff)

                // Calculate distance between two centers in y axis.
                val centerYDiff = childBounds.centerY() - mappingRectangle.centerY()
                val centerYDiffAbs = abs(centerYDiff)

                // If absolute value of difference two center x was in range of acceptable distance,
                // then store total difference to later shift the component.
                if (centerXDiffAbs <= finalDistanceValue) {
                    totalToShiftX = centerXDiff
                }
                if (centerYDiffAbs <= finalDistanceValue) {
                    totalToShiftY = centerYDiff
                }

                // Calculate distance between two lefts.
                val leftToLeft = childBounds.left - mappingRectangle.left
                val leftToLeftAbs = abs(leftToLeft)

                // Calculate distance between two other component left and selected component right.
                val leftToRight = childBounds.left - mappingRectangle.right
                val leftToRightAbs = abs(leftToRight)

                // Calculate distance between two rights.
                val rightToRight = childBounds.right - mappingRectangle.right
                val rightToRightAbs = abs(rightToRight)

                // Calculate distance between other component right and selected component left.
                val rightToLeft = childBounds.right - mappingRectangle.left
                val rightToLeftAbs = abs(rightToLeft)

                // If left to left of two components was less than left two right and
                // if the lesser value was in acceptable range then set total shift amount
                // in x axis to that value.
                if (leftToLeftAbs < leftToRightAbs) {
                    if (leftToLeftAbs <= finalDistanceValue) {
                        totalToShiftX = leftToLeft
                    }
                } else if (leftToRightAbs < leftToLeftAbs) {
                    if (leftToRightAbs <= finalDistanceValue) {
                        totalToShiftX = leftToRight
                    }
                }

                // If right to right of two components was less than right to left of them,
                // Then check if we haven't set the total shift amount so far, if either we didn't
                // set any value to shift so far or current difference is less than current
                // total shift amount, then set total shift amount to the right to right difference.
                if (rightToRightAbs < rightToLeftAbs) {
                    if (rightToRightAbs <= finalDistanceValue) {
                        if (totalToShiftX == 0f) {
                            totalToShiftX = rightToRight
                        } else if (totalToShiftX != 0f && rightToRightAbs < abs(totalToShiftX)) {
                            totalToShiftX = rightToRight
                        }
                    }
                } else if (rightToLeftAbs < rightToRightAbs) {
                    if (rightToLeftAbs <= finalDistanceValue) {
                        if (totalToShiftX == 0f) {
                            totalToShiftX = rightToLeft
                        } else if (totalToShiftX != 0f && rightToLeftAbs < abs(totalToShiftX)) {
                            totalToShiftX = rightToLeft
                        }
                    }
                }

                val topToTop = childBounds.top - mappingRectangle.top
                val topToTopAbs = abs(topToTop)
                val topToBottom = childBounds.top - mappingRectangle.bottom
                val topToBottomAbs = abs(topToBottom)

                val bottomToBottom = childBounds.bottom - mappingRectangle.bottom
                val bottomToBottomAbs = abs(bottomToBottom)
                val bottomToTop = childBounds.bottom - mappingRectangle.top
                val bottomToTopAbs = abs(bottomToTop)

                if (topToTopAbs < topToBottomAbs) {
                    if (topToTopAbs <= finalDistanceValue) {
                        totalToShiftY = topToTop
                    }
                } else if (topToBottomAbs < topToTopAbs) {
                    if (topToBottomAbs <= finalDistanceValue) {
                        totalToShiftY = topToBottom
                    }
                }

                if (bottomToBottomAbs < bottomToTopAbs) {
                    if (bottomToBottomAbs <= finalDistanceValue) {
                        if (totalToShiftY == 0f) {
                            totalToShiftY = bottomToBottom
                        } else if (totalToShiftY != 0f && bottomToBottomAbs < abs(totalToShiftY)) {
                            totalToShiftY = bottomToBottom
                        }
                    }
                } else if (bottomToTopAbs < bottomToBottomAbs) {
                    if (bottomToTopAbs <= finalDistanceValue) {
                        if (totalToShiftY == 0f) {
                            totalToShiftY = bottomToTop
                        } else if (totalToShiftY != 0f && bottomToTopAbs < abs(totalToShiftY)) {
                            totalToShiftY = bottomToTop
                        }
                    }
                }

                currentEditingView!!.run {
                    // Finally shift the component.
                    applyMovement(totalToShiftX, totalToShiftY)

                    // Refresh the bounds of component after shifting it.
                    mappingRectangle.set(reportBound())
                    mapRectToComponentRotation(currentEditingView!!, mappingRectangle)

                    // Calculate the minimum and maximum amount of two axes
                    // because we want to draw a line from leftmost to rightmost
                    // and topmost to bottommost component.
                    val minTop = min(mappingRectangle.top, childBounds.top)
                    val maxBottom = max(mappingRectangle.bottom, childBounds.bottom)

                    val minLeft = min(mappingRectangle.left, childBounds.left)
                    val maxRight = max(mappingRectangle.right, childBounds.right)

                    smartGuideLineHolder.run {

                        // Draw a line on left side of selected component if two lefts are the same
                        // or right of other component is same to left of selected component
                        if (totalToShiftX == leftToLeft || totalToShiftX == rightToLeft) {
                            add(mappingRectangle.left)
                            add(minTop)
                            add(mappingRectangle.left)
                            add(maxBottom)
                        }
                        // Draw a line on right side of selected component if left side of other
                        // component is right side of selected component or two rights are the same.
                        if (totalToShiftX == leftToRight || totalToShiftX == rightToRight) {
                            add(mappingRectangle.right)
                            add(minTop)
                            add(mappingRectangle.right)
                            add(maxBottom)
                        }

                        // Draw a line on other component top if it's top is same as
                        // selected component top or bottom of selected component is same as
                        // top of other component.
                        if (totalToShiftY == topToTop || totalToShiftY == topToBottom) {
                            add(minLeft)
                            add(childBounds.top)
                            add(maxRight)
                            add(childBounds.top)
                        }
                        // Draw a line on other component bottom if bottom of it is same as
                        // selected component's top or two bottoms are the same.
                        if (totalToShiftY == bottomToTop || totalToShiftY == bottomToBottom) {
                            add(minLeft)
                            add(childBounds.bottom)
                            add(maxRight)
                            add(childBounds.bottom)
                        }

                        // Finally draw a line from center of each component to another.
                        if (totalToShiftX == centerXDiff || totalToShiftY == centerYDiff) {
                            add(mappingRectangle.centerX())
                            add(mappingRectangle.centerY())
                            add(childBounds.centerX())
                            add(childBounds.centerY())
                        }

                    }
                }
            }
        }
    }

    /**
     * Maps the provided destination rectangle to rotation component, This way [dstRectangle] would have
     * exact points of rectangle if it's rotated.
     * @param component Component that we get rotation and base bounds from.
     * @param dstRectangle Destination rotation that would have exact location of rotated rectangle from [component]
     */
    private fun mapRectToComponentRotation(component: MananComponent, dstRectangle: RectF) {
        mappingMatrix.run {
            setRotate(
                component.reportRotation(),
                component.reportBoundPivotX(),
                component.reportBoundPivotY()
            )

            mapRect(dstRectangle)
        }
    }

    /**
     * Determines if child's x coordinate should offset.
     */
    private fun getOffsetX(v: View): Int {
        return if (v.layoutParams.width == LayoutParams.MATCH_PARENT)
            paddingLeft
        else 0
    }

    /**
     * Determines if child's y coordinate should offset.
     */
    private fun getOffsetY(v: View): Int {
        return if (v.layoutParams.width == LayoutParams.MATCH_PARENT)
            paddingTop
        else 0
    }

    /**
     * Returns the child at given coordinates.
     * If two child overlap it swaps between them on each tap.
     * @param x X coordinate of current touch.
     * @param y Y coordinate of current touch.
     */
    private fun getChildAtPoint(x: Float, y: Float): View? {
        if (childCount == 0) return null

        children.forEach { v ->
            v as MananComponent
            if (v !== currentEditingView) {
                // Converting points to float array is required to use matrix 'mapPoints' method.
                val touchPoints = floatArrayOf(x, y)

                // Get bounds of current component to later validate if touch is in area of
                // that component or not.
                val bounds = v.reportBound()

                mappingMatrix.run {
                    setTranslate(
                        -canvasMatrix.getTranslationX(true),
                        -canvasMatrix.getTranslationY()
                    )

                    // Scale down the current matrix as much as canvas matrix scale up.
                    // We do this because if we zoom in image, the rectangle our area that we see
                    // is also smaller so we do this to successfully map our touch points to that area (zoomed area).
                    val scale = canvasMatrix.getOppositeScale()
                    postScale(scale, scale)

                    val offsetX = getOffsetX(v)
                    val offsetY = getOffsetY(v)

                    // Finally handle the rotation of component.
                    postRotate(
                        -v.reportRotation(),
                        v.reportBoundPivotX() + offsetX,
                        v.reportBoundPivotY() + offsetY
                    )

                    // Finally map the touch points.
                    mapPoints(touchPoints)

                    if (touchPoints[0] in (bounds.left + offsetX)..(bounds.right + offsetX) && touchPoints[1] in (bounds.top + offsetY..(bounds.bottom + offsetY)))
                        return v

                }

            }
        }
        return null
    }

    private fun animateCanvasBack() {
        matrixAnimator.run {
            startAnimation(MAXIMUM_SCALE_FACTOR, extraSpaceToTriggerAnimation)
            setOnMatrixUpdateListener {
                invalidate()
            }
        }
    }

    /**
     * This method converts page into Bitmap.
     * Any pixels outside of page bounds will not be converted.
     * @return Bitmap of current page content with bitmap having matching size with page.
     * @throws IllegalStateException If page size hasn't been set.
     */
    fun convertPageToBitmap(transparentBackground: Boolean = false): Bitmap {
        if (pageWidth == 0 || pageHeight == 0) throw IllegalStateException("Page size should not be 0")


        val listLayerTypes = mutableListOf<Int>()
        // Set children layer type to none to get better quality for rendering.
        children.forEach {
            listLayerTypes.add(it.layerType)
            it.setLayerType(LAYER_TYPE_NONE, null)
        }

        // Create bitmap with size of page.
        val bitmapWithPageSize =
            Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)

        // Determine how much the rect is scaled down comparing to actual page size.
        // Note that we took width as a number to determine aspect ratio, since aspect ratio is applied
        // then it wouldn't matter if we use height or width.
        val totalScale = pageWidth.toFloat() / pageRect.width()

        // Store last selected view and make current selected null to disappear drawing rect around it.
        val lastSelectedView = currentEditingView
        currentEditingView = null

        val pageColor = pagePaint.color
        if (transparentBackground)
            pagePaint.color = Color.TRANSPARENT

        // Temporarily disable canvas matrix manipulation to correctly render the content of page into bitmap.
        isCanvasMatrixEnabled = false

        // Invalidate to disappear rectangle drawn around selected component.
        invalidate()

        // Create a canvas with created bitmap.
        val canvas = Canvas(bitmapWithPageSize)

        // Scale it to match content of current canvas to size of bitmap.
        // This way we can have better quality than to not scaling it.
        canvas.scale(totalScale, totalScale)

        // Translate it back to page bounds because page is
        // centered in view then we have to translate the content back.
        canvas.translate(-pageRect.left, -pageRect.top)

        // Finally draw content of page to bitmap.
        draw(canvas)

        // Return last selected view to selection.
        currentEditingView = lastSelectedView

        pagePaint.color = pageColor

        // Return the state of canvas matrix.
        isCanvasMatrixEnabled = true

        // Restore layer type of each child.
        listLayerTypes.forEachIndexed { index, i ->
            getChildAt(index).setLayerType(i, null)
        }

        invalidate()

        // Finally return bitmap.
        return bitmapWithPageSize
    }

    /**
     * Selects a view in view group to enter editing state.
     * It does not throw exception if child in given index is null.
     * @param index Index of view that is going to be selected.
     */
    fun selectView(index: Int) {
        val selectedChild = getChildAt(index) as? MananComponent
        if (selectedChild != null) {
            callOnChildClickListeners(selectedChild as View, true)
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

            callOnChildClickListeners(currentEditingView as View, false)

            currentEditingView = null
            invalidate()
        }
    }

    /**
     * Removes the view that is currently selected.
     */
    fun removeSelectedView() {
        if (currentEditingView != null) {

            callOnChildClickListeners(currentEditingView as View, false)

            removeView(currentEditingView as View)
            currentEditingView = null
        }
    }

    private fun callOnChildClickListeners(view: View, isSelected: Boolean) {
        onChildClicked?.invoke(view, isSelected)
        onChildClickedListener?.onClicked(view, isSelected)

    }

    private fun callOnChildrenChangedListener(view: View, deleted: Boolean) {
        onChildrenChanged?.invoke(view, deleted)
        onChildrenChangedListener?.onChanged(view, deleted)
    }

    /**
     * Returns currently selected child.
     */
    fun getSelectedView(): View? {
        return currentEditingView as? View
    }

    override fun onViewAdded(child: View?) {
        if (child !is MananComponent) throw IllegalStateException("only components that implement MananComponent can be added")

        initializeChild(child)
        super.onViewAdded(child)

        child.doOnLayout {
            callOnChildrenChangedListener(child, false)
        }
    }

    override fun onViewRemoved(child: View?) {
        super.onViewRemoved(child)
        callOnChildrenChangedListener(child!!, true)
    }

    private fun initializeChild(child: View) {
        child.run {

            updateLayoutParams<LayoutParams> {
                gravity = Gravity.CENTER
            }

            val component = (child as MananComponent)

            // Reset rotation of rotation detector to current component rotation.
            rotateDetector.resetRotation(component.reportRotation())

            callOnChildClickListeners(child, true)

            currentEditingView = component

            // Set this flag to later fit the component inside the page after child has been laid out.
            isChildScaleNormalized = false
        }
    }

    /**
     * Sets page size. Only children within page bounds can be converted to image files.
     * @param desiredWidth Width of page in pixels.
     * @param desiredHeight Height of page in pixels.
     */
    fun setPageSize(desiredWidth: Int, desiredHeight: Int) {
        pageWidth = desiredWidth
        pageHeight = desiredHeight
        pageSizeRatio = pageWidth.toFloat() / pageHeight.toFloat()
        pageRect.setEmpty()
        requestLayout()
    }

    /**
     * Sets step for rotation detector.
     * If greater than 0 then rotation snaps to steps of current number for example
     * if step was 8.5f then we would have 8.5f then 17f then 25.5f as rotation and so on.
     */
    fun setRotationStep(step: Float) {
        rotateDetector.step = step
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
     * Sets callback for when children changes inside view group.
     */
    fun setOnChildrenChanged(listener: (View, Boolean) -> Unit) {
        onChildrenChanged = listener
    }

    /**
     * Sets listener for when children changes inside view group.
     * @see OnChildrenListChanged
     */
    fun setOnChildrenChanged(listener: OnChildrenListChanged) {
        onChildrenChangedListener = listener
    }


    /**
     * Interface definition for callback that get invoked when selection state of
     * a child in [MananFrame] changes.
     */
    interface OnChildClickedListener {
        /**
         * This method get invoked when child selection state changes.
         * @param view Clicked view.
         * @param isSelected Determines if view is in selected state or deselected state.
         */
        fun onClicked(view: View, isSelected: Boolean)
    }


    /**
     * Interface definition for callback that get invoked when children size changes either by
     * adding a new child or removing it.
     */
    interface OnChildrenListChanged {
        /**
         * This method get invoked when children count changes either by adding or removing a child.
         * @param view View(child) that has been added or deleted.
         * @param isDeleted If true then child is removed else added.
         */
        fun onChanged(view: View, isDeleted: Boolean)
    }

}
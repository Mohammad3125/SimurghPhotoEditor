package ir.manan.mananpic.components

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.view.children
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import ir.manan.mananpic.R
import ir.manan.mananpic.components.parents.MananParent
import ir.manan.mananpic.properties.MananComponent
import ir.manan.mananpic.properties.Texturable
import ir.manan.mananpic.utils.MananMatrixAnimator
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.detectors.MoveDetector
import ir.manan.mananpic.utils.gesture.detectors.TwoFingerRotationDetector
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A class that overrides [MananParent] and provides custom functionalities.
 *
 * Functionalities:
 * - draws a box around selected component with customizable attributes.
 * - draws a page with customizable size set via [setPageSize] or XML inside it to act as a page that can later be rendered to bitmap with [convertPageToBitmap].
 * - finds smart guidelines for it's children with customizable attributes set via [setSmartGuidelineFlags].
 * - finds smarty rotation guidelines with customizable degrees that snaps into them via [setRotationSmartGuideline].
 * - can perform gestures on selected component like Translation, Scaling and Rotation.
 *
 */
open class MananFrame(context: Context, attr: AttributeSet?) : MananParent(context, attr) {
    constructor(context: Context) : this(context, null)

    /* Page related variables ------------------------------------------------------------------------------------------*/
    var pageWidth = 0
    var pageHeight = 0
    private var pageSizeRatio: Float = 0f

    /** Paint used to draw page. */
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


    /** Rectangle that later we create to draw an area that we consider as page. */
    private var pageRect = RectF()

    /* Selected component related variables ------------------------------------------------------------------------------------------ */

    private var previousSelectedComponent: MananComponent? = null

    /** Determines if child has been scaled down to fit page bounds. */
    private var isChildScaleNormalized: Boolean = false

    /** Later determines if a component should skip fitting inside page phase (used when adding a clone) */
    private var isClone = false

    /** If true this class enters texture applying mode */
    private var isApplyingTexture = false

    /* Box around selected view related variables ------------------------------------------------------------------------------------------*/

    /** Paint used to draw box around selected view */
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
            invalidate()
        }

    /* Smart guideline ------------------------------------------------------------------------------------------*/

    private val smartGuidePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG)
    }

    /**
     * Holds lines for smart guidelines.
     */
    private val smartGuidelineHolder = arrayListOf<Float>()

    private var smartGuidelineFlags: Int = 0

    private var smartRotationDegreeHolder: FloatArray? = null

    private var smartRotationLineHolder = arrayListOf<Float>()

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

    /**
     * Total distance for smart guideline to trigger itself.
     * Default values is 2 dp.
     * Value will be interpreted as pixels (use [Context.dp] extension function to convert a dp value to pixel.)
     * This value will become less as user zooms in; for example if user zoom two times the current factor, then
     * current value will become half; This is done to provide better accuracy.
     */
    var acceptableDistanceForSmartGuideline = dp(2)

    /**
     * Range that smart rotation guideline will use to determine if it should draw guideline and snap
     * the selected component to the provided degrees by user set in [setRotationSmartGuideline].
     *
     * For example if rotation of component is 10 degrees and degree holder contains a degree like 12 then
     * it would trigger and snap the component to 12 degree because the range is set to 5f by default, meaning
     * algorithm would accept any degrees from 12 - range and 12 + range which means 8 to 17 degrees.
     *
     * Set this variable to set the range mentioned before.
     *
     * It's best to not change this value or let it be an small number between 1-10
     *
     * ### Default value is 5f
     *
     * @throws IllegalStateException if value is less than 0 or greater than 360
     */
    var rangeForSmartRotationGuideline = 5f
        set(value) {
            if (value < 0f || value > 360) throw IllegalStateException("this value should not be less than 0 or greater than 360")
            field = value
        }

    /* Detectors --------------------------------------------------------------------------------------------- */

    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        detector?.run {
            val sf = detector.scaleFactor
            when {
                currentEditingView != null -> {
                    if (!isApplyingTexture) {
                        currentEditingView!!.applyScale(sf)
                        smartGuidelineHolder.clear()
                    } else {
                        (currentEditingView as? Texturable?)?.scaleTexture(
                            sf,
                            currentEditingView!!.reportPivotX(),
                            currentEditingView!!.reportPivotY()
                        )
                    }
                }
                else -> {
                    scaleCanvas(sf, focusX, focusY)
                }
            }
            invalidate()
        }
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector?) {
        super.onScaleEnd(detector)
        findSmartGuideLines()
        invalidate()
    }

    override fun onRotate(degree: Float): Boolean {
        currentEditingView?.run {
            if (!isApplyingTexture) {
                applyRotation(degree)
                findRotationSmartGuidelines()
                smartGuidelineHolder.clear()
            } else {
                (currentEditingView as? Texturable)?.rotateTexture(
                    degree,
                    reportPivotX(),
                    reportPivotY()
                )
            }
            invalidate()
        }
        return true
    }

    override fun onMove(dx: Float, dy: Float): Boolean {
        super.onMove(dx, dy)
        when {
            currentEditingView != null -> {
                if (!isApplyingTexture) {
                    val s = canvasMatrix.getOppositeScale()
                    currentEditingView!!.applyMovement(dx * s, dy * s)
                    findSmartGuideLines()
                    smartRotationLineHolder.clear()
                } else {
                    (currentEditingView as Texturable).shiftTexture(dx, dy)
                }
            }
            else -> {
                translateCanvas(dx, dy)
            }
        }
        invalidate()
        return true
    }

    override fun onSelectedComponentChanged(newChild: MananComponent) {
        super.onSelectedComponentChanged(newChild)
        findSmartGuideLines()
    }

    init {
        scaleDetector = ScaleGestureDetector(context, this).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                isQuickScaleEnabled = false
            }
        }

        translationDetector = MoveDetector(1, this)

        rotationDetector = TwoFingerRotationDetector(this)

        matrixAnimator = MananMatrixAnimator(
            canvasMatrix,
            pageRect,
            300L,
            FastOutSlowInInterpolator()
        )

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

        // If we're cloning, then match the current selected component (which is new cloned component) to last component that was selected.
        if (isClone) {
            matchFirstComponentToSecond(currentEditingView!!, previousSelectedComponent!!)
            isClone = false
            // Set this flag to not fit component in page again.
            isChildScaleNormalized = true
        } else {
            if (!isChildScaleNormalized || changed) {
                if (currentEditingView != null) {
                    fitChildInsidePage(currentEditingView!!)
                    isChildScaleNormalized = true
                }
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
            applyScale(min(pageWidth / bound.width(), pageHeight / bound.height()))

            bound = reportBound()

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

    /**
     * Matches properties of first component to second component for cloned object.
     * Properties that are matched:
     * - Scale
     * - Rotation
     * Note that translation shifts to not overlap first component with second component.
     * @param first First [MananComponent] that is going to be the same as second component.
     * @param second Second component that first component is going to be like.
     */
    private fun matchFirstComponentToSecond(first: MananComponent, second: MananComponent) {
        var firstBound = first.reportBound()
        val secondBound = second.reportBound()

        first.run {
            applyScale(secondBound.width() / firstBound.width())

            firstBound = reportBound()

            val finalRotation = second.reportRotation() - reportRotation()
            applyRotation(finalRotation)
            rotationDetector?.resetRotation(finalRotation)

            applyMovement(secondBound.left - firstBound.right, secondBound.top - firstBound.top)
        }

    }

    override fun dispatchDraw(canvas: Canvas?) {
        // Draws page rectangle to be visible to user.
        canvas?.drawRect(pageRect, pagePaint)

        super.dispatchDraw(canvas)
    }

    override fun draw(canvas: Canvas?) {
        canvas?.run {
            super.draw(this)

            currentEditingView?.run {

                // Take a snapshot of current state of canvas.
                save()

                // Get bounds of component to create a rectangle with it.
                val bound = reportBound()

                // If  width and or height is MATCH_PARENT then add offset.
                // This is because MATH_PARENT components shift while parent
                // has padding.
                val v = this as View
                val offsetX = getOffsetX(v)
                val offsetY = getOffsetY(v)

                // Match the rotation of canvas to view to be able to
                // draw rotated rectangle.
                rotate(
                    reportRotation(),
                    reportBoundPivotX() + offsetX,
                    reportBoundPivotY() + offsetY
                )

                val oppositeScale = canvasMatrix.getOppositeScale()

                // Draw the box around view.
                if (isDrawingBoxEnabled) {
                    if (isCanvasMatrixEnabled) {
                        boxPaint.strokeWidth =
                            frameBoxStrokeWidth * oppositeScale
                    }

                    // Draw a box around component.
                    drawRect(
                        bound.left + offsetX,
                        bound.top + offsetY,
                        bound.right + offsetX,
                        bound.bottom + offsetY,
                        boxPaint
                    )
                }

                val smartGuidelineStrokeWidth = smartGuideLineStrokeWidth * oppositeScale

                // Draw rotation smart guidelines.
                drawLines(smartRotationLineHolder.toFloatArray(), smartGuidePaint.apply {
                    strokeWidth = smartGuidelineStrokeWidth
                })

                // Restore the previous state of canvas which is not rotated.
                restore()

                // Draw smart guidelines.
                drawLines(smartGuidelineHolder.toFloatArray(), smartGuidePaint.apply {
                    strokeWidth = smartGuidelineStrokeWidth
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
        if (currentEditingView != null && smartGuidelineFlags != 0) {
            smartGuidelineHolder.clear()

            val finalDistanceValue =
                acceptableDistanceForSmartGuideline * canvasMatrix.getOppositeScale()

            // Get flags to determine if we should use corresponding guideline or not.
            val isLeftLeftEnabled = smartGuidelineFlags.and(Guidelines.LEFT_LEFT) != 0
            val isLeftRightEnabled = smartGuidelineFlags.and(Guidelines.LEFT_RIGHT) != 0
            val isRightLeftEnabled = smartGuidelineFlags.and(Guidelines.RIGHT_LEFT) != 0
            val isRightRightEnabled = smartGuidelineFlags.and(Guidelines.RIGHT_RIGHT) != 0
            val isTopTopEnabled = smartGuidelineFlags.and(Guidelines.TOP_TOP) != 0
            val isTopBottomEnabled = smartGuidelineFlags.and(Guidelines.TOP_BOTTOM) != 0
            val isBottomTopEnabled = smartGuidelineFlags.and(Guidelines.BOTTOM_TOP) != 0
            val isBottomBottomEnabled = smartGuidelineFlags.and(Guidelines.BOTTOM_BOTTOM) != 0
            val isCenterXEnabled = smartGuidelineFlags.and(Guidelines.CENTER_X) != 0
            val isCenterYEnabled = smartGuidelineFlags.and(Guidelines.CENTER_Y) != 0

            val offsetView = currentEditingView!! as View
            val ox = getOffsetX(offsetView).toFloat()
            val oy = getOffsetY(offsetView).toFloat()

            // Set global rectangle used for mapping to selected component's bounds.
            mappingRectangle.run {
                set(currentEditingView!!.reportBound())
                // Map it with it's rotation to get exact location of rectangle points.
                mapRectToComponentRotation(currentEditingView!!, this)

                offset(ox, oy)
            }


            // Remove selected component from list of children (because we don't need to find smart guideline for
            // selected component which is a undefined behaviour) and then map each bounds of children to get exact
            // location of points and then add page's bounds to get smart guidelines for page too.
            children.minus(currentEditingView as View).map { v ->
                (v as MananComponent).run {
                    RectF(reportBound()).also { rotatedBound ->
                        mapRectToComponentRotation(this, rotatedBound)
                        rotatedBound.offset(getOffsetX(v).toFloat(), getOffsetY(v).toFloat())
                    }
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
                if (centerXDiffAbs <= finalDistanceValue && isCenterXEnabled) {
                    totalToShiftX = centerXDiff
                }
                if (centerYDiffAbs <= finalDistanceValue && isCenterYEnabled) {
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
                // If we are currently centering in x direction then any of these
                // side should not be calculated or be smart guided.
                if (totalToShiftX != centerXDiff) {
                    if (leftToLeftAbs < leftToRightAbs) {
                        if (leftToLeftAbs <= finalDistanceValue && isLeftLeftEnabled) {
                            totalToShiftX = leftToLeft
                        }
                    } else if (leftToRightAbs < leftToLeftAbs) {
                        if (leftToRightAbs <= finalDistanceValue && isLeftRightEnabled) {
                            totalToShiftX = leftToRight
                        }
                    }
                    // If right to right of two components was less than right to left of them,
                    // Then check if we haven't set the total shift amount so far, if either we didn't
                    // set any value to shift so far or current difference is less than current
                    // total shift amount, then set total shift amount to the right to right difference.
                    if (rightToRightAbs < rightToLeftAbs) {
                        if (rightToRightAbs <= finalDistanceValue && isRightRightEnabled) {
                            if (totalToShiftX == 0f) {
                                totalToShiftX = rightToRight
                            } else if (rightToRightAbs < abs(totalToShiftX)) {
                                totalToShiftX = rightToRight
                            }
                        }
                    } else if (rightToLeftAbs < rightToRightAbs) {
                        if (rightToLeftAbs <= finalDistanceValue && isRightLeftEnabled) {
                            if (totalToShiftX == 0f) {
                                totalToShiftX = rightToLeft
                            } else if (rightToLeftAbs < abs(totalToShiftX)) {
                                totalToShiftX = rightToLeft
                            }
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

                if (totalToShiftY != centerYDiff) {
                    if (topToTopAbs < topToBottomAbs) {
                        if (topToTopAbs <= finalDistanceValue && isTopTopEnabled) {
                            totalToShiftY = topToTop
                        }
                    } else if (topToBottomAbs < topToTopAbs && isTopBottomEnabled) {
                        if (topToBottomAbs <= finalDistanceValue) {
                            totalToShiftY = topToBottom
                        }
                    }

                    if (bottomToBottomAbs < bottomToTopAbs) {
                        if (bottomToBottomAbs <= finalDistanceValue && isBottomBottomEnabled) {
                            if (totalToShiftY == 0f) {
                                totalToShiftY = bottomToBottom
                            } else if (bottomToBottomAbs < abs(totalToShiftY)) {
                                totalToShiftY = bottomToBottom
                            }
                        }
                    } else if (bottomToTopAbs < bottomToBottomAbs) {
                        if (bottomToTopAbs <= finalDistanceValue && isBottomTopEnabled) {
                            if (totalToShiftY == 0f) {
                                totalToShiftY = bottomToTop
                            } else if (bottomToTopAbs < abs(totalToShiftY)) {
                                totalToShiftY = bottomToTop
                            }
                        }
                    }
                }

                currentEditingView!!.run {
                    // Finally shift the component.
                    applyMovement(totalToShiftX, totalToShiftY)

                    // Refresh the bounds of component after shifting it.
                    mappingRectangle.set(reportBound())

                    mapRectToComponentRotation(currentEditingView!!, mappingRectangle)

                    mappingRectangle.offset(ox, oy)

                    // Calculate the minimum and maximum amount of two axes
                    // because we want to draw a line from leftmost to rightmost
                    // and topmost to bottommost component.
                    val minTop = min(mappingRectangle.top, childBounds.top)
                    val maxBottom = max(mappingRectangle.bottom, childBounds.bottom)

                    val minLeft = min(mappingRectangle.left, childBounds.left)
                    val maxRight = max(mappingRectangle.right, childBounds.right)

                    smartGuidelineHolder.run {

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
     * Sets flags of smart guideline to customize needed smart guidelines,
     * for example if user sets [Guidelines.CENTER_X] and [Guidelines.BOTTOM_BOTTOM], only these
     * guidelines would be detected.
     * If [Guidelines.ALL] is set then all flags would bet set to 1 indicating they are all enabled.
     * ### NOTE: Flags should OR each other to create desired output:
     *      setFlags(LEFT_LEFT.or(RIGHT_LEFT).or(CENTER_X)))
     *      setFlags(LEFT_LEFT | RIGHT_LEFT | CENTER_X)
     * @see Guidelines
     */
    fun setSmartGuidelineFlags(flags: Int) {
        // If flag has the ALL in it then store the maximum int value in flag holder to indicate
        // that all of flags has been set, otherwise set it to provided flags.
        smartGuidelineFlags =
            if (flags.and(Guidelines.ALL) != 0) Int.MAX_VALUE else flags
    }

    /**
     * Clears smart guidelines flags. clearing flags means no smart guideline will be detected.
     */
    fun clearSmartGuidelineFlags() {
        smartGuidelineFlags = 0
    }

    /**
     * Returns smart guidelines flags.
     * @see setSmartGuidelineFlags
     * @see clearSmartGuidelineFlags
     */
    fun getSmartGuidelineFlags(): Int = smartGuidelineFlags


    /**
     * Finds smart guidelines for rotation if [smartRotationDegreeHolder] does have target rotations.
     * @return True if it found smart guideline, false otherwise.
     */
    private fun findRotationSmartGuidelines(): Boolean {
        currentEditingView?.run {
            smartRotationDegreeHolder?.run {
                smartRotationLineHolder.clear()
                forEach { snapDegree ->
                    if (reportRotation() in (snapDegree - rangeForSmartRotationGuideline)..(snapDegree + rangeForSmartRotationGuideline)) {
                        applyRotation(snapDegree)

                        val offsetView = currentEditingView as View

                        mappingRectangle.run {
                            set(reportBound())
                            offset(
                                getOffsetX(offsetView).toFloat(),
                                getOffsetY(offsetView).toFloat()
                            )
                        }
                        val centerXBound = mappingRectangle.centerX()
                        val extraSpaceForLineY = mappingRectangle.height() * 0.33f

                        smartRotationLineHolder.add(centerXBound)
                        smartRotationLineHolder.add(mappingRectangle.top - extraSpaceForLineY)
                        smartRotationLineHolder.add(centerXBound)
                        smartRotationLineHolder.add(mappingRectangle.bottom + extraSpaceForLineY)

                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Add degrees that user wants to snap to it if rotation reaches it.
     * These values should be between 0 and 359 (360 is same as 0 degree so use 0 instead of 360).
     * @param degrees Array of specific degrees that rotation snaps to.
     * @throws IllegalStateException if provided array is empty or any element in array is not between 0-360 degrees.
     */
    fun setRotationSmartGuideline(degrees: FloatArray) {
        if (degrees.any { degree -> (degree < 0 || degree > 359) }) throw IllegalStateException("array elements should be between 0-359 degrees")
        if (degrees.isEmpty()) throw IllegalStateException("array should contain at least 1 element")
        smartRotationDegreeHolder = degrees
    }

    /**
     * Clears any degrees that smart guideline detector detects.
     * This way smart guideline wouldn't snap to any specific degree.
     */
    fun clearRotationSmartGuideline() {
        smartRotationDegreeHolder = null
    }

    /**
     * Returns the rotation degree holder. Smart guideline detector snaps to these
     * degrees if there is any.
     */
    fun getRotationSmartGuidelineDegreeHolder() = smartRotationDegreeHolder

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

    override fun onChildCloned() {
        isClone = true
        isApplyingTexture = false
    }

    override fun onViewAdded(child: View?) {
        previousSelectedComponent = currentEditingView
        isApplyingTexture = false
        super.onViewAdded(child)
    }

    override fun onViewRemoved(child: View?) {
        isApplyingTexture = false
        super.onViewRemoved(child)
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

    override fun initializeChild(child: View) {
        super.initializeChild(child)
        // Set this flag to later fit the component inside the page after child has been laid out.
        isChildScaleNormalized = false
    }


    /**
     * Sets texture on selected component (if it implements [Texturable].) and any gestures
     * from no on will be applied to texture instead of child unless you finalize the
     * texture replacement by [applyTexture] method.
     * This method doesn't throw any exception if selected component is not [Texturable].
     * ### YOU HAVE TO CALL [applyTexture] TO FINALIZE TEXTURE PLACEMENT.
     * @param texture Bitmap that is going to be textured on selected component.
     * @param tileMode Tile mode of shader if texture exceeds the component's bounds.
     * @param textureOpacity Opacity of texture that is going to be applied.
     */
    fun setTextureToSelectedChild(
        texture: Bitmap,
        tileMode: Shader.TileMode,
        textureOpacity: Float = 1f
    ) {
        (currentEditingView as? Texturable)?.run {
            applyTexture(texture, tileMode, textureOpacity)
            isApplyingTexture = true
        }
    }


    /**
     * Finalizes texture placement on selected component.
     * [setTextureToSelectedChild] method should be called before this method otherwise this method is useless.
     */
    fun applyTexture() {
        currentEditingView?.run {
            isApplyingTexture = false
            rotationDetector?.resetRotation(currentEditingView!!.reportRotation())
        }
    }

    override fun deselectSelectedView() {
        if (!isApplyingTexture) {
            super.deselectSelectedView()
        }
    }

    /**
     * A class holding static flags for smart guideline. User should
     * set the desired flags in [setSmartGuidelineFlags] method.
     */
    class Guidelines {
        companion object {
            const val ALL = 1
            const val LEFT_LEFT = 2
            const val LEFT_RIGHT = 4
            const val RIGHT_LEFT = 8
            const val RIGHT_RIGHT = 16
            const val TOP_TOP = 32
            const val TOP_BOTTOM = 64
            const val BOTTOM_TOP = 128
            const val BOTTOM_BOTTOM = 256
            const val CENTER_X = 512
            const val CENTER_Y = 1024
        }
    }

}
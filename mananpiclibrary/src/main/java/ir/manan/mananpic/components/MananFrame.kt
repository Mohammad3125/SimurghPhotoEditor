package ir.manan.mananpic.components

import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.toRect
import androidx.core.view.children
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import ir.manan.mananpic.R
import ir.manan.mananpic.components.parents.MananParent
import ir.manan.mananpic.properties.MananComponent
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


    private val tempRect = RectF()

    private val floatArrayAcc = FloatArray(2)

    /* Selected component related variables ------------------------------------------------------------------------------------------ */

    private var previousSelectedComponent: MananComponent? = null

    /** Determines if child has been scaled down to fit page bounds. */
    private var isChildScaleNormalized: Boolean = false

    /** Later determines if a component should skip fitting inside page phase (used when adding a clone) */
    private var isClone = false

    private var isChildRestored = false

    /** If true this class enters gesture sharing mode */
    private var isSharingGestures = false

    private var onGesturesShared: ((scale: Float, dx: Float, dy: Float, rotation: Float, pivotX: Float, pivotY: Float) -> Unit)? =
        null
    private var onGesturesSharedListener: OnGesturesSharedListener? = null

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

    /* Custom scale in x and y dimension ------------------------------------------------------------------------*/

    private val scaleHandlesPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = scaleHandlesColor
            style = Paint.Style.FILL_AND_STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }

    @ColorInt
    var scaleHandlesColor = Color.BLUE
        set(value) {
            field = value
            scaleHandlesPaint.color = value
            invalidate()
        }

    var scalesHandleThickness = dp(7.5f)
        set(value) {
            field = value
            scaleHandlesPaint.strokeWidth = value
            invalidate()
        }

    var scaleHandleWidth = dp(90)
        set(value) {
            field = value
            invalidate()
        }

    private var currentScaleHandleSelected: ScaleHandles? = null

    private var scaleHandlesTouchArea = dp(24)


    /* Smart guideline ------------------------------------------------------------------------------------------*/

    private val smartGuidePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG)
    }

    /**
     * Holds lines for smart guidelines.
     */
    private val smartGuidelineHolder = arrayListOf<Float>()
    private val smartGuidelineDashedLine = arrayListOf<Boolean>()

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

    private val smartGuideLineDashedPathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)

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


    var lockedBackgroundBitmap: Bitmap? = null
        set(value) {
            value?.let {
                lockedBitmapDstRect.set(0, 0, value.width, value.height)
            }
            field = value
        }

    private val lockedBitmapDstRect = Rect()

    private val pageRectRect = Rect()

    private val backgroundBitmapPaint = Paint()

    /* Detectors --------------------------------------------------------------------------------------------- */

    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        detector?.run {
            val sf = detector.scaleFactor
            when {
                currentEditingView != null -> {
                    if (!isSharingGestures) {
                        currentEditingView!!.applyScale(sf)
                        smartGuidelineHolder.clear()
                    } else {
                        callGestureSharedListeners(sf, 0f, 0f, NO_ROTATION, focusX, focusY)
                    }
                }
                else -> {
                    if (isSharingGestures) {
                        callGestureSharedListeners(sf, 0f, 0f, NO_ROTATION, 0f, 0f)
                    } else {
                        scaleCanvas(sf, focusX, focusY)
                    }
                }
            }
            invalidate()
        }
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector?) {
        super.onScaleEnd(detector)
        if (!isSharingGestures) {
            findSmartGuideLines()
        }
        invalidate()
    }

    override fun onRotate(degree: Float): Boolean {
        if (!isSharingGestures && currentEditingView != null) {
            currentEditingView!!.applyRotation(degree)
            findRotationSmartGuidelines()
            smartGuidelineHolder.clear()
            invalidate()
        } else if (isSharingGestures) {
            callGestureSharedListeners(
                1f,
                0f,
                0f,
                degree,
                0f,
                0f
            )
        }
        return true
    }

    override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
        // Just selects scale handles not any other views.
        getChildAtPoint(initialX, initialY)
        return true
    }

    override fun onMove(dx: Float, dy: Float): Boolean {
        super.onMove(dx, dy)
        currentEditingView?.run {
            when {
                currentScaleHandleSelected != null -> {

                    val bounds = reportBound()

                    val canvasScale = canvasMatrix.getScaleX(true)

                    val reportedRotation = reportRotation()

                    var sumOfOffset: Float

                    val arrayOffsets = floatArrayOf((dx / canvasScale), (dy / canvasScale))

                    val reportedScaleX = reportScaleX()
                    val reportedScaleY = reportScaleY()

                    val scaleXSign = if (reportedScaleX >= 0f) 1f else -1f
                    val scaleYSign = if (reportedScaleY >= 0f) 1f else -1f

                    var initialLeft: Float
                    var initialTop: Float

                    mappingMatrix.run {
                        setRotate(
                            -reportedRotation
                        )

                        postScale(
                            scaleXSign,
                            scaleYSign
                        )

                        mapPoints(arrayOffsets)

                        sumOfOffset = arrayOffsets[0] + arrayOffsets[1]

                        setScale(
                            1f / reportedScaleX,
                            1f / reportedScaleY,
                            bounds.centerX(),
                            bounds.centerY()
                        )

                        mapRect(tempRect, bounds)

                        initialLeft = tempRect.left
                        initialTop = tempRect.top

                        setScale(reportedScaleX, reportedScaleY, initialLeft, initialTop)

                        postRotate(reportedRotation, initialLeft, initialTop)

                        mapRect(tempRect, bounds)

                        val currentRight = tempRect.right
                        val currentLeft = tempRect.left
                        val currentTop = tempRect.top
                        val currentBottom = tempRect.bottom

                        when (currentScaleHandleSelected) {
                            ScaleHandles.LEFT_HANDLE -> {
                                val totalToScaleX =
                                    if (sumOfOffset == 0f) 1f else 1f + (-sumOfOffset / bounds.width())

                                applyScale(
                                    totalToScaleX,
                                    1f,
                                )

                            }
                            ScaleHandles.RIGHT_HANDLE -> {

                                val totalToScaleX =
                                    if (sumOfOffset == 0f) 1f else 1f + (sumOfOffset / bounds.width())

                                applyScale(
                                    totalToScaleX,
                                    1f
                                )
                            }
                            ScaleHandles.TOP_HANDLE -> {

                                val totalToScaleY =
                                    if (sumOfOffset == 0f) 1f else 1f + (-sumOfOffset / bounds.height())

                                applyScale(
                                    1f,
                                    totalToScaleY
                                )
                            }
                            ScaleHandles.BOTTOM_HANDLE -> {

                                val totalToScaleY =
                                    if (sumOfOffset == 0f) 1f else 1f + (sumOfOffset / bounds.height())

                                applyScale(
                                    1f,
                                    totalToScaleY
                                )
                            }
                            else -> {

                            }
                        }

                        val newScaleX = reportScaleX()
                        val newScaleY = reportScaleY()

                        setScale(
                            newScaleX,
                            newScaleY,
                            initialLeft,
                            initialTop
                        )

                        postRotate(
                            reportedRotation,
                            initialLeft,
                            initialTop
                        )

                        mapRect(tempRect, bounds)

                        val diffX =
                            ((tempRect.left - currentLeft) + (tempRect.right - currentRight)) * 0.5f
                        val diffY =
                            ((tempRect.top - currentTop) + (tempRect.bottom - currentBottom)) * 0.5f

                        when (currentScaleHandleSelected) {
                            ScaleHandles.RIGHT_HANDLE, ScaleHandles.BOTTOM_HANDLE -> {
                                applyMovement(diffX, diffY)
                            }
                            ScaleHandles.TOP_HANDLE, ScaleHandles.LEFT_HANDLE -> {
                                applyMovement(-diffX, -diffY)
                            }
                            else -> {

                            }
                        }

                    }
                }

                !isSharingGestures -> {
                    val s = canvasMatrix.getOppositeScale()
                    applyMovement(dx * s, dy * s)
                    findSmartGuideLines()
                    smartRotationLineHolder.clear()
                }

                isSharingGestures -> {
                    mappingMatrix.setRotate(-reportRotation())
                    floatArrayAcc[0] = dx
                    floatArrayAcc[1] = dy
                    mappingMatrix.mapPoints(floatArrayAcc)
                    callGestureSharedListeners(
                        1f,
                        floatArrayAcc[0],
                        floatArrayAcc[1],
                        NO_ROTATION,
                        0f,
                        0f
                    )
                }

                else -> {

                }
            }
            invalidate()
            return true
        }
        if (isSharingGestures) {
            callGestureSharedListeners(
                1f,
                dx,
                dy,
                NO_ROTATION,
                0f,
                0f
            )
        } else {
            translateCanvas(dx, dy)
        }
        invalidate()
        return true
    }


    override fun onMoveEnded(lastX: Float, lastY: Float) {
        super.onMoveEnded(lastX, lastY)
        currentScaleHandleSelected = null
    }

    override fun onSelectedComponentChanged(newChild: MananComponent) {
        super.onSelectedComponentChanged(newChild)
        findSmartGuideLines()
    }

    init {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        scaleDetector = ScaleGestureDetector(context, this).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                isQuickScaleEnabled = false
            }
        }

        translationDetector = MoveDetector(1, this)

        rotationDetector = TwoFingerRotationDetector(this)

        matrixAnimator = MananMatrixAnimator(
            canvasMatrix, pageRect, 300L, FastOutSlowInInterpolator()
        )

        context.theme.obtainStyledAttributes(attr, R.styleable.MananFrame, 0, 0).apply {
            try {
                isDrawingBoxEnabled =
                    getBoolean(R.styleable.MananFrame_isDrawingBoxEnabled, false)

                frameBoxColor = getColor(R.styleable.MananFrame_frameBoxColor, Color.BLACK)

                frameBoxStrokeWidth = getDimension(
                    R.styleable.MananFrame_frameBoxStrokeWidth, frameBoxStrokeWidth
                )

                pageBackgroundColor =
                    getColor(R.styleable.MananFrame_pageBackgroundColor, Color.WHITE)

                pageWidth = getInteger(R.styleable.MananFrame_pageWidth, 0)

                pageHeight = getInteger(R.styleable.MananFrame_pageHeight, 0)

                pageSizeRatio = pageWidth.toFloat() / pageHeight.toFloat()

                smartGuideLineStrokeColor = getColor(
                    R.styleable.MananFrame_smartGuidelineStrokeColor, smartGuideLineStrokeColor
                )

                smartGuideLineStrokeWidth = getDimension(
                    R.styleable.MananFrame_smartGuidelineStrokeWidth, smartGuideLineStrokeWidth
                )

                scaleHandlesColor =
                    getColor(R.styleable.MananFrame_scaleHandlesColor, scaleHandlesColor)

                scalesHandleThickness =
                    getDimension(
                        R.styleable.MananFrame_scaleHandleThickness,
                        scalesHandleThickness
                    )

                scaleHandleWidth =
                    getDimension(R.styleable.MananFrame_scaleHandleThickness, scaleHandleWidth)

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
            pageRectRect.set(pageRect.toRect())
        }

        // If we're cloning, then match the current selected component (which is new cloned component) to last component that was selected.
        if (isClone) {
            matchFirstComponentToSecond(currentEditingView!!, previousSelectedComponent!!)
            isClone = false
            // Set this flag to not fit component in page again.
            isChildScaleNormalized = true
        } else {
            if ((!isChildScaleNormalized || changed) && !isChildRestored) {
                if (currentEditingView != null) {
                    fitChildInsidePage(currentEditingView!!)
                    isChildScaleNormalized = true
                }
            }
        }
        isChildRestored = false
    }

    override fun setChildRestored() {
        super.setChildRestored()
        isChildRestored = true
        isChildScaleNormalized = true
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

        lockedBackgroundBitmap?.let {
            canvas?.drawBitmap(it, lockedBitmapDstRect, pageRectRect, backgroundBitmapPaint)
        }

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

                val widthScaleFromPage = bound.width() / pageRect.width()
                val heightScaleFromPage = bound.height() / pageRect.height()

                val maxScale = max(widthScaleFromPage, heightScaleFromPage)


                // Draw the box around view.
                if (isDrawingBoxEnabled) {
                    if (isCanvasMatrixEnabled) {
                        boxPaint.strokeWidth = frameBoxStrokeWidth * maxScale
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


                if (isCanvasMatrixEnabled) {
                    scaleHandlesPaint.strokeWidth = scalesHandleThickness * maxScale
                }


                val centerX = bound.centerX()
                val centerY = bound.centerY()

                val scaleHandleWidthHalf = (scaleHandleWidth * widthScaleFromPage) * 0.5f
                val scaleHandleHeightHalf = (scaleHandleWidth * heightScaleFromPage) * 0.5f

                val xMinusHandleWidth = centerX - scaleHandleWidthHalf
                val xPlusHandleWidth = centerX + scaleHandleWidthHalf

                val yMinusHandleHeight = centerY - scaleHandleHeightHalf
                val yPlusHandleHeight = centerY + scaleHandleHeightHalf

                drawLines(
                    floatArrayOf(
                        // TOP HANDLE
                        xMinusHandleWidth,
                        bound.top,
                        xPlusHandleWidth,
                        bound.top,

                        // RIGHT HANDLE
                        bound.right,
                        yMinusHandleHeight,
                        bound.right,
                        yPlusHandleHeight,


                        // BOTTOM HANDLE
                        xMinusHandleWidth,
                        bound.bottom,
                        xPlusHandleWidth,
                        bound.bottom,


                        // LEFT HANDLE
                        bound.left,
                        yMinusHandleHeight,
                        bound.left,
                        yPlusHandleHeight
                    ),
                    scaleHandlesPaint
                )


                val smartGuidelineStrokeWidth = smartGuideLineStrokeWidth * oppositeScale

                // Draw rotation smart guidelines.
                drawLines(smartRotationLineHolder.toFloatArray(), smartGuidePaint.apply {
                    strokeWidth = smartGuidelineStrokeWidth
                })

                // Restore the previous state of canvas which is not rotated.
                restore()

                for (i in smartGuidelineHolder.indices step 4) {

                    if (smartGuidelineDashedLine[i]) {
                        smartGuidePaint.pathEffect = smartGuideLineDashedPathEffect
                    }

                    smartGuidePaint.strokeWidth = smartGuidelineStrokeWidth

                    drawLine(
                        smartGuidelineHolder[i],
                        smartGuidelineHolder[i + 1],
                        smartGuidelineHolder[i + 2],
                        smartGuidelineHolder[i + 3],
                        smartGuidePaint
                    )

                    smartGuidePaint.pathEffect = null
                }

            }
        }
    }

    override fun getChildAtPoint(x: Float, y: Float): View? {
        if (childCount == 0) return null

        children.forEach { v ->
            v as MananComponent
            // Converting points to float array is required to use matrix 'mapPoints' method.
            val touchPoints = floatArrayOf(x, y)

            // Get bounds of current component to later validate if touch is in area of
            // that component or not.
            val bounds = v.reportBound()

            val vAsView = v as View

            val offsetX = getOffsetX(vAsView)
            val offsetY = getOffsetY(vAsView)

            val mappedPoints = mappedTouchToComponent(touchPoints, v)

            currentEditingView?.let {

                val vBounds = it.reportBound()

                val scaleXSign = if (it.reportScaleX() >= 0f) 1f else -1f
                val scaleYSign = if (it.reportScaleY() >= 0f) 1f else -1f

                val selectedMappedPoints = mappedTouchToComponent(touchPoints, it)

                val centerX = vBounds.centerX()
                val centerY = vBounds.centerY()

                val vv = it as View

                val vOffsetX = getOffsetX(vv)
                val vOffsetY = getOffsetY(vv)

                val l = vBounds.left + vOffsetX
                val t = vBounds.top + vOffsetY
                val r = vBounds.right + vOffsetX
                val b = vBounds.bottom + vOffsetY

                val touchArea = scaleHandlesTouchArea / canvasMatrix.getScaleX(true)

                currentScaleHandleSelected =
                    if (selectedMappedPoints[0] in (l - touchArea)..(l + touchArea) && selectedMappedPoints[1] in (centerY - touchArea)..(centerY + touchArea)) {
                        if (scaleXSign == 1f) ScaleHandles.LEFT_HANDLE else ScaleHandles.RIGHT_HANDLE
                    } else if (selectedMappedPoints[0] in (r - touchArea)..(r + touchArea) && selectedMappedPoints[1] in (centerY - touchArea)..(centerY + touchArea)) {
                        if (scaleXSign == 1f) ScaleHandles.RIGHT_HANDLE else ScaleHandles.LEFT_HANDLE
                    } else if (selectedMappedPoints[0] in (centerX - touchArea)..(centerX + touchArea) && selectedMappedPoints[1] in (t - touchArea)..(t + touchArea)) {
                        if (scaleYSign == 1f) ScaleHandles.TOP_HANDLE else ScaleHandles.BOTTOM_HANDLE
                    } else if (selectedMappedPoints[0] in (centerX - touchArea)..(centerX + touchArea) && selectedMappedPoints[1] in (b - touchArea)..(b + touchArea)) {
                        if (scaleYSign == 1f) ScaleHandles.BOTTOM_HANDLE else ScaleHandles.TOP_HANDLE
                    } else {
                        null
                    }

                if (currentScaleHandleSelected != null)
                    return currentEditingView as View
            }

            if (v !== currentEditingView) {
                if (mappedPoints[0] in (bounds.left + offsetX)..(bounds.right + offsetX) && mappedPoints[1] in (bounds.top + offsetY..(bounds.bottom + offsetY)))
                    return v
            }

        }
        return null
    }

    private fun mappedTouchToComponent(
        touchPoints: FloatArray, component: MananComponent
    ): FloatArray {

        val mappedPoints = FloatArray(touchPoints.size)

        mappingMatrix.run {
            setTranslate(
                -canvasMatrix.getTranslationX(true), -canvasMatrix.getTranslationY()
            )

            // Scale down the current matrix as much as canvas matrix scale up.
            // We do this because if we zoom in image, the rectangle our area that we see
            // is also smaller so we do this to successfully map our touch points to that area (zoomed area).
            val scale = canvasMatrix.getOppositeScale()
            postScale(scale, scale)

            val compAsView = component as View

            val offsetX = getOffsetX(compAsView)
            val offsetY = getOffsetY(compAsView)

            // Finally handle the rotation of component.
            postRotate(
                -component.reportRotation(),
                component.reportBoundPivotX() + offsetX,
                component.reportBoundPivotY() + offsetY
            )

            // Finally map the touch points.
            mapPoints(mappedPoints, touchPoints)
        }
        return mappedPoints
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

            smartGuidelineDashedLine.clear()

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

                        val isNotPage = childBounds !== pageRect

                        // Draw a line on left side of selected component if two lefts are the same
                        // or right of other component is same to left of selected component
                        if (totalToShiftX == leftToLeft || totalToShiftX == rightToLeft) {
                            add(mappingRectangle.left)
                            smartGuidelineDashedLine.add(isNotPage)
                            add(minTop)
                            smartGuidelineDashedLine.add(isNotPage)
                            add(mappingRectangle.left)
                            smartGuidelineDashedLine.add(isNotPage)
                            add(maxBottom)
                            smartGuidelineDashedLine.add(isNotPage)
                        }
                        // Draw a line on right side of selected component if left side of other
                        // component is right side of selected component or two rights are the same.
                        if (totalToShiftX == leftToRight || totalToShiftX == rightToRight) {
                            add(mappingRectangle.right)
                            smartGuidelineDashedLine.add(isNotPage)
                            add(minTop)
                            smartGuidelineDashedLine.add(isNotPage)
                            add(mappingRectangle.right)
                            smartGuidelineDashedLine.add(isNotPage)
                            add(maxBottom)
                            smartGuidelineDashedLine.add(isNotPage)
                        }

                        // Draw a line on other component top if it's top is same as
                        // selected component top or bottom of selected component is same as
                        // top of other component.
                        if (totalToShiftY == topToTop || totalToShiftY == topToBottom) {
                            add(minLeft)
                            smartGuidelineDashedLine.add(isNotPage)
                            add(childBounds.top)
                            smartGuidelineDashedLine.add(isNotPage)
                            add(maxRight)
                            smartGuidelineDashedLine.add(isNotPage)
                            add(childBounds.top)
                            smartGuidelineDashedLine.add(isNotPage)
                        }
                        // Draw a line on other component bottom if bottom of it is same as
                        // selected component's top or two bottoms are the same.
                        if (totalToShiftY == bottomToTop || totalToShiftY == bottomToBottom) {
                            add(minLeft)
                            smartGuidelineDashedLine.add(isNotPage)
                            add(childBounds.bottom)
                            smartGuidelineDashedLine.add(isNotPage)
                            add(maxRight)
                            smartGuidelineDashedLine.add(isNotPage)
                            add(childBounds.bottom)
                            smartGuidelineDashedLine.add(isNotPage)
                        }

                        // Finally draw a line from center of each component to another.
                        if (totalToShiftX == centerXDiff || totalToShiftY == centerYDiff) {
                            if (isNotPage) {
                                add(mappingRectangle.centerX())
                                smartGuidelineDashedLine.add(isNotPage)
                                add(mappingRectangle.centerY())
                                smartGuidelineDashedLine.add(isNotPage)
                                add(childBounds.centerX())
                                smartGuidelineDashedLine.add(isNotPage)
                                add(childBounds.centerY())
                                smartGuidelineDashedLine.add(isNotPage)
                            } else {
                                if (totalToShiftX == centerXDiff) {
                                    add(mappingRectangle.centerX())
                                    smartGuidelineDashedLine.add(isNotPage)
                                    add(pageRect.top)
                                    smartGuidelineDashedLine.add(isNotPage)
                                    add(mappingRectangle.centerX())
                                    smartGuidelineDashedLine.add(isNotPage)
                                    add(pageRect.bottom)
                                    smartGuidelineDashedLine.add(isNotPage)
                                }

                                if (totalToShiftY == centerYDiff) {
                                    add(pageRect.left)
                                    smartGuidelineDashedLine.add(isNotPage)
                                    add(mappingRectangle.centerY())
                                    smartGuidelineDashedLine.add(isNotPage)
                                    add(pageRect.right)
                                    smartGuidelineDashedLine.add(isNotPage)
                                    add(mappingRectangle.centerY())
                                    smartGuidelineDashedLine.add(isNotPage)
                                }
                            }
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
        smartGuidelineFlags = if (flags.and(Guidelines.ALL) != 0) Int.MAX_VALUE else flags
    }

    /**
     * Clears smart guidelines flags. clearing flags means no smart guideline will be detected.
     */
    fun clearSmartGuidelineFlags() {
        smartGuidelineFlags = 0
    }

    fun eraseSmartGuidelines() {
        smartGuidelineDashedLine.clear()
        smartGuidelineHolder.clear()
        invalidate()
    }

    fun eraseRotationSmartGuidelines() {
        smartRotationLineHolder.clear()
        invalidate()
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

                        val centerYBound = mappingRectangle.centerY()
                        val pW = pageRect.width()

                        smartRotationLineHolder.add(-pW)
                        smartRotationLineHolder.add(centerYBound)
                        smartRotationLineHolder.add(pW * 2f)
                        smartRotationLineHolder.add(centerYBound)

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
        if (degrees.any { degree -> (degree < 0 || degree > 359) }) throw IllegalStateException(
            "array elements should be between 0-359 degrees"
        )
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
        if (transparentBackground) pagePaint.color = Color.TRANSPARENT

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
        isSharingGestures = false
    }

    override fun onViewAdded(child: View?) {
        previousSelectedComponent = currentEditingView
        isSharingGestures = false
        super.onViewAdded(child)
    }

    override fun onViewRemoved(child: View?) {
        isSharingGestures = false
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
     * [MananFrame] starts sharing its gestures via [setOnGestureSharedListener] and [setOnGestureSharedListener].
     */
    fun startSharingGesture() {
        isSharingGestures = true
    }

    override fun bringChildToFront(child: View?) {
        super.bringChildToFront(child)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            requestLayout()
            invalidate()
        }
    }


    /**
     * [MananFrame] doesn't share its gestures anymore.
     * [startSharingGesture] method should be called before this method otherwise this method is useless.
     */
    fun setGestureSharingDone() {
        currentEditingView?.let { comp ->
            isSharingGestures = false
            rotationDetector?.resetRotation(comp.reportRotation())
        }
    }

    fun setOnGestureSharedListener(listener: OnGesturesSharedListener) {
        onGesturesSharedListener = listener
    }

    fun setOnGestureSharedListener(listener: (scale: Float, dx: Float, dy: Float, rotation: Float, pivotX: Float, pivotY: Float) -> Unit) {
        onGesturesShared = listener
    }

    private fun callGestureSharedListeners(
        scale: Float,
        dx: Float,
        dy: Float,
        rotation: Float,
        pivotX: Float,
        pivotY: Float
    ) {
        onGesturesSharedListener?.onShare(scale, dx, dy, rotation, pivotX, pivotY)
        onGesturesShared?.invoke(scale, dx, dy, rotation, pivotX, pivotY)
    }

    override fun deselectSelectedView() {
        if (!isSharingGestures) {
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

    private enum class ScaleHandles {
        LEFT_HANDLE, RIGHT_HANDLE, TOP_HANDLE, BOTTOM_HANDLE
    }


    interface OnGesturesSharedListener {
        fun onShare(
            scaleFactor: Float,
            dx: Float,
            dy: Float,
            rotation: Float,
            pivotX: Float,
            pivotY: Float
        )
    }

    companion object {
        const val NO_ROTATION = 999f
    }

}
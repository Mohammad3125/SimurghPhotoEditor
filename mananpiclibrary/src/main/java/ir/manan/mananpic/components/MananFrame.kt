package ir.manan.mananpic.components

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import ir.manan.mananpic.R
import ir.manan.mananpic.properties.MananComponent
import ir.manan.mananpic.utils.MananMatrix
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
        const val MAXIMUM_SCALE_FACTOR = 10f
    }

    // Page settings.
    var pageWidth = 0
    var pageHeight = 0
    private var pageSizeRatio: Float = 0f

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

    // Flag that determines if canvas should use matrix to manipulate scale and translation.
    private var isCanvasMatrixEnabled = true

    // Matrix that we later use to manipulate canvas scale and translation.
    private val canvasMatrix = MananMatrix()

    // This animator is used to animate the matrix changes.
    private val canvasMatrixAnimator by lazy {
        ValueAnimator().apply {
            interpolator = FastOutSlowInInterpolator()
            duration = 300

            addUpdateListener {
                // Get animating properties.
                val s = getAnimatedValue("scale")
                val tx = getAnimatedValue("translationX")
                val ty = getAnimatedValue("translationY")

                canvasMatrix.run {
                    // If translation isn't null or in other words, we should animate the translation, then animate it.
                    if (tx != null) {
                        postTranslate(
                            tx as Float - getTranslationX(true),
                            0f
                        )
                    }

                    // If translation isn't null or in other words, we should animate the translation, then animate it.
                    if (ty != null) {
                        postTranslate(
                            0f,
                            ty as Float - getTranslationY(true)
                        )
                    }

                    // If scale property isn't null then scale it.
                    if (s != null) {
                        val totalScale = (s as Float) / getScaleX(true)
                        postScale(totalScale, totalScale, pivotX, pivotY)
                    }

                    invalidate()
                }

            }
        }
    }


    private val scaleGestureListener by lazy {
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector?): Boolean {
                if (detector != null) {
                    val sf = detector.scaleFactor
                    if (currentEditingView != null) {
                        currentEditingView!!.applyScale(sf)
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
                animateCanvasBack()
            }
        }
    }

    private fun animateCanvasBack() {
        if (!canvasMatrixAnimator.isRunning) {


            // Get matrix values.
            val scale = canvasMatrix.getScaleX(true)
            val tx = canvasMatrix.getTranslationX()
            val ty = canvasMatrix.getTranslationY()

            // Here we calculate the edge of right side to later do not go further that point.
            val rEdge =
                calculateEdge(scale, 1f, width.toFloat(), 0f)

            // Here we calculate the edge of bottom side to later do not go further that point.
            val bEdge =
                calculateEdge(scale, 1f, height.toFloat(), 0f)

            // Calculate the valid scale (scale greater than maximum allowable scale and less than initial scale)
            val validatedScale =
                if (scale > MAXIMUM_SCALE_FACTOR) MAXIMUM_SCALE_FACTOR else if (scale < 1f) 1f else scale


            canvasMatrixAnimator.run {
                val animationPropertyHolderList = ArrayList<PropertyValuesHolder>()
                // Add PropertyValuesHolder for each animation property if they should be animated.
                if (scale < 1f || scale > MAXIMUM_SCALE_FACTOR)
                    animationPropertyHolderList.add(
                        PropertyValuesHolder.ofFloat(
                            "scale",
                            scale,
                            validatedScale
                        )
                    )

                if (tx < rEdge || tx > 0f)
                    animationPropertyHolderList.add(
                        PropertyValuesHolder.ofFloat(
                            "translationX",
                            tx,
                            if (tx > 0f || scale < 1f) 0f else rEdge
                        )
                    )

                if (ty < bEdge || ty > 0f)
                    animationPropertyHolderList.add(
                        PropertyValuesHolder.ofFloat(
                            "translationY",
                            ty,
                            if (ty > 0f || scale < 1f) 0f else bEdge
                        )
                    )


                // Finally convert the array list to array and set values of animator.
                setValues(
                    *Array(
                        animationPropertyHolderList.size
                    ) {
                        animationPropertyHolderList.get(it)
                    }
                )

                start()
            }
        }
    }

    /**
     * Calculates the edge of view with current applied scale.
     * @param scaled Total scale factor that user scaled the image.
     * @param initScale Initial scale of the image.
     * @param initialSize Initial size of current axis we're trying to calculate (x or y).
     * @param initialOffset Initial offset of that axis (initial translation)
     */
    private fun calculateEdge(
        scaled: Float,
        initScale: Float,
        initialSize: Float,
        initialOffset: Float
    ): Float =
        -((scaled * initialSize / initScale) - initialSize - initialOffset)

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
                    callOnChildClickListeners(childAtPosition as View, true)
                    currentEditingView = childAtPosition
                    invalidate()
                }

                return currentEditingView != null
            }

            override fun onMove(dx: Float, dy: Float): Boolean {
                if (currentEditingView != null) {
                    // Slow down the translation if canvas matrix is zoomed.
                    val s = canvasMatrix.getOppositeScale()
                    currentEditingView!!.applyMovement(dx * s, dy * s)
                } else {
                    // If there isn't any component selected, translate the canvas.
                    canvasMatrix.postTranslate(dx, dy)
                }
                return true
            }

            override fun onMoveEnded(lastX: Float, lastY: Float) {
                super.onMoveEnded(lastX, lastY)
                animateCanvasBack()
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
        commonGestureDetector.onTouchEvent(event)
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

    private fun fitChildInsidePage(child: MananComponent) {
        child.run {
            val bound = reportBound()
            // Take maximum dimension of component and compare it to minimum dimension of page.
            if (max(bound.width(), bound.height()) > min(
                    pageRect.width(),
                    pageRect.height()
                )
            ) {
                // Then determine how much we should scale to fit image within bounds.
                this.applyScale(
                    min(pageRect.width(), pageRect.height()) / max(
                        bound.width(),
                        bound.height()
                    )
                )
            }

            // Determine how much we should shift the view to center it.
            val totalXToShift = pageRect.left - bound.left
            val totalYToShift = pageRect.top - bound.top

            if (totalXToShift > 0)
                applyMovement(totalXToShift, 0f)
            if (totalYToShift > 0)
                applyMovement(0f, totalYToShift)
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

            // Draw the box around view.
            if (currentEditingView != null && isDrawingBoxEnabled) {
                if (isCanvasMatrixEnabled) {
                    boxPaint.strokeWidth =
                        frameBoxStrokeWidth * canvasMatrix.getOppositeScale()
                }
                val view = currentEditingView!!

                // Get bounds of component to create a rectangle with it.
                val bound = view.reportBound()

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

        // Matrix that later will be used to map the touch point
        // from screen coordinates to current view window inside canvas.
        val touchMatrix = Matrix()
        children.forEach { v ->
            v as MananComponent
            if (v !== currentEditingView) {
                // Converting points to float array is required to use matrix 'mapPoints' method.
                val touchPoints = floatArrayOf(x, y)

                // Get bounds of current component to later validate if touch is in area of
                // that component or not.
                val bounds = v.reportBound()

                touchMatrix.run {
                    setTranslate(
                        abs(canvasMatrix.getTranslationX(true)),
                        abs(canvasMatrix.getTranslationY())
                    )

                    // Scale down the current matrix as much as canvas matrix scale up.
                    // We do this because if we zoom in image, the rectangle our area that we see
                    // is also smaller so we do this to successfully map our touch points to that area (zoomed area).
                    val scale = canvasMatrix.getOppositeScale()
                    postScale(scale, scale)

                    // Finally handle the rotation of component.
                    postRotate(
                        -v.reportRotation(),
                        v.reportBoundPivotX(),
                        v.reportBoundPivotY()
                    )

                }

                // Finally map the touch points.
                touchMatrix.mapPoints(touchPoints)

                if (touchPoints[0] in bounds.left..bounds.right && touchPoints[1] in bounds.top..bounds.bottom)
                    return v
            }
        }
        return null
    }

    /**
     * This method converts page into Bitmap.
     * Any pixels outside of page bounds will not be converted.
     * @return Bitmap of current page content with bitmap having matching size with page.
     * @throws IllegalStateException If page size hasn't been set.
     */
    fun convertPageToBitmap(transparentBackground: Boolean = false): Bitmap {
        if (pageWidth == 0 || pageHeight == 0) throw IllegalStateException("Page size should not be 0")

        // Set children layer type to none to get better quality for rendering.
        children.forEach {
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

            rotateDetector.resetRotation(component.reportRotation())

            callOnChildClickListeners(child, true)

            currentEditingView = component

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
         * This method get invoked when child selection state chagnes.
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
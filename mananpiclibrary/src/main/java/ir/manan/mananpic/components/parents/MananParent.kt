package ir.manan.mananpic.components.parents

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import ir.manan.mananpic.components.MananFrame
import ir.manan.mananpic.properties.MananComponent
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.MananMatrixAnimator
import ir.manan.mananpic.utils.gesture.detectors.MoveDetector
import ir.manan.mananpic.utils.gesture.gestures.OnMoveListener
import ir.manan.mananpic.utils.gesture.gestures.OnRotateListener
import ir.manan.mananpic.utils.gesture.gestures.RotationDetectorGesture
import kotlin.math.abs

abstract class MananParent(context: Context, attributeSet: AttributeSet?) :
    FrameLayout(context, attributeSet), ScaleGestureDetector.OnScaleGestureListener,
    OnRotateListener, OnMoveListener {
    constructor(context: Context) : this(context, null)

    /* Detectors --------------------------------------------------- */

    protected var rotationDetector: RotationDetectorGesture? = null

    protected var scaleDetector: ScaleGestureDetector? = null

    protected var translationDetector: MoveDetector? = null

    /* Canvas related ---------------------------------------------- */

    /** Flag that determines if canvas should use matrix to manipulate scale and translation. */
    protected var isCanvasMatrixEnabled = true

    /** Matrix that we later use to manipulate canvas scale and translation. */
    protected val canvasMatrix = MananMatrix()

    protected var matrixAnimator: MananMatrixAnimator? = null

    /**
     * Extra space that user can translate the canvas before canvas animation triggers.
     * Default is 0.
     */
    var extraSpaceToTriggerAnimation = 0f

    protected var maximumScaleOfCanvas = 10f

    /* Allocations ------------------------------------------------------------------------------- */

    /**
     * Used for mapping operations like transforming a rectangle etc...
     */
    protected val mappingMatrix by lazy {
        Matrix()
    }

    /**
     * A rectangle to hold the mapped rectangles inside it. It is
     * defined here to avoid allocations.
     */
    protected val mappingRectangle by lazy {
        RectF()
    }

    /* Gesture related variables ------------------------------------------------------------------------------------------*/
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

    /* Listeners ------------------------------------------------------------------  */

    private var onChildClicked: ((View, Boolean) -> Unit)? = null
    private var onChildClickedListener: OnChildClickedListener? = null

    private var onChildrenChanged: ((View, Boolean) -> Unit)? = null
    private var onChildrenChangedListener: OnChildrenListChanged? = null

    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector?) {
        // Set 'isMoved' to true to prevent selecting the target view if it's been in user touch locations.
        isMoved = true
        animateCanvasBack()
    }

    override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
        return true
    }

    override fun onMove(dx: Float, dy: Float): Boolean {
        return true
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float): Boolean {
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
                onSelectedComponentChanged(childAtPosition)
                invalidate()
            }
        }

        // Reset for next gesture.
        totalDx = 0f
        totalDy = 0f
        isMoved = false

        animateCanvasBack()
    }

    protected open fun onSelectedComponentChanged(newChild: MananComponent) {
        rotationDetector?.resetRotation(newChild.reportRotation())
        callOnChildClickListeners(newChild as View, true)
        currentEditingView = newChild
    }

    override fun onRotateBegin(initialDegree: Float): Boolean {
        return true
    }

    override fun onRotate(degree: Float): Boolean {
        return true
    }

    override fun onRotateEnded() {
    }

    /* Selected component --------------------------------------------------------------------- */

    protected var currentEditingView: MananComponent? = null


    /* Methods --------------------------------------------------------------------------------- */

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        scaleDetector?.onTouchEvent(event)
        rotationDetector?.onTouchEvent(event)
        translationDetector?.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.run {
            if (isCanvasMatrixEnabled)
                concat(canvasMatrix)

            super.onDraw(this)
        }
    }

    /**
     * Scales canvas matrix.
     */
    protected fun scaleCanvas(scaleFactor: Float, pivotX: Float, pivotY: Float) {
        canvasMatrix.postScale(scaleFactor, scaleFactor, pivotX, pivotY)
    }

    /**
     * Translates canvas matrix.
     */
    protected fun translateCanvas(dx: Float, dy: Float) {
        canvasMatrix.postTranslate(dx, dy)
    }

    private fun animateCanvasBack() {
        matrixAnimator?.run {
            startAnimation(maximumScaleOfCanvas, extraSpaceToTriggerAnimation)
            setOnMatrixUpdateListener {
                invalidate()
            }
        }
    }

    /**
     * Clones the selected component. This method doesn't do anything (doesn't throw exception) if there isn't any component selected.
     */
    fun cloneSelectedComponent() {
        currentEditingView?.run {
            addView(clone())
            // Set the flag to later not fit the component inside page.
            onChildCloned()
        }
    }

    /**
     * Called after a clone of selected component has been made via [cloneSelectedComponent].
     */
    protected open fun onChildCloned() {

    }

    /**
     * Returns the child at given coordinates.
     * If two child overlap it swaps between them on each tap.
     * @param x X coordinate of current touch.
     * @param y Y coordinate of current touch.
     */
    protected open fun getChildAtPoint(x: Float, y: Float): View? {
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


    /**
     * Determines if child's x coordinate should offset.
     */
    protected fun getOffsetX(v: View): Int {
        return if (v.layoutParams.width == LayoutParams.MATCH_PARENT)
            paddingLeft
        else 0
    }

    /**
     * Determines if child's y coordinate should offset.
     */
    protected fun getOffsetY(v: View): Int {
        return if (v.layoutParams.width == LayoutParams.MATCH_PARENT)
            paddingTop
        else 0
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

    /**
     * Initializes children and sets necessary parameters on it.
     * @param child Children that is going to be initialized.
     */
    protected open fun initializeChild(child: View) {
        child.run {

            updateLayoutParams<LayoutParams> {
                gravity = Gravity.CENTER
            }

            val component = (child as MananComponent)

            // Reset rotation of rotation detector to current component rotation.
            rotationDetector?.resetRotation(component.reportRotation())

            callOnChildClickListeners(child, true)

            currentEditingView = component
        }
    }

    /**
     * Selects a view in view group to enter editing state.
     * It does not throw exception if child in given index is null.
     * @param index Index of view that is going to be selected.
     */
    open fun selectView(index: Int) {
        val selectedChild = getChildAt(index) as? MananComponent
        if (selectedChild != null) {
            callOnChildClickListeners(selectedChild as View, true)
            currentEditingView = selectedChild
            rotationDetector?.resetRotation(selectedChild.reportRotation())
            invalidate()
        }
    }

    /**
     * Deselects the current selected view.
     * This method doesn't throw exception if there isn't any child selected.
     */
    open fun deselectSelectedView() {
        if (currentEditingView != null) {

            callOnChildClickListeners(currentEditingView as View, false)

            currentEditingView = null
            invalidate()
        }
    }

    /**
     * Removes the view that is currently selected.
     */
    open fun removeSelectedView() {
        if (currentEditingView != null) {

            callOnChildClickListeners(currentEditingView as View, false)

            removeView(currentEditingView as View)
            currentEditingView = null
        }
    }

    protected fun callOnChildClickListeners(view: View, isSelected: Boolean) {
        onChildClicked?.invoke(view, isSelected)
        onChildClickedListener?.onClicked(view, isSelected)

    }

    protected fun callOnChildrenChangedListener(view: View, deleted: Boolean) {
        onChildrenChanged?.invoke(view, deleted)
        onChildrenChangedListener?.onChanged(view, deleted)
    }

    /**
     * Returns currently selected child.
     */
    fun getSelectedView(): View? {
        return currentEditingView as? View
    }

    /**
     * Sets step for rotation detector.
     * If greater than 0 then rotation snaps to steps of current number for example
     * if step was 8.5f then we would have 8.5f then 17f then 25.5f as rotation and so on.
     */
    fun setRotationStep(step: Float) {
        rotationDetector?.setRotationStep(step)
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
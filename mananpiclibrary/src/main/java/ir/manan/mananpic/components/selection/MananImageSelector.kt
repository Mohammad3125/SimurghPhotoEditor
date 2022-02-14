package ir.manan.mananpic.components.selection

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import ir.manan.mananpic.components.imageviews.MananGestureImageView
import ir.manan.mananpic.components.selection.selectors.Selector
import ir.manan.mananpic.utils.MananMatrix
import kotlin.math.abs

/**
 * View for selecting an area from image like selecting it with pen or selecting an area with brush etc...
 */
class MananImageSelector(context: Context, attributeSet: AttributeSet?) :
    MananGestureImageView(context, attributeSet), Selector.OnDispatchToInvalidate {

    constructor(context: Context) : this(context, null)

    companion object {
        private const val MAXIMUM_SCALE_FACTOR = 25f
        private const val MINIMUM_SCALE_ZOOM = 1f
    }

    private var initialX = 0f
    private var initialY = 0f

    private var secondPointerInitialX = 0f
    private var secondPointerInitialY = 0f

    private var isMatrixGesture = false

    private var onSelectorStateChangeListener: OnSelectorStateChangeListener? = null
    private var onCloseCallBack: ((Boolean) -> Unit)? = null

    // Holds value of matrix.
    private val matrixValueHolder by lazy {
        FloatArray(9)
    }

    private val canvasMatrix by lazy {
        MananMatrix()
    }

    private val canvasMatrixAnimator by lazy {
        ValueAnimator().apply {
            duration = 300L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                // Get animating properties.
                val s = getAnimatedValue("scale")
                val tx = getAnimatedValue("translationX")
                val ty = getAnimatedValue("translationY")

                canvasMatrix.run {
                    getValues(matrixValueHolder)

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

    var selector: Selector? = null
        set(value) {
            field = value
            value?.setOnInvalidateListener(this)
            callOnStateChangeListeners(false)
            requestLayout()
        }

    init {
        scaleDetector = ScaleGestureDetector(context, this).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                isQuickScaleEnabled = false
            }
        }
    }

    override fun onImageLaidOut() {
        // Initialize the selector.
        selector?.initialize(
            context,
            canvasMatrix,
            boundsRectangle
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        super.onTouchEvent(event)
        event?.run {
            val totalPoints = pointerCount
            when (actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // If selector is not null and there is only 1 pointer on
                    // screen then begin move in selector.
                    if (selector != null) {
                        val mappedPoints = mapTouchPoints(x, y)
                        selector!!.onMoveBegin(mappedPoints[0], mappedPoints[1])
                    }
                    // Save the initial points to later determine how much user has moved
                    // His/her finger across screen.
                    initialX = x
                    initialY = y
                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (totalPoints == 2) {
                        secondPointerInitialX = getX(1)
                        secondPointerInitialY = getY(1)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    // Determine total amount that user moved his/her finger on screen.
                    var dx = x - initialX
                    var dy = y - initialY

                    // If there are currently 2 pointers on screen and user is not scaling then
                    // translate the canvas matrix.
                    if (totalPoints == 2) {
                        val secondPointerX = getX(1)
                        val secondPointerY = getY(1)

                        // Calculate total difference by taking difference of first and second pointer
                        // and their initial point then append them and finally divide by two because
                        // we have two pointers that add up and it makes the gesture double as fast.
                        dx = ((secondPointerX - secondPointerInitialX) + (x - initialX)) / 2
                        dy = ((secondPointerY - secondPointerInitialY) + (y - initialY)) / 2

                        // Reset initial positions.
                        initialX = x
                        initialY = y

                        secondPointerInitialX = secondPointerX
                        secondPointerInitialY = secondPointerY

                        isMatrixGesture = true
                        canvasMatrix.postTranslate(dx, dy)
                        invalidate()

                        return true
                    }
                    // Else if selector is not null and there is currently 1 pointer on
                    // screen and user is not performing any other gesture like moving or
                    // scaling, then call 'onMove' method of selector.
                    if (selector != null && totalPoints == 1 && !isMatrixGesture) {

                        // Calculate how much the canvas is scaled then use
                        // that to slow down the translation by that factor.
                        val s = canvasMatrix.getOppositeScale()
                        val exactMapPoints = mapTouchPoints(x, y)
                        selector!!.onMove(
                            dx * s,
                            dy * s,
                            exactMapPoints[0],
                            exactMapPoints[1]
                        )

                        // Reset initial positions.
                        initialX = x
                        initialY = y

                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_UP -> {
                    if (selector != null && !isMatrixGesture) {
                        val mappedPoints = mapTouchPoints(x, y)
                        selector!!.onMoveEnded(mappedPoints[0], mappedPoints[1])
                        callOnStateChangeListeners(selector!!.isClosed())
                    }
                    animateCanvasBack()
                    isMatrixGesture = false
                    return false
                }
                else -> {
                    return false
                }
            }
        }
        return false
    }

    override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
        isMatrixGesture = true
        return true
    }

    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        detector?.run {
            val sf = scaleFactor
            canvasMatrix.postScale(sf, sf, focusX, focusY)
            invalidate()
            return true
        }
        return false
    }

    override fun onScaleEnd(detector: ScaleGestureDetector?) {
        super.onScaleEnd(detector)
        animateCanvasBack()
    }

    /**
     * This function maps the touch location provided with canvas matrix to provide
     * correct coordinates of touch if canvas is scaled and or translated.
     */
    private fun mapTouchPoints(touchX: Float, touchY: Float): FloatArray {
        val touchPoints = floatArrayOf(touchX, touchY)
        Matrix().run {

            val tx = canvasMatrix.getTranslationX(true)
            val ty = canvasMatrix.getTranslationY()

            setTranslate(
                if (tx < leftEdge) abs(tx) else abs(tx) - (tx * 2),
                if (ty < topEdge) abs(ty) else abs(ty) - (ty * 2)
            )

            val scale = canvasMatrix.getOppositeScale()
            postScale(scale, scale)

            mapPoints(touchPoints)

        }
        return touchPoints
    }

    fun select(): Bitmap? {
        if (drawable != null && selector != null)
            return selector!!.select(drawable)

        return null
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        canvasMatrix.reset()
    }

    fun resetSelection() {
        selector?.resetSelection()
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.run {
            // Save state of canvas.
            save()
            // Concat the canvas to 'canvasMatrix'.
            concat(canvasMatrix)
            super.onDraw(this)
            // Restore canvas state and let the selector scale itself based on canvas we passed earlier to it.
            restore()
            selector?.draw(this)
        }
    }

    override fun invalidateDrawings() {
        invalidate()
    }

    private fun animateCanvasBack() {
        if (!canvasMatrixAnimator.isRunning) {

            // Get matrix values.
            val scale = canvasMatrix.getScaleX(true)
            val tx = canvasMatrix.getTranslationX()
            val ty = canvasMatrix.getTranslationY()

            val zoomWindow = RectF(boundsRectangle)

            Matrix().run {
                setScale(scale, scale)
                mapRect(zoomWindow)
            }

            // Calculate the valid scale (scale greater than maximum allowable scale and less than initial scale)
            val validatedScale =
                if (scale > MAXIMUM_SCALE_FACTOR) MAXIMUM_SCALE_FACTOR else if (scale < MINIMUM_SCALE_ZOOM) MINIMUM_SCALE_ZOOM else scale

            // Here we calculate the edge of right side to later do not go further that point.
            val rEdge =
                -(zoomWindow.right - rightEdge)

            // Here we calculate the edge of bottom side to later do not go further that point.
            val bEdge =
                -(zoomWindow.bottom - bottomEdge)

            canvasMatrixAnimator.run {
                val animationPropertyHolderList = ArrayList<PropertyValuesHolder>()
                // Add PropertyValuesHolder for each animation property if they should be animated.
                if (scale < MINIMUM_SCALE_ZOOM || scale > MAXIMUM_SCALE_FACTOR)
                    animationPropertyHolderList.add(
                        PropertyValuesHolder.ofFloat(
                            "scale",
                            scale,
                            validatedScale
                        )
                    )

                if (tx > 0f || tx < rEdge)
                    animationPropertyHolderList.add(
                        PropertyValuesHolder.ofFloat(
                            "translationX",
                            tx,
                            if (scale < MINIMUM_SCALE_ZOOM || tx > 0f) 0f else rEdge
                        )
                    )

                if (ty > 0f || ty < bEdge)
                    animationPropertyHolderList.add(
                        PropertyValuesHolder.ofFloat(
                            "translationY",
                            ty,
                            if (scale < MINIMUM_SCALE_ZOOM || ty > 0f) 0f else bEdge
                        )
                    )


                // Finally convert the array list to array and set values of animator.
                setValues(
                    *Array(
                        animationPropertyHolderList.size
                    ) {
                        animationPropertyHolderList[it]
                    }
                )

                start()
            }
        }
    }

    /**
     * Register a callback for selector to be invoked when selector is ready to be selected or not.
     * @see OnSelectorStateChangeListener
     */
    fun setOnSelectorStateChangeListener(stateChangeListener: OnSelectorStateChangeListener) {
        onSelectorStateChangeListener = stateChangeListener
    }

    /**
     * Register a callback for selector to be invoked when selector is ready to be selected or not.
     * @see OnSelectorStateChangeListener
     */
    fun setOnSelectorStateChangeListener(listener: (Boolean) -> Unit) {
        onCloseCallBack = listener
    }

    private fun callOnStateChangeListeners(isClose: Boolean) {
        onCloseCallBack?.invoke(isClose)
        onSelectorStateChangeListener?.onStateChanged(isClose)
    }

    /**
     * Undoes the state of selector (if selector is not null.)
     */
    fun undo() {
        if (selector != null) {
            selector!!.undo()
            // Call listeners in case state of selector changes after undo operation.
            callOnStateChangeListeners(selector!!.isClosed())
        }
    }

    /**
     * Interface definition for a callback to be invoked when selector is ready to be selected
     * or not.
     */
    interface OnSelectorStateChangeListener {
        fun onStateChanged(isClose: Boolean)
    }
}
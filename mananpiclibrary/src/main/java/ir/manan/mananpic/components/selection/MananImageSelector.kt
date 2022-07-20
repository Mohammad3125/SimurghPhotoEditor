package ir.manan.mananpic.components.selection

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import ir.manan.mananpic.components.imageviews.MananGestureImageView
import ir.manan.mananpic.components.selection.selectors.Selector
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.MananMatrixAnimator
import ir.manan.mananpic.utils.dp
import kotlin.math.max
import kotlin.math.min

/**
 * View for selecting an area from image like selecting it with pen or selecting an area with brush etc...
 */
class MananImageSelector(context: Context, attributeSet: AttributeSet?) :
    MananGestureImageView(context, attributeSet), Selector.OnDispatchToInvalidate {

    constructor(context: Context) : this(context, null)

    private var initialX = 0f
    private var initialY = 0f

    private var secondPointerInitialX = 0f
    private var secondPointerInitialY = 0f

    private var isMatrixGesture = false

    private var isNewGesture = false

    private var onSelectorStateChangeListener: OnSelectorStateChangeListener? = null
    private var onCloseCallBack: ((Boolean) -> Unit)? = null

    private var maximumScale = 0f

    private val canvasMatrix by lazy {
        MananMatrix()
    }

    private val matrixAnimator by lazy {
        MananMatrixAnimator(canvasMatrix, RectF(boundsRectangle), 300L, FastOutSlowInInterpolator())
    }

    var selector: Selector? = null
        set(value) {
            field = value
            value?.setOnInvalidateListener(this)
            callOnStateChangeListeners(false)
            requestLayout()
            selector?.initialize(context, canvasMatrix, boundsRectangle)
        }

    init {
        scaleDetector = ScaleGestureDetector(context, this).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                isQuickScaleEnabled = false
            }
        }
    }

    override fun onImageLaidOut() {
        context.resources.displayMetrics.run {
            maximumScale = max(widthPixels, heightPixels) / min(bitmapWidth, bitmapHeight) * 10f
        }
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
                    if (totalPoints == 2 && isNewGesture) {
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
                MotionEvent.ACTION_POINTER_UP -> {
                    isNewGesture = false
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
                    isNewGesture = true
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
        return !matrixAnimator.isAnimationRunning()
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
                -tx,
                -ty
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
        matrixAnimator.run {
            startAnimation(maximumScale, dp(48))
            setOnMatrixUpdateListener {
                invalidate()
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
     * Returns path data of current selector if it's closed.
     */
    fun getPathData(): Path? {
        selector?.getClipPath()?.run {


            // Get how much the current bitmap displayed is scaled comparing to original drawable size.
            val totalScaled = drawable.intrinsicWidth / (rightEdge - leftEdge)

            return Path(this).apply {
                transform(Matrix().apply {
                    setScale(totalScaled, totalScaled, leftEdge, topEdge)
                    postTranslate(-leftEdge, -topEdge)
                })
            }
        }
        return null
    }

    /**
     * Interface definition for a callback to be invoked when selector is ready to be selected
     * or not.
     */
    interface OnSelectorStateChangeListener {
        fun onStateChanged(isClose: Boolean)
    }
}
package ir.manan.mananpic.components.selection

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Build
import android.util.AttributeSet
import android.view.ScaleGestureDetector
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import ir.manan.mananpic.components.imageviews.MananGestureImageView
import ir.manan.mananpic.components.selection.selectors.Selector
import ir.manan.mananpic.utils.gesture.detectors.MoveDetector
import kotlin.math.abs

class MananImageSelector(context: Context, attributeSet: AttributeSet?) :
    MananGestureImageView(context, attributeSet), Selector.OnDispatchToInvalidate {

    constructor(context: Context) : this(context, null)

    companion object {
        const val MAXIMUM_SCALE_FACTOR = 25f
        const val MINIMUM_SCALE_ZOOM = 1f
    }

    private var onCloseListener: OnCloseListener? = null
    private var onCloseCallBack: (() -> Unit)? = null

    /**
     * Determines if zoom on image is enabled.
     */
    var isZoomMode = false

    // Holds value of matrix.
    private val matrixValueHolder by lazy {
        FloatArray(9)
    }

    private val canvasMatrix by lazy {
        Matrix()
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
                    val matrixValueHolder = FloatArray(9)
                    getValues(matrixValueHolder)

                    // If translation isn't null or in other words, we should animate the translation, then animate it.
                    if (tx != null) {
                        postTranslate(
                            tx as Float - matrixValueHolder[Matrix.MTRANS_X],
                            0f
                        )
                    }

                    // If translation isn't null or in other words, we should animate the translation, then animate it.
                    if (ty != null) {
                        postTranslate(
                            0f,
                            ty as Float - matrixValueHolder[Matrix.MTRANS_Y]
                        )
                    }

                    // If scale property isn't null then scale it.
                    if (s != null) {
                        val totalScale = (s as Float) / matrixValueHolder[Matrix.MSCALE_X]
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
            requestLayout()
        }

    init {
        moveDetector = MoveDetector(1, this)
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

    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        if (isZoomMode)
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

    override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
        if (!isZoomMode) {
            val mappedPoints = mapTouchPoints(initialX, initialY)
            selector?.onMoveBegin(mappedPoints[0], mappedPoints[1])
        }
        return true
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float): Boolean {
        if (isZoomMode) {
            canvasMatrix.postTranslate(dx, dy)
            invalidate()
        } else {
            canvasMatrix.getValues(matrixValueHolder)
            // Calculate how much the canvas is scaled then use
            // that to slow down the translation by that factor.
            // Note that we divide 1 by matrix scale to get reverse of current
            // scale, for example if scale is 2 the we get 0.5 by doing that.
            val s = 1f / matrixValueHolder[Matrix.MSCALE_X]
            val exactMapPoints = mapTouchPoints(ex, ey)
            selector?.onMove(
                dx * s,
                dy * s,
                exactMapPoints[0],
                exactMapPoints[1]
            )
        }
        return true
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        super.onMoveEnded(lastX, lastY)
        if (!isZoomMode) {
            val mappedPoints = mapTouchPoints(lastX, lastY)
            selector?.onMoveEnded(mappedPoints[0], mappedPoints[1])
            if (selector != null && selector!!.isClosed()) callCloseListeners()
        }
        animateCanvasBack()
    }

    /**
     * This function maps the touch location provided with canvas matrix to provide
     * correct coordinates of touch if canvas is scaled and or translated.
     */
    private fun mapTouchPoints(touchX: Float, touchY: Float): FloatArray {
        val touchPoints = floatArrayOf(touchX, touchY)
        Matrix().run {

            val matrixValues = FloatArray(9)
            canvasMatrix.getValues(matrixValues)

            val tx = matrixValues[Matrix.MTRANS_X]
            val ty = matrixValues[Matrix.MTRANS_Y]

            setTranslate(
                if (tx < leftEdge) abs(tx) else abs(tx) - (tx * 2),
                if (ty < topEdge) abs(ty) else abs(ty) - (ty * 2)
            )

            val scale = 1f / matrixValues[Matrix.MSCALE_X]
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

            val matrixValueHolder = FloatArray(9)
            canvasMatrix.getValues(matrixValueHolder)

            // Get matrix values.
            val scale = matrixValueHolder[Matrix.MSCALE_X]
            val tx = matrixValueHolder[Matrix.MTRANS_X]
            val ty = matrixValueHolder[Matrix.MTRANS_Y]

            // Here we calculate the edge of right side to later do not go further that point.
            val rEdge =
                calculateEdge(scale, 1f, rightEdge, 0f)

            // Here we calculate the edge of bottom side to later do not go further that point.
            val bEdge =
                calculateEdge(scale, 1f, bottomEdge, 0f)

            // Calculate the valid scale (scale greater than maximum allowable scale and less than initial scale)
            val validatedScale =
                if (scale > MAXIMUM_SCALE_FACTOR) MAXIMUM_SCALE_FACTOR else if (scale < MINIMUM_SCALE_ZOOM) MINIMUM_SCALE_ZOOM else scale


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

                if (tx < rEdge || tx > 0f)
                    animationPropertyHolderList.add(
                        PropertyValuesHolder.ofFloat(
                            "translationX",
                            tx,
                            if (tx > 0f || scale < MINIMUM_SCALE_ZOOM) 0f else rEdge
                        )
                    )

                if (ty < bEdge || ty > 0f)
                    animationPropertyHolderList.add(
                        PropertyValuesHolder.ofFloat(
                            "translationY",
                            ty,
                            if (ty > 0f || scale < MINIMUM_SCALE_ZOOM) 0f else bEdge
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

    private fun calculateEdge(
        scaled: Float,
        initScale: Float,
        initialSize: Float,
        initialOffset: Float
    ): Float =
        -((scaled * initialSize / initScale) - initialSize - initialOffset)


    fun setOnCloseListener(listener: OnCloseListener) {
        onCloseListener = listener
    }

    fun setOnCloseListener(listener: () -> Unit) {
        onCloseCallBack = listener
    }

    private fun callCloseListeners() {
        onCloseCallBack?.invoke()
        onCloseListener?.onClose()
    }

    /**
     * Undoes the state of selector (if selector is not null.)
     */
    fun undo() {
        selector?.undo()
    }

    interface OnCloseListener {
        fun onClose()
    }
}
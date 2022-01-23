package ir.manan.mananpic.components.selection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Build
import android.util.AttributeSet
import android.view.ScaleGestureDetector
import ir.manan.mananpic.components.imageviews.MananGestureImageView
import ir.manan.mananpic.components.selection.selectors.Selector
import ir.manan.mananpic.utils.gesture.detectors.MoveDetector
import kotlin.math.abs

class MananImageSelector(context: Context, attributeSet: AttributeSet?) :
    MananGestureImageView(context, attributeSet), Selector.OnDispatchToInvalidate {

    constructor(context: Context) : this(context, null)

    companion object {
        const val MAXIMUM_SCALE_ZOOM = 15f
        const val MINIMUM_SCALE_ZOOM = 0.5f
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

    var selector: Selector? = null
        set(value) {
            field = value
            value?.invalidateListener = this
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

    override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
        val mappedPoints = mapTouchPoints(initialX, initialY)
        selector?.onMoveBegin(mappedPoints[0], mappedPoints[1])
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
            setMatrix(canvasMatrix)
            super.onDraw(this)
            selector?.draw(this)
        }
    }

    override fun invalidateDrawings() {
        invalidate()
    }

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

    interface OnCloseListener {
        fun onClose()
    }
}
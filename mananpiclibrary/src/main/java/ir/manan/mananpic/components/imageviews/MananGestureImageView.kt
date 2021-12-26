package ir.manan.mananpic.components.imageviews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import ir.manan.mananpic.utils.gesture.gestures.Gesture
import ir.manan.mananpic.utils.gesture.gestures.OnMoveListener
import ir.manan.mananpic.utils.gesture.gestures.OnRotateListener
import kotlin.math.sqrt

/**
 * A base class for all features that work on a view especially ones that modify [ImageView]'s matrix.
 * Derived classes could initialize gesture detectors they like and do certain thing like manipulating
 * ImageView's matrix and so on.
 */
open class MananGestureImageView(
    context: Context,
    attributeSet: AttributeSet?
) :
    AppCompatImageView(context, attributeSet), ScaleGestureDetector.OnScaleGestureListener,
    OnRotateListener, OnMoveListener, GestureDetector.OnDoubleTapListener,
    GestureDetector.OnGestureListener {

    /**
     * Matrix that we later modify and assign to image matrix.
     */
    protected val imageviewMatrix by lazy { Matrix() }

    /**
     * Holds values of matrix inside it.
     */
    protected val matrixValueHolder by lazy { FloatArray(9) }

    /**
     * Scale detector that is used to detect if user scaled matrix.
     * It is nullable; meaning a derived class could use scale gesture or not.
     */
    protected var scaleDetector: ScaleGestureDetector? = null

    /**
     * Rotation detector that is used to detect if user performed rotation gesture.
     * It is nullable; meaning a derived class could use rotating gesture or not.
     */
    protected var rotationDetector: Gesture? = null

    /**
     * Move detector that is used to detect if user performed move gesture (moved fingers across screen).
     * It is nullable; meaning a derived class could use moving gesture or not.
     */
    protected var moveDetector: Gesture? = null

    /**
     * Common gesture detector is used to detect if user performed a gesture like Fling, Scroll, DoubleTap and etc...
     * It is nullable; meaning a derived class could use common gesture or not.
     */
    protected var commonGestureDetector: GestureDetector? = null

    /**
     * Left edge of bitmap = leftPadding + matrix translation x.
     * This value is available after [onImageLaidOut] has ben called.
     */
    protected var leftEdge = 0f

    /**
     * Top edge of bitmap = topPadding + matrix translation y.
     * This value is available after [onImageLaidOut] has ben called.
     */
    protected var topEdge = 0f

    /**
     * Right edge of bitmap = scaled drawable width + leftPadding + matrix translation x.
     * This value is available after [onImageLaidOut] has ben called.
     */
    protected var rightEdge = 0f

    /**
     * Bottom edge of bitmap = scaled drawable height + topPadding + matrix translation y.
     * This value is available after [onImageLaidOut] has ben called.
     */
    protected var bottomEdge = 0f


    /**
     * Real width of current image's bitmap.
     * This value is available after [onImageLaidOut] has ben called.
     */
    protected var bitmapWidth = 0f

    /**
     * Real height of current image's bitmap.
     * This value is available after [onImageLaidOut] has ben called.
     */
    protected var bitmapHeight = 0f


    /**
     * This value represents scale value of matrix after calling [Matrix.setRectToRect].
     * This value is available after [onImageLaidOut] has ben called.
     */
    protected var initialScale = 0f

    /**
     * Later will be used to notify if imageview's bitmap has been changed.
     */
    private var isNewBitmap = false


    // Image's drawable size.
    protected var drawableWidth: Int = 0
    protected var drawableHeight: Int = 0

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onRotateBegin(initialDegree: Float): Boolean {
        return false
    }

    override fun onRotate(degree: Float): Boolean {
        return false
    }

    override fun onRotateEnded() {
    }

    override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
        return false
    }

    override fun onMove(dx: Float, dy: Float): Boolean {
        return false
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float): Boolean {
        return false
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
    }

    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        return false
    }

    override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector?) {

    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
        return false
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
        return false
    }

    override fun onDown(e: MotionEvent?): Boolean {
        return false
    }

    override fun onShowPress(e: MotionEvent?) {
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

    /**
     * This method gets called before child is about to be drawn.
     * Could be used to resize the image's matrix to fit the parent bounds and etc...
     */
    protected open fun onImageLaidOut() {
    }

    /**
     * Called when drawable is about to be resized to fit the view's dimensions.
     * @return Modified matrix.
     */
    protected open fun resizeDrawable() {
        val mDrawable = drawable
        val imgMatrix = Matrix(matrix)

        imgMatrix.setRectToRect(
            RectF(
                0f,
                0f,
                mDrawable.intrinsicWidth.toFloat(),
                mDrawable.intrinsicHeight.toFloat()
            ),
            RectF(
                0f,
                0f,
                (width - paddingRight - paddingLeft).toFloat(),
                (height - paddingBottom - paddingTop).toFloat()
            ),
            Matrix.ScaleToFit.CENTER
        )

        imageMatrix = imgMatrix
        imageviewMatrix.set(imgMatrix)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        val mDrawable = drawable ?: return

        drawableWidth = mDrawable.intrinsicWidth
        drawableHeight = mDrawable.intrinsicHeight

        if (changed || isNewBitmap) {

            resizeDrawable()

            initialScale = getMatrixValue(Matrix.MSCALE_X, true)

            calculateBounds()

            isNewBitmap = false

            onImageLaidOut()
        }

    }

    override fun setImageDrawable(drawable: Drawable?) {
        if (drawable !is BitmapDrawable) throw IllegalArgumentException(
            "Type of drawable should only be BitmapDrawable"
        )
        super.setImageDrawable(drawable)
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        isNewBitmap = true
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        isNewBitmap = true
    }

    /**
     * Creates a bitmap from current drawable.
     */
    open fun toBitmap(): Bitmap {
        val mDrawable =
            drawable ?: throw IllegalStateException("drawable is null")

        return (mDrawable as BitmapDrawable).bitmap
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        scaleDetector?.onTouchEvent(event)
        rotationDetector?.onTouchEvent(event)
        moveDetector?.onTouchEvent(event)
        commonGestureDetector?.onTouchEvent(event)
        return if (areGesturesNull()) super.onTouchEvent(event)
        else true
    }

    private fun areGesturesNull(): Boolean {
        return (scaleDetector == null && rotationDetector == null && moveDetector == null && commonGestureDetector == null)
    }

    /**
     * This method returns the value from matrix in given constant like [Matrix.MTRANS_X], [Matrix.MSCALE_X] and etc.
     * @param constant Constant that defines which item in matrix we're interested in.
     * @param refreshValues Determines if we want to fetch new values from matrix or use the latest fetch values.
     * @return A float number in matrix.
     */
    protected open fun getMatrixValue(constant: Int, refreshValues: Boolean = false): Float {
        if (refreshValues)
            imageviewMatrix.getValues(matrixValueHolder)

        return matrixValueHolder[constant]
    }

    /**
     * Sets a value in both value holder and matrix itself.
     * @param constant Constant in matrix to replace the value with.
     * @param value Value of that constant in matrix to replace.
     */
    protected open fun setMatrixValue(constant: Int, value: Float) {
        matrixValueHolder[constant] = value
        imageviewMatrix.setValues(matrixValueHolder)
    }

    /**
     * This method loads the matrix value holder with new data.
     */
    protected open fun refreshMatrixValueHolder() {
        imageviewMatrix.getValues(matrixValueHolder)
    }

    /**
     * Updates imageview matrix with current matrix.
     */
    protected open fun updateImageMatrix() {
        imageMatrix = imageviewMatrix
        calculateBounds()
    }

    /**
     * Calculates bounds of image with matrix values.
     */
    private fun calculateBounds() {
        leftEdge = paddingLeft + getMatrixValue(Matrix.MTRANS_X, true)
        topEdge = paddingTop + getMatrixValue(Matrix.MTRANS_Y)

        // Calculate real scale since rotation does affect it.
        val sx = getMatrixValue(Matrix.MSCALE_X)
        val skewY = getMatrixValue(Matrix.MSKEW_Y)

        val scale = sqrt(sx * sx + skewY * skewY)

        bitmapWidth = (drawableWidth * scale)
        bitmapHeight = (drawableHeight * scale)

        rightEdge = bitmapWidth + leftEdge
        bottomEdge = bitmapHeight + topEdge
    }
}
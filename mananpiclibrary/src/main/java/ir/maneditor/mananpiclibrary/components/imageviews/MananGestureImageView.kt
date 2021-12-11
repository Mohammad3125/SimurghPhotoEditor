package ir.maneditor.mananpiclibrary.components.imageviews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import ir.maneditor.mananpiclibrary.R
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.Gesture
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.OnMoveListener
import ir.maneditor.mananpiclibrary.utils.gesture.gestures.OnRotateListener

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
     * Set the initial padding left. Initial padding is a padding
     * that is temporary present when image is at it's initial scale.
     * As user zooms in image this padding doesn't restrict the bounds
     * of image anymore.
     */
    var initialPaddingLeft = 0f
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Set the initial padding top. Initial padding is a padding
     * that is temporary present when image is at it's initial scale.
     * As user zooms in image this padding doesn't restrict the bounds
     * of image anymore.
     */
    var initialPaddingTop = 0f
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Set the initial padding right. Initial padding is a padding
     * that is temporary present when image is at it's initial scale.
     * As user zooms in image this padding doesn't restrict the bounds
     * of image anymore.
     */
    var initialPaddingRight = 0f
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Set the initial padding bottom. Initial padding is a padding
     * that is temporary present when image is at it's initial scale.
     * As user zooms in image this padding doesn't restrict the bounds
     * of image anymore.
     */
    var initialPaddingBottom = 0f
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Set the initial padding horizontal. Initial padding is a padding
     * that is temporary present when image is at it's initial scale.
     * As user zooms in image this padding doesn't restrict the bounds
     * of image anymore.
     */
    var initialPaddingHorizontal = 0f
        set(value) {
            initialPaddingRight = value

            initialPaddingLeft = value

            field = value

            invalidate()
        }

    /**
     * Set the initial padding vertical. Initial padding is a padding
     * that is temporary present when image is at it's initial scale.
     * As user zooms in image this padding doesn't restrict the bounds
     * of image anymore.
     */
    var initialPaddingVertical = 0f
        set(value) {
            initialPaddingTop = value

            initialPaddingBottom = value

            field = value

            invalidate()
        }

    /**
     * Set the initial padding in all sides. Initial padding is a padding
     * that is temporary present when image is at it's initial scale.
     * As user zooms in image this padding doesn't restrict the bounds
     * of image anymore.
     */
    var initialPadding = 0f
        set(value) {
            initialPaddingRight = value

            initialPaddingLeft = value

            initialPaddingTop = value

            initialPaddingBottom = value

            field = value

            invalidate()
        }

    init {
        context.theme.obtainStyledAttributes(attributeSet, R.styleable.GestureImageView, 0, 0)
            .run {
                try {
                    var tempPadding =
                        getDimension(R.styleable.GestureImageView_initialPaddingLeft, 0f)

                    if (tempPadding != 0f)
                        initialPaddingLeft = tempPadding

                    tempPadding =
                        getDimension(R.styleable.GestureImageView_initialPaddingRight, 0f)

                    if (tempPadding != 0f)
                        initialPaddingRight = tempPadding

                    tempPadding =
                        getDimension(R.styleable.GestureImageView_initialPaddingBottom, 0f)

                    if (tempPadding != 0f)
                        initialPaddingBottom = tempPadding

                    tempPadding =
                        getDimension(R.styleable.GestureImageView_initialPaddingTop, 0f)

                    if (tempPadding != 0f)
                        initialPaddingTop = tempPadding

                    tempPadding =
                        getDimension(R.styleable.GestureImageView_initialPaddingHorizontal, 0f)

                    if (tempPadding != 0f)
                        initialPaddingHorizontal = tempPadding

                    tempPadding =
                        getDimension(R.styleable.GestureImageView_initialPaddingVertical, 0f)

                    if (tempPadding != 0f)
                        initialPaddingVertical = tempPadding

                    tempPadding =
                        getDimension(R.styleable.GestureImageView_initialPadding, 0f)

                    if (tempPadding != 0f)
                        initialPadding = tempPadding


                } finally {
                    recycle()
                }
            }
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
        fitImageViewInsideBounds()
    }

    /**
     * Fits ImageView's drawable inside ImageView bounds (only usable for [android.widget.ImageView.ScaleType] of type Matrix).
     */
    protected fun fitImageViewInsideBounds() {
        val mDrawable = drawable
        val imgMatrix = matrix

        imgMatrix.setRectToRect(
            RectF(
                0f,
                0f,
                mDrawable.intrinsicWidth.toFloat(),
                mDrawable.intrinsicHeight.toFloat()
            ),
            RectF(
                initialPaddingLeft,
                initialPaddingTop,
                (width - paddingRight - paddingLeft - initialPaddingRight),
                (height - paddingBottom - paddingTop - initialPaddingBottom)
            ),
            Matrix.ScaleToFit.CENTER
        )

        imageMatrix = imgMatrix
        imageviewMatrix.set(imgMatrix)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        onImageLaidOut()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        scaleDetector?.onTouchEvent(event)
        rotationDetector?.onTouchEvent(event)
        moveDetector?.onTouchEvent(event)
        commonGestureDetector?.onTouchEvent(event)
        return super.onTouchEvent(event)
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
    }

}
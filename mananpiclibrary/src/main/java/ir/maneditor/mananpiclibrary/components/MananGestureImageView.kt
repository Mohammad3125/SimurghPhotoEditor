package ir.maneditor.mananpiclibrary.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
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
open class MananGestureImageView protected constructor(
    context: Context,
    attributeSet: AttributeSet?
) :
    FrameLayout(context, attributeSet), ScaleGestureDetector.OnScaleGestureListener,
    OnRotateListener, OnMoveListener, GestureDetector.OnDoubleTapListener,
    GestureDetector.OnGestureListener {
    /**
     * Main [ImageView] that gestures or other operations get performed on it.
     */
    protected val mainImageView by lazy {
        AppCompatImageView(context, null).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            scaleType = ImageView.ScaleType.MATRIX
        }
    }

    /**
     * Matrix that we later modify and assign to [mainImageView]'s image matrix.
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

    init {
        context.theme.obtainStyledAttributes(attributeSet, R.styleable.GestureImageView, 0, 0)
            .run {
                try {
                    setImageResource(
                        getResourceId(
                            R.styleable.GestureImageView_src,
                            0
                        )
                    )
                } finally {
                    recycle()
                }
            }
        addView(mainImageView)
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

    override fun shouldDelayChildPressedState(): Boolean {
        return false
    }


    /**
     * This method gets called before child is about to be drawn.
     * Could be used to resize the image's matrix to fit the parent bounds and etc...
     */
    protected open fun onPreChildDraw() {
        fitImageViewInsideBounds()
    }

    /**
     * Fits ImageView's drawable inside ImageView bounds (only usable for [android.widget.ImageView.ScaleType] of type Matrix).
     */
    protected fun fitImageViewInsideBounds() {
        val mDrawable = mainImageView.drawable
        val imgMatrix = mainImageView.matrix

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

        mainImageView.imageMatrix = imgMatrix
        imageviewMatrix.set(imgMatrix)
    }

    override fun dispatchDraw(canvas: Canvas?) {
        onPreChildDraw()
        super.dispatchDraw(canvas)
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
        mainImageView.imageMatrix = imageviewMatrix
    }

    /**
     * Sets imageview bitmap.
     */
    open fun setImageBitmap(bitmap: Bitmap) {
        mainImageView.setImageBitmap(bitmap)
    }

    /**
     * Sets imageview drawable resource.
     */
    open fun setImageResource(@DrawableRes resId: Int) {
        mainImageView.setImageResource(resId)
    }

}
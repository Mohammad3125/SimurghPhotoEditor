package ir.manan.mananpic.components.imageviews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import ir.manan.mananpic.properties.Bitmapable
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.gesture.GestureUtils
import ir.manan.mananpic.utils.gesture.gestures.Gesture
import ir.manan.mananpic.utils.gesture.gestures.OnMoveListener
import ir.manan.mananpic.utils.gesture.gestures.OnRotateListener
import ir.manan.mananpic.utils.gesture.gestures.RotationDetectorGesture
import kotlin.math.*

/**
 * A base class for all features that work on a view especially ones that modify [ImageView]'s matrix.
 * Derived classes could initialize gesture detectors they like and do certain thing like manipulating
 * ImageView's matrix and so on.
 */
abstract class MananGestureImageView(
    context: Context,
    attributeSet: AttributeSet?
) :
    AppCompatImageView(context, attributeSet), ScaleGestureDetector.OnScaleGestureListener,
    OnRotateListener, OnMoveListener, GestureDetector.OnDoubleTapListener,
    GestureDetector.OnGestureListener, Bitmapable {

    /**
     * Matrix that we later modify and assign to image matrix.
     */
    protected val imageviewMatrix by lazy { MananMatrix() }

    /**
     * Scale detector that is used to detect if user scaled matrix.
     * It is nullable; meaning a derived class could use scale gesture or not.
     */
    protected var scaleDetector: ScaleGestureDetector? = null

    /**
     * Rotation detector that is used to detect if user performed rotation gesture.
     * It is nullable; meaning a derived class could use rotating gesture or not.
     */
    protected var rotationDetector: RotationDetectorGesture? = null

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


    protected val boundsRectangle by lazy {
        RectF()
    }


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


    // Image pivot points which is centered by default.
    protected var imagePivotX = 0f
    protected var imagePivotY = 0f

    // Image rotation.
    protected var imageRotation = 0f


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
        val imgMatrix = Matrix(matrix)

        imgMatrix.setRectToRect(
            RectF(
                0f,
                0f,
                drawableWidth.toFloat(),
                drawableHeight.toFloat()
            ),
            RectF(
                0f,
                0f,
                (width - paddingRight - paddingLeft).toFloat(),
                (height - paddingBottom - paddingTop).toFloat()
            ),
            Matrix.ScaleToFit.CENTER
        )
        setToMatrix(imgMatrix)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        val mDrawable = drawable ?: return

        drawableWidth = mDrawable.intrinsicWidth
        drawableHeight = mDrawable.intrinsicHeight

        if (changed || isNewBitmap) {

            resizeDrawable()

            initialScale = imageviewMatrix.getScaleX(true)

            calculateBounds()

            onImageLaidOut()

            isNewBitmap = false
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

    override fun toBitmap(config: Bitmap.Config): Bitmap {

        val mDrawable =
            drawable ?: throw IllegalStateException("drawable is null")

        val b =
            (mDrawable as BitmapDrawable).bitmap


        val finalBitmap = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply {
            postScale(
                if (imageviewMatrix.getScaleX(true) < 0f) -1f else 1f,
                if (imageviewMatrix.getScaleY() < 0f) -1f else 1f
            )
        }, false)

        // Return the mutable copy.
        return finalBitmap.copy(finalBitmap.config, true)
    }

    override fun toBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {

        if (drawable == null) throw IllegalStateException("drawable is null")

        // Determine how much the desired width and height is scaled base on
        // smallest desired dimension divided by maximum image dimension.
        var totalScaled = width / boundsRectangle.width()

        if (boundsRectangle.height() * totalScaled > height) {
            totalScaled = height / boundsRectangle.height()
        }

        // Create output bitmap matching desired width,height and config.
        val outputBitmap = Bitmap.createBitmap(width, height, config)

        // Calculate extra width and height remaining to later use to center the image inside bitmap.
        val extraWidth = (width / totalScaled) - boundsRectangle.width()
        val extraHeight = (height / totalScaled) - boundsRectangle.height()

        Canvas(outputBitmap).run {
            scale(totalScaled, totalScaled)
            // Finally translate to center the content.
            translate(-leftEdge + extraWidth * 0.5f, -topEdge + extraHeight * 0.5f)
            draw(this)
        }

        return outputBitmap
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
     * Updates imageview matrix with current matrix.
     */
    private fun updateImageMatrix() {
        imageMatrix = imageviewMatrix
        calculateBounds()
    }

    /**
     * Calculates bounds of image with matrix values.
     */
    private fun calculateBounds() {
        imageviewMatrix.run {

            leftEdge = paddingLeft + getTranslationX(true)
            topEdge = paddingTop + getTranslationY()

            val sx = getScaleX()
            val skewY = getSkewY()

            // Calculate real scale since rotation does affect it.
            val scale = sqrt(sx * sx + skewY * skewY)

            bitmapWidth = (drawableWidth * scale)
            bitmapHeight = (drawableHeight * scale)

            rightEdge = bitmapWidth + leftEdge
            bottomEdge = bitmapHeight + topEdge

            val r = -atan2(getSkewX().toDouble(), getScaleX().toDouble()) * (180.0 / PI)

            imageRotation = GestureUtils.mapTo360(r.toFloat())

            // Calculate pivot points.
            // Rotation does affect pivot points and it should be calculated.
            val cx = bitmapWidth * 0.5f
            val cy = bitmapHeight * 0.5f

            val radian = Math.toRadians(r)

            val cosTheta = cos(radian)
            val sinTheta = sin(radian)

            // Calculates the rotated bounds' center.
            imagePivotX = ((leftEdge + cx * cosTheta - cy * sinTheta) - paddingLeft).toFloat()
            imagePivotY = ((topEdge + cx * sinTheta + cy * cosTheta)- paddingTop).toFloat()

            boundsRectangle.set(leftEdge, topEdge, rightEdge, bottomEdge)
        }
    }

    fun flipHorizontal() {
        imageviewMatrix.postScale(-1f, 1f, imagePivotX, 0f)
        imageMatrix = imageviewMatrix
    }

    fun flipVertical() {
        imageviewMatrix.postScale(1f, -1f, 0f, imagePivotY)
        imageMatrix = imageviewMatrix
    }

    /**
     * Post scales the matrix and updates it.
     * @param scaleFactor Total amount to scale the matrix.
     * @param scalePivotX Pivot point which matrix scales around.
     * @param scalePivotY Pivot point which matrix scales around.
     */
    protected fun postScale(scaleFactor: Float, scalePivotX: Float, scalePivotY: Float) {
        imageviewMatrix.postScale(scaleFactor, scaleFactor, scalePivotX, scalePivotY)
        updateImageMatrix()
    }

    /**
     * Scales the matrix and updates it.
     * @param scaleFactor Total amount to scale the matrix.
     * @param scalePivotX Pivot point which matrix scales around.
     * @param scalePivotY Pivot point which matrix scales around.
     */
    protected fun setScale(scaleFactor: Float, scalePivotX: Float, scalePivotY: Float) {
        imageviewMatrix.setScale(scaleFactor, scaleFactor, scalePivotX, scalePivotY)
        updateImageMatrix()
    }

    /**
     * Pre scales the matrix and updates it.
     * @param scaleFactor Total amount to scale the matrix.
     * @param scalePivotX Pivot point which matrix scales around.
     * @param scalePivotY Pivot point which matrix scales around.
     */
    protected fun preScale(scaleFactor: Float, scalePivotX: Float, scalePivotY: Float) {
        imageviewMatrix.preScale(scaleFactor, scaleFactor, scalePivotX, scalePivotY)
        updateImageMatrix()
    }


    /**
     * Post translates the matrix and updates it.
     * @param dx Total pixels to translate in x direction.
     * @param dy Total pixels to translate in y direction.
     */
    protected fun postTranslate(dx: Float, dy: Float) {
        imageviewMatrix.postTranslate(dx, dy)
        updateImageMatrix()
    }


    /**
     * Translates the matrix and updates it.
     * @param x Total pixels to set translate in x direction.
     * @param y Total pixels to set translate in y direction.
     */
    protected fun setTranslate(x: Float, y: Float) {
        imageviewMatrix.setTranslate(x, y)
        updateImageMatrix()
    }

    /**
     * Pre translates the matrix and updates it.
     * @param dx Total pixels to translate in x direction.
     * @param dy Total pixels to translate in y direction.
     */
    protected fun preTranslate(dx: Float, dy: Float) {
        imageviewMatrix.preTranslate(dx, dy)
        updateImageMatrix()
    }

    /**
     * Post rotates the matrix and updates it.
     * @param degree Total degree to rotate matrix around pivot point.
     * @param rotationPivotX Pivot point of x which matrix rotates around.
     * @param rotationPivotY Pivot point of y which matrix rotates around.
     */
    protected fun postRotate(degree: Float, rotationPivotX: Float, rotationPivotY: Float) {
        imageviewMatrix.postRotate(degree, rotationPivotX, rotationPivotY)
        updateImageMatrix()
    }

    /**
     * Rotates the matrix and updates it.
     * @param degree Total degree to rotate matrix around pivot point.
     * @param rotationPivotX Pivot point of x which matrix rotates around.
     * @param rotationPivotY Pivot point of y which matrix rotates around.
     */
    protected fun setRotate(degree: Float, rotationPivotX: Float, rotationPivotY: Float) {
        imageviewMatrix.setRotate(degree, rotationPivotX, rotationPivotY)
        updateImageMatrix()
    }


    /**
     * Pre rotates the matrix and updates it.
     * @param degree Total degree to rotate matrix around pivot point.
     * @param rotationPivotX Pivot point of x which matrix rotates around.
     * @param rotationPivotY Pivot point of y which matrix rotates around.
     */
    protected fun preRotate(degree: Float, rotationPivotX: Float, rotationPivotY: Float) {
        imageviewMatrix.preRotate(degree, rotationPivotX, rotationPivotY)
        updateImageMatrix()
    }

    /**
     * Sets matrix to a new matrix.
     */
    protected fun setToMatrix(toMatrix: Matrix) {
        imageviewMatrix.set(toMatrix)
        updateImageMatrix()
    }

}
package ir.manan.mananpic.components.imageviews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import ir.manan.mananpic.properties.Bitmapable
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.gesture.gestures.Gesture
import ir.manan.mananpic.utils.gesture.gestures.OnMoveListener
import ir.manan.mananpic.utils.gesture.gestures.OnRotateListener
import ir.manan.mananpic.utils.gesture.gestures.RotationDetectorGesture
import kotlin.math.min

/**
 * A base class for all features that work on a view especially ones that modify [ImageView]'s matrix.
 * Derived classes could initialize gesture detectors they like and do certain thing like manipulating
 * ImageView's matrix and so on.
 */
abstract class MananGestureImageView(
    context: Context,
    attributeSet: AttributeSet?
) :
    View(context, attributeSet), ScaleGestureDetector.OnScaleGestureListener,
    OnRotateListener, OnMoveListener, GestureDetector.OnDoubleTapListener,
    GestureDetector.OnGestureListener, Bitmapable, java.io.Serializable {

    private val bitmapPaint by lazy {
        Paint()
    }

    /**
     * Matrix that we later modify and assign to image matrix.
     */
    @Transient
    protected val imageviewMatrix = MananMatrix()

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


    @Transient
    protected val boundsRectangle = RectF()


    /**
     * Real width of current image's bitmap.
     * This value is available after [onImageLaidOut] has ben called.
     */
    protected var bitmapWidth = 0

    /**
     * Real height of current image's bitmap.
     * This value is available after [onImageLaidOut] has ben called.
     */
    protected var bitmapHeight = 0

    protected var finalWidth = 0f
    protected var finalHeight = 0f

    protected var matrixScale = 0f

    /**
     * Later will be used to notify if imageview's bitmap has been changed.
     */
    private var isNewBitmap = true

    var bitmap: Bitmap? = null
        private set

    override fun onRotateBegin(initialDegree: Float, px: Float, py: Float): Boolean {
        return false
    }

    override fun onRotate(degree: Float, px: Float, py: Float): Boolean {
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

    override fun onScale(p0: ScaleGestureDetector): Boolean {
        return false
    }

    override fun onScaleBegin(p0: ScaleGestureDetector): Boolean {
        return true
    }

    override fun onScaleEnd(p0: ScaleGestureDetector) {

    }

    override fun onSingleTapUp(p0: MotionEvent): Boolean {
        return true
    }

    override fun onSingleTapConfirmed(p0: MotionEvent): Boolean {
        return false
    }

    override fun onDoubleTap(p0: MotionEvent): Boolean {
        return true
    }

    override fun onDoubleTapEvent(p0: MotionEvent): Boolean {
        return false
    }

    override fun onDown(p0: MotionEvent): Boolean {
        return false
    }

    override fun onShowPress(p0: MotionEvent) {
    }

    override fun onScroll(
        p0: MotionEvent,
        p1: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return false
    }

    override fun onLongPress(p0: MotionEvent) {

    }

    override fun onFling(
        p0: MotionEvent,
        p1: MotionEvent,
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        resizeDrawable(w.toFloat() - paddingRight, h.toFloat() - paddingBottom)

        calculateBounds()
    }

    /**
     * Called when drawable is about to be resized to fit the view's dimensions.
     * @return Modified matrix.
     */
    protected open fun resizeDrawable(targetWidth: Float, targetHeight: Float) {
        imageviewMatrix.setRectToRect(
            RectF(
                0f,
                0f,
                bitmapWidth.toFloat(),
                bitmapHeight.toFloat()
            ),
            RectF(
                paddingLeft.toFloat(),
                paddingTop.toFloat(),
                targetWidth,
                targetHeight
            ),
            Matrix.ScaleToFit.CENTER
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (isNewBitmap && bitmap != null) {

            resizeDrawable(width.toFloat() - paddingRight, height.toFloat() - paddingBottom)

            calculateBounds()

            onImageLaidOut()

            isNewBitmap = false
        }
    }

    override fun onDraw(canvas: Canvas?) {
        if (bitmap != null && canvas != null) {
            canvas.drawBitmap(bitmap!!, imageviewMatrix, bitmapPaint)
        }
    }

    override fun toBitmap(config: Bitmap.Config): Bitmap? {
        return bitmap
    }

    override fun toBitmap(
        width: Int,
        height: Int,
        config: Bitmap.Config,
    ): Bitmap? {

        if (bitmap == null) throw IllegalStateException("bitmap is null")

        val scale = min(width.toFloat() / bitmapWidth, height.toFloat() / bitmapHeight)

        val ws = (bitmapWidth * scale)
        val hs = (bitmapHeight * scale)

        val outputBitmap = Bitmap.createBitmap(width, height, config)

        val extraWidth = width - ws
        val extraHeight = height - hs

        Canvas(outputBitmap).run {
            translate(extraWidth * 0.5f, extraHeight * 0.5f)

            scale(ws / bitmapWidth, hs / bitmapHeight)

            draw(this)
        }

        return outputBitmap
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let { ev ->
            scaleDetector?.onTouchEvent(ev)
            rotationDetector?.onTouchEvent(ev)
            moveDetector?.onTouchEvent(ev)
            commonGestureDetector?.onTouchEvent(ev)
        }
        return if (areGesturesNull()) super.onTouchEvent(event)
        else true
    }

    private fun areGesturesNull(): Boolean {
        return (scaleDetector == null && rotationDetector == null && moveDetector == null && commonGestureDetector == null)
    }

    open fun setImageBitmap(bitmap: Bitmap?) {
        if (bitmap != null) {
            this.bitmap = bitmap
            isNewBitmap = true
            bitmapWidth = bitmap.width
            bitmapHeight = bitmap.height
            requestLayout()
            invalidate()
        }
    }

    /**
     * Calculates bounds of image with matrix values.
     */
    private fun calculateBounds() {
        imageviewMatrix.run {

            matrixScale = imageviewMatrix.getScaleX(true)

            leftEdge = getTranslationX()
            topEdge = getTranslationY()

            finalWidth = (bitmapWidth * matrixScale)
            finalHeight = (bitmapHeight * matrixScale)

            rightEdge = finalWidth + leftEdge
            bottomEdge = finalHeight + topEdge

            boundsRectangle.set(leftEdge, topEdge, rightEdge, bottomEdge)

        }
    }

}
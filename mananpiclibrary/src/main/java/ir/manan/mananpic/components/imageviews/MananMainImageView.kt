package ir.manan.mananpic.components.imageviews

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.AccelerateDecelerateInterpolator
import ir.manan.mananpic.R
import ir.manan.mananpic.utils.gesture.detectors.MoveDetector

/**
 * A class that extends [MananGestureImageView] and implements scaling and transform ability to images that
 * help user better examine the image they loaded.
 */
class MananMainImageView(context: Context, attr: AttributeSet?) :
    MananGestureImageView(context, attr) {

    companion object {
        /**
         * Maximum amount the image could be scaled.
         */
        const val MAXIMUM_SCALE_FACTOR = 10f
    }

    private var initialRightEdge = 0f
    private var initialBottomEdge = 0f

    // Initial translation of image.
    private var initialTransX = 0f
    private var initialTransY = 0f

    // Pivot point of scaling gesture (later will be used by 'Matrix.postScale' and other methods.)
    private var pivotPointX = 0f
    private var pivotPointY = 0f

    // Keeps track of double tap that user performed. If user has already performed a double tap
    // then we know that if double tap get repeated another time we are going to scale down
    // and vice-versa.
    private var isDoubleTapping = false


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


    /**
     * Animation interpolator used in auto-center, over-scale and over-transform animations.
     */
    var animationInterpolator: TimeInterpolator = AccelerateDecelerateInterpolator()

    /**
     * Animation duration used in auto-center, over-scale and over-transform animations.
     */
    var animationsDuration = 350L

    private val scaleAnimator by lazy {
        ValueAnimator().apply {
            duration = animationsDuration
            interpolator = animationInterpolator

            addUpdateListener {

                val animatedValue = it.animatedValue as Float

                val lastScale = imageviewMatrix.getScaleX(true)

                postScale(
                    animatedValue / lastScale,
                    pivotPointX,
                    pivotPointY
                )

            }
        }
    }

    private val xTranslateAnimator by lazy {
        ValueAnimator().apply {
            duration = animationsDuration
            interpolator = animationInterpolator
            addUpdateListener {
                val animatedValue = it.animatedValue as Float

                val lastAnimatedValue = imageviewMatrix.getTranslationX(true)

                postTranslate(animatedValue - lastAnimatedValue, 0f)
            }
        }
    }

    private val yTranslateAnimator by lazy {
        ValueAnimator().apply {
            duration = animationsDuration
            interpolator = animationInterpolator

            addUpdateListener {
                val animatedValue = it.animatedValue as Float

                val lastAnimatedValue = imageviewMatrix.getTranslationY(true)

                postTranslate(0f, animatedValue - lastAnimatedValue)
            }

        }
    }


    init {
        context.theme.obtainStyledAttributes(attr, R.styleable.MananMainImageView, 0, 0)
            .run {
                try {
                    var tempPadding =
                        getDimension(R.styleable.MananMainImageView_initialPaddingLeft, 0f)

                    if (tempPadding != 0f)
                        initialPaddingLeft = tempPadding

                    tempPadding =
                        getDimension(R.styleable.MananMainImageView_initialPaddingRight, 0f)

                    if (tempPadding != 0f)
                        initialPaddingRight = tempPadding

                    tempPadding =
                        getDimension(R.styleable.MananMainImageView_initialPaddingBottom, 0f)

                    if (tempPadding != 0f)
                        initialPaddingBottom = tempPadding

                    tempPadding =
                        getDimension(R.styleable.MananMainImageView_initialPaddingTop, 0f)

                    if (tempPadding != 0f)
                        initialPaddingTop = tempPadding

                    tempPadding =
                        getDimension(R.styleable.MananMainImageView_initialPaddingHorizontal, 0f)

                    if (tempPadding != 0f)
                        initialPaddingHorizontal = tempPadding

                    tempPadding =
                        getDimension(R.styleable.MananMainImageView_initialPaddingVertical, 0f)

                    if (tempPadding != 0f)
                        initialPaddingVertical = tempPadding

                    tempPadding =
                        getDimension(R.styleable.MananMainImageView_initialPadding, 0f)

                    if (tempPadding != 0f)
                        initialPadding = tempPadding

                } finally {
                    recycle()
                }
            }

        // Initialize gesture detectors that we're interested in.
        scaleDetector = ScaleGestureDetector(context, this).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // This needs to be false because it will interfere with double-tap gesture.
                isQuickScaleEnabled = false
            }
        }
        moveDetector = MoveDetector(2, this)
        commonGestureDetector = GestureDetector(context, this)
        commonGestureDetector!!.setOnDoubleTapListener(this)
    }

    constructor(context: Context) : this(context, null)

    override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
        // Only start moving gesture when we're not in middle of double tapping.
        return !isDoubleTapping
    }

    override fun onMove(dx: Float, dy: Float): Boolean {
        // Shift the pivot point if user moves the picture while scaling.
        pivotPointX += dx
        pivotPointY += dy

        postTranslate(dx, dy)

        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
        // Only start scaling when we're not double tapping.
        return !isDoubleTapping
    }

    override fun onScale(detector: ScaleGestureDetector?): Boolean {
        return if (detector != null) {
            val scalingFactor = detector.scaleFactor

            // Calculate difference between scaling pivot points and center of screen.
            val diffCenterAndCurrentPivotX = pivotX - pivotPointX
            val diffCenterAndCurrentPivotY = pivotY - pivotPointY

            postScale(
                scalingFactor,
                if (scalingFactor > 1) pivotPointX - diffCenterAndCurrentPivotX else pivotPointX + diffCenterAndCurrentPivotX,
                if (scalingFactor > 1) pivotPointY - diffCenterAndCurrentPivotY else pivotPointY + diffCenterAndCurrentPivotY
            )

            true
        } else false
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {

        if (e == null) return false

        val scaled = imageviewMatrix.getScaleX(true)

        // If event coordinate (user touch) isn't in image bounds then do not
        // count that event as a double tap.
        if (e.x < leftEdge || e.y < topEdge || e.y > bottomEdge || e.x > rightEdge) return false

        // Only start double-tap animations when we're not in middle of any other animations.
        if (!isAnyAnimationRunning()) {

            // Set flag to true to notify other methods that a double-tap gesture is running.
            isDoubleTapping = true

            pivotPointX = e.x
            pivotPointY = e.y

            val maximumZoomFactor = MAXIMUM_SCALE_FACTOR / 2
            val isZooming = scaled < (maximumZoomFactor / 2)

            // This if-else block determines if we should zoom-in or zoom-out.
            // If we are not at initial scale of image then we should perform
            // a zoom-out or else we zoom-in.
            animateScale(
                scaled,
                if (!isZooming) initialScale else maximumZoomFactor
            ).run {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        // Make double tap flag false indicating that double tap animations ended.
                        isDoubleTapping = false
                        removeListener(this)
                    }

                    override fun onAnimationCancel(animation: Animator?) {
                        // Make double tap flag false indicating that double tap animations ended.
                        isDoubleTapping = false
                        removeListener(this)
                    }
                })
            }
            // Checking to see if user is zoomed without double tapping.
            // If did so then animate translations to their initial state.
            if (!isZooming) {
                animateXAxis(imageviewMatrix.getTranslationX(true), initialTransX)
                animateYAxis(imageviewMatrix.getTranslationY(), initialTransY)
            }
        }
        return true
    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        isDoubleTapping = true
        return super.onSingleTapUp(e)
    }

    override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
        isDoubleTapping = false
        return super.onSingleTapConfirmed(e)
    }

    private fun isAnyAnimationRunning(): Boolean {
        return scaleAnimator.isRunning || xTranslateAnimator.isRunning || yTranslateAnimator.isRunning
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        super.onTouchEvent(event)
        return when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                performClick()
                true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                event.run {
                    // Calculate pivot points between two fingers
                    // Formula: (x1 + x2) / 2
                    if (pointerCount == 2) {
                        pivotPointX = (getX(0) + getX(1)) / 2
                        pivotPointY = (getY(0) + getY(1)) / 2
                    }
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDoubleTapping)
                    animateToNormalBounds()
                false
            }

            else -> {
                false
            }
        }
    }

    /**
     * Animates the image's matrix to normal state (if over-scaled, over-translated).
     */
    private fun animateToNormalBounds() {
        // Get current scale.
        val scaled = imageviewMatrix.getScaleX(true)
        // Get current translation.
        val matrixTransX = imageviewMatrix.getTranslationX()
        // Get current translation y.
        val matrixTransY = imageviewMatrix.getTranslationY()

        // Here we calculate the edge of right side to later do not go further that point.
        val rEdge =
            calculateEdge(scaled, initialScale, initialRightEdge, initialTransX)

        // Here we calculate the edge of bottom side to later do not go further that point.
        val bEdge =
            calculateEdge(scaled, initialScale, initialBottomEdge, initialTransY)

        // Calculate the valid scale (scale greater than maximum allowable scale and less than initial scale)
        val validatedScale =
            if (scaled > MAXIMUM_SCALE_FACTOR) MAXIMUM_SCALE_FACTOR else if (scaled < initialScale) initialScale else scaled

        // If scaled value is greater than maximum scale allowed or less than initial scale then
        // animate it to a valid scale.
        if (scaled < initialScale || scaled > MAXIMUM_SCALE_FACTOR) {

            // Perform over-scale animations with pivot point in center of layout.
            pivotPointX = pivotX
            pivotPointY = pivotY

            animateScale(
                scaled,
                validatedScale
            )
        }

        // If we translated the image more than the edge (we use less than operator because edge is a negative value) or
        // translated image further from initial translation point then animate it back to a valid range.
        if (matrixTransX < rEdge || matrixTransX > initialTransX) {
            // Calculate the width with validated scale.
            val edgeWithNormalizedScale = calculateEdge(
                validatedScale,
                initialScale,
                initialRightEdge,
                initialTransX
            )

            animateXAxis(
                matrixTransX,
                if (matrixTransX > initialTransX || scaled < initialScale) initialTransX else edgeWithNormalizedScale
            )
        }

        // If we translated the image more than the edge (we use less than operator because edge is a negative value) or
        // translated image further from initial translation point then animate it back to a valid range.
        if (matrixTransY < bEdge || matrixTransY > initialTransY) {
            // Calculate the height with validated scale.
            val edgeWithNormalizedScale = calculateEdge(
                validatedScale,
                initialScale,
                initialBottomEdge,
                initialTransY
            )

            animateYAxis(
                matrixTransY,
                if (matrixTransY > initialTransY || scaled < initialScale) initialTransY else edgeWithNormalizedScale
            )
        }
    }

    /**
     * Animates the scaling animation.
     * Example usage: scaling from 1 to 3 scale factor.
     * @param from Starting point of scale value.
     * @param to Target scale to animate to.
     * @return Animator that used inside function.
     */
    private fun animateScale(from: Float, to: Float): ValueAnimator {
        return scaleAnimator.apply {
            setFloatValues(from, to)
            start()
        }
    }


    /**
     * Animates the x axis of matrix.
     * @param from Starting/Current point of x axis.
     * @param to Target and end value that we're translating to.
     * @return Animator that used inside function.
     */
    private fun animateXAxis(from: Float, to: Float): ValueAnimator {
        return xTranslateAnimator.apply {
            setFloatValues(from, to)
            start()
        }
    }

    /**
     * Animates the y axis of matrix.
     * @param from Starting/Current point of y axis.
     * @param to Target and end value that we're translating to.
     * @return Animator that used inside function.
     */
    private fun animateYAxis(from: Float, to: Float): ValueAnimator {
        return yTranslateAnimator.apply {
            setFloatValues(from, to)
            start()
        }
    }


    /**
     * Calculates the edge of image with current applied scale.
     * @param scaled Total scale factor that user scaled the image.
     * @param initScale Initial scale of the image.
     * @param initialSize Initial size of current axis we're trying to calculate (x or y).
     * @param initialOffset Initial offset of that axis (initial translation)
     */
    private fun calculateEdge(
        scaled: Float,
        initScale: Float,
        initialSize: Float,
        initialOffset: Float
    ): Float =
        -((scaled * initialSize / initScale) - initialSize - initialOffset)

    /**
     * Cancels all running animations.
     */
    private fun cancelRunningAnimations() {
        yTranslateAnimator.cancel()
        xTranslateAnimator.cancel()
        scaleAnimator.cancel()
    }

    override fun onImageLaidOut() {
        initialRightEdge = rightEdge
        initialBottomEdge = bottomEdge

        initialTransX = leftEdge
        initialTransY = topEdge
    }

    override fun resizeDrawable() {
        val imgMatrix = Matrix(matrix)

        imgMatrix.setRectToRect(
            RectF(
                0f,
                0f,
                drawableWidth.toFloat(),
                drawableHeight.toFloat()
            ),
            RectF(
                initialPaddingLeft,
                initialPaddingTop,
                (width - paddingRight - paddingLeft - initialPaddingRight),
                (height - paddingBottom - paddingTop - initialPaddingBottom)
            ),
            Matrix.ScaleToFit.CENTER
        )
        setToMatrix(imgMatrix)
    }
}
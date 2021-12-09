package ir.maneditor.mananpiclibrary.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.AccelerateDecelerateInterpolator
import ir.maneditor.mananpiclibrary.utils.gesture.detectors.TwoFingerMoveDetector

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

    // Image's drawable size.
    private var drawableWidth: Int = 0
    private var drawableHeight: Int = 0

    // Initial scale factor of image.
    private var initialImageScale = 0f

    // Initial translation of image.
    private var initialTransX = 0f
    private var initialTransY = 0f

    // Initial width and height of image.
    private var initialHeight = 0f
    private var initialWidth = 0f

    // Pivot point of scaling gesture (later will be used by 'Matrix.postScale' and other methods.)
    private var pivotPointX = 0f
    private var pivotPointY = 0f

    // Keeps track of double tap that user performed. If user has already performed a double tap
    // then we know that if double tap get repeated another time we are going to scale down
    // and vice-versa.
    private var isDoubleTapping = false

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

                val lastScale = getMatrixValue(Matrix.MSCALE_X, true)

                val scaledValue =
                    animatedValue / lastScale

                imageviewMatrix.postScale(
                    scaledValue,
                    scaledValue,
                    pivotPointX,
                    pivotPointY
                )

                updateImageMatrix()
            }
        }
    }

    private val xTranslateAnimator by lazy {
        ValueAnimator().apply {
            duration = animationsDuration
            interpolator = animationInterpolator
            addUpdateListener {
                val animatedValue = it.animatedValue as Float

                val lastAnimatedValue = getMatrixValue(Matrix.MTRANS_X, true)

                imageviewMatrix.postTranslate(animatedValue - lastAnimatedValue, 0f)

                updateImageMatrix()
            }
        }
    }

    private val yTranslateAnimator by lazy {
        ValueAnimator().apply {
            duration = animationsDuration
            interpolator = animationInterpolator

            addUpdateListener {
                val animatedValue = it.animatedValue as Float

                val lastAnimatedValue = getMatrixValue(Matrix.MTRANS_Y, true)

                imageviewMatrix.postTranslate(0f, animatedValue - lastAnimatedValue)

                updateImageMatrix()
            }

        }
    }


    init {
        // Initialize gesture detectors that we're interested in.

        scaleDetector = ScaleGestureDetector(context, this).apply {
            // This needs to be false because it will interfere with double-tap gesture.
            isQuickScaleEnabled = false
        }
        moveDetector = TwoFingerMoveDetector(this)
        commonGestureDetector = GestureDetector(context, this)
        commonGestureDetector!!.setOnDoubleTapListener(this)
    }

    override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
        // Only start moving gesture when we're not in middle of double tapping.
        return !isDoubleTapping
    }

    override fun onMove(dx: Float, dy: Float): Boolean {
        // Shift the pivot point if user moves the picture while scaling.
        pivotPointX += dx
        pivotPointY += dy

        imageviewMatrix.postTranslate(dx, dy)

        updateImageMatrix()

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

            imageviewMatrix.postScale(
                scalingFactor,
                scalingFactor,
                if (scalingFactor > 1) pivotPointX - diffCenterAndCurrentPivotX else pivotPointX + diffCenterAndCurrentPivotX,
                if (scalingFactor > 1) pivotPointY - diffCenterAndCurrentPivotY else pivotPointY + diffCenterAndCurrentPivotY
            )
            updateImageMatrix()

            true
        } else false
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {

        if (e == null) return false

        // Get values of matrix to later validate if touch point is inside
        // the image bounds.
        val tx = getMatrixValue(Matrix.MTRANS_X, true)
        val ty = getMatrixValue(Matrix.MTRANS_Y)
        val scaled = getMatrixValue(Matrix.MSCALE_X)
        val maxWidth = drawableWidth * scaled + initialTransX
        val maxHeight = drawableHeight * scaled + initialTransY

        // If event coordinate (user touch) isn't in image bounds then do not
        // count that event as a double tap.
        if (e.x < tx || e.y < ty || e.y > maxHeight || e.x > maxWidth) return false

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
                if (!isZooming) initialImageScale else maximumZoomFactor
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
                animateXAxis(tx, initialTransX)
                animateYAxis(ty, initialTransY)
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
        val scaled = getMatrixValue(Matrix.MSCALE_X, true)
        // Get current translation.
        val matrixTransX = getMatrixValue(Matrix.MTRANS_X)
        // Get current translation y.
        val matrixTransY = getMatrixValue(Matrix.MTRANS_Y)

        // Here we calculate the edge of right side to later do not go further that point.
        val rightEdge =
            calculateEdge(scaled, initialImageScale, initialWidth, initialTransX)

        // Here we calculate the edge of bottom side to later do not go further that point.
        val bottomEdge =
            calculateEdge(scaled, initialImageScale, initialHeight, initialTransY)

        // Calculate the valid scale (scale greater than maximum allowable scale and less than initial scale)
        val validatedScale =
            if (scaled > MAXIMUM_SCALE_FACTOR) MAXIMUM_SCALE_FACTOR else if (scaled < initialImageScale) initialImageScale else scaled

        // If scaled value is greater than maximum scale allowed or less than initial scale then
        // animate it to a valid scale.
        if (scaled < initialImageScale || scaled > MAXIMUM_SCALE_FACTOR) {

            // Perform over-scale animations with pivot point in center of layout.
            pivotPointX = pivotX
            pivotPointY = pivotY

            animateScale(
                scaled,
                if (scaled < initialImageScale) initialImageScale else MAXIMUM_SCALE_FACTOR
            )
        }

        // If we translated the image more than the edge (we use less than operator because edge is a negative value) or
        // translated image further from initial translation point then animate it back to a valid range.
        if (matrixTransX < rightEdge || matrixTransX > initialTransX) {
            // Calculate the width with validated scale.
            val edgeWithNormalizedScale = calculateEdge(
                validatedScale,
                initialImageScale,
                initialWidth,
                initialTransX
            )

            animateXAxis(
                matrixTransX,
                if (matrixTransX > initialTransX || scaled < initialImageScale) initialTransX else edgeWithNormalizedScale
            )
        }

        // If we translated the image more than the edge (we use less than operator because edge is a negative value) or
        // translated image further from initial translation point then animate it back to a valid range.
        if (matrixTransY < bottomEdge || matrixTransY > initialTransY) {
            // Calculate the height with validated scale.
            val edgeWithNormalizedScale = calculateEdge(
                validatedScale,
                initialImageScale,
                initialHeight,
                initialTransY
            )

            animateYAxis(
                matrixTransY,
                if (matrixTransY > initialTransY || scaled < initialImageScale) initialTransY else edgeWithNormalizedScale
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
     * @param initialScale Initial scale of the image.
     * @param initialSize Initial size of current axis we're trying to calculate (x or y).
     * @param initialOffset Initial offset of that axis (initial translation)
     */
    private fun calculateEdge(
        scaled: Float,
        initialScale: Float,
        initialSize: Float,
        initialOffset: Float
    ): Float =
        -((scaled * initialSize / initialScale) - initialSize - initialOffset)

    /**
     * Cancels all running animations.
     */
    private fun cancelRunningAnimations() {
        yTranslateAnimator.cancel()
        xTranslateAnimator.cancel()
        scaleAnimator.cancel()
    }

    override fun onPreChildDraw() {
        super.onPreChildDraw()

        initialTransX = getMatrixValue(Matrix.MTRANS_X, true)
        initialTransY = getMatrixValue(Matrix.MTRANS_Y)

        initialImageScale = getMatrixValue(Matrix.MSCALE_X)

        val mDrawable = mainImageView.drawable

        initialWidth = mDrawable.intrinsicWidth * initialImageScale
        initialHeight = mDrawable.intrinsicHeight * initialImageScale

        drawableWidth = mDrawable.intrinsicWidth
        drawableHeight = mDrawable.intrinsicHeight

    }
}
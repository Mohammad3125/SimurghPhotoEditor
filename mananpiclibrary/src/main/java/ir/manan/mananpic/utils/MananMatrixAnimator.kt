package ir.manan.mananpic.utils

import android.animation.PropertyValuesHolder
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.Matrix
import android.graphics.RectF

/**
 * A class that is responsible for animating a canvas towards it's initial bounds.
 * @param targetMatrix Reference to target matrix that is going to be animated (should not be copied).
 * @param initialBounds Initial bounds of component used to compare if it is needed to animate matrix.
 * @param animationDuration Duration of animation.
 * @param animationInterpolator Interpolator of animation [TimeInterpolator]
 */
class MananMatrixAnimator(
    private var targetMatrix: MananMatrix,
    private var initialBounds: RectF,
    animationDuration: Long,
    animationInterpolator: TimeInterpolator
) {
    private var onMatrixUpdateCallback: ((MananMatrix) -> Unit)? = null
    private var onMatrixUpdateListener: OnMatrixUpdateListener? = null

    private val canvasMatrixAnimator by lazy {
        ValueAnimator().apply {
            duration = animationDuration
            interpolator = animationInterpolator
            addUpdateListener {
                // Get animating properties.
                val s = getAnimatedValue("scale")
                val tx = getAnimatedValue("translationX")
                val ty = getAnimatedValue("translationY")

                targetMatrix.run {
                    // If scale property isn't null then scale it.
                    if (s != null) {
                        val totalScale = (s as Float) / getScaleX(true)
                        postScale(
                            totalScale,
                            totalScale,
                            initialBounds.centerX(),
                            initialBounds.centerY()
                        )
                    }
                    // If translation isn't null or in other words, we should animate the translation, then animate it.
                    if (tx != null) {
                        postTranslate(
                            tx as Float - getTranslationX(true),
                            0f
                        )
                    }

                    // If translation isn't null or in other words, we should animate the translation, then animate it.
                    if (ty != null) {
                        postTranslate(
                            0f,
                            ty as Float - getTranslationY(true)
                        )
                    }

                }

                callCallbacks()
            }
        }
    }

    private fun callCallbacks() {
        onMatrixUpdateCallback?.invoke(targetMatrix)
        onMatrixUpdateListener?.onMatrixUpdated(targetMatrix)
    }

    /**
     * Returns true if matrix is currently being animated, false otherwise.
     */
    fun isAnimationRunning(): Boolean {
        return canvasMatrixAnimator.isRunning
    }


    /**
     * Starts animating target canvas towards it's initial bounds.
     * @param maximumScaleFactorAllowed Maximum scale factor that is allowed for user to zoom in.
     * @param extraSpaceAroundAxes Extra space around axes in case it is needed for special purpose.
     */
    fun startAnimation(maximumScaleFactorAllowed: Float, extraSpaceAroundAxes: Float) {
        if (!canvasMatrixAnimator.isRunning) {

            // Get matrix values.
            val scale = targetMatrix.getScaleX(true)
            val tx = targetMatrix.getTranslationX()
            val ty = targetMatrix.getTranslationY()

            val zoomWindow = RectF(initialBounds)

            Matrix().run {
                setScale(scale, scale)
                mapRect(zoomWindow)
            }

            // Calculate the valid scale (scale greater than maximum allowable scale and less than initial scale)
            val validatedScale =
                if (scale > maximumScaleFactorAllowed) maximumScaleFactorAllowed else if (scale < 1f) 1f else scale

            // Here we calculate the edge of right side to later do not go further that point.
            val rEdge =
                -(zoomWindow.right - initialBounds.right)

            // Here we calculate the edge of bottom side to later do not go further that point.
            val bEdge =
                -(zoomWindow.bottom - initialBounds.bottom)

            canvasMatrixAnimator.run {
                val animationPropertyHolderList = ArrayList<PropertyValuesHolder>()
                // Add PropertyValuesHolder for each animation property if they should be animated.
                if (scale < 1f || scale > maximumScaleFactorAllowed)
                    animationPropertyHolderList.add(
                        PropertyValuesHolder.ofFloat(
                            "scale",
                            scale,
                            validatedScale
                        )
                    )

                if (tx > extraSpaceAroundAxes || tx < rEdge - extraSpaceAroundAxes)
                    animationPropertyHolderList.add(
                        PropertyValuesHolder.ofFloat(
                            "translationX",
                            tx,
                            if (scale < 1f || tx > 0f) 0f else rEdge
                        )
                    )

                if (ty > extraSpaceAroundAxes || ty < bEdge - extraSpaceAroundAxes)
                    animationPropertyHolderList.add(
                        PropertyValuesHolder.ofFloat(
                            "translationY",
                            ty,
                            if (scale < 1f || ty > 0f) 0f else bEdge
                        )
                    )


                // Finally convert the array list to array and set values of animator.
                setValues(
                    *Array(
                        animationPropertyHolderList.size
                    ) {
                        animationPropertyHolderList[it]
                    }
                )

                start()
            }
        }
    }


    /**
     * Register a callback for when matrix is updated.
     */
    fun setOnMatrixUpdateListener(callback: (MananMatrix) -> Unit) {
        onMatrixUpdateCallback = callback
    }

    /**
     * Register a listener for when matrix is updated.
     */
    fun setOnMatrixUpdateListener(listener: OnMatrixUpdateListener) {
        onMatrixUpdateListener = listener
    }


    /**
     * Callback definition for a callback to be invoked when matrix is updated.
     */
    interface OnMatrixUpdateListener {
        /**
         * Called when matrix is updated.
         */
        fun onMatrixUpdated(matrix: MananMatrix)
    }
}
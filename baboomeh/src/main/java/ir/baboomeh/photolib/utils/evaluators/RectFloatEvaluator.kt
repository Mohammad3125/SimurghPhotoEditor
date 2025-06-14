package ir.baboomeh.photolib.utils.evaluators

import android.animation.TypeEvaluator
import android.graphics.RectF

/**
 * Copy of [android.animation.RectEvaluator] for [RectF] class.
 */
open class RectFloatEvaluator : TypeEvaluator<RectF> {

    protected val tempRect by lazy {
        RectF()
    }

    override fun evaluate(fraction: Float, startValue: RectF, endValue: RectF): RectF {
        return tempRect.apply {
            left = startValue.left + ((endValue.left - startValue.left) * fraction)
            top = startValue.top + ((endValue.top - startValue.top) * fraction)
            right = startValue.right + ((endValue.right - startValue.right) * fraction)
            bottom = startValue.bottom + ((endValue.bottom - startValue.bottom) * fraction)
        }
    }
}
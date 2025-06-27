package ir.simurgh.photolib.utils.evaluators

import android.animation.TypeEvaluator
import android.graphics.Matrix
import ir.simurgh.photolib.utils.matrix.SimurghMatrix


/**
 * Type evaluator for [Matrix] interpolation. Copied from
 * androidx.transition.TransitionUtils.MatrixEvaluator.
 */
open class MatrixEvaluator : TypeEvaluator<SimurghMatrix> {
    protected val tempStartValues = FloatArray(9)
    protected val tempEndValues = FloatArray(9)
    protected val tempMatrix = SimurghMatrix()

    override fun evaluate(fraction: Float, startValue: SimurghMatrix, endValue: SimurghMatrix): SimurghMatrix {
        startValue.getValues(tempStartValues)
        endValue.getValues(tempEndValues)
        for (i in 0..8) {
            val diff = tempEndValues[i] - tempStartValues[i]
            tempEndValues[i] = tempStartValues[i] + fraction * diff
        }
        tempMatrix.setValues(tempEndValues)
        return tempMatrix
    }
}
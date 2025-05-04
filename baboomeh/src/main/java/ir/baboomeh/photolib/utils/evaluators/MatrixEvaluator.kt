package ir.baboomeh.photolib.utils.evaluators

import android.animation.TypeEvaluator
import android.graphics.Matrix
import ir.baboomeh.photolib.utils.MananMatrix


/**
 * Type evaluator for [Matrix] interpolation. Copied from
 * androidx.transition.TransitionUtils.MatrixEvaluator.
 */
class MatrixEvaluator : TypeEvaluator<MananMatrix> {
    private val tempStartValues = FloatArray(9)
    private val tempEndValues = FloatArray(9)
    private val tempMatrix = MananMatrix()

    override fun evaluate(fraction: Float, startValue: MananMatrix, endValue: MananMatrix): MananMatrix {
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
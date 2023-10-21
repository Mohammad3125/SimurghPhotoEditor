package ir.manan.mananpic.utils

import android.graphics.Matrix
import kotlin.math.sqrt

/**
 * A class that extends [Matrix] and provides custom function and cleaner interface for retrieving matrix values.
 */
class MananMatrix : Matrix() {
    private val matrixValueHolder by lazy {
        FloatArray(9)
    }

    /**
     * Returns opposite scale in given axis. for example a 2f scale would return 0.5f.
     * @throws IllegalStateException If provided axis is not [Matrix.MSCALE_X] or [Matrix.MSCALE_Y]
     * @param axis Represents scale in specific axis.
     */
    fun getOppositeScale(axis: Int = MSCALE_X): Float {
        if (axis != MSCALE_X && axis != MSCALE_Y) throw IllegalStateException("Provided axis should be either x or y to be able to retrieve scale")

        getValues(matrixValueHolder)
        return 1f / matrixValueHolder[axis]
    }

    /**
     * Returns scale x inside matrix.
     * @param refresh Determines if this call should retrieve the newest value.
     */
    fun getScaleX(refresh: Boolean = false): Float {
        if (refresh) {
            getValues(matrixValueHolder)
        }

        return matrixValueHolder[MSCALE_X]
    }

    /**
     * Returns real scale. If rotation is applied to matrix the scale value will change; this method returns the true unaffected scale value.
     */
    fun getRealScaleX(): Float {
        val sx = getScaleX(true)
        val skewY = getSkewY()
        return sqrt(sx * sx + skewY * skewY)
    }

    /**
     * Returns real scale. If rotation is applied to matrix the scale value will change; this method returns the true unaffected scale value.
     */
    fun getRealScaleY() : Float{
        val sy = getScaleY(true)
        val skewX = getSkewX()
        return sqrt(sy * sy + skewX * skewX)
    }

    /**
     * Returns scale y inside matrix.
     * @param refresh Determines if this call should retrieve the newest value.
     */
    fun getScaleY(refresh: Boolean = false): Float {
        if (refresh) {
            getValues(matrixValueHolder)
        }

        return matrixValueHolder[MSCALE_Y]
    }

    /**
     * Returns translation x inside matrix.
     * @param refresh Determines if this call should retrieve the newest value.
     */
    fun getTranslationX(refresh: Boolean = false): Float {
        if (refresh) {
            getValues(matrixValueHolder)
        }

        return matrixValueHolder[MTRANS_X]
    }

    /**
     * Returns translation y inside matrix.
     * @param refresh Determines if this call should retrieve the newest value.
     */
    fun getTranslationY(refresh: Boolean = false): Float {
        if (refresh) {
            getValues(matrixValueHolder)
        }

        return matrixValueHolder[MTRANS_Y]
    }

    /**
     * Returns skew x inside matrix.
     * @param refresh Determines if this call should retrieve the newest value.
     */
    fun getSkewX(refresh: Boolean = false): Float {
        if (refresh) {
            getValues(matrixValueHolder)
        }

        return matrixValueHolder[MSKEW_X]
    }

    /**
     * Returns skew y inside matrix.
     * @param refresh Determines if this call should retrieve the newest value.
     */
    fun getSkewY(refresh: Boolean = false): Float {
        if (refresh) {
            getValues(matrixValueHolder)
        }

        return matrixValueHolder[MSKEW_Y]
    }

    /**
     * Returns perspective 0 inside matrix.
     * @param refresh Determines if this call should retrieve the newest value.
     */
    fun getPerspective0(refresh: Boolean = false): Float {
        if (refresh) {
            getValues(matrixValueHolder)
        }

        return matrixValueHolder[MPERSP_0]
    }

    /**
     * Returns perspective 1 inside matrix.
     * @param refresh Determines if this call should retrieve the newest value.
     */
    fun getPerspective1(refresh: Boolean = false): Float {
        if (refresh) {
            getValues(matrixValueHolder)
        }

        return matrixValueHolder[MPERSP_1]
    }

    /**
     * Returns perspective 2 inside matrix.
     * @param refresh Determines if this call should retrieve the newest value.
     */
    fun getPerspective2(refresh: Boolean = false): Float {
        if (refresh) {
            getValues(matrixValueHolder)
        }

        return matrixValueHolder[MPERSP_2]
    }
}
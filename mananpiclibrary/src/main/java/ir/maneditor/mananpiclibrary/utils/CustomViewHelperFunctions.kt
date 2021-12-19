package ir.maneditor.mananpiclibrary.utils

import android.content.res.Resources
import android.util.TypedValue
import android.view.View

private val displayMetrics = Resources.getSystem().displayMetrics

/**
 * This extension property converts an SP number into pixels.
 */
@Deprecated("Use new extension function instead")
val Number.sp: Float
    get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this.toFloat(), displayMetrics)

/**
 * This extension property converts a DP number into pixels.
 */
@Deprecated("Use new extension function instead")
val Number.dp: Float
    get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), displayMetrics)

/**
 * Extension function for converting a dp value to pixels.
 * @param number Number to be converted.
 */
fun View.dp(number: Number): Float {
    val metric =
        if (context != null) context.resources.displayMetrics else Resources.getSystem().displayMetrics

    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, number.toFloat(), metric)
}

/**
 * Extension function for converting a sp number to pixels.
 * @param number Number to be converted.
 */
fun View.sp(number: Number): Float {
    val metric =
        if (context != null) context.resources.displayMetrics else Resources.getSystem().displayMetrics

    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, number.toFloat(), metric)
}

/**
 * This extension function invokes a block and invalidate the view afterwards.
 */
inline fun View.invalidateAfter(block: (Unit) -> Unit) {
    block(Unit)
    invalidate()
}
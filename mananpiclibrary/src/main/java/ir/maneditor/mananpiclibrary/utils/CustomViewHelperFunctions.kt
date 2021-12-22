package ir.maneditor.mananpiclibrary.utils

import android.content.res.Resources
import android.util.TypedValue
import android.view.View

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

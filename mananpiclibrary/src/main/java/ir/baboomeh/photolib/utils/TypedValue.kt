package ir.baboomeh.photolib.utils

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import androidx.fragment.app.Fragment

/**
 * Extension function for converting a dp value to pixels.
 * This extension runs inside a view.
 * @param number Number to be converted.
 */
fun View.dp(number: Number): Float {
    val metric =
        getDisplayMetric(context)

    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, number.toFloat(), metric)
}

/**
 * Extension function for converting a sp number to pixels.
 * This extension runs inside a view.
 * @param number Number to be converted.
 */
fun View.sp(number: Number): Float {
    val metric =
        getDisplayMetric(context)

    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, number.toFloat(), metric)
}

/**
 * Extension function for converting mm(millimeter) to pixels.
 * This extension function runs inside a view.
 * @param number Number to be converted.
 */
fun View.mm(number: Number): Float {
    val metric =
        getDisplayMetric(context)
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, number.toFloat(), metric)
}


fun View.dpInt(number: Number): Int {
    return dp(number).toInt()
}


/**
 * This method returns DisplayMetric of current device.
 * If Context is null the default system display metric would be returned which has default
 * density etc...
 */
fun getDisplayMetric(context: Context?): DisplayMetrics {
    return if (context != null) context.resources.displayMetrics else Resources.getSystem().displayMetrics
}


/**
 * Extension function for converting a dp value to pixels.
 * This extension runs inside a context.
 * @param number Number to be converted.
 */
fun Context?.dp(number: Number): Float {
    val metric =
        getDisplayMetric(this)

    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, number.toFloat(), metric)
}

fun Context?.dpInt(number: Number): Int {
    return dp(number).toInt()
}

/**
 * Extension function for converting a sp number to pixels.
 * This extension runs inside a context.
 * @param number Number to be converted.
 */
fun Context?.sp(number: Number): Float {
    val metric =
        getDisplayMetric(this)

    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, number.toFloat(), metric)
}

/**
 * Extension function for converting mm(millimeter) to pixels.
 * This extension function runs inside a context.
 * @param number Number to be converted.
 */
fun Context?.mm(number: Number): Float {
    val metric =
        getDisplayMetric(this)

    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, number.toFloat(), metric)
}

fun Fragment.dp(number: Number): Float {
    val metric =
        getDisplayMetric(context)
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, number.toFloat(), metric)
}

fun Fragment.dpInt(number: Number): Int {
    return dp(number).toInt()
}

fun Fragment.sp(number: Number): Float {
    val metric =
        getDisplayMetric(context)
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, number.toFloat(), metric)
}




package ir.maneditor.mananpiclibrary.utils

import android.content.res.Resources
import android.util.TypedValue
import android.view.View

private val displayMetrics = Resources.getSystem().displayMetrics

/**
 * This extension property converts an SP number into pixels.
 */
val Number.sp: Float
    get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,this.toFloat(), displayMetrics)

/**
 * This extension property converts a DP number into pixels.
 */
val Number.dp: Float
    get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,this.toFloat(), displayMetrics)

/**
 * This extension function invokes a block and invalidate the view afterwards.
 */
inline fun View.invalidateAfter(block: (Unit) -> Unit) {
    block(Unit)
    invalidate()
}
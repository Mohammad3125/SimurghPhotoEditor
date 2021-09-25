package ir.maneditor.mananpiclibrary.utils

import android.content.res.Resources
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup

private val displayMetrics = Resources.getSystem().displayMetrics

/**
 * This extension property converts an SP number into pixels.
 */
val Number.sp: Float
    get() = this.toFloat() * displayMetrics.scaledDensity


/**
 * This extension property converts a DP number into pixels.
 */
val Number.dp: Float
    get() = this.toFloat() * displayMetrics.density


/**
 * Calculates the center of canvas in X coordinates.
 */
val Canvas.centerX
    get() = (this.width / 2)


/**
 * Calculates the center of canvas in Y coordinate.
 */
val Canvas.centerY get() = (this.height / 2)

/**
 * This property returns center of a ViewGroup in horizontal axis (ConstrainLayout,RelativeLayout,FrameLayout and etc.)
 */
private val ViewGroup.centerX get() = (this.measuredWidth * 0.5f)


/**
 * This property returns center of a ViewGroup in vertical axis (ConstrainLayout,RelativeLayout,FrameLayout and etc.)
 */
private val ViewGroup.centerY get() = (this.measuredHeight * 0.5f)


/**
 * This property returns the center point of a view in x direction.
 */
val View.centerX get() = (this.measuredWidth * 0.5f)

/**
 * This property returns the center point of a view in y direction.
 */
val View.centerY get() = (this.measuredHeight * 0.5f)


/**
 * This extension function invokes a block and invalidate the view afterwards.
 */
fun View.invalidateAfter(block: (Unit) -> Unit) {
    block(Unit)
    invalidate()
}
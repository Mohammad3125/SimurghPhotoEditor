package ir.manan.mananpic.properties

import android.graphics.BlurMaskFilter

/**
 * An interface that makes a view blur capable.
 */
interface Blurable {

    /**
     * This function defines how a view should provide a blur on itself.
     * @param blurRadius Blur radius that is going to be applied.
     */
    fun applyBlur(blurRadius: Float)

    /**
     * This function defines how a view should provide a blur and blur style on itself.
     * @param blurRadius blur radius that is going to be applied.
     * @param filter Represents style of the blur with enums.
     */
    fun applyBlur(blurRadius: Float, filter: BlurMaskFilter.Blur)


    /**
     * This function defines a way that blurable view should remove it's blur (if there is any).
     */
    fun removeBlur()
}
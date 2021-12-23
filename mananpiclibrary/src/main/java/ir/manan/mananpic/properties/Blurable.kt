package ir.manan.mananpic.properties

import android.graphics.BlurMaskFilter

/**
 * An interface that makes a view shadow capable.
 */
interface Blurable {

    /**
     * This function defines how a view should provide a blur on itself.
     * @param shadowRadius Shadow radius that is going to be applied.
     */
    fun applyBlur(shadowRadius: Float)

    /**
     * This function defines how a view should provide a shadow and shadow style on itself.
     * @param shadowRadius Shadow radius that is going to be applied.
     * @param filter Represents style of the shadow with enums.
     */
    fun applyBlur(shadowRadius: Float, filter: BlurMaskFilter.Blur)


    /**
     * This function defines a way that shadowable view should remove it's shadow (if there is any).
     */
    fun removeBlur()
}
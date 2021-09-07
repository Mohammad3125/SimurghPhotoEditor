package ir.maneditor.mananpiclibrary.properties

import android.graphics.BlurMaskFilter

/**
 * An interface that makes a view shadow capable.
 */
interface Shadowable {

    /**
     * This function defines how a view should provide a shadow on itself (default mask filter is [BlurMaskFilter.Blur.NORMAL]).
     * @param shadowRadius Shadow radius that is going to be applied.
     */
    fun applyShadow(shadowRadius: Float)

    /**
     * This function defines how a view should provide a shadow and shadow style on itself.
     * @param shadowRadius Shadow radius that is going to be applied.
     * @param filter Represents style of the shadow with enums.
     */
    fun applyShadow(shadowRadius: Float, filter: BlurMaskFilter.Blur)


    /**
     * This function defines a way that shadowable view should remove it's shadow (if there is any).
     */
    fun removeShadow()
}
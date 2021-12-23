package ir.manan.mananpic.properties

import android.graphics.ColorFilter


/**
 * Interface definition for a filterable view.
 */
interface Filterable {

    /**
     * This method applies the color matrix color changes to the target view.
     * @param colorFilter The color modifier of a view.
     */
    fun applyFilter(colorFilter: ColorFilter)


    /**
     * Removes any applied filter to view.
     */
    fun removeFilter()
}
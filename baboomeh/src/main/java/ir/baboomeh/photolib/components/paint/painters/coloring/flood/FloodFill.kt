package ir.baboomeh.photolib.components.paint.painters.coloring.flood

import android.graphics.Bitmap

/**
 * Interface for flood fill algorithms that fill connected regions of similar colors.
 *
 * Flood fill is a technique used to fill a connected area with a new color, starting from
 * a seed point and spreading to all adjacent pixels that match the target color within
 * a specified threshold tolerance.
 */
interface FloodFill {
    /**
     * Fills a connected region starting from the specified point with the replacement color.
     *
     * The algorithm identifies all pixels connected to the starting point that have a color
     * similar to the target color (within the threshold tolerance) and replaces them with
     * the specified replacement color.
     *
     * @param bitmap The bitmap to perform flood fill on
     * @param ex Starting X coordinate for the fill operation
     * @param ey Starting Y coordinate for the fill operation
     * @param replaceColor The color to fill the region with
     * @param threshold Color similarity tolerance (0.0-1.0), where 0.0 means exact match
     *                 and 1.0 means any color will be considered similar
     */
    fun fill(bitmap: Bitmap, ex: Int, ey: Int, replaceColor: Int, threshold: Float)
}

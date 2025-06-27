package ir.simurgh.photolib.components.paint.painters.selection.clippers

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * Abstract base class for implementing different types of clipping operations on bitmaps.
 *
 * A clipper defines how to extract, copy, or cut portions of a bitmap based on various
 * selection criteria (paths, masks, etc.). This class provides a common interface for
 * different clipping strategies while allowing each implementation to define its own
 * specific clipping logic.
 *
 * Typical usage pattern:
 * 1. Create a clipper instance with source bitmap
 * 2. Configure the clipper with selection criteria (path, mask, etc.)
 * 3. Call clip(), copy(), or cut() to perform the desired operation
 * 4. Use getClippingBounds() to determine the affected area
 *
 * @param bitmap The source bitmap to perform clipping operations on. Can be null if set later.
 */
abstract class Clipper(
    /**
     * The source bitmap that clipping operations will be performed on.
     * This can be modified or replaced during the clipper's lifecycle.
     */
    var bitmap: Bitmap? = null,
) {

    /**
     * Secondary constructor that creates a clipper without an initial bitmap.
     * The bitmap must be set before performing any clipping operations.
     */
    constructor() : this(null)

    /**
     * Performs an in-place clipping operation on the source bitmap.
     * This modifies the original bitmap by removing or altering pixels
     * outside the clipping area. The specific behavior depends on the
     * concrete implementation.
     *
     * Note: This operation is destructive and cannot be undone.
     */
    abstract fun clip()

    /**
     * Creates a copy of the clipped region without modifying the source bitmap.
     *
     * @return A new bitmap containing only the pixels within the clipping area,
     *         or null if the operation fails or produces an empty result
     */
    abstract fun copy(): Bitmap?

    /**
     * Cuts out the clipped region and returns it as a new bitmap.
     * This operation typically:
     * 1. Creates a copy of the clipped region
     * 2. Removes the clipped region from the source bitmap
     *
     * @return A new bitmap containing the cut region, or null if the operation
     *         fails or produces an empty result
     */
    abstract fun cut(): Bitmap?

    /**
     * Calculates and returns the rectangular bounds of the clipping area.
     * This is useful for determining the affected region before performing
     * expensive clipping operations, or for UI feedback purposes.
     *
     * @param rect RectF object that will be populated with the clipping bounds.
     *             The coordinates are in the bitmap's coordinate system.
     */
    abstract fun getClippingBounds(rect: RectF)
}

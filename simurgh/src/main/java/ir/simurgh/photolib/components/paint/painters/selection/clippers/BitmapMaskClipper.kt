package ir.simurgh.photolib.components.paint.painters.selection.clippers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

/**
 * A clipper implementation that uses a bitmap mask to define clipping areas.
 *
 * This clipper works by using a secondary bitmap (mask) where non-transparent pixels
 * define the clipping region. The mask is applied using Porter-Duff blend modes to
 * achieve different clipping effects:
 * - DST_OUT: Removes pixels where the mask is present (clip operation)
 * - DST_IN: Keeps only pixels where the mask is present (copy operation)
 *
 * The mask bitmap should have the same dimensions as the source bitmap for best results.
 *
 * @param bitmap The source bitmap to clip from
 * @param maskBitmap The mask bitmap that defines the clipping region
 */
open class BitmapMaskClipper(bitmap: Bitmap?, var maskBitmap: Bitmap?) : Clipper(bitmap) {

    /**
     * Default constructor that creates a clipper without initial bitmaps.
     * Both bitmap and maskBitmap must be set before performing operations.
     */
    constructor() : this(null, null)

    /**
     * Paint object used for drawing bitmap operations
     */
    protected val bitmapPaint by lazy {
        Paint()
    }

    /**
     * Canvas used for performing bitmap composition operations
     */
    protected val canvasOperation by lazy {
        Canvas()
    }

    /**
     * Clips the source bitmap using the mask.
     * Removes pixels from the source bitmap where the mask has non-transparent pixels.
     * This is a destructive operation that modifies the original bitmap.
     */
    override fun clip() {
        doIfBitmapAndMaskNotNull { bit, mask ->
            changePaintPorterDuffMode(PorterDuff.Mode.DST_OUT) {
                drawMaskLayer(bit, mask)
            }
        }
    }

    /**
     * Cuts out the masked region and returns it as a new bitmap.
     * First creates a copy of the source bitmap, then removes the masked area
     * from the original bitmap.
     *
     * @return A new bitmap containing the cut region, or null if mask is empty
     */
    override fun cut(): Bitmap? {
        val copiedBitmap = copy()

        doIfBitmapAndMaskNotNull { bit, mask ->
            if (isMaskEmpty(mask)) {
                return null
            }
            changePaintPorterDuffMode(PorterDuff.Mode.DST_OUT) {
                drawMaskLayer(bit, mask)
            }
        }

        return copiedBitmap
    }

    /**
     * Creates a copy of the bitmap with only the masked region visible.
     * Returns a new bitmap containing only pixels where the mask is non-transparent.
     *
     * @return A new bitmap with the masked region, or null if mask is empty
     */
    override fun copy(): Bitmap? {
        doIfBitmapAndMaskNotNull { bit, mask ->
            if (isMaskEmpty(mask)) {
                return null
            }

            // Create a copy of the source bitmap
            val copy = bit.copy(bit.config ?: Bitmap.Config.ARGB_8888, true)

            // Use DST_IN mode to keep only pixels where mask is present
            changePaintPorterDuffMode(PorterDuff.Mode.DST_IN) {
                drawMaskLayer(copy, mask)
            }

            return copy
        }
        return null
    }

    /**
     * Checks if the mask bitmap is effectively empty (all transparent pixels).
     * This is done by comparing the mask with a completely transparent bitmap.
     *
     * @param mask The mask bitmap to check
     * @return true if the mask is empty (all transparent), false otherwise
     */
    protected open fun isMaskEmpty(mask: Bitmap): Boolean {
        // Quickly check if mask is empty by comparing with transparent bitmap
        mask.copy(mask.config ?: Bitmap.Config.ARGB_8888, true).apply {
            eraseColor(Color.TRANSPARENT)
        }.also { emptyMask ->
            return emptyMask.sameAs(mask)
        }
    }

    /**
     * Draws the mask bitmap onto the target bitmap using the current paint settings.
     *
     * @param targetBitmap The bitmap to draw the mask onto
     * @param mask The mask bitmap to apply
     */
    protected open fun drawMaskLayer(targetBitmap: Bitmap, mask: Bitmap) {
        canvasOperation.setBitmap(targetBitmap)
        canvasOperation.drawBitmap(mask, 0f, 0f, bitmapPaint)
    }

    /**
     * Utility function to temporarily change the paint's Porter-Duff mode,
     * execute an operation, then restore the original mode.
     *
     * @param mode The Porter-Duff mode to temporarily apply
     * @param operation The operation to execute with the specified mode
     */
    protected inline fun changePaintPorterDuffMode(
        mode: PorterDuff.Mode,
        operation: () -> Unit
    ) {
        bitmapPaint.xfermode = PorterDuffXfermode(mode)
        operation()
        bitmapPaint.xfermode = null
    }

    /**
     * Utility function that ensures both bitmap and mask are not null before
     * executing the provided operation. Throws an exception if either is null.
     *
     * @param operation The operation to execute with valid bitmap and mask
     * @throws IllegalStateException if either bitmap or mask is null
     */
    protected inline fun doIfBitmapAndMaskNotNull(operation: (bit: Bitmap, mask: Bitmap) -> Unit) {
        bitmap?.let { bit ->
            maskBitmap?.let { mask ->
                operation(bit, mask)
                return
            }
        }
        throw IllegalStateException("bitmap or mask bitmap was null")
    }

    /**
     * Gets the clipping bounds for this mask clipper.
     * Currently not implemented - the bounds would need to be calculated
     * based on the non-transparent regions of the mask bitmap.
     *
     * @param rect RectF to populate with clipping bounds
     */
    override fun getClippingBounds(rect: RectF) {
        // TODO: Calculate bounds based on mask bitmap's non-transparent regions
    }
}

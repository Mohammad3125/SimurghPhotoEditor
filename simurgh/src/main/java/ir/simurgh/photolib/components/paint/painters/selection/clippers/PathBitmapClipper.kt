package ir.simurgh.photolib.components.paint.painters.selection.clippers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.toRect
import ir.simurgh.photolib.components.paint.painters.painting.BrushPreview
import ir.simurgh.photolib.components.paint.painters.painting.brushes.Brush
import ir.simurgh.photolib.components.paint.smoothers.BasicSmoother

open class PathBitmapClipper(
    var path: Path? = null,
    bitmap: Bitmap? = null,
    var isInverse: Boolean = false,
    var edgeBrush: Brush,
) : Clipper(bitmap) {

    /** Alternative constructor for creating clipper with only edge brush specification. */
    constructor(edgeBrush: Brush) : this(null, null, false, edgeBrush)

    /** Canvas used for rendering operations during clipping process. */
    protected val canvas by lazy {
        Canvas()
    }

    /** Copy of the original path to avoid modifying the source path during operations. */
    protected val pathCopy by lazy {
        Path()
    }

    /** Paint configured for filling path areas with solid color. */
    protected val lassoFillPaint by lazy {
        Paint().apply {
            style = Paint.Style.FILL
        }
    }

    /** Basic line smoother for creating smooth brush strokes on edges. */
    protected val basicLineSmoother by lazy {
        BasicSmoother()
    }

    /** Paint configured with DST_OUT blend mode for creating transparency masks. */
    protected val dstOutBitmapPaint by lazy {
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
    }

    /** Rectangle bounds for optimizing clipping operations to relevant areas only. */
    protected val bitmapRectangle by lazy {
        RectF()
    }

    /**
     * Performs destructive clipping operation on the bitmap.
     * This modifies the original bitmap by making selected areas transparent.
     */
    override fun clip() {
        doIfPathAndBitmapNotNull { b, p ->
            // Store original fill type to restore later.
            val fillType = path!!.fillType

            // Set appropriate fill type based on inverse mode.
            path!!.fillType =
                if (isInverse) Path.FillType.INVERSE_WINDING else Path.FillType.WINDING

            // Perform the clipping operation.
            clip(b)

            // Restore original fill type.
            path!!.fillType = fillType
        }
    }

    /**
     * Creates a non-destructive copy of the selected area.
     * @return New bitmap containing only the selected content, or null if operation fails.
     */
    override fun copy(): Bitmap? {
        doIfPathAndBitmapNotNull { bitmap, path ->
            return drawBitmapOnMask(bitmap)
        }
        return null
    }

    /**
     * Performs a cut operation that combines copy and clip.
     * Creates a copy of the selected area then removes it from the original.
     * @return New bitmap containing the cut content, or null if operation fails.
     */
    override fun cut(): Bitmap? {
        doIfPathAndBitmapNotNull { bitmap, path ->
            // First create a copy of the selected area.
            val finalBitmap = drawBitmapOnMask(bitmap)
            // Then remove the area from original bitmap.
            clip()
            return finalBitmap
        }
        return null
    }

    /**
     * Utility function that executes operations only when both path and bitmap are available.
     * Provides null safety for all clipping operations.
     */
    protected inline fun doIfPathAndBitmapNotNull(function: (bitmap: Bitmap, path: Path) -> Unit) {
        bitmap?.let { bit ->
            path?.let { p ->
                function(bit, p)
            }
        }
    }

    /**
     * Creates a masked bitmap with the selected area isolated.
     * This method handles the complex process of creating smooth selections.
     */
    protected fun drawBitmapOnMask(bitmap: Bitmap): Bitmap {
        // Calculate the bounds for efficient processing.
        setRectangleBounds(bitmap)

        // Create a copy of the bitmap for masking operations.
        val maskedBitmap = createMaskedBitmap(bitmap)

        // Store original fill type for restoration.
        val fillType = path!!.fillType

        // Invert the fill type for masking (opposite of clipping).
        path!!.fillType =
            if (isInverse) Path.FillType.WINDING else Path.FillType.INVERSE_WINDING

        // Apply the mask to isolate the selected area.
        clip(maskedBitmap)

        // Restore original fill type.
        path!!.fillType = fillType

        // Extract only the relevant rectangle to optimize memory usage.
        val rect = bitmapRectangle.toRect()

        return Bitmap.createBitmap(
            maskedBitmap,
            rect.left,
            rect.top,
            rect.width(),
            rect.height()
        )
    }

    /**
     * Internal clipping method that applies smooth edges and masking.
     * @param targetBitmap The bitmap to apply clipping operations to.
     */
    protected fun clip(targetBitmap: Bitmap) {
        // Create smooth edges bitmap for high-quality selection boundaries.
        val smoothEdgesBitmap =
            createSmoothEdgesBitmap(targetBitmap.width, targetBitmap.height)

        // Apply the mask to create transparency in selected areas.
        maskOutBitmap(smoothEdgesBitmap, targetBitmap)
    }

    /**
     * Calculates the optimal rectangle bounds for clipping operations.
     * Optimizes performance by working only with relevant image areas.
     */
    protected fun setRectangleBounds(bitmap: Bitmap) {
        if (isInverse) {
            // For inverse selections, use the entire bitmap bounds.
            bitmapRectangle.set(
                0f,
                0f,
                bitmap.width.toFloat(),
                bitmap.height.toFloat()
            )
        } else {
            // For normal selections, compute tight bounds around the path.
            path!!.computeBounds(bitmapRectangle, true)

            // Clamp bounds to bitmap dimensions to prevent errors.
            bitmapRectangle.left = bitmapRectangle.left.coerceAtLeast(0f)
            bitmapRectangle.top = bitmapRectangle.top.coerceAtMost(0f)
            bitmapRectangle.right = bitmapRectangle.right.coerceAtMost(bitmap.width.toFloat())
            bitmapRectangle.bottom = bitmapRectangle.bottom.coerceAtMost(bitmap.height.toFloat())
        }
    }

    /**
     * Retrieves the bounds of the clipping area.
     * @param rect Rectangle to store the clipping bounds.
     * @throws IllegalStateException if path or bitmap is null.
     */
    override fun getClippingBounds(rect: RectF) {
        doIfPathAndBitmapNotNull { bitmap, path ->
            setRectangleBounds(bitmap)
            rect.set(bitmapRectangle)
            return
        }
        throw IllegalStateException("path and/or bitmap is null; cannot get the clipping bounds")
    }

    /**
     * Creates a working copy of the bitmap for masking operations.
     * Uses the same configuration as the original to maintain quality.
     */
    protected open fun createMaskedBitmap(bitmap: Bitmap): Bitmap {
        return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
    }

    /**
     * Creates a bitmap with smooth edges using the configured brush.
     * This provides professional-quality anti-aliased selection boundaries.
     */
    protected open fun createSmoothEdgesBitmap(width: Int, height: Int): Bitmap {
        // Create a copy of the path to avoid modifying the original.
        pathCopy.set(path!!)
        pathCopy.close()

        // Generate smooth edges using brush rendering system.
        val b = BrushPreview.createBrushSnapshot(
            width,
            height,
            0f,
            0f,
            edgeBrush,
            resolution = 1024,
            basicLineSmoother,
            pathCopy
        )
        return b
    }

    /**
     * Applies the smooth edges mask to create transparency in the target bitmap.
     * Uses Porter-Duff DST_OUT mode for precise alpha channel manipulation.
     */
    protected open fun maskOutBitmap(smoothEdgesBitmap: Bitmap, bitmap: Bitmap) {
        canvas.apply {
            // First, fill the path area on the smooth edges bitmap.
            setBitmap(smoothEdgesBitmap)
            drawPath(pathCopy, lassoFillPaint)

            // Then apply the mask to the target bitmap to create transparency.
            setBitmap(bitmap)
            drawBitmap(smoothEdgesBitmap, 0f, 0f, dstOutBitmapPaint)
        }
    }
}

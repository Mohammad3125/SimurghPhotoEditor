package ir.baboomeh.photolib.components.paint.painters.coloring

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.annotation.ColorInt
import ir.baboomeh.photolib.components.paint.painters.selection.PenToolBase

/**
 * Pen tool painter specialized for precise color filling operations.
 *
 * This tool extends the base pen tool functionality to provide advanced coloring
 * capabilities with support for both normal and inverse filling modes. Users can
 * create precise selections using pen-like strokes and then fill these selections
 * with solid colors.
 *
 * The tool supports:
 * - Normal filling: Fill the selected path with the chosen color
 * - Inverse filling: Fill everything except the selected path with the chosen color
 *
 * @param context Android context required for initialization
 */
open class PenToolColorPainter(context: Context) : PenToolBase(context) {

    /** Canvas used for applying color operations to bitmaps */
    protected val canvasApply by lazy {
        Canvas()
    }

    /**
     * The color used for filling operations.
     * This color will be applied to the selected area when coloring is applied.
     */
    @ColorInt
    open var fillingColor = Color.BLACK

    /** Temporary bitmap used for inverse coloring operations */
    protected lateinit var coloringBitmap: Bitmap

    /**
     * Applies the selected color to the current layer based on the drawn path.
     *
     * This method supports two filling modes:
     * - Normal mode: Fills only the area enclosed by the drawn path
     * - Inverse mode: Fills the entire layer with the color, then cuts out the path area
     *
     * The inverse mode is useful for creating selections where you want to color
     * everything except the area you've outlined.
     *
     * @param isInverse true for inverse filling (color everything except the path),
     *                  false for normal filling (color only the path area)
     */
    open fun applyColoring(isInverse: Boolean) {

        selectedLayer?.let { layer ->
            // Initialize the coloring bitmap if not already done
            if (!this::coloringBitmap.isInitialized && layer.bitmap.config != null) {
                coloringBitmap = layer.bitmap.copy(layer.bitmap.config ?: Bitmap.Config.ARGB_8888, true)
            }

            // Convert the drawn lines into a closed path for filling
            drawLinesIntoPath(path)

            if (isInverse) {
                // Inverse mode: Color everything except the selected path

                // Fill the entire coloring bitmap with the selected color
                coloringBitmap.eraseColor(fillingColor)

                // Set up canvas to work on the coloring bitmap
                canvasApply.setBitmap(coloringBitmap)

                // Configure paint for creating a "hole" in the colored area
                linesPaint.style = Paint.Style.FILL
                linesPaint.color = Color.BLACK
                linesPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

                // Cut out the path area (creating transparency)
                canvasApply.drawPath(path, linesPaint)

                // Reset paint settings
                linesPaint.xfermode = null
                linesPaint.style = Paint.Style.STROKE
                linesPaint.color = Color.BLACK

                // Apply the result to the actual layer
                canvasApply.setBitmap(layer.bitmap)
                canvasApply.drawBitmap(coloringBitmap, 0f, 0f, linesPaint)

            } else {
                // Normal mode: Color only the selected path area

                // Set up canvas to work directly on the layer bitmap
                canvasApply.setBitmap(layer.bitmap)

                // Configure paint for solid fill
                linesPaint.style = Paint.Style.FILL
                linesPaint.color = fillingColor

                // Fill the path with the selected color
                canvasApply.drawPath(path, linesPaint)

                // Reset paint settings for future operations
                linesPaint.style = Paint.Style.STROKE
                linesPaint.color = Color.BLACK
            }
        }
    }
}

package ir.baboomeh.photolib.components.paint.painters.coloring

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import androidx.annotation.ColorInt
import ir.baboomeh.photolib.components.paint.painters.masking.LassoMaskPainterTool
import ir.baboomeh.photolib.utils.gesture.TouchData

/**
 * Painter tool for creating lasso selections and filling them with solid colors.
 *
 * This tool extends the lasso mask functionality to provide color filling capabilities.
 * Users can draw a freeform lasso selection by dragging their finger/stylus, and when
 * the gesture is completed, the enclosed area is filled with the specified color.
 *
 * The tool uses a fill paint style to ensure the entire selected area is covered
 * with the chosen color, rather than just outlining the selection boundary.
 *
 * @param context Android context required for initialization
 */
open class LassoColorPainter(context: Context) : LassoMaskPainterTool(context) {

    /**
     * The color used to fill the lasso selection area.
     *
     * When set, this color is applied to the paint object and the view is invalidated
     * to reflect the color change in the UI.
     */
    @ColorInt
    open var fillingColor = Color.BLACK
        set(value) {
            field = value
            // Update the paint color to match the new filling color
            lassoPaint.color = field
            // Trigger a redraw to show the color change
            sendMessage(PainterMessage.INVALIDATE)
        }

    init {
        // Configure paint for solid fill rather than stroke outline
        lassoPaint.style = Paint.Style.FILL
    }

    /**
     * Called when the touch gesture ends, triggering the color fill operation.
     *
     * First calls the parent implementation to complete the lasso path, then
     * applies the color fill to the selected area.
     *
     * @param touchData Final touch event data when the gesture is completed
     */
    override fun onMoveEnded(touchData: TouchData) {
        super.onMoveEnded(touchData)
        // Apply the color fill to the completed lasso selection
        applyOnLayer()
    }

    /**
     * Applies the color fill to the current layer within the lasso selection area.
     *
     * This method draws the lasso path filled with the current filling color
     * directly onto the layer's bitmap. After application, the paint state is
     * reset and the view is invalidated to show the changes.
     */
    override fun applyOnLayer() {
        selectedLayer?.bitmap.let { layerBitmap ->
            // Set the canvas to draw on the layer's bitmap
            canvasColorApply.setBitmap(layerBitmap)

            // Fill the lasso path with the selected color
            canvasColorApply.drawPath(lassoPath, lassoPaint)

            // Clean up the paint state for the next operation
            resetPaint()

            // Trigger a redraw to show the applied color fill
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

}

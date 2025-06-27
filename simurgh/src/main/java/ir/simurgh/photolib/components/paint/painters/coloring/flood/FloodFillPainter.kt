package ir.simurgh.photolib.components.paint.painters.coloring.flood

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.simurgh.photolib.components.paint.painters.painter.Painter
import ir.simurgh.photolib.components.paint.view.PaintLayer
import ir.simurgh.photolib.utils.gesture.TouchData

/**
 * Painter implementation for flood fill (bucket fill) functionality.
 *
 * This painter handles touch events to trigger flood fill operations on bitmap layers.
 * Unlike brush-based painters, it doesn't draw continuously but performs a single fill
 * operation when the touch gesture ends.
 *
 * The actual flood fill algorithm is delegated to external handlers via callback,
 * allowing for different flood fill implementations to be plugged in.
 */
open class FloodFillPainter : Painter() {

    /** The bitmap of the currently selected layer to perform flood fill on */
    protected var layerBitmap: Bitmap? = null

    /** Callback function invoked when a flood fill operation is requested */
    protected var floodFillRequestCallback: ((bitmap: Bitmap, ex: Int, ey: Int) -> Unit)? = null

    /** Flag to prevent automatic history saving since flood fill handles its own history */
    protected var preventHistorySave = true

    /**
     * Called when touch gesture begins. No action needed for flood fill.
     *
     * @param touchData Touch event data containing position and pressure info
     */
    override fun onMoveBegin(touchData: TouchData) {
        // No action needed for flood fill on touch begin
    }

    /**
     * Called during touch movement. No action needed for flood fill.
     *
     * @param touchData Touch event data containing position and pressure info
     */
    override fun onMove(touchData: TouchData) {
        // No action needed for flood fill during movement
    }

    /**
     * Called when touch gesture ends. Triggers the flood fill operation.
     *
     * Validates the touch coordinates are within bitmap bounds and invokes
     * the flood fill callback if available.
     *
     * @param touchData Final touch event data containing the fill position
     */
    override fun onMoveEnded(touchData: TouchData) {
        layerBitmap?.let { bitmap ->
            val ex = touchData.ex.toInt()
            val ey = touchData.ey.toInt()

            // Ensure the touch point is within bitmap boundaries
            if (!isPointValidInBitmap(bitmap, ex, ey)) {
                return
            }

            // Trigger the flood fill operation via callback
            floodFillRequestCallback?.invoke(bitmap, ex, ey)
        }

        // Ensure history saving is handled externally
        preventHistorySave = true
    }

    /**
     * Validates that the given coordinates are within the bitmap boundaries.
     *
     * @param bitmap The bitmap to check against
     * @param ex X coordinate to validate
     * @param ey Y coordinate to validate
     * @return true if the point is valid within bitmap bounds, false otherwise
     */
    protected fun isPointValidInBitmap(bitmap: Bitmap, ex: Int, ey: Int): Boolean {
        return (ex.coerceIn(0, bitmap.width - 1) == ex) && (ey.coerceIn(0, bitmap.height - 1) == ey)
    }

    /**
     * Draws the painter's visual representation. No drawing needed for flood fill.
     *
     * @param canvas The canvas to draw on
     */
    override fun draw(canvas: Canvas) {
        // No visual representation needed for flood fill painter
    }

    /**
     * Resets the painter's state. No state to reset for flood fill.
     */
    override fun resetPaint() {
        // No paint state to reset for flood fill
    }

    /**
     * Sets the callback function to be invoked when a flood fill operation is requested.
     *
     * @param func Callback function that receives (bitmap, x, y) parameters
     */
    open fun setOnFloodFillRequest(func: (bitmap: Bitmap, ex: Int, ey: Int) -> Unit) {
        floodFillRequestCallback = func
    }

    /**
     * Called when the active paint layer changes. Updates the target bitmap reference.
     *
     * @param layer The new active paint layer, or null if no layer is active
     */
    override fun onLayerChanged(layer: PaintLayer?) {
        if (layer == null) {
            return
        }

        // Update the target bitmap for flood fill operations
        layerBitmap = layer.bitmap
    }

    /**
     * Indicates whether this painter handles its own history management.
     *
     * Flood fill operations typically require special history handling due to
     * their potentially large impact on the bitmap.
     *
     * @return true if history saving should be prevented, false otherwise
     */
    override fun doesHandleHistory(): Boolean {
        val toReturn = preventHistorySave
        preventHistorySave = false
        return toReturn
    }

}

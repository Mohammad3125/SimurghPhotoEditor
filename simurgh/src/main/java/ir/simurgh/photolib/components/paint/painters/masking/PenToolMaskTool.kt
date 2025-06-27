package ir.simurgh.photolib.components.paint.painters.masking

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import ir.simurgh.photolib.components.paint.painters.painter.PainterMessage
import ir.simurgh.photolib.components.paint.painters.selection.PenToolBase

/**
 * A precision masking tool that uses pen-based path creation for mask generation.
 *
 * This tool extends the [PenToolBase] to provide mask-specific functionality,
 * allowing users to create precise masks using bezier curves and straight lines.
 * It's particularly useful for:
 *
 * **Precision Masking:**
 * - Create complex selection paths with bezier curve support
 * - Precise point-by-point mask definition
 * - Support for both straight lines and curved segments
 * - Interactive handle manipulation for curve adjustment
 *
 * **Mask Operations:**
 * - **Add to mask**: Paint mask areas using normal blend mode
 * - **Subtract from mask**: Remove areas from existing masks using DST_OUT mode
 * - **Path completion**: Automatically close paths for area filling
 * - **Undo support**: Remove individual path segments
 *
 * **Professional Workflow:**
 * - Non-destructive path editing before mask application
 * - Visual feedback during path creation
 * - Smooth integration with the masking system
 * - Support for complex multi-segment paths
 *
 * The tool is designed for scenarios requiring high precision, such as:
 * - Product photography masking
 * - Architectural detail selection
 * - Technical illustration work
 * - Any situation where pixel-perfect selections are needed
 *
 * **Usage Example:**
 * ```kotlin
 * val penMaskTool = PenToolMaskTool(context)
 * penMaskTool.lineType = LineType.QUAD_BEZIER // Use curved segments
 *
 * // Create mask path interactively, then:
 * penMaskTool.applyOnMaskLayer() // Add to mask
 * // or
 * penMaskTool.cutFromMaskLayer() // Subtract from mask
 * ```
 *
 * @param context Android context for accessing resources and display metrics
 */
open class PenToolMaskTool(context: Context) : PenToolBase(context) {

    /**
     * Canvas used for applying path operations to the mask layer.
     */
    protected val canvasApply by lazy {
        Canvas()
    }

    /**
     * Applies the current path as a filled area to the mask layer.
     *
     * This method converts the constructed path into a mask region by:
     * 1. Drawing all path segments into a single path object
     * 2. Switching paint to fill mode for area coverage
     * 3. Drawing the filled path onto the mask layer
     * 4. Restoring stroke mode for continued path editing
     *
     * The operation is additive, meaning it adds to existing mask content.
     */
    open fun applyOnMaskLayer() {
        drawLinesIntoPath(path)
        selectedLayer?.let { maskLayer ->
            canvasApply.setBitmap(maskLayer.bitmap)
            // Switch to fill mode to create mask areas  
            linesPaint.style = Paint.Style.FILL
            canvasApply.drawPath(path, linesPaint)
            // Restore stroke mode for path visualization
            linesPaint.style = Paint.Style.STROKE
        }
    }

    /**
     * Removes (cuts) the current path area from the existing mask.
     *
     * This method performs a subtractive mask operation by:
     * 1. Setting the paint to DST_OUT blend mode for pixel removal
     * 2. Applying the path to remove mask content
     * 3. Restoring normal blend mode for future operations
     *
     * This is useful for creating holes in masks or refining mask edges
     * by removing unwanted areas.
     */
    open fun cutFromMaskLayer() {
        // Set blend mode to remove pixels where path is drawn
        linesPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        applyOnMaskLayer()
        // Restore normal blend mode
        linesPaint.xfermode = null
    }

    /**
     * Removes the most recently added line segment from the path.
     *
     * This provides undo functionality for path creation by:
     * 1. Removing the last line from the internal stack
     * 2. Restoring the previous line as selected (if any exists)
     * 3. Updating the path state and visual representation
     * 4. Handling path closure state if the removed line closed the path
     *
     * This allows users to iteratively refine their paths without starting over.
     */
    open fun removeLastLine() {
        lines.run {
            if (isNotEmpty()) {
                // Remove last line in stack
                pop()

                // If it's not empty...
                if (isNotEmpty()) {
                    // Then get the previous line and select it and restore its state
                    val currentLine = peek()

                    selectedLine = currentLine

                    setLineRelatedVariables(currentLine)

                    isNewLineDrawn = true
                } else {
                    isNewLineDrawn = false
                    selectedLine = null
                }

                // If path is close and user undoes the operation,
                // then open the path and reset its offset and cancel path animation
                if (isPathClose) {
                    isPathClose = false
                    cancelAnimation()
                }
            }

            // Decrement the counter
            if (pointCounter > 0) {
                --pointCounter
            }

            sendMessage(PainterMessage.INVALIDATE)
        }
    }
}

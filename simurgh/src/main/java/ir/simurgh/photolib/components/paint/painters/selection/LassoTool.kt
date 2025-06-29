package ir.simurgh.photolib.components.paint.painters.selection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import ir.simurgh.photolib.components.paint.painters.coloring.LassoColorPainter
import ir.simurgh.photolib.components.paint.painters.painter.PainterMessage
import ir.simurgh.photolib.components.paint.painters.selection.clippers.PathBitmapClipper
import ir.simurgh.photolib.components.paint.view.PaintLayer
import ir.simurgh.photolib.utils.gesture.TouchData
import ir.simurgh.photolib.utils.history.HistoryHandler
import ir.simurgh.photolib.utils.history.HistoryState
import ir.simurgh.photolib.utils.history.handlers.StackHistoryHandler
import ir.simurgh.photolib.utils.matrix.SimurghMatrix

/**
 * An advanced lasso selection tool for creating freehand selections in image editing applications.
 *
 * This tool extends the basic lasso functionality to provide comprehensive selection capabilities
 * with professional-grade features commonly found in image editing software. It combines
 * intuitive freehand drawing with powerful selection operations.
 *
 * **Core Features:**
 * - **Freehand Selection**: Draw selection boundaries with finger or stylus input.
 * - **Visual Feedback**: Animated selection outline with marching ants effect.
 * - **Multiple Operations**: Support for copy, cut, and clip operations on selections.
 * - **Inverse Selection**: Toggle between normal and inverse selection modes.
 * - **Smooth Edges**: High-quality anti-aliased selection boundaries.
 *
 * **Selection Modes:**
 * - **Normal Mode**: Selects the area inside the drawn lasso path.
 * - **Inverse Mode**: Selects everything outside the drawn lasso path.
 * - Real-time visual preview of which areas will be selected.
 *
 * **User Experience:**
 * - Responsive touch input with smooth path generation.
 * - Visual indication of selection state with animated outlines.
 * - Automatic path closure for complete selections.
 * - Zoom-aware stroke width for consistent appearance at all scales.
 *
 * **Professional Operations:**
 * - **Copy**: Create isolated bitmap of selected content.
 * - **Cut**: Extract selected content and remove from original.
 * - **Clip**: Remove selected areas leaving transparent regions.
 * - All operations maintain edge quality and support undo/redo.
 *
 * **Technical Integration:**
 * - Built on top of LassoColorPainter for consistent behavior.
 * - Integrates with PathBitmapClipper for high-quality selection processing.
 * - Supports matrix transformations for zoom and pan operations.
 * - Thread-safe operations with proper state management.
 *
 * **Usage Example:**
 * ```kotlin
 * val lassoTool = LassoTool(context, pathClipper)
 * lassoTool.isInverse = false // Normal selection mode
 * // User draws selection path
 * val selectedBitmap = lassoTool.copy() // Get selection as bitmap
 * lassoTool.clip() // Remove selected area from original
 * ```
 */
open class LassoTool(context: Context, var clipper: PathBitmapClipper) :
    LassoColorPainter(context) {

    /** Copy of the lasso path used for rendering with modifications like inverse selection. */
    protected val lassoCopy by lazy {
        Path()
    }

    /** Bounds of the current view/layer for defining inverse selection areas. */
    protected val viewBounds = RectF()

    /**
     * Determines whether the selection is inverted (selecting outside the path).
     * When true, everything outside the drawn path is selected instead of inside.
     */
    open var isInverse = false
        set(value) {
            if (field != value) {
                // Prepare for new operation before changing the state.
                prepareForNewOperation()
                field = value
                // Save the state change for undo/redo.
                saveHistoryState()
                // Trigger visual update to show selection change.
                sendMessage(PainterMessage.INVALIDATE)
            }
        }

    /** Canvas transformation matrix for handling zoom and pan operations. */
    protected lateinit var canvasMatrix: SimurghMatrix

    /** History handler for managing undo/redo operations. */
    override var historyHandler: HistoryHandler? = StackHistoryHandler()

    /** Backup of the initial lasso tool state for history operations. */
    protected var initialState: LassoToolStateSnapshot? = null

    init {
        // Configure lasso paint for stroke-only rendering of selection outline.
        lassoPaint.style = Paint.Style.STROKE
    }

    /**
     * Initializes the lasso tool with transformation matrices and layer bounds.
     * Sets up the animation system and prepares the tool for selection operations.
     */
    override fun initialize(
        context: Context,
        transformationMatrix: SimurghMatrix,
        fitInsideMatrix: SimurghMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)

        // Store canvas matrix for zoom-aware rendering.
        canvasMatrix = transformationMatrix

        // Start marching ants animation for visual feedback.
        pathEffectAnimator.start()

        // Store view bounds for inverse selection calculations.
        viewBounds.set(layerBounds)
    }

    /**
     * Renders the lasso selection with appropriate visual styling.
     * Handles both normal and inverse selection display modes.
     */
    override fun draw(canvas: Canvas) {
        // Adjust stroke width based on current zoom level for consistent appearance.
        lassoPaint.strokeWidth = lassoStrokeWidth / canvasMatrix.getRealScaleX()

        // Create a copy of the path for rendering modifications.
        lassoCopy.set(lassoPath)
        lassoCopy.close()

        // For inverse selections, add the entire view area to show what will be selected.
        if (isInverse && !lassoPath.isEmpty) {
            lassoCopy.addRect(
                viewBounds, Path.Direction.CW
            )
        }

        // Draw the selection outline with animated marching ants effect.
        canvas.drawPath(lassoCopy, lassoPaint)
    }

    /**
     * Resets the lasso tool to its initial state.
     * Clears the current selection path and prepares for new selection.
     */
    override fun reset() {
        // Clear both working paths.
        lassoPath.rewind()
        lassoCopy.rewind()

        // Reset drawing state to allow new selection.
        isFirstPoint = true

        // Request screen update to remove selection display.
        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Called when user begins drawing a selection stroke.
     * Prepares the tool for a new selection operation.
     */
    override fun onMoveBegin(touchData: TouchData) {
        // Prepare for new drawing operation.
        prepareForNewOperation()
        super.onMoveBegin(touchData)
    }

    /**
     * Called when user finishes drawing a selection stroke.
     * Completes the path smoothing process and saves the state.
     */
    override fun onMoveEnded(touchData: TouchData) {
        // Complete the smooth line generation for the selection path.
        touchSmoother.setLastPoint(touchData, smoothnessBrush)

        // Save state after completing the path drawing.
        if (!lassoPath.isEmpty) {
            saveHistoryState()
        }
    }

    /**
     * Configures the clipper with current selection parameters.
     * Sets up the clipper for processing operations on the selected layer.
     */
    protected fun setClipper(layer: PaintLayer) {
        clipper.path = lassoPath
        clipper.bitmap = layer.bitmap
        clipper.isInverse = isInverse
    }

    /**
     * Creates a non-destructive copy of the selected area.
     * @return New bitmap containing only the selected content, or null if no valid selection.
     */
    open fun copy(): Bitmap? {
        doIfLayerNotNullAndPathIsNotEmpty { layer ->
            val finalBitmap = clipper.copy()
            return finalBitmap
        }

        return null
    }

    /**
     * Performs a cut operation that extracts the selected area.
     * Creates a copy of the selection then removes it from the original image.
     * @return New bitmap containing the cut content, or null if no valid selection.
     */
    open fun cut(): Bitmap? {
        doIfLayerNotNullAndPathIsNotEmpty { layer ->
            // Extract the selected content into a new bitmap.
            val finalBitmap = clipper.cut()

            // Save the operation for undo/redo functionality.
            sendMessage(PainterMessage.SAVE_HISTORY)

            return finalBitmap
        }

        return null
    }

    /**
     * Performs a destructive clip operation on the selected area.
     * Removes the selected content from the original image, leaving transparency.
     */
    open fun clip() {
        doIfLayerNotNullAndPathIsNotEmpty { layer ->
            // Remove the selected area from the original image.
            clipper.clip()

            // Save the operation for undo/redo functionality.
            sendMessage(PainterMessage.SAVE_HISTORY)
        }
    }

    /**
     * Retrieves the bounds of the current selection area.
     * @param rect Rectangle to store the selection bounds.
     */
    open fun getClippingBounds(rect: RectF) {
        doIfLayerNotNullAndPathIsNotEmpty {
            clipper.getClippingBounds(rect)
        }
    }

    /**
     * Releases resources and stops animations when the tool is no longer needed.
     * Ensures proper cleanup to prevent memory leaks.
     */
    override fun release() {
        super.release()
        // Stop the marching ants animation to save resources.
        pathEffectAnimator.cancel()
    }

    /**
     * Utility function that executes operations only when conditions are met.
     * Ensures that operations only proceed with valid selections and active layers.
     *
     * @param function The operation to execute when conditions are satisfied.
     */
    protected inline fun doIfLayerNotNullAndPathIsNotEmpty(function: (layer: PaintLayer) -> Unit) {
        // Only proceed if we have a valid selection path and an active layer.
        if (!isFirstPoint && !lassoPath.isEmpty) {
            selectedLayer?.let { layer ->
                // Configure the clipper for the current selection.
                setClipper(layer)
                // Execute the requested operation.
                function(layer)
            }
        }
    }

    /**
     * Data class that captures a snapshot of the lasso tool's state at a specific point in time.
     * Used for creating backups before operations that need to be undoable.
     *
     * @param isInverse The inverse selection setting at the time of capture.
     * @param lassoPath Copy of the lasso path at the time of capture.
     */
    protected data class LassoToolStateSnapshot(
        val isInverse: Boolean,
        val lassoPath: Path
    ) {
        /**
         * Creates a deep copy of this state snapshot.
         */
        fun clone(): LassoToolStateSnapshot {
            return LassoToolStateSnapshot(
                isInverse = isInverse,
                lassoPath = Path(lassoPath)
            )
        }
    }

    /**
     * Captures the current state of the lasso tool as a snapshot.
     *
     * @return A snapshot containing the current isInverse setting and lasso path.
     */
    protected fun getCurrentStateSnapshot(): LassoToolStateSnapshot {
        return LassoToolStateSnapshot(
            isInverse = isInverse,
            lassoPath = Path(lassoPath)
        )
    }

    /**
     * Internal state class for managing undo/redo operations.
     * Captures both initial and final states of the lasso tool configuration.
     */
    protected inner class LassoToolState(
        private val initialSnapshot: LassoToolStateSnapshot?,
        private val reference: LassoTool = this@LassoTool
    ) : HistoryState {

        /** Snapshot of the current state when this history state was created. */
        private val clonedSnapshot = getCurrentStateSnapshot().clone()

        /**
         * Restores the lasso tool to its initial state.
         */
        override fun undo() {
            restoreState(initialSnapshot)
        }

        /**
         * Re-applies the final state to the lasso tool.
         */
        override fun redo() {
            restoreState(clonedSnapshot)
        }

        /**
         * Internal method to restore a specific state configuration.
         *
         * @param targetSnapshot The state snapshot to restore, or null to clear state.
         */
        private fun restoreState(targetSnapshot: LassoToolStateSnapshot?) {
            if (targetSnapshot != null) {
                reference.isInverse = targetSnapshot.isInverse
                reference.lassoPath.set(targetSnapshot.lassoPath)
            } else {
                // Clear state if no target provided.
                reference.reset()
            }
            reference.sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Saves the current state of the lasso tool to the history handler.
     * Uses the LayeredPainterView pattern with initial and cloned values.
     */
    protected fun saveHistoryState() {
        val state = LassoToolState(initialState)

        // Update the initial state for the next operation.
        initialState = getCurrentStateSnapshot()

        historyHandler?.addState(state)
    }

    /**
     * Prepares the lasso tool for a new operation by capturing the initial state.
     * Should be called before operations that will modify the tool's state.
     */
    protected fun prepareForNewOperation() {
        initialState = getCurrentStateSnapshot()
    }

    override fun undo() {
        historyHandler?.undo()
    }

    override fun redo() {
        historyHandler?.redo()
    }

    override fun doesHandleHistory(): Boolean {
        return true
    }
}

package ir.simurgh.photolib.components.paint.painters.masking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import ir.simurgh.photolib.components.paint.painters.painter.Painter
import ir.simurgh.photolib.components.paint.painters.painter.PainterMessage
import ir.simurgh.photolib.components.paint.painters.painting.brushes.Brush
import ir.simurgh.photolib.components.paint.painters.painting.engines.DrawingEngine
import ir.simurgh.photolib.components.paint.smoothers.BezierLineSmoother
import ir.simurgh.photolib.components.paint.smoothers.LineSmoother
import ir.simurgh.photolib.utils.gesture.TouchData
import ir.simurgh.photolib.utils.history.HistoryHandler
import ir.simurgh.photolib.utils.history.HistoryState
import ir.simurgh.photolib.utils.history.handlers.StackFullRestoreHistoryHandler
import ir.simurgh.photolib.utils.matrix.SimurghMatrix

/**
 * A bitmap masking tool that allows selective masking/erasing of content using brush strokes.
 *
 * This tool implements a sophisticated masking system commonly used in photo editing applications
 * for operations like background removal, object isolation, or selective editing. It works by
 * maintaining two separate bitmaps:
 *
 * **Core Functionality:**
 * - **Source Bitmap**: The original image content to be masked
 * - **Mask Bitmap**: A separate bitmap that defines which areas are visible/hidden
 * - **Brush-based Editing**: Uses configurable brushes to paint mask areas
 * - **Real-time Preview**: Shows masking results immediately during editing
 *
 * **Masking Technique:**
 * The tool uses Porter-Duff compositing with DST_OUT blend mode, which means:
 * - Where the mask is painted (white/opaque), the source image becomes transparent
 * - Where the mask is not painted (transparent), the source image remains visible
 * - This creates a "cookie cutter" effect for precise image masking
 *
 * **Drawing System Integration:**
 * - Integrates with the drawing engine for consistent brush behavior
 * - Supports line smoothing for natural stroke appearance
 * - Uses the same brush system as other painting tools for consistency
 *
 * **Use Cases:**
 * - Background removal and replacement
 * - Object isolation for selective editing
 * - Creating custom-shaped image cutouts
 * - Non-destructive masking workflows
 *
 * **Technical Implementation:**
 * The tool renders by first drawing the source bitmap, then applying the mask bitmap
 * with DST_OUT blend mode. This approach allows for:
 * - Efficient real-time rendering
 * - Non-destructive editing (original image unchanged)
 * - Precise control over transparency levels
 * - Support for brush opacity and blending
 *
 * **Usage Example:**
 * ```kotlin
 * val maskTool = BitmapMaskModifierTool(sourceImage, maskBitmap, drawingEngine)
 * maskTool.brush = eraserBrush
 * // User draws to create mask areas
 * // Result shows sourceImage with masked areas transparent
 * ```
 */
open class BitmapMaskModifierTool(
    open var bitmap: Bitmap,
    maskBitmap: Bitmap,
    var engine: DrawingEngine
) :
    Painter(),
    LineSmoother.OnDrawPoint {

    /**
     * The mask bitmap that defines which areas of the source image are visible.
     * White/opaque areas in this bitmap will make corresponding areas in the source transparent.
     * This bitmap is modified when the user draws with brushes.
     */
    open var maskBitmap: Bitmap = maskBitmap
        set(value) {
            field = value
            // Update the canvas to draw on the new mask bitmap
            paintCanvas.setBitmap(field)
            // Create backup for undo operations
            initialMaskBitmapState = value.copy(value.config ?: Bitmap.Config.ARGB_8888, true)
        }

    /**
     * Backup of the initial mask bitmap state for history management.
     */
    protected var initialMaskBitmapState: Bitmap? = null

    /**
     * The brush used for painting mask areas. This determines the size, opacity,
     * and blending characteristics of the masking strokes.
     */
    open var brush: Brush? = null
        set(value) {
            field = value
            if (value != null) {
                finalBrush = value
            }
        }

    /** Internal reference to the active brush, guaranteed to be non-null when drawing */
    protected lateinit var finalBrush: Brush

    /**
     * Line smoother for creating natural-looking brush strokes.
     * Interpolates between touch points to create smooth curves instead of jagged lines.
     */
    open var lineSmoother: LineSmoother = BezierLineSmoother()
        set(value) {
            field = value
            // Set this tool as the drawing callback for smoothed points
            field.onDrawPoint = this
        }

    /** Canvas used for drawing brush strokes onto the mask bitmap */
    protected val paintCanvas by lazy {
        Canvas()
    }

    /**
     * Paint configured with DST_OUT blend mode for erasing/masking.
     * This blend mode makes the source image transparent where the mask is painted.
     */
    protected val dstOutBitmapPaint by lazy {
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
    }

    /** Standard paint for drawing the source bitmap without modifications */
    protected val bitmapPaint by lazy {
        Paint()
    }

    /** Bounds of the layer being masked, used for efficient rendering */
    protected val layerBound = RectF()


    override var historyHandler: HistoryHandler? = StackFullRestoreHistoryHandler()

    /**
     * Initializes the masking tool with transformation matrices and layer bounds.
     * Sets up the mask canvas and prepares the tool for drawing operations.
     */
    override fun initialize(
        context: Context,
        transformationMatrix: SimurghMatrix,
        fitInsideMatrix: SimurghMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)

        prepareInitialState()

        // Configure the canvas to draw on the mask bitmap
        paintCanvas.setBitmap(maskBitmap)

        // Set up the line smoother callback
        lineSmoother.onDrawPoint = this

        // Store layer bounds for efficient rendering
        layerBound.set(layerBounds)
    }

    /**
     * Called when user starts a brush stroke. Initializes the drawing engine
     * and line smoother for the new stroke.
     */
    override fun onMoveBegin(touchData: TouchData) {
        if (shouldDraw()) {
            touchData.run {
                prepareInitialState()

                // Initialize the drawing engine for the new stroke
                engine.onMoveBegin(touchData, finalBrush)

                // Set the first point for line smoothing
                lineSmoother.setFirstPoint(
                    touchData,
                    finalBrush
                )
            }
        }
    }

    /**
     * Called during brush stroke movement. Continues the stroke and adds
     * points to the line smoother for smooth curve generation.
     */
    override fun onMove(touchData: TouchData) {
        if (shouldDraw()) {
            // Continue the stroke in the drawing engine
            engine.onMove(touchData, finalBrush)

            // Add points to the line smoother for interpolation
            lineSmoother.addPoints(touchData, finalBrush)
        }
    }

    /**
     * Called when user ends a brush stroke. Finalizes the stroke in both
     * the drawing engine and line smoother, then triggers a screen refresh.
     */
    override fun onMoveEnded(touchData: TouchData) {
        if (shouldDraw()) {
            // Finalize the stroke in the drawing engine
            engine.onMoveEnded(touchData, finalBrush)

            // Set the last point for line smoothing completion
            lineSmoother.setLastPoint(
                touchData,
                finalBrush
            )

            // Save state for undo/redo functionality
            saveState()

            // Request screen update to show the new mask effect
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Determines if drawing operations should proceed.
     * Only allows drawing when a brush has been properly configured.
     */
    protected open fun shouldDraw(): Boolean {
        return this::finalBrush.isInitialized
    }

    /**
     * Renders the masked bitmap by compositing the source image with the mask.
     *
     * The rendering process:
     * 1. Creates a layer for proper blending
     * 2. Draws the source bitmap normally
     * 3. Applies the mask bitmap with DST_OUT mode to create transparency
     * 4. Restores the layer to complete the composite
     *
     * This approach ensures that masked areas become transparent while
     * preserving the quality and blending of the original image.
     */
    override fun draw(canvas: Canvas) {
        canvas.apply {
            // Create a layer for proper alpha blending
            saveLayer(layerBound, bitmapPaint)

            // Draw the source image
            drawBitmap(bitmap, 0f, 0f, bitmapPaint)

            // Apply the mask to create transparency where mask is painted
            drawBitmap(maskBitmap, 0f, 0f, dstOutBitmapPaint)

            // Complete the composite operation
            restore()
        }
    }

    /**
     * Resets the paint tool state. Currently no specific reset behavior needed
     * as the mask bitmap and brush settings are managed externally.
     */
    override fun reset() {
        // Reset history
        historyHandler?.reset()
        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Callback from the line smoother when a smoothed point should be drawn.
     * This creates the actual brush strokes on the mask bitmap.
     *
     * @param ex X coordinate of the point to draw
     * @param ey Y coordinate of the point to draw
     * @param angleDirection Direction angle for brush orientation
     * @param totalDrawCount Total number of points drawn in current stroke
     * @param isLastPoint Whether this is the final point in the stroke
     */
    override fun onDrawPoint(
        ex: Float,
        ey: Float,
        angleDirection: Float,
        totalDrawCount: Int,
        isLastPoint: Boolean
    ) {
        // Draw the brush stroke point onto the mask bitmap
        engine.draw(
            ex,
            ey,
            angleDirection,
            paintCanvas,
            finalBrush,
            totalDrawCount
        )

        // Request screen update to show the drawing progress
        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Saves the current state for undo/redo functionality.
     */
    protected open fun saveState() {
        initialMaskBitmapState?.let { initialBitmap ->
            historyHandler?.addState(State(initialBitmap))
        }

        prepareInitialState()
    }

    protected open fun prepareInitialState() {
        initialMaskBitmapState =
            maskBitmap.copy(maskBitmap.config ?: Bitmap.Config.ARGB_8888, true)
    }

    /**
     * Performs undo operation to restore previous mask state.
     */
    override fun undo() {
        historyHandler?.undo()?.let {
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Performs redo operation to restore next mask state.
     */
    override fun redo() {
        historyHandler?.redo()?.let {
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Indicates this tool handles its own history management.
     */
    override fun doesHandleHistory(): Boolean {
        return true
    }

    /**
     * History state implementation for mask bitmap operations.
     * Stores mask bitmap states for undo/redo functionality.
     */
    protected open inner class State(val initialBitmap: Bitmap) : HistoryState {
        private val clonedBitmap =
            maskBitmap.copy(maskBitmap.config ?: Bitmap.Config.ARGB_8888, true)

        override fun undo() {
            restoreState(initialBitmap)
        }

        override fun redo() {
            restoreState(clonedBitmap)
        }

        /**
         * Restores the mask bitmap to a specific state.
         *
         * @param targetBitmap The bitmap state to restore to
         */
        protected open fun restoreState(targetBitmap: Bitmap) {
            // Copy the target bitmap content to the current mask bitmap
            val canvas = Canvas(maskBitmap)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            canvas.drawBitmap(targetBitmap, 0f, 0f, null)
        }
    }
}
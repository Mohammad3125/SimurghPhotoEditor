package ir.baboomeh.photolib.components.paint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import ir.baboomeh.photolib.components.paint.paintview.PaintLayer
import ir.baboomeh.photolib.utils.gesture.TouchData
import ir.baboomeh.photolib.utils.history.HistoryHandler
import ir.baboomeh.photolib.utils.matrix.MananMatrix

/**
 * Abstract base class for all painting tools in the photo editing library.
 *
 * This class defines the common interface and lifecycle for painting tools such as:
 * - Brush painting tools
 * - Selection tools (lasso, pen tool, etc.)
 * - Transform tools
 * - Masking tools
 * - Color picker tools
 *
 * Key Responsibilities:
 * - Handle touch events and gestures
 * - Manage drawing operations on canvas
 * - Provide history/undo functionality
 * - Handle layer and size changes
 * - Communicate with the parent paint view through messages
 *
 * Lifecycle:
 * 1. [initialize] - Set up the tool with context and matrices
 * 2. Touch events: [onMoveBegin] -> [onMove] -> [onMoveEnded]
 * 3. [draw] - Render tool-specific graphics
 * 4. [release] - Clean up resources
 *
 * @see ir.baboomeh.photolib.components.paint.painters.brushpaint.BrushPainter
 * @see ir.baboomeh.photolib.components.paint.painters.transform.TransformTool
 * @see ir.baboomeh.photolib.components.paint.painters.selection.LassoTool
 */
abstract class Painter {

    /**
     * Message channel for communicating with the parent paint view.
     * Used to request invalidation, save history, etc.
     */
    private var messageListener: MessageChannel? = null

    /**
     * Optional history handler for undo/redo functionality.
     * Set this if the tool needs to manage its own history.
     */
    open var historyHandler: HistoryHandler? = null

    /**
     * Indicates whether the painter has been properly initialized.
     * Tools should check this before performing operations.
     */
    var isInitialized = false
        private set

    /**
     * Sets the message channel for communicating with the parent view.
     *
     * @param listener MessageChannel implementation
     */
    fun setOnMessageListener(listener: MessageChannel) {
        messageListener = listener
    }

    /**
     * Sends a message to the parent paint view.
     * Common messages include INVALIDATE, SAVE_HISTORY, CACHE_LAYERS.
     *
     * @param message The message to send
     */
    protected fun sendMessage(message: PainterMessage) {
        messageListener?.onSendMessage(message)
    }

    /**
     * Initializes the painter with necessary context and transformation matrices.
     * This method must be called before using the painter.
     *
     * @param context Android context for accessing resources
     * @param transformationMatrix Matrix for canvas transformations (zoom, pan, rotate)
     * @param fitInsideMatrix Matrix for fitting content inside view bounds
     * @param layerBounds Bounds of the current layer being painted on
     * @param clipBounds Clipping bounds for the paint area
     */
    open fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        isInitialized = true
    }

    /**
     * Called when user starts to move their finger on screen.
     * Use this to initialize drawing operations, set starting points, etc.
     *
     * @param touchData Touch information including position, pressure, and timing
     */
    abstract fun onMoveBegin(touchData: TouchData)

    /**
     * Called continuously while the user moves their finger on screen.
     * This is where most calculations for drawing operations occur.
     *
     * @param touchData Touch information including current position and delta movement
     */
    abstract fun onMove(touchData: TouchData)

    /**
     * Called when user lifts their finger from the screen.
     * Use this to finalize drawing operations, apply effects, save history, etc.
     *
     * @param touchData Final touch information
     */
    abstract fun onMoveEnded(touchData: TouchData)

    /**
     * Draws any visual content that this painter needs to render.
     * This may include:
     * - Tool-specific overlays (selection outlines, handles, etc.)
     * - Preview effects
     * - Guide lines or indicators
     *
     * Note: This is called on every frame, so keep operations lightweight.
     *
     * @param canvas Canvas to draw on (already transformed by parent view)
     */
    abstract fun draw(canvas: Canvas)

    /**
     * Resets the painter to its initial state.
     * This should clear any temporary data, selections, or operations in progress.
     */
    open fun resetPaint() {
        // Default implementation does nothing
    }

    /**
     * Called when the paint view's size changes.
     * Painters should update their internal bounds and coordinates accordingly.
     *
     * @param newBounds New bounds of the paint area
     * @param clipBounds New clipping bounds
     * @param changeMatrix Matrix representing the size change transformation
     */
    open fun onSizeChanged(newBounds: RectF, clipBounds: Rect, changeMatrix: Matrix) {
        // Default implementation does nothing
    }

    /**
     * Called when the active layer changes.
     * Painters that work with specific layers should update their references.
     *
     * @param layer The new active layer, or null if no layer is selected
     */
    open fun onLayerChanged(layer: PaintLayer?) {
        // Default implementation does nothing
    }

    /**
     * Indicates whether this painter manages its own history.
     * If true, the parent view won't handle undo/redo for this painter.
     *
     * @return true if painter handles its own history, false otherwise
     */
    open fun doesHandleHistory(): Boolean {
        return false
    }

    /**
     * Indicates whether this painter should receive gesture events.
     * If false, gestures will be handled by the parent view (zoom, pan, etc.).
     *
     * @return true if painter should receive gestures, false otherwise
     */
    open fun doesTakeGestures(): Boolean {
        return false
    }

    /**
     * Called when a transformation (zoom, pan, rotate) begins.
     * Painters can use this to prepare for transformation or pause operations.
     */
    open fun onTransformBegin() {
        // Default implementation does nothing
    }

    /**
     * Called during transformation with the transformation matrix.
     * Painters that need to respond to transformations should override this.
     *
     * @param transformMatrix Matrix representing the transformation
     */
    open fun onTransformed(transformMatrix: Matrix) {
        // Default implementation does nothing
    }

    /**
     * Called when transformation ends.
     * Painters can use this to finalize transformation-related operations.
     */
    open fun onTransformEnded() {
        // Default implementation does nothing
    }

    /**
     * Called when a reference layer bitmap is created.
     * This provides painters with a composite bitmap of all layers for operations
     * that need to analyze the full image (like color picking).
     *
     * @param reference Composite bitmap of all layers
     */
    open fun onReferenceLayerCreated(reference: Bitmap) {
        // Default implementation does nothing
    }

    /**
     * Undoes the last operation.
     * Only called if [doesHandleHistory] returns true.
     */
    open fun undo() {
        // Default implementation does nothing
    }

    /**
     * Redoes the last undone operation.
     * Only called if [doesHandleHistory] returns true.
     */
    open fun redo() {
        // Default implementation does nothing
    }

    /**
     * Releases resources and performs cleanup.
     * Called when the painter is no longer needed.
     */
    open fun release() {
        isInitialized = false
    }

    /**
     * Indicates whether this painter needs touch slope detection.
     * Touch slope helps prevent accidental gestures and improves precision.
     *
     * @return true if touch slope detection is needed, false otherwise
     */
    open fun doesNeedTouchSlope(): Boolean {
        return true
    }

    /**
     * Interface for receiving messages from painters.
     * Implemented by parent paint views to handle painter requests.
     */
    interface MessageChannel {
        /**
         * Called when a painter sends a message.
         *
         * @param message The message sent by the painter
         */
        fun onSendMessage(message: PainterMessage)
    }

    /**
     * Messages that painters can send to the parent view.
     */
    enum class PainterMessage {
        /** Request the parent view to invalidate and redraw */
        INVALIDATE,

        /** Request the parent view to save the current state to history */
        SAVE_HISTORY,

        /** Request the parent view to cache the current layers */
        CACHE_LAYERS
    }
}

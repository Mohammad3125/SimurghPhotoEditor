package ir.simurgh.photolib.components.paint.painters.masking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import ir.simurgh.photolib.components.paint.painter_view.PaintLayer
import ir.simurgh.photolib.components.paint.painters.painter.MessageChannel
import ir.simurgh.photolib.components.paint.painters.painter.Painter
import ir.simurgh.photolib.components.paint.painters.painter.PainterMessage
import ir.simurgh.photolib.components.paint.painters.selection.clippers.BitmapMaskClipper
import ir.simurgh.photolib.utils.gesture.TouchData
import ir.simurgh.photolib.utils.history.HistoryState
import ir.simurgh.photolib.utils.history.handlers.StackFullRestoreHistoryHandler
import ir.simurgh.photolib.utils.matrix.SimurghMatrix

/**
 * A comprehensive mask editing tool that provides real-time mask preview and editing capabilities.
 * 
 * This tool creates an interactive masking workflow where users can:
 * 
 * **Mask Creation & Editing:**
 * - Create masks using any painter tool (brushes, shapes, etc.)
 * - Real-time visual feedback with customizable mask overlay
 * - Non-destructive editing with live preview
 * - Support for additive and subtractive mask operations
 * 
 * **Visual Feedback:**
 * - Colored overlay showing mask areas with adjustable opacity
 * - Customizable mask visualization colors
 * - Real-time preview of mask effects on the underlying image
 * - Smooth integration with various painting tools
 * 
 * **Mask Operations:**
 * - **Clip**: Apply mask to modify the target layer directly
 * - **Cut**: Extract masked region as a new bitmap while removing from original
 * - **Copy**: Create a new bitmap with only the masked region
 * - **Invert**: Reverse the mask to select opposite areas
 * 
 * **History Management:**
 * - Full undo/redo support for all mask operations
 * - State preservation during complex editing sessions
 * - Automatic history snapshots after each operation
 * 
 * **Integration Features:**
 * - Works with any Painter tool for mask creation
 * - Automatic tool initialization and configuration
 * - Message passing for coordinated tool behavior
 * - Layer-aware operations with proper state management
 * 
 * The tool is designed for advanced photo editing workflows where precise
 * selection and masking are essential, such as object removal, selective
 * adjustments, or complex compositing operations.
 * 
 * **Usage Example:**
 * ```kotlin
 * val maskTool = MaskModifierTool(BitmapMaskClipper())
 * maskTool.maskTool = MyBrushTool(context) // Set the painting tool
 * maskTool.maskColor = Color.RED
 * maskTool.maskOpacity = 128
 * 
 * // After mask creation:
 * val maskedBitmap = maskTool.copy() // Get masked content
 * maskTool.clip() // Apply mask to layer
 * ```
 * 
 * @param clipper The bitmap mask clipper used for applying mask operations
 */
open class MaskModifierTool(var clipper: BitmapMaskClipper) : Painter(), MessageChannel {

    /**
     * Paint used for rendering the mask overlay with color filtering.
     */
    protected val maskPaint by lazy {
        Paint().apply {
            isFilterBitmap = true
            LightingColorFilter(Color.BLACK, Color.BLACK)
        }
    }

    /**
     * Paint used for bitmap operations during mask application.
     */
    protected val bitmapPaint by lazy {
        Paint()
    }

    /**
     * The painter tool used for creating and editing the mask.
     * When set, the tool is automatically initialized with current settings.
     */
    open var maskTool: Painter? = null
        set(value) {
            field = value
            value?.let { p ->
                if (isInitialized) {
                    initializeTool(p)
                }
            }
        }

    /**
     * Canvas used for mask manipulation operations.
     */
    protected  val canvasOperation by lazy {
        Canvas()
    }

    /**
     * Backup of the initial mask layer state for history management.
     */
    protected var initialMaskLayerState: PaintLayer? = null

    /**
     * The mask layer containing the current mask bitmap.
     * Setting this creates a backup for undo operations.
     */
    protected  var maskLayer: PaintLayer? = null
        set(value) {
            field = value
            initialMaskLayerState = value?.clone(true)
        }

    /**
     * Context and matrix storage for tool initialization
     */
    protected  lateinit var context: Context
    protected  lateinit var transformationMatrix: SimurghMatrix
    protected  lateinit var fitInsideMatrix: SimurghMatrix
    protected  val boundsRect by lazy {
        Rect()
    }
    protected  val clipBounds by lazy {
        Rect()
    }

    /**
     * The currently selected layer that mask operations will be applied to.
     */
    protected  var selectedLayer: PaintLayer? = null

    /**
     * Color used for the mask overlay visualization.
     * Changes are immediately reflected in the display.
     */
    open var maskColor = Color.BLACK
        set(value) {
            field = value
            maskPaint.colorFilter = LightingColorFilter(field, field)
            sendMessage(PainterMessage.INVALIDATE)
        }

    /**
     * Opacity of the mask overlay (0-255).
     * Controls how prominently the mask is displayed over the image.
     */
    open var maskOpacity = 255
        set(value) {
            field = value
            maskPaint.alpha = field
            sendMessage(PainterMessage.INVALIDATE)
        }

    init {
        historyHandler = StackFullRestoreHistoryHandler()
    }

    /**
     * Initializes the mask tool with view dimensions and creates the mask layer.
     */
    override fun initialize(
        context: Context,
        transformationMatrix: SimurghMatrix,
        fitInsideMatrix: SimurghMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)
        this.context = context
        this.transformationMatrix = transformationMatrix
        this.fitInsideMatrix = fitInsideMatrix
        boundsRect.set(layerBounds)
        this.clipBounds.set(clipBounds)

        maskLayer = PaintLayer(
            createBitmap(layerBounds.width(), layerBounds.height())
        )

        maskTool?.let { initializeTool(it) }
    }

    /**
     * Initializes the mask painting tool with current settings.
     *
     * @param tool The painter tool to initialize for mask creation
     */
    protected open fun initializeTool(tool: Painter) {
        tool.initialize(context, transformationMatrix, fitInsideMatrix, boundsRect, clipBounds)
        tool.onLayerChanged(maskLayer)
        tool.setOnMessageListener(this)
    }


    /**
     * Delegates touch begin events to the mask tool.
     */
    override fun onMoveBegin(touchData: TouchData) {
        maskTool?.onMoveBegin(touchData)
    }

    /**
     * Delegates touch move events to the mask tool.
     */
    override fun onMove(touchData: TouchData) {
        maskTool?.onMove(touchData)
    }

    /**
     * Delegates touch end events to the mask tool and saves state for undo.
     */
    override fun onMoveEnded(touchData: TouchData) {
        maskTool?.onMoveEnded(touchData)
        saveState()
    }

    /**
     * Renders the mask overlay and delegates drawing to the mask tool.
     */
    override fun draw(canvas: Canvas) {
        maskLayer?.let { layer ->
            canvas.drawBitmap(layer.bitmap, 0f, 0f, maskPaint)
            maskTool?.draw(canvas)
        }
    }

    /**
     * Inverts the current mask, making masked areas unmasked and vice versa.
     * This is useful for selecting the opposite of the current selection.
     */
    open fun invertMaskLayer() {
        maskLayer?.let { layer ->
            val invert =
                layer.bitmap.copy(layer.bitmap.config ?: Bitmap.Config.ARGB_8888, true)
            invert.eraseColor(Color.BLACK)

            canvasOperation.setBitmap(invert)
            bitmapPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            canvasOperation.drawBitmap(layer.bitmap, 0f, 0f, bitmapPaint)
            bitmapPaint.xfermode = null

            layer.bitmap.recycle()
            layer.bitmap = invert
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Handles messages from the mask tool, typically for redraw requests.
     */
    override fun onSendMessage(message: PainterMessage) {
        sendMessage(message)
    }

    /**
     * Updates the selected layer reference when the active layer changes.
     */
    override fun onLayerChanged(layer: PaintLayer?) {
        selectedLayer = layer
    }

    /**
     * Returns the current mask bitmap.
     *
     * @return The mask bitmap
     * @throws IllegalStateException if no mask layer exists
     */
    open fun getMaskLayer(): Bitmap {
        if (maskLayer == null) {
            throw IllegalStateException("Mask layer isn't created yet")
        }
        return maskLayer!!.bitmap
    }

    /**
     * Clears the mask and resets the history.
     */
    override fun resetPaint() {
        maskLayer?.bitmap?.eraseColor(Color.TRANSPARENT)
        historyHandler!!.reset()
        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Applies the mask to clip the selected layer.
     * Areas outside the mask are removed from the layer.
     *
     * @param shouldSaveHistory Whether to save this operation in history
     */
    open fun clip(shouldSaveHistory: Boolean = true) {
        setClipper()
        clipper.clip()

        if (shouldSaveHistory) {
            sendMessage(PainterMessage.SAVE_HISTORY)
        }
    }

    /**
     * Cuts the masked region from the selected layer and returns it as a new bitmap.
     * The original layer is modified to remove the cut area.
     *
     * @param shouldSaveHistory Whether to save this operation in history
     * @return Bitmap containing the cut region, or null if operation fails
     */
    open fun cut(shouldSaveHistory: Boolean = true): Bitmap? {
        setClipper()
        val cutBitmap = clipper.cut()

        if (shouldSaveHistory) {
            sendMessage(PainterMessage.SAVE_HISTORY)
        }

        return cutBitmap
    }

    /**
     * Creates a copy of the masked region without modifying the original layer.
     *
     * @return Bitmap containing the masked region, or null if operation fails
     */
    open fun copy(): Bitmap? {
        setClipper()
        return clipper.copy()
    }

    /**
     * Saves the current state for undo/redo functionality.
     */
    protected open fun saveState() {
        initialMaskLayerState?.let { initialLayer ->
            historyHandler!!.addState(State(initialLayer))
        }

        initialMaskLayerState = maskLayer?.clone(true)
    }

    /**
     * Configures the clipper with current mask and layer references.
     */
    protected fun setClipper() {
        clipper.maskBitmap = maskLayer?.bitmap
        clipper.bitmap = selectedLayer?.bitmap
    }

    /**
     * Performs undo operation to restore previous mask state.
     */
    override fun undo() {
        historyHandler!!.undo()?.let {
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Performs redo operation to restore next mask state.
     */
    override fun redo() {
        historyHandler!!.redo()?.let {
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
     * Delegates touch slope requirement to the mask tool.
     */
    override fun doesNeedTouchSlope(): Boolean {
        return maskTool?.doesNeedTouchSlope() == true
    }

    /**
     * History state implementation for mask operations.
     * Stores mask layer states for undo/redo functionality.
     */
    protected open inner class State(val initialLayer: PaintLayer) : HistoryState {
        private val clonedLayer = maskLayer?.clone(true)

        override fun undo() {
            restoreState(initialLayer)
        }

        override fun redo() {
            restoreState(clonedLayer)
        }

        /**
         * Restores the mask layer to a specific state.
         *
         * @param targetLayer The layer state to restore to
         */
        protected open fun restoreState(targetLayer: PaintLayer?) {
            maskLayer = targetLayer?.clone(true)
            maskTool?.onLayerChanged(maskLayer)
        }
    }
}

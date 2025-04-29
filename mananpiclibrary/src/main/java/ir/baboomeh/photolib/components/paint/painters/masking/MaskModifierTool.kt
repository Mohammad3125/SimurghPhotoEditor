package ir.baboomeh.photolib.components.paint.painters.masking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import ir.baboomeh.photolib.components.paint.Painter
import ir.baboomeh.photolib.components.paint.painters.selection.clippers.BitmapMaskClipper
import ir.baboomeh.photolib.components.paint.paintview.PaintLayer
import ir.baboomeh.photolib.utils.MananMatrix
import ir.baboomeh.photolib.utils.gesture.TouchData
import java.util.Stack

class MaskModifierTool(var clipper: BitmapMaskClipper) : Painter(), Painter.MessageChannel {

    private val maskPaint by lazy {
        Paint().apply {
            isFilterBitmap = true
            LightingColorFilter(Color.BLACK, Color.BLACK)
        }
    }

    private val bitmapPaint by lazy {
        Paint()
    }

    var maskTool: Painter? = null
        set(value) {
            field = value
            value?.let { p ->
                if (isInitialized) {
                    initializeTool(p)
                }
            }
        }

    private val canvasOperation by lazy {
        Canvas()
    }

    private lateinit var maskLayer: PaintLayer

    private lateinit var context: Context
    private lateinit var transformationMatrix: MananMatrix
    private lateinit var fitInsideMatrix: MananMatrix
    private val boundsRect by lazy {
        Rect()
    }
    private val clipBounds by lazy {
        Rect()
    }

    private var selectedLayer: PaintLayer? = null

    protected val undoStack = Stack<Bitmap>()

    protected val redoStack = Stack<Bitmap>()

    var maskColor = Color.BLACK
        set(value) {
            field = value
            maskPaint.colorFilter = LightingColorFilter(field, field)
            sendMessage(PainterMessage.INVALIDATE)
        }

    var maskOpacity = 255
        set(value) {
            field = value
            maskPaint.alpha = field
            sendMessage(PainterMessage.INVALIDATE)
        }

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
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
            Bitmap.createBitmap(
                layerBounds.width(),
                layerBounds.height(),
                Bitmap.Config.ARGB_8888
            )
        )

        maskTool?.let { initializeTool(it) }
    }

    private fun initializeTool(tool: Painter) {
        tool.initialize(context, transformationMatrix, fitInsideMatrix, boundsRect, clipBounds)
        tool.onLayerChanged(maskLayer)
        tool.setOnMessageListener(this)
    }


    override fun onMoveBegin(touchData: TouchData) {
        maskTool?.onMoveBegin(touchData)
    }

    override fun onMove(touchData: TouchData) {
        maskTool?.onMove(touchData)
    }

    override fun onMoveEnded(touchData: TouchData) {
        maskTool?.onMoveEnded(touchData)
        saveState()
        // TODO: Fix undo
    }

    override fun draw(canvas: Canvas) {
        canvas.drawBitmap(maskLayer.bitmap, 0f, 0f, maskPaint)
        maskTool?.draw(canvas)
    }

    fun invertMaskLayer() {
        if (this::maskLayer.isInitialized) {
            val invert =
                maskLayer.bitmap.copy(maskLayer.bitmap.config ?: Bitmap.Config.ARGB_8888, true)
            invert.eraseColor(Color.BLACK)

            canvasOperation.setBitmap(invert)
            bitmapPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            canvasOperation.drawBitmap(maskLayer.bitmap, 0f, 0f, bitmapPaint)
            bitmapPaint.xfermode = null

            maskLayer.bitmap.recycle()
            maskLayer.bitmap = invert
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    override fun onSendMessage(message: PainterMessage) {
        sendMessage(message)
    }

    override fun onLayerChanged(layer: PaintLayer?) {
        selectedLayer = layer
    }

    fun getMaskLayer(): Bitmap {
        if (!this::maskLayer.isInitialized) {
            throw IllegalStateException("Mask layer isn't created yet")
        }
        return maskLayer.bitmap
    }

    fun getSelectedLayerBitmap(): Bitmap? {
        return selectedLayer?.bitmap
    }

    override fun resetPaint() {
        maskLayer.bitmap.eraseColor(Color.TRANSPARENT)
        redoStack.clear()
        undoStack.clear()
        sendMessage(PainterMessage.INVALIDATE)
    }

    fun clip(shouldSaveHistory: Boolean = true) {
        setClipper()
        clipper.clip()

        if (shouldSaveHistory) {
            sendMessage(PainterMessage.SAVE_HISTORY)
        }
    }

    fun cut(shouldSaveHistory: Boolean = true): Bitmap? {
        setClipper()
        val cutBitmap = clipper.cut()

        if (shouldSaveHistory) {
            sendMessage(PainterMessage.SAVE_HISTORY)
        }

        return cutBitmap
    }

    fun copy(): Bitmap? {
        setClipper()
        return clipper.copy()
    }

    private fun saveState() {
        redoStack.clear()

        if (undoStack.isEmpty()) {
            undoStack.push(
                Bitmap.createBitmap(
                    boundsRect.width().toInt(),
                    boundsRect.height().toInt(),
                    Bitmap.Config.ARGB_8888
                )
            )
        }

        maskLayer.bitmap.let { layer ->
            undoStack.push(layer.copy(layer.config ?: Bitmap.Config.ARGB_8888, true))
        }

    }

    private fun setClipper() {
        clipper.maskBitmap = maskLayer.bitmap
        clipper.bitmap = selectedLayer?.bitmap
    }

    override fun undo() {
        swapStacks(undoStack, redoStack)
    }

    override fun redo() {
        swapStacks(redoStack, undoStack)
    }

    private fun swapStacks(popStack: Stack<Bitmap>, pushStack: Stack<Bitmap>) {
        if (popStack.isNotEmpty()) {
            val poppedState = popStack.pop()

            if (popStack.isNotEmpty() && pushStack.isEmpty()) {
                val newPopped = popStack.pop()
                restoreBitmapState(newPopped)
                pushStack.push(poppedState)
                pushStack.push(newPopped)
            } else {
                restoreBitmapState(poppedState)
                pushStack.push(poppedState)
            }

            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    override fun doesHandleHistory(): Boolean {
        return true
    }

    override fun doesNeedTouchSlope(): Boolean {
        return maskTool?.doesNeedTouchSlope() == true
    }

    private fun restoreBitmapState(bitmap: Bitmap) {
        maskLayer.bitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        maskTool?.onLayerChanged(maskLayer)
    }

}
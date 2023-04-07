package ir.manan.mananpic.components.paint.painters.masking

import android.content.Context
import android.graphics.*
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.components.paint.painters.selection.clippers.BitmapMaskClipper
import ir.manan.mananpic.utils.MananMatrix
import java.util.*

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

    @MaskTool
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
    private lateinit var bounds: RectF

    private var isInitialized = false

    private var selectedLayer: PaintLayer? = null

    protected val undoStack = Stack<Bitmap>()

    protected val redoStack = Stack<Bitmap>()

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        bounds: RectF
    ) {

        this.context = context
        this.transformationMatrix = transformationMatrix
        this.fitInsideMatrix = fitInsideMatrix
        this.bounds = bounds

        maskLayer = PaintLayer(
            Bitmap.createBitmap(
                bounds.width().toInt(),
                bounds.height().toInt(),
                Bitmap.Config.ARGB_8888
            ), Matrix(), false, 1f
        )

        maskTool?.let { initializeTool(it) }

        isInitialized = true
    }

    private fun initializeTool(tool: Painter) {
        tool.initialize(context, transformationMatrix, fitInsideMatrix, bounds)
        tool.onLayerChanged(maskLayer)
        tool.setOnMessageListener(this)
    }


    override fun onMoveBegin(initialX: Float, initialY: Float) {
        maskTool?.onMoveBegin(initialX, initialY)
    }

    override fun onMove(ex: Float, ey: Float, dx: Float, dy: Float) {
        maskTool?.onMove(ex, ey, dx, dy)
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        maskTool?.onMoveEnded(lastX, lastY)
        saveState()
    }

    override fun draw(canvas: Canvas) {
        canvas.drawBitmap(maskLayer.bitmap, 0f, 0f, maskPaint)
        maskTool?.draw(canvas)
    }

    fun invertMaskLayer() {
        if (this::maskLayer.isInitialized) {
            val invert = maskLayer.bitmap.copy(maskLayer.bitmap.config, true)
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
        sendMessage(PainterMessage.INVALIDATE)
    }

    fun clip() {
        setClipper()
        clipper.clip()
        sendMessage(PainterMessage.SAVE_HISTORY)
    }

    fun cut(): Bitmap? {
        setClipper()
        val cutBitmap = clipper.cut()
        sendMessage(PainterMessage.SAVE_HISTORY)
        return cutBitmap
    }

    fun copy(): Bitmap? {
        setClipper()
        val copied = clipper.copy()
        return copied
    }

    private fun saveState() {
        redoStack.clear()

        if (undoStack.isEmpty()) {
            undoStack.push(
                Bitmap.createBitmap(
                    bounds.width().toInt(),
                    bounds.height().toInt(),
                    Bitmap.Config.ARGB_8888
                )
            )
        }

        maskLayer.bitmap.let { layer ->
            undoStack.push(layer.copy(layer.config, true))
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

    private fun restoreBitmapState(bitmap: Bitmap) {
        maskLayer.bitmap = bitmap.copy(bitmap.config, true)
    }

    fun setMaskOpacity(opacity: Int) {
        maskPaint.alpha = opacity
        sendMessage(PainterMessage.INVALIDATE)
    }

    fun setMaskColor(color: Int) {
        maskPaint.colorFilter = LightingColorFilter(color, color)
        sendMessage(PainterMessage.INVALIDATE)
    }

}
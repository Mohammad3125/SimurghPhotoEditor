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
import androidx.core.graphics.createBitmap
import ir.baboomeh.photolib.components.paint.Painter
import ir.baboomeh.photolib.components.paint.painters.selection.clippers.BitmapMaskClipper
import ir.baboomeh.photolib.components.paint.paintview.PaintLayer
import ir.baboomeh.photolib.utils.gesture.TouchData
import ir.baboomeh.photolib.utils.history.HistoryState
import ir.baboomeh.photolib.utils.history.handlers.StackFullRestoreHistoryHandler
import ir.baboomeh.photolib.utils.matrix.MananMatrix

open class MaskModifierTool(var clipper: BitmapMaskClipper) : Painter(), Painter.MessageChannel {

    protected val maskPaint by lazy {
        Paint().apply {
            isFilterBitmap = true
            LightingColorFilter(Color.BLACK, Color.BLACK)
        }
    }

    protected val bitmapPaint by lazy {
        Paint()
    }

    open var maskTool: Painter? = null
        set(value) {
            field = value
            value?.let { p ->
                if (isInitialized) {
                    initializeTool(p)
                }
            }
        }

    protected  val canvasOperation by lazy {
        Canvas()
    }

    protected var initialMaskLayerState: PaintLayer? = null

    protected  var maskLayer: PaintLayer? = null
        set(value) {
            field = value
            initialMaskLayerState = value?.clone(true)
        }

    protected  lateinit var context: Context
    protected  lateinit var transformationMatrix: MananMatrix
    protected  lateinit var fitInsideMatrix: MananMatrix
    protected  val boundsRect by lazy {
        Rect()
    }
    protected  val clipBounds by lazy {
        Rect()
    }

    protected  var selectedLayer: PaintLayer? = null

    open var maskColor = Color.BLACK
        set(value) {
            field = value
            maskPaint.colorFilter = LightingColorFilter(field, field)
            sendMessage(PainterMessage.INVALIDATE)
        }

    open var maskOpacity = 255
        set(value) {
            field = value
            maskPaint.alpha = field
            sendMessage(PainterMessage.INVALIDATE)
        }

    init {
        historyHandler = StackFullRestoreHistoryHandler()
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
            createBitmap(layerBounds.width(), layerBounds.height())
        )

        maskTool?.let { initializeTool(it) }
    }

    protected open fun initializeTool(tool: Painter) {
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
    }

    override fun draw(canvas: Canvas) {
        maskLayer?.let { layer ->
            canvas.drawBitmap(layer.bitmap, 0f, 0f, maskPaint)
            maskTool?.draw(canvas)
        }
    }

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

    override fun onSendMessage(message: PainterMessage) {
        sendMessage(message)
    }

    override fun onLayerChanged(layer: PaintLayer?) {
        selectedLayer = layer
    }

    open fun getMaskLayer(): Bitmap {
        if (maskLayer == null) {
            throw IllegalStateException("Mask layer isn't created yet")
        }
        return maskLayer!!.bitmap
    }

    override fun resetPaint() {
        maskLayer?.bitmap?.eraseColor(Color.TRANSPARENT)
        historyHandler!!.reset()
        sendMessage(PainterMessage.INVALIDATE)
    }

    open fun clip(shouldSaveHistory: Boolean = true) {
        setClipper()
        clipper.clip()

        if (shouldSaveHistory) {
            sendMessage(PainterMessage.SAVE_HISTORY)
        }
    }

    open fun cut(shouldSaveHistory: Boolean = true): Bitmap? {
        setClipper()
        val cutBitmap = clipper.cut()

        if (shouldSaveHistory) {
            sendMessage(PainterMessage.SAVE_HISTORY)
        }

        return cutBitmap
    }

    open fun copy(): Bitmap? {
        setClipper()
        return clipper.copy()
    }

    protected open fun saveState() {
        initialMaskLayerState?.let { initialLayer ->
            historyHandler!!.addState(State(initialLayer))
        }

        initialMaskLayerState = maskLayer?.clone(true)
    }

    protected fun setClipper() {
        clipper.maskBitmap = maskLayer?.bitmap
        clipper.bitmap = selectedLayer?.bitmap
    }

    override fun undo() {
        historyHandler!!.undo()?.let {
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    override fun redo() {
        historyHandler!!.redo()?.let {
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    override fun doesHandleHistory(): Boolean {
        return true
    }

    override fun doesNeedTouchSlope(): Boolean {
        return maskTool?.doesNeedTouchSlope() == true
    }

    protected open inner class State(val initialLayer: PaintLayer) : HistoryState {
        private val clonedLayer = maskLayer?.clone(true)

        override fun undo() {
            restoreState(initialLayer)
        }

        override fun redo() {
            restoreState(clonedLayer)
        }

        protected open fun restoreState(targetLayer: PaintLayer?) {
            maskLayer = targetLayer?.clone(true)
            maskTool?.onLayerChanged(maskLayer)
        }
    }

}
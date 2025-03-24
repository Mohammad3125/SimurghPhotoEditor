package ir.manan.mananpic.components.paint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import ir.manan.mananpic.components.paint.paintview.PaintLayer
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.gesture.TouchData

abstract class Painter {
    private var messageListener: MessageChannel? = null

    var isInitialized = false
        private set

    fun setOnMessageListener(listener: MessageChannel) {
        messageListener = listener
    }

    protected fun sendMessage(message: PainterMessage) {
        messageListener?.onSendMessage(message)
    }

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
     * Called when user starts to move his/her finger on screen.
     * A selector might use it to draw something.
     * @param initialX Coordinate of current x.
     * @param initialY Coordinate of current y.
     */
    abstract fun onMoveBegin(touchData: TouchData)

    /**
     * Called when user is currently moving his/her finger on screen.
     * A selector might use it to draw something.
     * @param dx Delta x.
     * @param dy Delta y.
     * @param ex Exact location of current x.
     * @param ey Exact location of current y.
     */
    abstract fun onMove(touchData: TouchData)

    /**
     * Called when user raises his/her finger on screen.
     * A selector might use it to draw something.
     * @param lastX Exact location of last x user touched.
     * @param lastY Exact location of last y user touched.
     */
    abstract fun onMoveEnded(touchData: TouchData)

    /**
     * Draws any content that a selector might draw; for example [BrushSelector] draws circle indicating
     * interested areas in image.
     * @param canvas Canvas that selector draws content on.
     */
    abstract fun draw(canvas: Canvas)


    open fun resetPaint() {

    }


    open fun onSizeChanged(newBounds: RectF, clipBounds: Rect, changeMatrix: Matrix) {

    }


    open fun onLayerChanged(layer: PaintLayer?) {

    }

    open fun doesHandleHistory(): Boolean {
        return false
    }

    open fun doesTakeGestures(): Boolean {
        return false
    }

    open fun onTransformBegin() {

    }

    open fun onTransformed(transformMatrix: Matrix) {
    }

    open fun onTransformEnded() {

    }

    open fun onReferenceLayerCreated(reference: Bitmap) {

    }

    open fun undo() {

    }

    open fun redo() {

    }

    open fun release() {
        isInitialized = false
    }

    open fun doesNeedTouchSlope(): Boolean {
        return true
    }

    interface MessageChannel {
        fun onSendMessage(message: PainterMessage)
    }

    enum class PainterMessage {
        INVALIDATE,
        SAVE_HISTORY,
        CACHE_LAYERS
    }

}
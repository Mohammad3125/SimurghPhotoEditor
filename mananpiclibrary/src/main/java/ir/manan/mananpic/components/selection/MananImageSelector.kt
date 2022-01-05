package ir.manan.mananpic.components.selection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import ir.manan.mananpic.components.imageviews.MananGestureImageView
import ir.manan.mananpic.components.selection.selectors.BrushSelector
import ir.manan.mananpic.components.selection.selectors.Selector
import ir.manan.mananpic.utils.gesture.detectors.MoveDetector

class MananImageSelector(context: Context, attributeSet: AttributeSet?) :
    MananGestureImageView(context, attributeSet) {

    constructor(context: Context) : this(context, null)

    private var onCloseListener: OnCloseListener? = null
    private var onCloseCallBack: (() -> Unit)? = null

    var selector: Selector = BrushSelector()
        set(value) {
            field = value
            requestLayout()
        }

    init {
        moveDetector = MoveDetector(1, this)
    }

    override fun onImageLaidOut() {
        super.onImageLaidOut()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Initialize the selector.
        selector.initialize(
            this,
            getImageBitmap(),
            boundsRectangle,
        )
    }

    private fun getImageBitmap(): Bitmap? {
        if (drawable != null)
            return Bitmap.createScaledBitmap(
                (drawable as BitmapDrawable).bitmap,
                bitmapWidth.toInt(),
                bitmapHeight.toInt(),
                true
            )
        return null
    }

    override fun onMoveBegin(initialX: Float, initialY: Float): Boolean {
        selector.onMoveBegin(initialX, initialY)
        return true
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float): Boolean {
        selector.onMove(dx, dy, ex, ey)
        return true
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        super.onMoveEnded(lastX, lastY)
        selector.onMoveEnded(lastX, lastY)
        if (selector.isClosed()) callCloseListeners()
    }

    fun select(): Bitmap? {
        if (drawable != null)
            return selector.select(drawable)

        return null
    }

    fun resetSelection() {
        selector.resetSelection()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        selector.draw(canvas)
    }

    fun setOnCloseListener(listener: OnCloseListener) {
        onCloseListener = listener
    }

    fun setOnCloseListener(listener: () -> Unit) {
        onCloseCallBack = listener
    }

    private fun callCloseListeners() {
        onCloseCallBack?.invoke()
        onCloseListener?.onClose()
    }

    interface OnCloseListener {
        fun onClose()
    }
}
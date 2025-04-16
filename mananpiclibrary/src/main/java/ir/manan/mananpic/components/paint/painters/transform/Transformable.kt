package ir.manan.mananpic.components.paint.painters.transform

import android.graphics.Canvas
import android.graphics.RectF

abstract class Transformable {

    var onInvalidateListener: OnInvalidate? = null

    abstract fun getBounds(bounds: RectF)

    abstract fun draw(canvas: Canvas)

    interface OnInvalidate {
        fun onInvalidate()

        fun onBoundsChange()
    }

    fun invalidate() {
        onInvalidateListener?.onInvalidate()
    }

    fun notifyBoundsChanged() {
        onInvalidateListener?.onBoundsChange()
    }

    abstract fun clone(): Transformable
}
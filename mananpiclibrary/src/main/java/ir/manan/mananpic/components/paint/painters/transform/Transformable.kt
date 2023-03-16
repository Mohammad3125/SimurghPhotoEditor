package ir.manan.mananpic.components.paint.painters.transform

import android.graphics.Canvas
import android.graphics.RectF

abstract class Transformable {

    var onInvalidateListener: OnInvalidate? = null

    abstract fun getBounds(bounds: RectF)

    abstract fun draw(canvas: Canvas)

    interface OnInvalidate {
        fun invalidate()

        fun indicateBoundsChange()
    }

    fun invalidate() {
        onInvalidateListener?.invalidate()
    }

    fun indicateBoundsChange() {
        onInvalidateListener?.indicateBoundsChange()
    }

    abstract fun clone() : Transformable
}
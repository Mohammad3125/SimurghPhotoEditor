package ir.manan.mananpic.components.paint.painters.selection.clippers

import android.graphics.Bitmap
import android.graphics.RectF

abstract class Clipper(
    var bitmap: Bitmap? = null,
) {

    constructor() : this(null)

    abstract fun clip()

    abstract fun copy(): Bitmap?

    abstract fun cut(): Bitmap?

    abstract fun getClippingBounds(rect: RectF)
}
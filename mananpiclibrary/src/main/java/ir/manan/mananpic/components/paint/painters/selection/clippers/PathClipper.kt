package ir.manan.mananpic.components.paint.painters.selection.clippers

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.RectF

abstract class PathClipper(
    var path: Path? = null,
    var bitmap: Bitmap? = null,
    var isInverse: Boolean = false
) {

    constructor() : this(null, null, false)

    abstract fun clip()

    abstract fun copy(): Bitmap?

    abstract fun cut(): Bitmap?

    abstract fun getClippingBounds(rect: RectF)
}
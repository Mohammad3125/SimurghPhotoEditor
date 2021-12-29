package ir.manan.mananpic.properties

import android.graphics.Bitmap

interface Bitmapable {
    /**
     * Converts content of implementor to bitmap.
     * @param config Bitmap config for output bitmap.
     */
    fun toBitmap(config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap
}
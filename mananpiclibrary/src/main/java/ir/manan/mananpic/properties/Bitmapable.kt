package ir.manan.mananpic.properties

import android.graphics.Bitmap

interface Bitmapable {
    /**
     * Converts content of implementor to bitmap.
     * @param config Bitmap config for output bitmap.
     */
    fun toBitmap(
        config: Bitmap.Config = Bitmap.Config.ARGB_8888,
        ignoreAxisScale: Boolean = true
    ): Bitmap

    /**
     * Converts content to bitmap.
     * @param width Desired width for bitmap.
     * @param height Desired height for bitmap.
     * @param config Bitmap configuration.
     */
    fun toBitmap(
        width: Int,
        height: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888,
        ignoreAxisScale: Boolean = true
    ): Bitmap
}
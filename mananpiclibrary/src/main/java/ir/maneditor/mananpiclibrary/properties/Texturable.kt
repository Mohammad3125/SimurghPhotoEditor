package ir.maneditor.mananpiclibrary.properties

import android.graphics.Bitmap
import android.graphics.Shader

/**
 * This interface makes a view texturable.
 */
interface Texturable {
    /**
     * This method makes a view texturable.
     * @param bitmap The texture that is going to be applied to the view.
     * @param opacity The opacity of the texture that is going to be applied. It should be in range of [0f to 1f]
     */
    fun applyTexture(bitmap: Bitmap, opacity: Float = 1f)


    /**
     * This method make a view texturable with TileMode option on the provided bitmap.
     * @param bitmap The texture that is going to be applied to the view.
     * @param tileMode The bitmap mode [Shader.TileMode]
     * @param opacity The opacity of the texture that is going to be applied. It should be in range of [0f to 1f]
     */
    fun applyTexture(bitmap: Bitmap, tileMode: Shader.TileMode, opacity: Float = 1f)

    fun removeTexture()

}
package ir.manan.mananpic.components.paint.painters.brushpaint

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.annotation.ColorInt

abstract class Brush {
    open var size: Float = 1f

    @ColorInt
    open var color: Int = Color.BLACK

    open var opacity: Float = 1f

    open var opacityJitter: Float = 0f

    open var opacityVariance: Float = 0f

    open var spacing: Float = 0.1f

    open var scatter: Float = 0f

    open var angle = 0f

    open var angleJitter: Float = 0f

    open var sizeJitter: Float = 0f

    open var sizeVariance: Float = 0f

    open var squish = 0f

    open var hueJitter = 0

    open var smoothness: Float = 0.5f

    open var alphaBlend: Boolean = false

    open var texture: Bitmap? = null

    open var textureScale = 1f

    open var hueFlow : Float = 0f

    open var hueDistance : Int = 0


    abstract fun draw(canvas: Canvas,opacity: Int)


}
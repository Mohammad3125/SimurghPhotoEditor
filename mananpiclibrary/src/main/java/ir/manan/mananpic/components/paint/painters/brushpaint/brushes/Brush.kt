package ir.manan.mananpic.components.paint.painters.brushpaint.brushes

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.annotation.ColorInt

abstract class Brush {
    open var size: Int = 1

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

    open var sizeVariance: Float = 0.5f

    open var sizeVarianceSpeed : Float = 0.1f

    open var squish = 0f

    open var hueJitter = 0

    open var smoothness: Float = 0f

    open var alphaBlend: Boolean = false

    open var hueFlow: Float = 0f

    open var hueDistance: Int = 0

    open var startTaperSpeed = 0f

    open var startTaperSize = 1f

    var spacedWidth: Float = 0.0f
        get() = size * spacing
    private set


    internal abstract var brushBlending: PorterDuff.Mode
    abstract fun draw(canvas: Canvas, opacity: Int)

}
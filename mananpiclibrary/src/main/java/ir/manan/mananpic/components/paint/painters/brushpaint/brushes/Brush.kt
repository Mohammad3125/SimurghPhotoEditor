package ir.manan.mananpic.components.paint.painters.brushpaint.brushes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import androidx.annotation.ColorInt

abstract class Brush {
    open var size: Int = 1

    @ColorInt
    open var color: Int = Color.BLACK

    open var opacity: Float = 1f

    open var opacityJitter: Float = 0f

    open var opacityVariance: Float = 0f

    open var opacityVarianceSpeed: Float = 0.6f

    open var opacityVarianceEasing: Float = 0.1f

    open var sizePressureSensitivity: Float = 0.6f

    open var minimumPressureSize: Float = 0.3f

    open var maximumPressureSize: Float = 1f

    open var isSizePressureSensitive = true

    open var opacityPressureSensitivity: Float = 0.5f

    open var minimumPressureOpacity: Float = 0f

    open var maximumPressureOpacity: Float = 1f

    open var isOpacityPressureSensitive = false

    open var spacing: Float = 0.1f

    open var scatter: Float = 0f

    open var angle = 0f

    open var angleJitter: Float = 0f

    open var sizeJitter: Float = 0f

    open var sizeVariance: Float = 1f

    open var sizeVarianceSensitivity: Float = 0.1f

    open var sizeVarianceEasing: Float = 0.05f

    open var squish: Float = 0f

    open var hueJitter = 0

    open var smoothness: Float = 0f

    open var alphaBlend: Boolean = false

    open var autoRotate = false

    open var hueFlow: Float = 0f

    open var hueDistance: Int = 0

    open var startTaperSpeed = 0.03f

    open var startTaperSize: Float = 1f

    var spacedWidth: Float = 0.0f
        get() = size * spacing
        private set

    var texture: Bitmap? = null

    var textureTransformation: Matrix? = null

    internal abstract var brushBlending: PorterDuff.Mode
    abstract fun draw(canvas: Canvas, opacity: Int)

}
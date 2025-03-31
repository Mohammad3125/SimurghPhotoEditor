package ir.manan.mananpic.components.paint.painters.transform

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.annotation.ColorInt
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import ir.manan.mananpic.components.shapes.MananShape
import ir.manan.mananpic.properties.Bitmapable
import ir.manan.mananpic.properties.Blendable
import ir.manan.mananpic.properties.Colorable
import ir.manan.mananpic.properties.Gradientable
import ir.manan.mananpic.properties.Opacityable
import ir.manan.mananpic.properties.Shadowable
import ir.manan.mananpic.properties.StrokeCapable
import ir.manan.mananpic.properties.Texturable
import ir.manan.mananpic.utils.MananMatrix
import kotlin.math.min
import kotlin.math.roundToInt

class ShapePainter(shape: MananShape, var shapeWidth: Int, var shapeHeight: Int) : Transformable(),
    Bitmapable, StrokeCapable, Colorable,
    Texturable, Gradientable,
    Shadowable, Blendable, Opacityable {


    var shape = shape
        set(value) {
            field = value
            strokeShape = field.clone()
            invalidate()
        }

    private var shadowRadius = 0f
    private var shadowDx = 0f
    private var shadowDy = 0f
    private var shadowColor = Color.YELLOW

    private var opacityHolder: Int = 255

    private var strokeShape = shape.clone()

    private var rawWidth = 0f
    private var rawHeight = 0f

    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var paintShader: Shader? = null

    private var shaderRotationHolder = 0f

    var shapeColor = Color.BLACK
        set(value) {
            field = value
            shapePaint.color = value
            invalidate()
        }

    private var strokeSize = 0f

    private var strokeColor = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    private val shaderMatrix = MananMatrix()

    private var blendMode: PorterDuff.Mode = PorterDuff.Mode.SRC

    private var gradientColors: IntArray? = null

    private var gradientPositions: FloatArray? = null

    private var pivotX = 0f
    private var pivotY = 0f

    private var currentTexture: Bitmap? = null

    override fun changeColor(color: Int) {
        shapeColor = color
    }

    override fun getColor(): Int {
        return shapeColor
    }

    override fun clone(): ShapePainter {
        return ShapePainter(shape.clone(), shapeWidth, shapeHeight).also { painter ->
            painter.shapeColor = shapeColor
            painter.shapePaint.style = shapePaint.style
            painter.shapePaint.strokeWidth = shapePaint.strokeWidth
            painter.shapePaint.pathEffect = shapePaint.pathEffect

            painter.strokeSize = strokeSize
            painter.strokeColor = strokeColor
            painter.shaderRotationHolder = shaderRotationHolder
            getTexture()?.let { t ->
                painter.applyTexture(t)
            }
            painter.shaderMatrix.set(shaderMatrix)
            if (painter.shapePaint.shader != null) {
                painter.shapePaint.shader.setLocalMatrix(shaderMatrix)
            }
            painter.shapePaint.maskFilter = shapePaint.maskFilter
            if (blendMode != PorterDuff.Mode.SRC) {
                painter.setBlendMode(blendMode)
            }
            painter.setShadow(
                shadowRadius,
                shadowDx,
                shadowDy,
                shadowColor
            )
            if (gradientColors != null) {
                painter.gradientColors = gradientColors!!.clone()
            }
            if (gradientPositions != null) {
                painter.gradientPositions = gradientPositions!!.clone()
            }
            painter.setOpacity(getOpacity())
        }
    }


    override fun setStroke(strokeRadiusPx: Float, strokeColor: Int) {
        strokeSize = strokeRadiusPx
        this.strokeColor = strokeColor
        indicateBoundsChange()
    }

    override fun getStrokeColor(): Int {
        return strokeColor
    }

    override fun getStrokeWidth(): Float {
        return strokeSize
    }


    override fun applyTexture(bitmap: Bitmap) {
        applyTexture(bitmap, Shader.TileMode.MIRROR)
    }

    /**
     * Applies a texture to the text with default tileMode of [Shader.TileMode.REPEAT]
     * @param bitmap The bitmap texture that is going to be applied to the view.
     * @param tileMode The bitmap mode [Shader.TileMode]
     */
    override fun applyTexture(bitmap: Bitmap, tileMode: Shader.TileMode) {
        currentTexture = bitmap
        paintShader = BitmapShader(bitmap, tileMode, tileMode).apply {
            setLocalMatrix(shaderMatrix)
        }

        shapePaint.shader = BitmapShader(bitmap, tileMode, tileMode).apply {
            setLocalMatrix(shaderMatrix)
        }
        invalidate()
    }

    override fun getTexture(): Bitmap? {
        return currentTexture
    }


    override fun shiftColor(dx: Float, dy: Float) {
        shapePaint.shader?.run {
            shaderMatrix.postTranslate(dx, dy)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun scaleColor(scaleFactor: Float) {
        shapePaint.shader?.run {
            shaderMatrix.postScale(
                scaleFactor, scaleFactor, pivotX,
                pivotY
            )
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun rotateColor(rotation: Float) {
        shapePaint.shader?.run {
            shaderMatrix.postRotate(
                rotation - shaderRotationHolder,
                pivotX,
                pivotY
            )

            shaderRotationHolder = rotation
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun resetComplexColorMatrix() {
        shapePaint.shader?.run {
            shaderMatrix.reset()
            shaderRotationHolder = 0f
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun concatColorMatrix(matrix: Matrix) {
        shapePaint.shader?.run {
            shaderMatrix.postConcat(matrix)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun removeTexture() {
        currentTexture = null
        paintShader = null
        shapePaint.shader = null
        invalidate()
    }

    override fun toBitmap(config: Bitmap.Config): Bitmap? {
        return Bitmap.createBitmap(
            rawWidth.toInt(),
            rawHeight.toInt(),
            config
        ).also { bitmap ->
            draw(Canvas(bitmap))
        }
    }

    override fun toBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap? {
        val wStroke = rawWidth
        val hStroke = rawHeight

        val scale = min(width.toFloat() / wStroke, height.toFloat() / hStroke)

        val ws = (wStroke * scale)
        val hs = (hStroke * scale)

        val outputBitmap = Bitmap.createBitmap(width, height, config)

        val extraWidth = width - ws
        val extraHeight = height - hs

        Canvas(outputBitmap).run {
            translate(extraWidth * 0.5f, extraHeight * 0.5f)

            scale(ws / wStroke, hs / hStroke)

            draw(this)
        }

        return outputBitmap
    }

    override fun applyLinearGradient(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        colors: IntArray,
        position: FloatArray?,
        tileMode: Shader.TileMode
    ) {
        gradientColors = colors
        gradientPositions = position

        paintShader = LinearGradient(x0, y0, x1, y1, colors, position, tileMode).apply {
            setLocalMatrix(shaderMatrix)
        }

        shapePaint.shader =
            LinearGradient(x0, y0, x1, y1, colors, position, tileMode).apply {
                setLocalMatrix(shaderMatrix)
            }

        invalidate()
    }

    override fun applyRadialGradient(
        centerX: Float,
        centerY: Float,
        radius: Float,
        colors: IntArray,
        stops: FloatArray?,
        tileMode: Shader.TileMode
    ) {
        gradientColors = colors
        gradientPositions = stops

        paintShader = RadialGradient(
            centerX,
            centerY,
            radius,
            colors,
            stops,
            tileMode
        ).apply {
            setLocalMatrix(shaderMatrix)
        }

        shapePaint.shader =
            RadialGradient(
                centerX,
                centerY,
                radius,
                colors,
                stops,
                tileMode
            ).apply {
                setLocalMatrix(shaderMatrix)
            }
        invalidate()
    }

    override fun applySweepGradient(
        cx: Float,
        cy: Float,
        colors: IntArray,
        positions: FloatArray?
    ) {
        gradientColors = colors
        gradientPositions = positions

        paintShader = SweepGradient(cx, cy, colors, positions).apply {
            setLocalMatrix(shaderMatrix)
        }

        shapePaint.shader =
            SweepGradient(cx, cy, colors, positions).apply {
                setLocalMatrix(shaderMatrix)
            }
        invalidate()
    }

    override fun removeGradient() {
        paintShader = null
        shapePaint.shader = null
        gradientColors = null
        gradientPositions = null
        invalidate()
    }

    override fun isGradientApplied(): Boolean {
        return (shapePaint.shader != null && (shapePaint.shader is LinearGradient || shapePaint.shader is RadialGradient || shapePaint.shader is SweepGradient))
    }

    override fun getShadowDx(): Float {
        return shadowDx
    }

    override fun getShadowDy(): Float {
        return shadowDy
    }

    override fun getShadowRadius(): Float {
        return shadowRadius
    }

    override fun getShadowColor(): Int {
        return shadowColor
    }

    override fun setShadow(radius: Float, dx: Float, dy: Float, shadowColor: Int) {
        shadowRadius = radius
        shadowDx = dx
        shadowDy = dy
        this.shadowColor = shadowColor
        invalidate()
    }

    override fun clearShadow() {
        shapePaint.clearShadowLayer()
        shadowRadius = 0f
        shadowDx = 0f
        shadowDy = 0f
        shadowColor = Color.YELLOW
        invalidate()
    }

    override fun setBlendMode(blendMode: PorterDuff.Mode) {
        shapePaint.xfermode = PorterDuffXfermode(blendMode)
        this.blendMode = blendMode
        invalidate()
    }

    override fun clearBlend() {
        shapePaint.xfermode = null
        blendMode = PorterDuff.Mode.SRC
        invalidate()
    }

    override fun getBlendMode(): PorterDuff.Mode {
        return blendMode
    }

    override fun reportPositions(): FloatArray? {
        return gradientPositions
    }

    override fun reportColors(): IntArray? {
        return gradientColors
    }


    override fun getBounds(bounds: RectF) {
        rawWidth = shapeWidth.toFloat()
        rawHeight = shapeHeight.toFloat()

        shape.resize(rawWidth, rawHeight)

        strokeShape.resize(rawWidth + strokeSize, rawHeight + strokeSize)

        val finalS = strokeSize * 2f

        rawWidth += finalS
        rawHeight += finalS

        pivotX = rawWidth * 0.5f
        pivotY = rawHeight * 0.5f

        bounds.set(0f, 0f, rawWidth, rawHeight)
    }

    override fun draw(canvas: Canvas) {
        canvas.run {
            val half = strokeSize * 0.5f

            save()

            translate(half, half)

            val opacityFactor = opacityHolder / 255f

            if (shadowRadius > 0) {
                save()
                translate(half, half)
                val transformedColor =
                    shadowColor.calculateColorAlphaWithOpacityFactor(opacityFactor)
                val currentStyle = shapePaint.style
                val currentShader = shapePaint.shader
                shapePaint.shader = null
                shapePaint.style = Paint.Style.FILL_AND_STROKE
                shapePaint.strokeWidth = strokeSize
                shapePaint.color = transformedColor
                shapePaint.setShadowLayer(
                    shadowRadius,
                    shadowDx,
                    shadowDy,
                    transformedColor
                )

                shape.draw(canvas, shapePaint)

                shapePaint.clearShadowLayer()
                shapePaint.shader = currentShader
                shapePaint.style = currentStyle
                shapePaint.strokeWidth = 0f
                restore()
            }

            if (strokeSize > 0f) {
                val currentStyle = shapePaint.style
                shapePaint.style = Paint.Style.STROKE
                shapePaint.strokeWidth = strokeSize
                val currentShader = shapePaint.shader
                shapePaint.shader = null
                shapePaint.color = strokeColor.calculateColorAlphaWithOpacityFactor(opacityFactor)

                strokeShape.draw(canvas, shapePaint)

                shapePaint.shader = currentShader
                shapePaint.style = currentStyle
                shapePaint.strokeWidth = 0f
            }

            restore()

            translate(strokeSize, strokeSize)

            shapePaint.color = shapeColor.calculateColorAlphaWithOpacityFactor(opacityFactor)
            shape.draw(canvas, shapePaint)
        }
    }

    override fun getOpacity(): Int {
        return opacityHolder
    }

    override fun setOpacity(opacity: Int) {
        opacityHolder = opacity
        invalidate()
    }

    private fun @receiver: ColorInt Int.calculateColorAlphaWithOpacityFactor(
        factor: Float
    ): Int =
        if (factor == 1f) this else Color.argb(
            (this.alpha * factor).roundToInt(),
            this.red,
            this.green,
            this.blue,
        )
}
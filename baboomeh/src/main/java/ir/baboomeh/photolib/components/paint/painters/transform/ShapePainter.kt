package ir.baboomeh.photolib.components.paint.painters.transform

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
import androidx.core.graphics.createBitmap
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.graphics.withSave
import ir.baboomeh.photolib.components.shapes.MananShape
import ir.baboomeh.photolib.properties.Bitmapable
import ir.baboomeh.photolib.properties.Blendable
import ir.baboomeh.photolib.properties.Colorable
import ir.baboomeh.photolib.properties.Gradientable
import ir.baboomeh.photolib.properties.Opacityable
import ir.baboomeh.photolib.properties.Shadowable
import ir.baboomeh.photolib.properties.StrokeCapable
import ir.baboomeh.photolib.properties.Texturable
import ir.baboomeh.photolib.utils.MananMatrix
import kotlin.math.min
import kotlin.math.roundToInt

open class ShapePainter(shape: MananShape, var shapeWidth: Int, var shapeHeight: Int) : Transformable(),
    Bitmapable, StrokeCapable, Colorable,
    Texturable, Gradientable,
    Shadowable, Blendable, Opacityable {


    open var shape = shape
        set(value) {
            field = value
            notifyBoundsChanged()
        }

    protected var shapeShadowRadius = 0f
    protected var shapeShadowDx = 0f
    protected var shapeShadowDy = 0f
    protected var shapeShadowColor = Color.YELLOW

    protected var opacityHolder: Int = 255

    protected var rawWidth = 0f
    protected var rawHeight = 0f

    protected val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    protected var shaderRotationHolder = 0f

    open var shapeColor = Color.BLACK
        set(value) {
            field = value
            shapePaint.color = value
            invalidate()
        }

    protected var strokeSize = 0f

    protected var shapeStrokeColor = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    protected val shaderMatrix by lazy {
        MananMatrix()
    }

    protected val saveLayerPaint by lazy {
        Paint()
    }

    protected var shapeBlendMode: PorterDuff.Mode = PorterDuff.Mode.SRC

    protected var gradientColors: IntArray? = null

    protected var gradientPositions: FloatArray? = null

    protected var pivotX = 0f
    protected var pivotY = 0f

    protected var currentTexture: Bitmap? = null

    protected val dstOutMode by lazy {
        PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

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
            painter.shapePaint.shader = shapePaint.shader

            val childBounds = RectF()
            getBounds(childBounds)

            when (shapePaint.shader) {
                is LinearGradient -> {
                    painter.applyLinearGradient(
                        0f,
                        childBounds.height() * 0.5f,
                        childBounds.width(),
                        childBounds.height() * 0.5f,
                        gradientColors!!,
                        gradientPositions!!,
                    )
                }

                is RadialGradient -> {
                    painter.applyRadialGradient(
                        childBounds.width() * 0.5f,
                        childBounds.height() * 0.5f,
                        childBounds.width() * 0.5f,
                        gradientColors!!,
                        gradientPositions!!,
                    )
                }

                is SweepGradient -> {
                    painter.applySweepGradient(
                        childBounds.width() * 0.5f,
                        childBounds.height() * 0.5f,
                        gradientColors!!,
                        gradientPositions!!,
                    )
                }
            }
            painter.strokeSize = strokeSize
            painter.shapeStrokeColor = shapeStrokeColor
            painter.shaderRotationHolder = shaderRotationHolder
            getTexture()?.let { t ->
                painter.applyTexture(t)
            }
            painter.shaderMatrix.set(shaderMatrix)
            if (painter.shapePaint.shader != null) {
                painter.shapePaint.shader.setLocalMatrix(shaderMatrix)
            }
            painter.shapePaint.maskFilter = shapePaint.maskFilter
            if (shapeBlendMode != PorterDuff.Mode.SRC) {
                painter.setBlendMode(shapeBlendMode)
            }
            painter.setShadow(
                shapeShadowRadius,
                shapeShadowDx,
                shapeShadowDy,
                shapeShadowColor
            )


            painter.setOpacity(getOpacity())
        }
    }


    override fun setStroke(strokeRadiusPx: Float, strokeColor: Int) {
        strokeSize = strokeRadiusPx
        this.shapeStrokeColor = strokeColor
        notifyBoundsChanged()
    }

    override fun getStrokeColor(): Int {
        return shapeStrokeColor
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
        removeGradient()
        currentTexture = bitmap
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
        shapePaint.shader = null
        invalidate()
    }

    override fun toBitmap(config: Bitmap.Config): Bitmap {
        return createBitmap(rawWidth.toInt(), rawHeight.toInt(), config).also { bitmap ->
            draw(Canvas(bitmap))
        }
    }

    override fun toBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        val wStroke = rawWidth
        val hStroke = rawHeight

        val scale = min(width.toFloat() / wStroke, height.toFloat() / hStroke)

        val ws = (wStroke * scale)
        val hs = (hStroke * scale)

        val outputBitmap = createBitmap(width, height, config)

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
        removeTexture()
        gradientColors = colors
        gradientPositions = position

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
        removeTexture()
        gradientColors = colors
        gradientPositions = stops

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
        removeTexture()
        gradientColors = colors
        gradientPositions = positions

        shapePaint.shader =
            SweepGradient(cx, cy, colors, positions).apply {
                setLocalMatrix(shaderMatrix)
            }
        invalidate()
    }

    override fun removeGradient() {
        shapePaint.shader = null
        gradientColors = null
        gradientPositions = null
        invalidate()
    }

    override fun isGradientApplied(): Boolean {
        return (shapePaint.shader != null && (shapePaint.shader is LinearGradient || shapePaint.shader is RadialGradient || shapePaint.shader is SweepGradient))
    }

    override fun getShadowDx(): Float {
        return shapeShadowDx
    }

    override fun getShadowDy(): Float {
        return shapeShadowDy
    }

    override fun getShadowRadius(): Float {
        return shapeShadowRadius
    }

    override fun getShadowColor(): Int {
        return shapeShadowColor
    }

    override fun setShadow(radius: Float, dx: Float, dy: Float, shadowColor: Int) {
        shapeShadowRadius = radius
        shapeShadowDx = dx
        shapeShadowDy = dy
        this.shapeShadowColor = shadowColor
        invalidate()
    }

    override fun clearShadow() {
        shapePaint.clearShadowLayer()
        shapeShadowRadius = 0f
        shapeShadowDx = 0f
        shapeShadowDy = 0f
        shapeShadowColor = Color.YELLOW
        invalidate()
    }

    override fun setBlendMode(blendMode: PorterDuff.Mode) {
        shapePaint.xfermode = PorterDuffXfermode(blendMode)
        this.shapeBlendMode = blendMode
        invalidate()
    }

    override fun clearBlend() {
        shapePaint.xfermode = null
        shapeBlendMode = PorterDuff.Mode.SRC
        invalidate()
    }

    override fun getBlendMode(): PorterDuff.Mode {
        return shapeBlendMode
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

        shape.resize(rawWidth + strokeSize, rawHeight + strokeSize)

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
            withSave {

                translate(half, half)

                val opacityFactor = opacityHolder / 255f

                if (shapeShadowRadius > 0) {
                    val transformedColor =
                        shapeShadowColor.calculateColorAlphaWithOpacityFactor(opacityFactor)
                    val currentStyle = shapePaint.style
                    val currentShader = shapePaint.shader
                    shapePaint.shader = null
                    shapePaint.style = Paint.Style.FILL_AND_STROKE
                    shapePaint.strokeWidth = strokeSize
                    shapePaint.color = transformedColor
                    shapePaint.setShadowLayer(
                        shapeShadowRadius,
                        shapeShadowDx,
                        shapeShadowDy,
                        transformedColor
                    )

                    shape.draw(canvas, shapePaint)

                    shapePaint.clearShadowLayer()
                    shapePaint.shader = currentShader
                    shapePaint.style = currentStyle
                    shapePaint.strokeWidth = 0f
                }

                if (strokeSize > 0f) {
                    val currentStyle = shapePaint.style
                    shapePaint.style = Paint.Style.STROKE
                    shapePaint.strokeWidth = strokeSize
                    val currentShader = shapePaint.shader
                    shapePaint.shader = null
                    shapePaint.color =
                        shapeStrokeColor.calculateColorAlphaWithOpacityFactor(opacityFactor)

                    saveLayer(-half, -half, rawWidth, rawHeight, saveLayerPaint)

                    shape.draw(canvas, shapePaint)

                    shapePaint.xfermode = dstOutMode
                    shapePaint.color = Color.BLACK
                    shapePaint.style = Paint.Style.FILL

                    shape.draw(canvas, shapePaint)

                    shapePaint.xfermode = null
                    shapePaint.shader = currentShader
                    shapePaint.style = currentStyle
                    shapePaint.strokeWidth = 0f

                    restore()
                }

                shapePaint.color = shapeColor.calculateColorAlphaWithOpacityFactor(opacityFactor)
                shape.draw(canvas, shapePaint)
            }
        }
    }

    override fun getOpacity(): Int {
        return opacityHolder
    }

    override fun setOpacity(opacity: Int) {
        opacityHolder = opacity
        invalidate()
    }

    protected fun @receiver: ColorInt Int.calculateColorAlphaWithOpacityFactor(
        factor: Float
    ): Int =
        if (factor == 1f) this else Color.argb(
            (this.alpha * factor).roundToInt(),
            this.red,
            this.green,
            this.blue,
        )
}
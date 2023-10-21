package ir.manan.mananpic.components.imageviews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.View
import androidx.core.view.doOnPreDraw
import ir.manan.mananpic.components.shapes.MananShape
import ir.manan.mananpic.properties.*
import ir.manan.mananpic.utils.MananFactory
import ir.manan.mananpic.utils.MananMatrix
import kotlin.math.min

@SuppressLint("ViewConstructor")
class MananShapeView(
    context: Context,
    @Transient var shape: MananShape,
    var shapeWidth: Int,
    var shapeHeight: Int
) : View(context), Bitmapable, MananComponent, StrokeCapable, Colorable, Texturable, Gradientable,
    Shadowable, Blendable,
    java.io.Serializable {

    private var shadowRadius = 0f
    private var trueShadowRadius = 0f
    private var shadowDx = 0f
    private var shadowDy = 0f
    private var shadowLColor = Color.YELLOW
    private var isShadowCleared = true

    @Transient
    private var strokeShape = shape.clone()

    private var rawWidth = 0f
    private var rawHeight = 0f

    @Transient
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    @Transient
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

    @Transient
    private val bounds = RectF()

    @Transient
    private val mappingMatrix = Matrix()

    @Transient
    private val shaderMatrix = MananMatrix()

    private var blendMode: PorterDuff.Mode = PorterDuff.Mode.SRC

    private var gradientColors: IntArray? = null

    private var gradientPositions: FloatArray? = null

    override fun changeColor(color: Int) {
        shapeColor = color
    }

    override fun getColor(): Int {
        return shapeColor
    }

    override fun reportRotation(): Float {
        return rotation
    }

    override fun reportScaleX(): Float {
        return scaleX
    }

    override fun reportScaleY(): Float {
        return scaleY
    }

    override fun reportBoundPivotX(): Float {
        return bounds.centerX()
    }

    override fun reportBound(): RectF {
        bounds.set(
            x,
            y,
            width + x,
            height + y
        )
        mappingMatrix.run {
            setScale(
                scaleX,
                scaleY,
                bounds.centerX(),
                bounds.centerY()
            )
            mapRect(bounds)
        }
        return bounds
    }

    override fun clone(): View {
        return MananFactory.createShapeView(context, shape, shapeWidth, shapeHeight)
            .also { shapeView ->
                shapeView.setLayerType(layerType, null)
                shapeView.scaleX = scaleX
                shapeView.scaleY = scaleY
                shapeView.shapeColor = shapeColor
                shapeView.shapePaint.style = shapePaint.style
                shapeView.shapePaint.strokeWidth = shapePaint.strokeWidth
                shapeView.shapePaint.pathEffect = shapePaint.pathEffect

                shapeView.strokeSize = strokeSize
                shapeView.strokeColor = strokeColor
                shapeView.shaderRotationHolder = shaderRotationHolder
                shapeView.paintShader = paintShader
                doOnPreDraw {
                    shapeView.shaderMatrix.set(shaderMatrix)
                    shapeView.shapePaint.shader = paintShader
                    if (shapeView.shapePaint.shader != null) {
                        shapeView.shapePaint.shader.setLocalMatrix(shaderMatrix)
                    }
                }
                shapeView.shapePaint.maskFilter = shapePaint.maskFilter
                if (blendMode != PorterDuff.Mode.SRC) {
                    shapeView.setBlendMode(blendMode)
                }
                shapeView.setShadow(
                    trueShadowRadius,
                    shadowDx,
                    shadowDy,
                    shadowLColor
                )
                shapeView.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)

                if (gradientColors != null) {
                    shapeView.gradientColors = gradientColors!!.clone()
                }
                if (gradientPositions != null) {
                    shapeView.gradientPositions = gradientPositions!!.clone()
                }
            }
    }

    override fun reportBoundPivotY(): Float {
        return bounds.centerY()
    }

    override fun reportPivotX(): Float {
        return pivotX
    }

    override fun reportPivotY(): Float {
        return pivotY
    }

    override fun applyRotation(degree: Float) {
        rotation = degree
    }

    override fun applyScale(scaleFactor: Float) {
        scaleX *= scaleFactor
        scaleY *= scaleFactor
    }

    override fun applyScale(xFactor: Float, yFactor: Float) {
        scaleX *= xFactor
        scaleY *= yFactor
    }

    override fun applyMovement(dx: Float, dy: Float) {
        x += dx
        y += dy
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        rawWidth = shapeWidth.toFloat()
        rawHeight = shapeHeight.toFloat()

        shape.resize(rawWidth, rawHeight)

        strokeShape.resize(rawWidth + strokeSize,rawHeight + strokeSize)

        val finalS = strokeSize * 2f

        rawWidth += finalS
        rawHeight += finalS

        setMeasuredDimension(
            (rawWidth + paddingEnd + paddingStart).toInt(),
            (rawHeight + paddingTop + paddingBottom).toInt()
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        bounds.set(
            x,
            y,
            width + x,
            height + y
        )
    }

    override fun setStroke(strokeRadiusPx: Float, strokeColor: Int) {
        strokeSize = strokeRadiusPx
        this.strokeColor = strokeColor
        requestLayout()
    }

    override fun getStrokeColor(): Int {
        return strokeColor
    }

    override fun getStrokeWidth(): Float {
        return strokeSize
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvas?.run {
            val half = strokeSize * 0.5f

            save()

            translate(half + (paddingStart), half + (paddingTop))

            if (shadowRadius > 0) {
                val currentColor = shapeColor
                val currentStyle = shapePaint.style
                val currentShader = shapePaint.shader
                shapePaint.shader = null
                shapePaint.style = Paint.Style.FILL_AND_STROKE
                shapePaint.strokeWidth = strokeSize

                shapePaint.setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowLColor)

                shape.draw(canvas, shapePaint)

                shapePaint.shader = currentShader
                shapePaint.style = currentStyle
                shapePaint.strokeWidth = 0f
                shapePaint.color = currentColor

                shapePaint.clearShadowLayer()
            }

            if (strokeSize > 0f) {
                val currentColor = shapeColor
                val currentStyle = shapePaint.style
                shapePaint.style = Paint.Style.STROKE
                shapePaint.strokeWidth = strokeSize
                val currentShader = shapePaint.shader
                shapePaint.shader = null
                shapePaint.color = strokeColor

                strokeShape.draw(canvas, shapePaint)

                shapePaint.shader = currentShader
                shapePaint.style = currentStyle
                shapePaint.strokeWidth = 0f
                shapePaint.color = currentColor
            }

            restore()

            translate(strokeSize + (paddingStart), strokeSize + (paddingTop))

            shape.draw(canvas, shapePaint)
        }
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
        paintShader = BitmapShader(bitmap, tileMode, tileMode).apply {
            setLocalMatrix(shaderMatrix)
        }

        shapePaint.shader = BitmapShader(bitmap, tileMode, tileMode).apply {
            setLocalMatrix(shaderMatrix)
        }
        invalidate()
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
                scaleFactor, scaleFactor, pivotX - paddingStart,
                pivotY - paddingTop
            )
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    override fun rotateColor(rotation: Float) {
        shapePaint.shader?.run {
            shaderMatrix.postRotate(
                rotation - shaderRotationHolder,
                pivotX - paddingStart,
                pivotY - paddingTop
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

    override fun removeTexture() {
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
        return trueShadowRadius
    }

    override fun getShadowColor(): Int {
        return shadowLColor
    }

    override fun setShadow(radius: Float, dx: Float, dy: Float, shadowColor: Int) {
        shadowRadius = radius
        trueShadowRadius = radius
        shadowDx = dx
        shadowDy = dy
        shadowLColor = shadowColor
        invalidate()
    }

    override fun clearShadow() {
        shapePaint.clearShadowLayer()
        shadowRadius = 0f
        shadowDx = 0f
        shadowDy = 0f
        shadowLColor = Color.YELLOW
        isShadowCleared = true
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


}
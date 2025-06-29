package ir.simurgh.photolib.components.paint.painters.transform.transformables

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
import ir.simurgh.photolib.components.shapes.SimurghShape
import ir.simurgh.photolib.properties.Bitmapable
import ir.simurgh.photolib.properties.Blendable
import ir.simurgh.photolib.properties.Colorable
import ir.simurgh.photolib.properties.Gradientable
import ir.simurgh.photolib.properties.Opacityable
import ir.simurgh.photolib.properties.Shadowable
import ir.simurgh.photolib.properties.StrokeCapable
import ir.simurgh.photolib.properties.Texturable
import ir.simurgh.photolib.utils.matrix.SimurghMatrix
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A comprehensive painter class for rendering shapes with advanced styling capabilities.
 *
 * ShapePainter provides extensive customization options including:
 * - Color fills and strokes
 * - Gradient effects (linear, radial, sweep)
 * - Texture mapping with bitmap shaders
 * - Shadow effects with customizable parameters
 * - Blend modes for advanced compositing
 * - Opacity control with alpha blending
 * - Transformations and matrix operations
 *
 * This class serves as a bridge between abstract Shape objects and their visual representation,
 * handling all rendering operations while maintaining performance through optimized drawing routines.
 *
 * @param simurghShape The Shape object to be rendered
 * @param shapeWidth The width of the shape in pixels
 * @param shapeHeight The height of the shape in pixels
 *
 * @see SimurghShape
 * @see Transformable
 */
open class ShapeTransformable(simurghShape: SimurghShape, var shapeWidth: Int, var shapeHeight: Int) : Transformable(),
    Bitmapable, StrokeCapable, Colorable,
    Texturable, Gradientable,
    Shadowable, Blendable, Opacityable {

    /**
     * The shape object being painted. Setting this property will trigger bounds recalculation.
     */
    open var shape = simurghShape
        set(value) {
            field = value
            notifyBoundsChanged()
        }

    // Shadow properties
    /** Shadow blur radius in pixels */
    protected var shapeShadowRadius = 0f

    /** Horizontal shadow offset in pixels */
    protected var shapeShadowDx = 0f

    /** Vertical shadow offset in pixels */
    protected var shapeShadowDy = 0f

    /** Shadow color with alpha channel */
    protected var shapeShadowColor = Color.YELLOW

    /** Opacity value from 0-255, used for alpha blending calculations */
    protected var opacityHolder: Int = 255

    /** Calculated raw dimensions for bounds calculations */
    protected var rawWidth = 0f
    protected var rawHeight = 0f

    /** Main paint object used for shape rendering with anti-aliasing enabled */
    protected val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Tracks current shader rotation for transformation operations */
    protected var shaderRotationHolder = 0f

    /**
     * Primary fill color for the shape. Setting this updates the paint color immediately.
     */
    open var shapeColor = Color.BLACK
        set(value) {
            field = value
            shapePaint.color = value
            invalidate()
        }

    /** Stroke width in pixels */
    protected var strokeSize = 0f

    /**
     * Stroke color for shape outlines. Setting this triggers invalidation.
     */
    protected var shapeStrokeColor = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    /** Matrix for shader transformations (scaling, rotation, translation) */
    protected val shaderMatrix by lazy {
        SimurghMatrix()
    }

    /** Paint object used for save layer operations during stroke rendering */
    protected val saveLayerPaint by lazy {
        Paint()
    }

    /** Current blend mode for compositing operations */
    protected var shapeBlendMode: PorterDuff.Mode = PorterDuff.Mode.SRC

    /** Color array for gradient effects */
    protected var gradientColors: IntArray? = null

    /** Position array for gradient color stops */
    protected var gradientPositions: FloatArray? = null

    /** Pivot point coordinates for transformations */
    protected var pivotX = 0f
    protected var pivotY = 0f

    /** Currently applied texture bitmap */
    protected var currentTexture: Bitmap? = null

    /** Pre-configured blend mode for destination-out operations */
    protected val dstOutMode by lazy {
        PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    override fun changeColor(color: Int) {
        shapeColor = color
    }

    override fun getColor(): Int {
        return shapeColor
    }

    /**
     * Creates a deep copy of this ShapePainter with all properties and state preserved.
     *
     * This method performs a comprehensive clone operation including:
     * - Shape cloning with preserved geometry
     * - Paint state replication (style, stroke, effects)
     * - Gradient reconstruction with original parameters
     * - Texture mapping with shader matrix preservation
     * - Shadow and blend mode replication
     *
     * @return A new ShapePainter instance identical to this one
     */
    override fun clone(): ShapeTransformable {
        return ShapeTransformable(shape.clone(), shapeWidth, shapeHeight).also { painter ->
            // Copy basic paint properties
            painter.shapeColor = shapeColor
            painter.shapePaint.style = shapePaint.style
            painter.shapePaint.strokeWidth = shapePaint.strokeWidth
            painter.shapePaint.pathEffect = shapePaint.pathEffect
            painter.shapePaint.shader = shapePaint.shader

            // Calculate bounds for gradient reconstruction
            val childBounds = RectF()
            getBounds(childBounds)

            // Reconstruct gradients based on current shader type
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

            // Copy additional properties
            painter.strokeSize = strokeSize
            painter.shapeStrokeColor = shapeStrokeColor
            painter.shaderRotationHolder = shaderRotationHolder

            // Apply texture if present
            getTexture()?.let { t ->
                painter.applyTexture(t)
            }

            // Copy transformation matrix
            painter.shaderMatrix.set(shaderMatrix)
            if (painter.shapePaint.shader != null) {
                painter.shapePaint.shader.setLocalMatrix(shaderMatrix)
            }

            // Copy effects and filters
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

    /**
     * Configures stroke properties for the shape outline.
     *
     * @param strokeRadiusPx Width of the stroke in pixels
     * @param strokeColor Color of the stroke outline
     */
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

    /**
     * Applies a bitmap texture to the shape with mirror tiling mode.
     *
     * @param bitmap The bitmap texture to apply
     */
    override fun applyTexture(bitmap: Bitmap) {
        applyTexture(bitmap, Shader.TileMode.MIRROR)
    }

    /**
     * Applies a bitmap texture to the shape with specified tiling behavior.
     * This method removes any existing gradients and replaces them with the texture.
     *
     * @param bitmap The bitmap texture to be applied to the shape
     * @param tileMode How the texture should tile when the shape is larger than the bitmap
     */
    override fun applyTexture(bitmap: Bitmap, tileMode: Shader.TileMode) {
        removeGradient() // Clear existing gradients
        currentTexture = bitmap
        shapePaint.shader = BitmapShader(bitmap, tileMode, tileMode).apply {
            setLocalMatrix(shaderMatrix)
        }
        invalidate()
    }

    override fun getTexture(): Bitmap? {
        return currentTexture
    }

    /**
     * Translates the shader/texture by the specified offset.
     * This is useful for animating texture positions or fine-tuning texture alignment.
     *
     * @param dx Horizontal translation in pixels
     * @param dy Vertical translation in pixels
     */
    override fun shiftColor(dx: Float, dy: Float) {
        shapePaint.shader?.run {
            shaderMatrix.postTranslate(dx, dy)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    /**
     * Scales the shader/texture by the specified factor around the pivot point.
     *
     * @param scaleFactor Uniform scale factor (1.0 = no change, 2.0 = double size, 0.5 = half size)
     */
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

    /**
     * Rotates the shader/texture around the pivot point.
     *
     * @param rotation Target rotation angle in degrees
     */
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

    /**
     * Resets all shader transformations to their default state.
     */
    override fun resetComplexColorMatrix() {
        shapePaint.shader?.run {
            shaderMatrix.reset()
            shaderRotationHolder = 0f
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    /**
     * Applies additional matrix transformations to the shader.
     *
     * @param matrix The transformation matrix to concatenate
     */
    override fun concatColorMatrix(matrix: Matrix) {
        shapePaint.shader?.run {
            shaderMatrix.postConcat(matrix)
            setLocalMatrix(shaderMatrix)
            invalidate()
        }
    }

    /**
     * Removes the current texture and reverts to solid color fill.
     */
    override fun removeTexture() {
        currentTexture = null
        shapePaint.shader = null
        invalidate()
    }

    /**
     * Converts the shape to a bitmap with the specified configuration.
     *
     * @param config Bitmap configuration (e.g., ARGB_8888, RGB_565)
     * @return A bitmap representation of the shape
     */
    override fun toBitmap(config: Bitmap.Config): Bitmap {
        return createBitmap(rawWidth.toInt(), rawHeight.toInt(), config).also { bitmap ->
            draw(Canvas(bitmap))
        }
    }

    /**
     * Converts the shape to a bitmap with specific dimensions and configuration.
     * The shape is scaled to fit within the specified dimensions while maintaining aspect ratio.
     *
     * @param width Target bitmap width
     * @param height Target bitmap height
     * @param config Bitmap configuration
     * @return A scaled bitmap representation of the shape
     */
    override fun toBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        val wStroke = rawWidth
        val hStroke = rawHeight

        // Calculate uniform scale factor to fit within target dimensions
        val scale = min(width.toFloat() / wStroke, height.toFloat() / hStroke)

        val ws = (wStroke * scale)
        val hs = (hStroke * scale)

        val outputBitmap = createBitmap(width, height, config)

        // Center the scaled shape in the target dimensions
        val extraWidth = width - ws
        val extraHeight = height - hs

        Canvas(outputBitmap).run {
            translate(extraWidth * 0.5f, extraHeight * 0.5f)
            scale(ws / wStroke, hs / hStroke)
            draw(this)
        }

        return outputBitmap
    }

    /**
     * Applies a linear gradient to the shape.
     *
     * @param x0 Starting X coordinate
     * @param y0 Starting Y coordinate
     * @param x1 Ending X coordinate
     * @param y1 Ending Y coordinate
     * @param colors Array of colors for the gradient
     * @param position Optional array of color stop positions (0.0 to 1.0)
     * @param tileMode How the gradient behaves outside its bounds
     */
    override fun applyLinearGradient(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        colors: IntArray,
        position: FloatArray?,
        tileMode: Shader.TileMode
    ) {
        removeTexture() // Clear any existing texture
        gradientColors = colors
        gradientPositions = position

        shapePaint.shader =
            LinearGradient(x0, y0, x1, y1, colors, position, tileMode).apply {
                setLocalMatrix(shaderMatrix)
            }

        invalidate()
    }

    /**
     * Applies a radial gradient to the shape.
     *
     * @param centerX Center X coordinate of the gradient
     * @param centerY Center Y coordinate of the gradient
     * @param radius Radius of the gradient effect
     * @param colors Array of colors for the gradient
     * @param stops Optional array of color stop positions
     * @param tileMode How the gradient behaves outside its bounds
     */
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

    /**
     * Applies a sweep (angular) gradient to the shape.
     *
     * @param cx Center X coordinate
     * @param cy Center Y coordinate
     * @param colors Array of gradient colors
     * @param positions Optional array of angular positions (0.0 to 1.0)
     */
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

    /**
     * Removes any applied gradient and reverts to solid color fill.
     */
    override fun removeGradient() {
        shapePaint.shader = null
        gradientColors = null
        gradientPositions = null
        invalidate()
    }

    /**
     * Checks if a gradient is currently applied to the shape.
     *
     * @return true if any gradient type is active, false otherwise
     */
    override fun isGradientApplied(): Boolean {
        return (shapePaint.shader != null && (shapePaint.shader is LinearGradient || shapePaint.shader is RadialGradient || shapePaint.shader is SweepGradient))
    }

    // Shadow property getters
    override fun getShadowDx(): Float = shapeShadowDx
    override fun getShadowDy(): Float = shapeShadowDy
    override fun getShadowRadius(): Float = shapeShadowRadius
    override fun getShadowColor(): Int = shapeShadowColor

    /**
     * Configures drop shadow parameters for the shape.
     *
     * @param radius Blur radius of the shadow
     * @param dx Horizontal offset of the shadow
     * @param dy Vertical offset of the shadow
     * @param shadowColor Color of the shadow including alpha
     */
    override fun setShadow(radius: Float, dx: Float, dy: Float, shadowColor: Int) {
        shapeShadowRadius = radius
        shapeShadowDx = dx
        shapeShadowDy = dy
        this.shapeShadowColor = shadowColor
        invalidate()
    }

    /**
     * Removes the drop shadow effect.
     */
    override fun clearShadow() {
        shapePaint.clearShadowLayer()
        shapeShadowRadius = 0f
        shapeShadowDx = 0f
        shapeShadowDy = 0f
        shapeShadowColor = Color.YELLOW
        invalidate()
    }

    // Blend mode operations
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

    override fun getBlendMode(): PorterDuff.Mode = shapeBlendMode

    // Gradient information getters
    override fun reportPositions(): FloatArray? = gradientPositions
    override fun reportColors(): IntArray? = gradientColors

    /**
     * Calculates and sets the bounding rectangle for the shape.
     * This includes the base shape dimensions plus stroke width and any padding.
     *
     * @param bounds RectF to be populated with the calculated bounds
     */
    override fun getBounds(bounds: RectF) {
        // Start with base shape dimensions
        rawWidth = shapeWidth.toFloat()
        rawHeight = shapeHeight.toFloat()

        // Resize the shape to account for stroke
        shape.resize(rawWidth + strokeSize, rawHeight + strokeSize)

        // Add stroke width to both dimensions
        val finalS = strokeSize * 2f
        rawWidth += finalS
        rawHeight += finalS

        // Set pivot points for transformations
        pivotX = rawWidth * 0.5f
        pivotY = rawHeight * 0.5f

        bounds.set(0f, 0f, rawWidth, rawHeight)
    }

    /**
     * Main drawing method that renders the shape with all applied effects.
     *
     * The drawing process follows this order:
     * 1. Shadow rendering (if enabled)
     * 2. Stroke rendering (if enabled) using destination-out technique
     * 3. Main shape fill rendering
     *
     * @param canvas The canvas to draw on
     */
    override fun draw(canvas: Canvas) {
        canvas.run {
            val half = strokeSize * 0.5f
            withSave {
                // Offset for stroke centering
                translate(half, half)

                val opacityFactor = opacityHolder / 255f

                // Render shadow if enabled
                if (shapeShadowRadius > 0) {
                    val transformedColor =
                        shapeShadowColor.calculateColorAlphaWithOpacityFactor(opacityFactor)
                    val currentStyle = shapePaint.style
                    val currentShader = shapePaint.shader

                    // Configure paint for shadow rendering
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

                    // Restore paint state
                    shapePaint.clearShadowLayer()
                    shapePaint.shader = currentShader
                    shapePaint.style = currentStyle
                    shapePaint.strokeWidth = 0f
                }

                // Render stroke if enabled using destination-out technique
                if (strokeSize > 0f) {
                    val currentStyle = shapePaint.style
                    shapePaint.style = Paint.Style.STROKE
                    shapePaint.strokeWidth = strokeSize
                    val currentShader = shapePaint.shader
                    shapePaint.shader = null
                    shapePaint.color =
                        shapeStrokeColor.calculateColorAlphaWithOpacityFactor(opacityFactor)

                    // Create layer for stroke composition
                    saveLayer(-half, -half, rawWidth, rawHeight, saveLayerPaint)

                    // Draw stroke outline
                    shape.draw(canvas, shapePaint)

                    // Cut out the interior using destination-out blend mode
                    shapePaint.xfermode = dstOutMode
                    shapePaint.color = Color.BLACK
                    shapePaint.style = Paint.Style.FILL

                    shape.draw(canvas, shapePaint)

                    // Restore paint state
                    shapePaint.xfermode = null
                    shapePaint.shader = currentShader
                    shapePaint.style = currentStyle
                    shapePaint.strokeWidth = 0f

                    restore()
                }

                // Render main shape fill
                shapePaint.color = shapeColor.calculateColorAlphaWithOpacityFactor(opacityFactor)
                shape.draw(canvas, shapePaint)
            }
        }
    }

    // Opacity operations
    override fun getOpacity(): Int = opacityHolder

    override fun setOpacity(opacity: Int) {
        opacityHolder = opacity
        invalidate()
    }

    /**
     * Calculates a color with adjusted alpha based on the opacity factor.
     * This is used to blend the opacity setting with existing color alpha values.
     *
     * @param factor Opacity factor from 0.0 to 1.0
     * @return Color with adjusted alpha channel
     */
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

package ir.simurgh.photolib.components.paint.painters.painting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import ir.simurgh.photolib.components.paint.painter_view.PaintLayer
import ir.simurgh.photolib.components.paint.painters.painter.Painter
import ir.simurgh.photolib.components.paint.painters.painter.PainterMessage
import ir.simurgh.photolib.components.paint.painters.painting.brushes.Brush
import ir.simurgh.photolib.components.paint.painters.painting.engines.DrawingEngine
import ir.simurgh.photolib.components.paint.smoothers.BezierLineSmoother
import ir.simurgh.photolib.components.paint.smoothers.LineSmoother
import ir.simurgh.photolib.properties.MaskTool
import ir.simurgh.photolib.utils.gesture.TouchData
import ir.simurgh.photolib.utils.matrix.SimurghMatrix

/**
 * A comprehensive painter implementation for brush-based drawing operations.
 *
 * This class orchestrates the entire brush painting pipeline, managing:
 * - Drawing engine coordination for brush effects
 * - Line smoothing for natural stroke curves
 * - Layer management and compositing
 * - Alpha blending and texture application
 * - Touch input processing and stroke generation
 *
 * The painter supports advanced features like:
 * - Real-time stroke smoothing using configurable smoothers
 * - Alpha blending for natural paint mixing
 * - Texture overlay effects with custom shaders
 * - Eraser mode functionality
 * - Multi-layer composition
 *
 * @param engine The drawing engine that handles brush effect calculations
 * @param lineSmoother The line smoother for stroke interpolation (defaults to Bezier)
 */
open class BrushPainter(
    var engine: DrawingEngine,
    lineSmoother: LineSmoother = BezierLineSmoother()
) : Painter(), LineSmoother.OnDrawPoint, MaskTool {

    // Paint objects for different rendering stages
    /** Paint for layer composition operations */
    protected var layerPaint = Paint().apply {
        isFilterBitmap = true
    }

    /** Paint for alpha blending operations */
    protected val blendPaint = Paint().apply {
        isFilterBitmap = true
    }

    /** Paint for texture overlay effects */
    protected var texturePaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        isFilterBitmap = true
    }

    // Brush management
    /**
     * The currently active brush for painting operations.
     * When set, becomes the finalBrush used for actual drawing.
     */
    var brush: Brush? = null
        set(value) {
            field = value
            if (value != null) {
                finalBrush = value
            }
        }

    /** The brush instance actually used for drawing operations */
    protected lateinit var finalBrush: Brush

    // Canvas and bitmap management
    /** The bitmap of the current paint layer */
    protected lateinit var ccBitmap: Bitmap

    /** Canvas for drawing directly to the paint layer */
    protected val paintCanvas by lazy {
        Canvas()
    }

    /** Temporary bitmap for alpha blending operations */
    protected lateinit var alphaBlendBitmap: Bitmap

    /** Canvas for alpha blending operations */
    protected val alphaBlendCanvas by lazy {
        Canvas()
    }

    // Rendering state flags
    /** Whether alpha blending mode is currently active */
    protected var isAlphaBlending = false

    /** Bounds of the view for rendering calculations */
    protected var viewBounds = RectF()

    /** Whether the current layer is null (no drawing possible) */
    protected var isLayerNull = true

    /** Whether the current brush is null (no drawing possible) */
    protected var isBrushNull = false

    /** Whether texture blending is currently active */
    protected var isBlendingTexture = false

    /** The canvas that will be drawn to (either paint or alpha blend canvas) */
    protected lateinit var finalCanvasToDraw: Canvas

    /**
     * The line smoother responsible for creating smooth stroke curves.
     * When changed, automatically sets this painter as the draw point callback.
     */
    open var lineSmoother: LineSmoother = lineSmoother
        set(value) {
            field = value
            field.onDrawPoint = this
        }

    init {
        // Set up the initial line smoother callback
        lineSmoother.onDrawPoint = this
    }

    /**
     * Initializes the painter with the necessary transformation matrices and bounds.
     * Sets up the view bounds and ensures the line smoother callback is properly configured.
     */
    override fun initialize(
        context: Context,
        transformationMatrix: SimurghMatrix,
        fitInsideMatrix: SimurghMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)
        viewBounds.set(layerBounds)
        lineSmoother.onDrawPoint = this
    }

    /**
     * Handles the beginning of a touch-based drawing stroke.
     *
     * Initializes the drawing engine, sets up the line smoother with the first point,
     * configures alpha blending and texture effects, and prepares the appropriate
     * canvas for drawing operations.
     */
    override fun onMoveBegin(touchData: TouchData) {
        // Check if we can draw (need both brush and layer)
        if (!this::finalBrush.isInitialized || isLayerNull) {
            isBrushNull = true
            return
        }

        isBrushNull = false

        // Determine which canvas to draw to based on brush properties
        chooseCanvasToDraw()

        // Initialize the drawing engine for this stroke
        engine.onMoveBegin(touchData, finalBrush)

        // Set the first point for stroke smoothing
        lineSmoother.setFirstPoint(touchData, finalBrush)

        // Set up alpha blending bitmap if needed
        createAlphaBitmapIfNeeded()

        // Configure texture shader if texture blending is enabled
        if (isBlendingTexture) {
            texturePaint.shader =
                BitmapShader(
                    finalBrush.texture!!,
                    Shader.TileMode.MIRROR,
                    Shader.TileMode.MIRROR
                ).apply {
                    setLocalMatrix(finalBrush.textureTransformation)
                }
        }
    }

    /**
     * Creates or recreates the alpha blending bitmap if conditions require it.
     * Sets the appropriate alpha value for blending operations.
     */
    protected open fun createAlphaBitmapIfNeeded() {
        // Set blend paint alpha based on current mode
        blendPaint.alpha = if (isAlphaBlending) (finalBrush.opacity * 255f).toInt() else 255

        // Create alpha blend bitmap if needed
        if (shouldCreateAlphaBitmap()) {
            alphaBlendBitmap = createBitmap(ccBitmap.width, ccBitmap.height)
            alphaBlendCanvas.setBitmap(alphaBlendBitmap)
        }
    }

    /**
     * Determines whether a new alpha blending bitmap needs to be created.
     *
     * @return true if alpha bitmap should be created or recreated
     */
    protected open fun shouldCreateAlphaBitmap(): Boolean {
        return (isAlphaBlending || isBlendingTexture) &&
                (!this::alphaBlendBitmap.isInitialized ||
                        (alphaBlendBitmap.width != ccBitmap.width || alphaBlendBitmap.height != ccBitmap.height))
    }

    /**
     * Handles ongoing touch movement during a stroke.
     *
     * Updates the drawing engine state and adds points to the line smoother
     * for smooth curve generation. The actual drawing happens in onDrawPoint.
     */
    override fun onMove(touchData: TouchData) {
        if (shouldDraw()) {
            // Update engine state with new touch data
            engine.onMove(touchData, finalBrush)

            // Add points to line smoother for curve generation
            lineSmoother.addPoints(touchData, finalBrush)
        }
    }

    /**
     * Callback from the line smoother when a point should be drawn.
     *
     * This is where the actual brush stamping happens. The drawing engine
     * renders a brush stamp at the specified coordinates with the given
     * rotation and opacity.
     */
    override fun onDrawPoint(
        ex: Float,
        ey: Float,
        angleDirection: Float,
        totalDrawCount: Int,
        isLastPoint: Boolean
    ) {
        // Draw brush stamp at the calculated point
        engine.draw(
            ex,
            ey,
            angleDirection,
            finalCanvasToDraw,
            finalBrush,
            totalDrawCount
        )

        // Request view invalidation for real-time display
        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Handles the end of a touch-based drawing stroke.
     *
     * Finalizes the drawing engine state, sets the last point for stroke smoothing,
     * and composites any alpha blending or texture effects to the main canvas.
     */
    override fun onMoveEnded(touchData: TouchData) {
        // Update canvas choice in case brush properties changed
        chooseCanvasToDraw()

        if (shouldDraw()) {
            // Finalize the drawing engine for this stroke
            engine.onMoveEnded(touchData, finalBrush)

            // Set the last point for stroke completion
            lineSmoother.setLastPoint(touchData, finalBrush)

            // Composite texture or alpha effects to main canvas
            if (isBlendingTexture) {
                // Apply texture mask and composite to main canvas
                alphaBlendCanvas.drawRect(viewBounds, texturePaint)
                paintCanvas.drawBitmap(alphaBlendBitmap, 0f, 0f, blendPaint)
                alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
            } else if (isAlphaBlending) {
                // Composite alpha blended stroke to main canvas
                paintCanvas.drawBitmap(alphaBlendBitmap, 0f, 0f, blendPaint)
                alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
            }

            // Request final view invalidation
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Determines which canvas to draw to based on current brush properties.
     *
     * Sets up the appropriate rendering pipeline:
     * - Direct to paint canvas for normal brushes
     * - To alpha blend canvas for alpha blending or texture effects
     */
    protected open fun chooseCanvasToDraw() {
        isAlphaBlending = finalBrush.alphaBlend
        isBlendingTexture = finalBrush.texture != null

        finalCanvasToDraw =
            if (isAlphaBlending || isBlendingTexture) alphaBlendCanvas else paintCanvas
    }

    /**
     * Called when the active paint layer changes.
     *
     * Updates the internal bitmap reference and configures the paint canvas
     * to draw to the new layer's bitmap.
     */
    override fun onLayerChanged(layer: PaintLayer?) {
        if (layer == null) {
            isLayerNull = true
            return
        }

        isLayerNull = false
        ccBitmap = layer.bitmap
        paintCanvas.setBitmap(ccBitmap)
    }

    /**
     * Draws any temporary effects (like texture or alpha blending previews) to the display canvas.
     *
     * This method is called during view rendering to show real-time drawing effects
     * that haven't been composited to the main layer yet.
     */
    override fun draw(canvas: Canvas) {
        if (isBrushNull) {
            return
        }

        // Draw temporary effects based on current mode
        if (isBlendingTexture) {
            drawTextureOnBrush(canvas)
        } else if (isAlphaBlending) {
            canvas.drawBitmap(alphaBlendBitmap, 0f, 0f, blendPaint)
        }
    }

    /**
     * Draws texture-blended brush strokes as a layer composite operation.
     *
     * Uses saveLayer to create a temporary compositing layer where the
     * texture can be properly masked with the brush strokes.
     */
    protected open fun drawTextureOnBrush(canvas: Canvas) {
        canvas.apply {
            saveLayer(viewBounds, layerPaint)
            drawBitmap(alphaBlendBitmap, 0f, 0f, blendPaint)
            drawRect(viewBounds, texturePaint)
            restore()
        }
    }

    /**
     * Determines whether drawing operations should proceed.
     *
     * @return true if both brush and layer are available for drawing
     */
    protected open fun shouldDraw(): Boolean = !isBrushNull && !isLayerNull

    /**
     * Resets the paint state by clearing all bitmaps.
     *
     * Clears both the main canvas bitmap and any alpha blending bitmaps,
     * effectively erasing all paint content.
     */
    override fun resetPaint() {
        if (this::ccBitmap.isInitialized) {
            ccBitmap.eraseColor(Color.TRANSPARENT)
        }

        if (this::alphaBlendBitmap.isInitialized) {
            alphaBlendBitmap.eraseColor(Color.TRANSPARENT)
        }

        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Changes the blending mode used for brush texture effects.
     *
     * @param blendMode The new PorterDuff blending mode for texture operations
     */
    open fun changeBrushTextureBlending(blendMode: PorterDuff.Mode) {
        texturePaint.xfermode = PorterDuffXfermode(blendMode)
        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Sets whether the painter should operate in eraser mode.
     *
     * @param isEnabled true to enable eraser mode, false for normal painting
     */
    override fun setEraserMode(isEnabled: Boolean) {
        engine.setEraserMode(isEnabled)
    }
}

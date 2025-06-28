package ir.simurgh.photolib.components.paint.painters.masking

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ComposePathEffect
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.view.animation.LinearInterpolator
import ir.simurgh.photolib.components.paint.painters.painter.Painter
import ir.simurgh.photolib.components.paint.painters.painter.PainterMessage
import ir.simurgh.photolib.components.paint.painters.painting.brushes.NativeBrush
import ir.simurgh.photolib.components.paint.smoothers.BezierLineSmoother
import ir.simurgh.photolib.components.paint.smoothers.LineSmoother
import ir.simurgh.photolib.components.paint.view.PaintLayer
import ir.simurgh.photolib.properties.MaskTool
import ir.simurgh.photolib.utils.extensions.dp
import ir.simurgh.photolib.utils.gesture.TouchData
import ir.simurgh.photolib.utils.matrix.SimurghMatrix

/**
 * A painter tool that implements lasso-based masking functionality.
 * This tool allows users to draw freehand selection areas (lasso) that can be used
 * to create masks on image layers. The lasso path is drawn with smooth bezier curves
 * and animated dashed lines for visual feedback.
 *
 * Key features:
 * - Smooth bezier curve drawing for natural lasso selection
 * - Animated dashed line visualization
 * - Support for both additive and subtractive (erase) masking modes
 * - Adjustable stroke width and smoothness
 * - Real-time path visualization during drawing
 *
 * @param context Android context for accessing resources and display metrics
 */
open class LassoMaskPainterTool(context: Context) : Painter(), LineSmoother.OnDrawPoint, MaskTool {

    /**
     * Paint object used for drawing the lasso outline with anti-aliasing
     */
    protected val lassoPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }

    /**
     * Path object that stores the lasso selection path with winding fill type
     */
    protected val lassoPath by lazy {
        Path().apply {
            fillType = Path.FillType.WINDING
        }
    }

    /**
     * Canvas used for applying the lasso mask to the target layer
     */
    protected val canvasColorApply by lazy {
        Canvas()
    }

    /**
     * Stroke width for the lasso outline in pixels.
     * When set, automatically updates the paint and triggers a redraw.
     */
    open var lassoStrokeWidth = context.dp(4)
        set(value) {
            field = value
            lassoPaint.strokeWidth = field
            sendMessage(PainterMessage.INVALIDATE)
        }

    /**
     * Flag indicating whether the tool is in erase mode.
     * When true, the lasso will subtract from existing masks instead of adding to them.
     */
    protected open var isEraseMode = false
        set(value) {
            field = value
            // Set appropriate blend mode for erasing or adding
            if (isEraseMode) {
                lassoPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            } else {
                lassoPaint.xfermode = null
            }
        }

    /**
     * Path effect for creating rounded corners on the lasso path
     */
    protected val cornerPathEffect = CornerPathEffect(context.dp(2))

    /**
     * Animator that creates the animated dashed line effect for visual feedback.
     * The animation continuously shifts the dash pattern to create a "marching ants" effect.
     */
    protected val pathEffectAnimator = ValueAnimator().apply {
        duration = 500
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        setFloatValues(0f, 20f)
        addUpdateListener {
            // Create animated dashed line effect
            lassoPaint.pathEffect =
                ComposePathEffect(
                    DashPathEffect(floatArrayOf(10f, 10f), it.animatedValue as Float),
                    cornerPathEffect
                )

            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Reference to the currently selected paint layer where the mask will be applied
     */
    protected var selectedLayer: PaintLayer? = null

    /**
     * Flag to track if this is the first point being drawn in the current lasso path
     */
    protected var isFirstPoint = true

    /**
     * Transformation matrix for coordinate system conversion
     */
    protected lateinit var transformationMatrix: SimurghMatrix

    /**
     * Line smoother that converts raw touch input into smooth bezier curves
     */
    protected val touchSmoother = BezierLineSmoother().apply {
        onDrawPoint = this@LassoMaskPainterTool
    }

    /**
     * Brush configuration for controlling the smoothness of the drawn lines
     */
    protected val smoothnessBrush = NativeBrush().apply {
        smoothness = 0.5f
        size = 10
    }

    /**
     * Controls how smooth the lasso lines are drawn (0.0 to 1.0).
     * Higher values create smoother curves but may be less responsive to quick movements.
     */
    open var lassoSmoothness = 0.5f
        set(value) {
            field = value
            smoothnessBrush.smoothness = field
        }

    /**
     * Initializes the painter tool with necessary matrices and bounds.
     * Starts the animated dashed line effect.
     *
     * @param context Android context
     * @param transformationMatrix Matrix for coordinate transformations
     * @param fitInsideMatrix Matrix for fitting content inside bounds
     * @param layerBounds Bounds of the layer being painted on
     * @param clipBounds Clipping bounds for the painting area
     */
    override fun initialize(
        context: Context,
        transformationMatrix: SimurghMatrix,
        fitInsideMatrix: SimurghMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        this.transformationMatrix = transformationMatrix
        pathEffectAnimator.start()
        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)
    }

    /**
     * Called when user starts drawing a lasso selection.
     * Initializes the line smoother with the first touch point.
     *
     * @param touchData Contains the initial touch coordinates and pressure
     */
    override fun onMoveBegin(touchData: TouchData) {
        touchSmoother.setFirstPoint(touchData, smoothnessBrush)
    }

    /**
     * Called continuously while user is drawing the lasso selection.
     * Adds new points to the line smoother for smooth curve generation.
     *
     * @param touchData Contains current touch coordinates and movement data
     */
    override fun onMove(touchData: TouchData) {
        touchSmoother.addPoints(touchData, smoothnessBrush)
    }

    /**
     * Called when user finishes drawing the lasso selection.
     * Finalizes the path and applies the mask to the selected layer.
     *
     * @param touchData Contains the final touch coordinates
     */
    override fun onMoveEnded(touchData: TouchData) {
        touchSmoother.setLastPoint(touchData, smoothnessBrush)
        applyOnLayer()
    }

    /**
     * Callback from the line smoother when a new point should be drawn.
     * This creates smooth bezier curves from the raw touch input.
     *
     * @param ex X coordinate of the point to draw
     * @param ey Y coordinate of the point to draw
     * @param angleDirection Direction angle of the curve at this point
     * @param totalDrawCount Total number of points drawn so far
     * @param isLastPoint Whether this is the final point in the path
     */
    override fun onDrawPoint(
        ex: Float,
        ey: Float,
        angleDirection: Float,
        totalDrawCount: Int,
        isLastPoint: Boolean
    ) {
        drawLine(ex, ey)
    }

    /**
     * Applies the completed lasso path as a mask to the selected layer.
     * The mask is drawn as a filled path using the current blend mode.
     */
    protected open fun applyOnLayer() {
        selectedLayer?.bitmap?.let { layer ->
            canvasColorApply.setBitmap(layer)
            // Temporarily change to fill mode to create the mask
            lassoPaint.style = Paint.Style.FILL
            canvasColorApply.drawPath(lassoPath, lassoPaint)
            // Restore stroke mode for outline drawing
            lassoPaint.style = Paint.Style.STROKE
            reset()
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Adds a line segment to the lasso path.
     * For the first point, moves to that position; for subsequent points, draws lines.
     *
     * @param ex X coordinate of the point
     * @param ey Y coordinate of the point
     */
    open fun drawLine(ex: Float, ey: Float) {
        if (isFirstPoint) {
            // Start the path at the first point
            lassoPath.moveTo(ex, ey)
            isFirstPoint = false
        } else {
            // Draw line to subsequent points
            lassoPath.lineTo(ex, ey)
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Draws the lasso path on the provided canvas.
     * Adjusts stroke width based on current zoom level for consistent visual appearance.
     *
     * @param canvas Canvas to draw on
     */
    override fun draw(canvas: Canvas) {
        // Adjust stroke width based on current zoom level
        lassoPaint.strokeWidth = lassoStrokeWidth / transformationMatrix.getRealScaleX()
        canvas.drawPath(lassoPath, lassoPaint)
    }

    /**
     * Resets the lasso path and prepares for a new selection.
     * Clears the current path and resets the first point flag.
     */
    override fun reset() {
        lassoPath.rewind()
        isFirstPoint = true
        sendMessage(PainterMessage.INVALIDATE)
    }

    /**
     * Called when the active layer changes.
     * Updates the reference to the layer where masks will be applied.
     *
     * @param layer The new active paint layer, or null if no layer is selected
     */
    override fun onLayerChanged(layer: PaintLayer?) {
        selectedLayer = layer
    }

    /**
     * Sets whether the tool should work in eraser mode.
     * In eraser mode, the lasso selections will remove from existing masks.
     *
     * @param isEnabled true to enable eraser mode, false for normal additive mode
     */
    override fun setEraserMode(isEnabled: Boolean) {
        isEraseMode = isEnabled
    }
}

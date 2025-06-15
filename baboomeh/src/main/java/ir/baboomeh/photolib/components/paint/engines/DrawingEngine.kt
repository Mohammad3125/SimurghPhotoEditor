package ir.baboomeh.photolib.components.paint.engines

import android.graphics.Canvas
import ir.baboomeh.photolib.components.paint.painters.brushpaint.brushes.Brush
import ir.baboomeh.photolib.utils.gesture.TouchData

/**
 * Interface that defines how brush strokes are rendered onto a canvas.
 *
 * Drawing engines handle the actual rendering of individual brush stamps/marks
 * along a stroke path. Different implementations can provide various rendering
 * strategies such as:
 *
 * - **Real-time rendering**: Draw directly as the user moves their finger
 * - **Cached rendering**: Pre-generate random values for consistent stroke appearance
 * - **Optimized rendering**: Use efficient drawing techniques for performance
 *
 * The engine works in conjunction with:
 * - [Brush] objects that define appearance (size, opacity, texture, etc.)
 * - [TouchData] that provides position, pressure, and movement information
 * - Line smoothers that generate interpolated points along the stroke path
 *
 * Typical workflow:
 * 1. [onMoveBegin] - Initialize stroke state
 * 2. [draw] - Called multiple times to render individual brush stamps
 * 3. [onMoveEnded] - Finalize stroke rendering
 *
 * @see ir.baboomeh.photolib.components.paint.engines.CanvasDrawingEngine
 * @see ir.baboomeh.photolib.components.paint.engines.CachedCanvasEngine
 */
interface DrawingEngine {

    /**
     * Renders a single brush stamp at the specified location.
     *
     * This method is called for each point along a smoothed stroke path.
     * The engine should apply all brush properties (size, opacity, rotation,
     * scatter, etc.) and render the brush stamp accordingly.
     *
     * @param ex X coordinate where to draw the brush stamp
     * @param ey Y coordinate where to draw the brush stamp
     * @param directionalAngle Angle of stroke direction at this point (in degrees)
     *                        Used for brush rotation if auto-rotate is enabled
     * @param canvas Canvas to draw on
     * @param brush Brush definition containing all rendering properties
     * @param drawCount Current draw iteration count (used for animations/variations)
     */
    fun draw(
        ex: Float,
        ey: Float,
        directionalAngle: Float,
        canvas: Canvas,
        brush: Brush,
        drawCount: Int
    )

    /**
     * Called when a new stroke begins.
     *
     * Use this to initialize stroke-specific state such as:
     * - Taper start size
     * - Pressure sensitivity baseline
     * - Random seed values for consistent stroke appearance
     * - Brush blending mode setup
     *
     * @param touchData Initial touch information (position, pressure, time)
     * @param brush Brush definition for this stroke
     */
    fun onMoveBegin(touchData: TouchData, brush: Brush)

    /**
     * Called during stroke movement.
     *
     * Use this to update stroke state that changes over time:
     * - Size variance calculations
     * - Opacity variance calculations
     * - Pressure sensitivity values
     * - Dynamic spacing adjustments
     *
     * @param touchData Current touch information
     * @param brush Brush definition for this stroke
     */
    fun onMove(touchData: TouchData, brush: Brush)

    /**
     * Called when a stroke ends.
     *
     * Use this to:
     * - Finalize taper effects
     * - Reset stroke-specific state
     * - Apply any end-of-stroke effects
     * - Clean up temporary resources
     *
     * @param touchData Final touch information
     * @param brush Brush definition for this stroke
     */
    fun onMoveEnded(touchData: TouchData, brush: Brush)

    /**
     * Sets the eraser mode for this drawing engine.
     *
     * When eraser mode is enabled, the engine should configure rendering
     * to remove content rather than add it (typically using DST_OUT blend mode).
     *
     * @param isEnabled true to enable eraser mode, false to disable
     */
    fun setEraserMode(isEnabled: Boolean)

    /**
     * Returns whether eraser mode is currently enabled.
     *
     * @return true if eraser mode is enabled, false otherwise
     */
    fun isEraserModeEnabled(): Boolean
}

package ir.simurgh.photolib.components.shapes

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path

/**
 * Abstract base class for all drawable shapes in the photo editing library.
 *
 * This class provides a common interface for geometric shapes that can be:
 * - Resized to fit specific dimensions
 * - Drawn on a canvas with custom paint properties
 * - Converted to paths for advanced operations
 * - Transformed using matrices
 * - Cloned for duplication
 *
 * Available shape implementations:
 * - [Circle]: Perfect circles
 * - [Rectangle]: Rectangles and squares
 * - [RoundRect]: Rectangles with rounded corners
 * - [Triangle]: Triangular shapes
 * - [CustomPathShape]: Custom vector shapes from XML or paths
 * @see Circle
 * @see Rectangle
 * @see RoundRect
 * @see Triangle
 * @see CustomPathShape
 */
abstract class Shape {

    /**
     * Resizes the shape to the specified dimensions.
     *
     * This method should update the internal geometry of the shape
     * to fit within the given width and height. The exact behavior
     * depends on the shape type:
     *
     * - Circles: Use the smaller dimension as diameter
     * - Rectangles: Use exact width and height
     * - Complex shapes: Scale proportionally or fit to bounds
     *
     * @param width Target width in pixels
     * @param height Target height in pixels
     *
     * Note: This method should be called before drawing or getting paths.
     */
    abstract fun resize(width: Float, height: Float)

    /**
     * Draws the shape on the specified canvas using the given paint.
     *
     * The paint object determines how the shape appears:
     * - Color and transparency
     * - Fill vs stroke style
     * - Stroke width (for outline styles)
     * - Shader effects (gradients, patterns)
     * - Blend modes
     *
     * @param canvas Canvas to draw the shape on
     * @param paint Paint object defining the visual appearance
     *
     * @throws IllegalStateException if [resize] hasn't been called first
     */
    abstract fun draw(canvas: Canvas, paint: Paint)

    /**
     * Returns a Path object representing the shape's geometry.
     *
     * The returned path can be used for:
     * - Canvas clipping operations
     * - Hit testing and bounds calculation
     * - Path-based animations
     * - Complex shape operations (union, intersection, etc.)
     * - Creating custom shapes by combining paths
     *
     * @return Path object representing this shape's outline
     * @throws IllegalStateException if [resize] hasn't been called first
     */
    abstract fun getPath(): Path

    /**
     * Draws the shape's path into the provided path object.
     *
     * This is useful for combining multiple shapes into a single
     * complex path without creating intermediate Path objects.
     *
     * @param path The target path to draw into
     * @param transform Optional transformation matrix to apply to the shape
     *                 before adding it to the path. If null, no transformation is applied.
     *
     * Example usage:
     * ```kotlin
     * val combinedPath = Path()
     * shape1.drawToPath(combinedPath, null)
     * shape2.drawToPath(combinedPath, translationMatrix)
     * canvas.drawPath(combinedPath, paint)
     * ```
     */
    abstract fun drawToPath(path: Path, transform: Matrix?)

    /**
     * Creates a copy of this shape with the same properties.
     *
     * The cloned shape will have:
     * - The same type and geometry
     * - The same size (if [resize] was called)
     * - Independent state (modifications won't affect the original)
     *
     * @return A new shape instance that is a copy of this one
     * @throws IllegalStateException if the shape type doesn't support cloning
     *
     * Note: Subclasses should override this method to provide proper cloning.
     * The default implementation throws an exception.
     */
    open fun clone(): Shape {
        throw IllegalStateException("Cannot clone an abstract class [${javaClass.name}]")
    }
}

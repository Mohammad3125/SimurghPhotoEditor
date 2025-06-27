package ir.simurgh.photolib.components.paint.painters.cropper.aspect_ratios

import android.graphics.RectF
import ir.simurgh.photolib.components.paint.painters.cropper.HandleBar

/**
 * Abstract base class for implementing different aspect ratio behaviors in cropping operations.
 *
 * This class defines the contract for how crop rectangles should be resized and validated
 * when different aspect ratio constraints are applied. Subclasses implement specific
 * behaviors such as:
 *
 * - **Free aspect ratio**: No constraints, allowing any rectangle dimensions
 * - **Locked aspect ratio**: Maintaining a fixed width-to-height ratio (e.g., 1:1, 4:3, 16:9)
 * - **Custom behaviors**: Specialized ratio handling for specific use cases
 *
 * The aspect ratio system works by:
 * 1. Receiving resize requests with handle information and movement deltas
 * 2. Calculating new rectangle dimensions according to ratio rules
 * 3. Validating results against boundary constraints
 * 4. Providing normalized dimensions for initial rectangle creation
 *
 * This abstraction allows the cropping system to support various aspect ratio modes
 * while maintaining consistent behavior and smooth user interactions.
 */
abstract class AspectRatio {

    /**
     * Calculates new rectangle dimensions when a crop handle is moved.
     *
     * This method applies aspect ratio constraints to resize operations, ensuring
     * that the resulting rectangle maintains the desired proportions while
     * accommodating user input.
     *
     * @param rect The current crop rectangle to be resized
     * @param handleBar Which handle was moved (corner, edge, or null for general resize)
     * @param dx The horizontal movement distance in pixels
     * @param dy The vertical movement distance in pixels
     * @return Modified rectangle with new dimensions respecting aspect ratio constraints
     */
    abstract fun resize(rect: RectF, handleBar: HandleBar?, dx: Float, dy: Float): RectF

    /**
     * Validates and corrects rectangle dimensions to ensure they stay within bounds
     * and maintain proper aspect ratio.
     *
     * This method is called after resize operations to ensure the resulting rectangle:
     * - Stays within the allowed boundary limits
     * - Maintains the correct aspect ratio
     * - Has valid dimensions (positive width/height)
     * - Doesn't exceed minimum or maximum size constraints
     *
     * @param rect The original rectangle before modification
     * @param dirtyRect The modified rectangle that needs validation
     * @param limitRect The boundary rectangle that constrains the crop area
     * @return Validated and corrected rectangle that satisfies all constraints
     */
    abstract fun validate(
        rect: RectF,
        dirtyRect: RectF,
        limitRect: RectF
    ): RectF

    /**
     * Calculates optimal dimensions for a rectangle with this aspect ratio
     * that fits within the specified maximum bounds.
     *
     * This method is used when initially creating crop rectangles or when
     * aspect ratios are changed, ensuring the rectangle:
     * - Maintains the correct aspect ratio
     * - Fits within the available space
     * - Uses maximum possible size while respecting constraints
     *
     * @param maxWidth Maximum allowed width for the rectangle
     * @param maxHeight Maximum allowed height for the rectangle
     * @return Pair containing the optimal (width, height) that respects aspect ratio
     */
    abstract fun normalizeAspectRatio(
        maxWidth: Float,
        maxHeight: Float
    ): Pair<Float, Float>
}

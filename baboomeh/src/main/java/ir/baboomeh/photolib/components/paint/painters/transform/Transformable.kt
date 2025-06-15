package ir.baboomeh.photolib.components.paint.painters.transform

import android.graphics.Canvas
import android.graphics.RectF

/**
 * Abstract base class for objects that can be transformed, positioned, and rendered on a canvas.
 *
 * This class provides the foundation for any drawable object that needs to support:
 * - Rendering on a canvas
 * - Bounds calculation for layout and hit testing
 * - Invalidation notifications when the object changes
 * - Bounds change notifications for layout updates
 * - Cloning for creating copies or snapshots
 *
 * Typical subclasses include text painters, shape painters, bitmap painters, etc.
 * These objects are commonly used in graphics editing applications where objects
 * need to be moved, resized, rotated, and manipulated independently.
 *
 * The invalidation system allows for efficient rendering by only updating when necessary.
 */
abstract class Transformable {

    /**
     * Listener for invalidation and bounds change events.
     * Set this to receive notifications when the transformable needs to be redrawn
     * or when its bounds have changed.
     */
    var onInvalidateListener: OnInvalidate? = null

    /**
     * Calculates and returns the bounding rectangle of this transformable object.
     * The bounds define the rectangular area that completely contains the transformed object.
     *
     * This is used for:
     * - Layout calculations
     * - Hit testing (determining if a point is within the object)
     * - Clipping optimizations
     * - UI selection bounds display
     *
     * @param bounds RectF object that will be populated with the calculated bounds.
     *               Coordinates are in the object's local coordinate system.
     */
    abstract fun getBounds(bounds: RectF)

    /**
     * Renders this transformable object onto the provided canvas.
     *
     * Implementations should draw their content using the current transformation
     * state and styling properties. The canvas may have transformations applied
     * (translation, rotation, scale) that affect how the object appears.
     *
     * @param canvas The canvas to draw on. May have transformations pre-applied.
     */
    abstract fun draw(canvas: Canvas)

    /**
     * Interface for receiving notifications about transformable state changes.
     */
    interface OnInvalidate {
        /**
         * Called when the transformable's visual appearance has changed and needs to be redrawn.
         * This typically happens when properties like color, text, or styling are modified.
         */
        fun onInvalidate()

        /**
         * Called when the transformable's bounds have changed.
         * This happens when the object is resized, content changes, or layout properties
         * are modified. Listeners can use this to update layout or selection handles.
         */
        fun onBoundsChange()
    }

    /**
     * Notifies listeners that this transformable needs to be redrawn.
     * Call this when visual properties change but the bounds remain the same.
     *
     * Examples of when to call this:
     * - Color changes
     * - Text content changes (if bounds don't change)
     * - Style property changes
     */
    fun invalidate() {
        onInvalidateListener?.onInvalidate()
    }

    /**
     * Notifies listeners that this transformable's bounds have changed.
     * Call this when the object's size, position, or shape changes.
     *
     * Examples of when to call this:
     * - Size changes
     * - Text content changes that affect dimensions
     * - Adding/removing visual elements that affect bounds
     */
    fun notifyBoundsChanged() {
        onInvalidateListener?.onBoundsChange()
    }

    /**
     * Creates a deep copy of this transformable object.
     * The clone should be independent of the original and have identical
     * visual appearance and properties.
     *
     * This is used for:
     * - Undo/redo functionality
     * - Creating backups before modifications
     * - Duplicating objects in the UI
     *
     * @return A new instance that is a complete copy of this transformable
     */
    abstract fun clone(): Transformable
}

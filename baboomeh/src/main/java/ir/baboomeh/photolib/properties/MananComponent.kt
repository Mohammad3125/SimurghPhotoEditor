package ir.baboomeh.photolib.properties

import android.graphics.RectF
import android.view.View

interface MananComponent {

    /**
     * Reports boundaries of a component regardless of implementation to rotate a component.
     * Note that a component might return a direct reference to bounds object to avoid allocations. Avoid
     * changing it if possible.
     */
    fun reportBound(): RectF

    /**
     * Reports rotation of a component regardless of implementation to rotate a component.
     */
    fun reportRotation(): Float


    /**
     * Returns current component's scale in x dimension.
     */
    fun reportScaleX(): Float

    /**
     * Returns current component's scale in y dimension.
     */
    fun reportScaleY(): Float

    /**
     * Reports location of point around which bound is rotated.
     */
    fun reportBoundPivotX(): Float

    /**
     * Reports location of point around which bound is rotated.
     */
    fun reportBoundPivotY(): Float

    /**
     * Reports location of point around which component is rotated.
     */
    fun reportPivotX(): Float

    /**
     * Reports location of point around which component is rotated.
     */
    fun reportPivotY(): Float

    /**
     * Applies rotation to target component.
     * @param degree Total degree component should rotate.
     */
    fun applyRotation(degree: Float)

    /**
     * Applies scale to target component.
     * @param scaleFactor Factor that determines how much the component should scale.
     * scale factor of 1f means no scaling is applied.
     */
    fun applyScale(scaleFactor: Float)


    fun applyScale(xFactor: Float, yFactor: Float)

    /**
     * Applies movement to target component.
     * @param dx Total pixel the component should be moved in x direction.
     * @param dy Total pixel the component should be moved in y direction.
     */
    fun applyMovement(dx: Float, dy: Float)

    /**
     * Returns deep copy of component.
     */
    fun clone(): View
}
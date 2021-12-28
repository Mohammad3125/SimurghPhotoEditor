package ir.manan.mananpic.properties

import android.graphics.RectF

interface MananComponent {

    /**
     * Reports boundaries of a component regardless of implementation to rotate a component.
     */
    fun reportBound(): RectF

    /**
     * Reports rotation of a component regardless of implementation to rotate a component.
     */
    fun reportRotation(): Float

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

    /**
     * Applies movement to target component.
     * @param dx Total pixel the component should be moved in x direction.
     * @param dy Total pixel the component should be moved in y direction.
     */
    fun applyMovement(dx: Float, dy: Float)
}
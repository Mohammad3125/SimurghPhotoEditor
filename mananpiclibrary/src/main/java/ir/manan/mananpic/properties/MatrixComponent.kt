package ir.manan.mananpic.properties

import android.graphics.RectF

interface MatrixComponent {

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
}
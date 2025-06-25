package ir.baboomeh.photolib.components.paint.painters.transform

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import androidx.annotation.Size
import androidx.core.graphics.withSave
import ir.baboomeh.photolib.components.paint.painters.transform.transformables.Transformable
import ir.baboomeh.photolib.utils.extensions.setMaximumRect
import ir.baboomeh.photolib.utils.matrix.MananMatrix

/**
 * Data class representing a transformable child object with its transformation state.
 * This class encapsulates all information needed to manage and transform an object.
 *
 * @param transformable The transformable object being managed.
 * @param transformationMatrix The transformation matrix applied to the object.
 * @param polyMatrix The poly-to-poly transformation matrix for free transform mode.
 * @param centerBasePoints The base positions for size change handles.
 * @param meshPoints The current mesh points for transformation calculations.
 * @param bounds The current bounds of the child after transformations.
 * @param targetRect The target rectangle for positioning, or null for default positioning.
 */
open class Child(
    var transformable: Transformable,
    val transformationMatrix: MananMatrix = MananMatrix(),
    val polyMatrix: MananMatrix = MananMatrix(),
    @Size(8) var centerBasePoints: FloatArray = FloatArray(8),
    @Size(8) var cornerBasePoints: FloatArray = FloatArray(8),
    @Size(8) var meshPoints: FloatArray = FloatArray(8),
) {
    protected val bounds: RectF = RectF()

    /** Temporary array for mapped mesh points calculations */
    protected val tempMappedPoints = FloatArray(8)

    open fun initialize(targetRect: RectF) {
        updateBounds()

        transformationMatrix.reset()
        polyMatrix.reset()

        transformationMatrix.setRectToRect(
            bounds,
            targetRect,
            Matrix.ScaleToFit.CENTER
        )

        val w = bounds.width()
        val h = bounds.height()
        val wh = w * 0.5f
        val hh = h * 0.5f

        cornerBasePoints[0] = 0f
        cornerBasePoints[1] = 0f
        cornerBasePoints[2] = w
        cornerBasePoints[3] = 0f
        cornerBasePoints[4] = w
        cornerBasePoints[5] = h
        cornerBasePoints[6] = 0f
        cornerBasePoints[7] = h

        centerBasePoints[0] = wh
        centerBasePoints[1] = 0f
        centerBasePoints[2] = w
        centerBasePoints[3] = hh
        centerBasePoints[4] = wh
        centerBasePoints[5] = h
        centerBasePoints[6] = 0f
        centerBasePoints[7] = hh

        cornerBasePoints.copyInto(meshPoints)
    }

    open fun mapMeshPointsByMatrices(mappingMatrix: Matrix, array: FloatArray) {
        mappingMatrix.set(transformationMatrix)
        meshPoints.copyInto(array)
        mappingMatrix.mapPoints(array)
    }

    open fun invertMapMeshPointsByMatrices(
        mappingMatrix: Matrix,
        array: FloatArray,
    ) {
        transformationMatrix.invert(mappingMatrix)
        mappingMatrix.mapPoints(array)
    }

    open fun mapSizeChangePointsByMatrices(array: FloatArray) {
        calculatePolyMatrix()
        centerBasePoints.copyInto(array)
        polyMatrix.mapPoints(array)
        transformationMatrix.mapPoints(array)
    }

    open fun calculatePolyMatrix() {
        polyMatrix.setPolyToPoly(cornerBasePoints, 0, meshPoints, 0, 4)
    }

    /**
     * Calculates and updates the current bounds of the child based on its transformation matrices.
     * This method applies both the transformation matrix and poly matrix to get the final bounds.
     *
     * @param mappingMatrix The mapping matrix to use for transformation calculations.
     */
    open fun getTransformedBounds(mappingMatrix: Matrix, targetRect: RectF) {
        // Map mesh points to get the transformed coordinates
        mapMeshPointsByMatrices(mappingMatrix, tempMappedPoints)

        // Calculate the bounding rectangle from the mapped points
        updateBoundsFromPoints(tempMappedPoints, targetRect)
    }

    /**
     * Updates the bounds rectangle based on the provided point array.
     * The points array should contain 8 values representing 4 points (x1,y1,x2,y2,x3,y3,x4,y4).
     *
     * @param points Array of 8 floats representing the transformed corner points.
     */
    protected open fun updateBoundsFromPoints(points: FloatArray, targetRect: RectF) {
        if (points.size < 8) return
        targetRect.setMaximumRect(points)
    }

    /**
     * Gets the current bounds of the child.
     * Note: Call calculateAndUpdateBounds() first to ensure bounds are up-to-date.
     *
     * @param rect The rectangle to store the bounds in.
     */
    open fun getBounds(rect: RectF) {
        rect.set(bounds)
    }

    open fun updateBounds() {
        transformable.getBounds(bounds)

        val w = bounds.width()
        val h = bounds.height()

        val wh = w * 0.5f
        val hh = h * 0.5f

        cornerBasePoints[0] = 0f
        cornerBasePoints[1] = 0f
        cornerBasePoints[2] = w
        cornerBasePoints[3] = 0f
        cornerBasePoints[4] = w
        cornerBasePoints[5] = h
        cornerBasePoints[6] = 0f
        cornerBasePoints[7] = h

        centerBasePoints[0] = wh
        centerBasePoints[1] = 0f
        centerBasePoints[2] = w
        centerBasePoints[3] = hh
        centerBasePoints[4] = wh
        centerBasePoints[5] = h
        centerBasePoints[6] = 0f
        centerBasePoints[7] = hh

        calculatePolyMatrix()
    }

    open fun draw(canvas: Canvas) {
        canvas.withSave {
            concat(transformationMatrix)
            concat(polyMatrix)
            transformable.draw(canvas)
        }
    }

    open fun clone(cloneTransformable: Boolean = false): Child {
        return Child(
            if (cloneTransformable) transformable.clone() else transformable,
            MananMatrix().apply {
                set(transformationMatrix)
            },
            MananMatrix().apply {
                set(polyMatrix)
            },
            centerBasePoints.clone(),
            cornerBasePoints.clone(),
            meshPoints.clone(),
        )
    }

    open fun set(otherChild: Child) {
        transformable = otherChild.transformable
        transformationMatrix.set(otherChild.transformationMatrix)
        polyMatrix.set(otherChild.polyMatrix)
        centerBasePoints = otherChild.centerBasePoints.clone()
        meshPoints = otherChild.meshPoints
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Child

        if (transformable != other.transformable) return false
        if (transformationMatrix != other.transformationMatrix) return false
        if (polyMatrix != other.polyMatrix) return false
        if (!centerBasePoints.contentEquals(other.centerBasePoints)) return false
        if (!meshPoints.contentEquals(other.meshPoints)) return false
        if (bounds != other.bounds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transformable.hashCode()
        result = 31 * result + transformationMatrix.hashCode()
        result = 31 * result + polyMatrix.hashCode()
        result = 31 * result + centerBasePoints.contentHashCode()
        result = 31 * result + meshPoints.contentHashCode()
        result = 31 * result + bounds.hashCode()
        return result
    }
}
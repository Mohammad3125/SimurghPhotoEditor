package ir.baboomeh.photolib.components.paint.painters.transform

import android.graphics.Matrix
import android.graphics.RectF
import ir.baboomeh.photolib.components.paint.painters.transform.transformables.Transformable
import ir.baboomeh.photolib.utils.matrix.MananMatrix

/**
 * Data class representing a transformable child object with its transformation state.
 * This class encapsulates all information needed to manage and transform an object.
 *
 * @param transformable The transformable object being managed.
 * @param transformationMatrix The transformation matrix applied to the object.
 * @param polyMatrix The poly-to-poly transformation matrix for free transform mode.
 * @param baseSizeChangeArray The base positions for size change handles.
 * @param meshPoints The current mesh points for transformation calculations.
 * @param targetRect The target rectangle for positioning, or null for default positioning.
 */
data class Child(
    var transformable: Transformable,
    val transformationMatrix: MananMatrix,
    val polyMatrix: MananMatrix,
    var baseSizeChangeArray: FloatArray,
    var meshPoints: FloatArray,
    val targetRect: RectF?
) {

    fun mapMeshPointsByMatrices(mappingMatrix: Matrix, array: FloatArray) {
        mappingMatrix.set(transformationMatrix)
        meshPoints.copyInto(array)
        mappingMatrix.mapPoints(array)
    }

    fun mapSizeChangePointsByMatrices(mappingMatrix: Matrix, array: FloatArray) {
        baseSizeChangeArray.copyInto(array)
        mappingMatrix.set(transformationMatrix)
        mappingMatrix.preConcat(polyMatrix)
        mappingMatrix.mapPoints(array)
    }

    fun clone(cloneTransformable: Boolean = false): Child {
        return Child(
            if (cloneTransformable) transformable.clone() else transformable,
            MananMatrix().apply {
                set(transformationMatrix)
            },
            MananMatrix().apply {
                set(polyMatrix)
            },
            baseSizeChangeArray.clone(),
            meshPoints.clone(),
            targetRect
        )
    }

    fun set(otherChild: Child) {
        transformable = otherChild.transformable
        transformationMatrix.set(otherChild.transformationMatrix)
        polyMatrix.set(otherChild.polyMatrix)
        baseSizeChangeArray = otherChild.baseSizeChangeArray.clone()
        meshPoints = otherChild.meshPoints

        otherChild.targetRect?.let {
            targetRect?.set(it)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Child

        if (transformable != other.transformable) return false
        if (transformationMatrix != other.transformationMatrix) return false
        if (polyMatrix != other.polyMatrix) return false
        if (!baseSizeChangeArray.contentEquals(other.baseSizeChangeArray)) return false
        if (!meshPoints.contentEquals(other.meshPoints)) return false
        if (targetRect != other.targetRect) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transformable.hashCode()
        result = 31 * result + transformationMatrix.hashCode()
        result = 31 * result + polyMatrix.hashCode()
        result = 31 * result + baseSizeChangeArray.contentHashCode()
        result = 31 * result + meshPoints.contentHashCode()
        result = 31 * result + (targetRect?.hashCode() ?: 0)
        return result
    }
}

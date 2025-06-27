package ir.simurgh.photolib.components.paint.painters.transform.smartguideline

import android.graphics.Matrix
import android.graphics.RectF
import ir.simurgh.photolib.components.paint.painters.transform.Child
import ir.simurgh.photolib.utils.extensions.setMaximumRect
import ir.simurgh.photolib.utils.gesture.GestureUtils
import ir.simurgh.photolib.utils.matrix.SimurghMatrix
import kotlin.math.PI
import kotlin.math.atan2

open class DefaultRotationSmartGuidelineDetector() : SmartRotationGuidelineDetector {
    /** Array of target degrees for rotation smart guidelines (null if disabled). */
    protected var smartRotationDegreeHolder: FloatArray? = null

    /** Original rotation degree holder for external access (excludes internal 360-degree addition). */
    protected var originalRotationHolder: FloatArray? = null

    /** Temporary rectangle for various calculations to avoid object allocation. */
    protected val tempRect by lazy {
        RectF()
    }

    /** Array holding coordinates for the smart rotation guideline (x1, y1, x2, y2). */
    protected var smartRotationLineHolder = FloatArray(4)

    protected val mappingMatrix by lazy {
        SimurghMatrix()
    }

    /** Array holding mapped mesh points for drawing after transformation calculations. */
    protected val mappedMeshPoints by lazy {
        FloatArray(8)
    }

    /**
     * The degree range for smart rotation guideline detection.
     * Objects will snap to target rotations when within this range.
     *
     * @throws IllegalStateException if value is less than 0 or greater than 360.
     */
    override var rotationDetectionRange: Float = 2f
        set(value) {
            if (value < 0f || value > 360) throw IllegalStateException("this value should not be less than 0 or greater than 360")
            field = value
        }

    override var rotationSmartGuidelineDegrees: FloatArray = floatArrayOf()
        set(value) {
            if (value.any { degree -> (degree < 0 || degree > 359) }) throw IllegalStateException(
                "array elements should be between 0-359 degrees"
            )
            if (value.isEmpty()) throw IllegalStateException("array should contain at least 1 element")

            field = value

            smartRotationDegreeHolder = if (value.any { it == 0f } && !value.any { it == 360f }) {
                FloatArray(value.size + 1).also { array ->
                    value.copyInto(array)
                    array[array.lastIndex] = 360f
                }
            } else {
                value
            }
        }

    override fun detectRotationGuidelines(child: Child): RotationGuidelineResult? {
        val imageRotation =
            child.transformationMatrix.run {
                GestureUtils.mapTo360(
                    -atan2(
                        getSkewX(true),
                        (getScaleX())
                    ) * (180f / PI)
                ).toFloat()
            }

        return smartRotationDegreeHolder?.find { imageRotation in (it - rotationDetectionRange)..(it + rotationDetectionRange) }
            ?.let { snapDegree ->
                child.mapMeshPointsByMatrices(mappingMatrix, mappedMeshPoints)

                tempRect.setMaximumRect(mappedMeshPoints)

                val centerXBound = tempRect.centerX()

                smartRotationLineHolder[0] = (centerXBound)
                smartRotationLineHolder[1] = (-10000f)
                smartRotationLineHolder[2] = (centerXBound)
                smartRotationLineHolder[3] = (10000f)

                mappingMatrix.setRotate(snapDegree, tempRect.centerX(), tempRect.centerY())
                mappingMatrix.mapPoints(smartRotationLineHolder)

                RotationGuidelineResult(Matrix().apply {
                    setRotate(
                        snapDegree - imageRotation, tempRect.centerX(),
                        tempRect.centerY()
                    )
                }, Guideline(smartRotationLineHolder, false))
            }
    }

    override fun resetRotationSmartGuidelineDegrees() {
        originalRotationHolder = null
        smartRotationDegreeHolder = null
    }
}
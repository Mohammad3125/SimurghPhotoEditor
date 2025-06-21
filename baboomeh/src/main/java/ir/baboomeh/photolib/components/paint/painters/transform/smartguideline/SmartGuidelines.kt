package ir.baboomeh.photolib.components.paint.painters.transform.smartguideline

import android.graphics.Matrix
import android.graphics.RectF
import androidx.annotation.Size
import ir.baboomeh.photolib.components.paint.painters.transform.Child

interface SmartAlignmentGuidelineDetector {

    var alignmentAcceptableDistance: Float

    var alignmentFlags : Int

    fun detectAlignmentGuidelines(
        selectedTransformable: Child,
        otherTransformable: List<Child>,
        pageBounds: RectF
    ): AlignmentGuidelineResult?
}

interface SmartRotationGuidelineDetector {

    var rotationDetectionRange : Float

    var rotationSmartGuidelineDegrees : FloatArray

    fun detectRotationGuidelines(
        child: Child
    ): RotationGuidelineResult?

    fun resetRotationSmartGuidelineDegrees()
}

/**
 * A class holding static flags for smart guideline. User should
 * set the desired flags in [setSmartGuidelineFlags] method.
 */
/**
 * Class containing static constants for smart guideline types.
 * These flags can be combined using bitwise OR operations to enable specific guidelines.
 */
class Guidelines {
    companion object {
        const val NONE = 0
        const val ALL = 1
        const val LEFT_LEFT = 2
        const val LEFT_RIGHT = 4
        const val RIGHT_LEFT = 8
        const val RIGHT_RIGHT = 16
        const val TOP_TOP = 32
        const val TOP_BOTTOM = 64
        const val BOTTOM_TOP = 128
        const val BOTTOM_BOTTOM = 256
        const val CENTER_X = 512
        const val CENTER_Y = 1024
    }
}


data class AlignmentGuidelineResult(
    val lines: List<Guideline>,
    val transformation: Matrix,
)

data class RotationGuidelineResult(
    val transformation: Matrix,
    val guideline: Guideline
)

data class Guideline(
    @Size(4) val lineArray: FloatArray,
    val isDashed: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Guideline

        if (isDashed != other.isDashed) return false
        if (!lineArray.contentEquals(other.lineArray)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isDashed.hashCode()
        result = 31 * result + lineArray.contentHashCode()
        return result
    }
}
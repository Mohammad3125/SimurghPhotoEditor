package ir.manan.mananpic.utils.gesture

import kotlin.math.atan2

/**
 * A class with bunch of utility functions to help gesture detectors.
 */
class GestureUtils {
    companion object {
        /**
         * This function calculates the degree of rotation with the given delta x and y.
         * The rotation is calculated by the angle of two touch points.
         * @param deltaX Difference between x in touches (two pointers).
         * @param deltaY Difference between y in touches (two pointers).
         */
        fun calculateAngle(deltaX: Double, deltaY: Double): Float {
            return Math.toDegrees(
                atan2(
                    deltaX,
                    deltaY
                )
            ).toFloat()
        }

        /**
         * Converts the current degree to be between 0-360 degrees.
         */
        fun mapTo360(degree: Float): Float {
            return when {
                degree > 360 -> {
                    degree - 360
                }
                degree < 0f -> {
                    degree + 360
                }
                else -> degree
            }
        }

    }
}
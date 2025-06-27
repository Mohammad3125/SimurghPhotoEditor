package ir.simurgh.photolib.utils.gesture

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
                degree >= 360 -> {
                    degree - 360
                }
                degree < 0f -> {
                    degree + 360
                }
                else -> degree
            }
        }

        /**
         * Converts the current degree to be between 0-360 degrees.
         */
        fun mapTo360(degree: Double): Double {
            return when {
                degree >= 360 -> {
                    degree - 360
                }
                degree < 0f -> {
                    degree + 360
                }
                else -> degree
            }
        }

        /**
         * Calculates if a vector is in range of target point.
         * @param x Touch point x.
         * @param y Touch point y.
         * @param targetX Target location x.
         * @param targetY Target location y.
         * @param range Acceptable range to check if current touch location is near target location.
         */
        fun isNearTargetPoint(
            x: Float,
            y: Float,
            targetX: Float,
            targetY: Float,
            range: Float,
        ) = (x in (targetX - range)..(targetX + range) && y in (targetY - range)..(targetY + range))

    }
}
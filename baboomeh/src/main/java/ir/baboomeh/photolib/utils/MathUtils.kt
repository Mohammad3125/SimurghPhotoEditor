package ir.baboomeh.photolib.utils

import ir.baboomeh.photolib.utils.MathUtils.Companion.convertIntRange


/**
 * Utility class providing mathematical operations and conversions used throughout the photo editing library.
 *
 * This class contains commonly used mathematical functions that don't fit into specific categories
 * but are essential for various operations like range mapping, coordinate transformations,
 * and value scaling.
 *
 * All methods are static and the class is designed to be used without instantiation.
 */
class MathUtils {
    companion object {
        /**
         * Converts a value from one integer range to another integer range.
         *
         * This function performs linear interpolation to map a value from an input range
         * to an output range. It's commonly used for:
         * - Converting between different coordinate systems
         * - Scaling values for UI components
         * - Mapping slider values to actual parameter ranges
         *
         * The conversion formula is:
         * ```
         * output = ((input - inputStart) * (outputEnd - outputStart)) / (inputEnd - inputStart) + outputStart
         * ```
         *
         * Example usage:
         * ```kotlin
         * // Convert progress (0-100) to opacity (0-255)
         * val opacity = MathUtils.convertIntRange(0, 100, 0, 255, progress)
         *
         * // Convert slider value (0-10) to actual size (50-200)
         * val size = MathUtils.convertIntRange(0, 10, 50, 200, sliderValue)
         * ```
         *
         * @param inputStart Minimum value of the input range
         * @param inputEnd Maximum value of the input range
         * @param outputStart Minimum value of the output range
         * @param outputEnd Maximum value of the output range
         * @param input The value to convert (must be within input range)
         * @return The converted value in the output range
         * @throws IllegalStateException if input is outside the input range
         */
        fun convertIntRange(
            inputStart: Int,
            inputEnd: Int,
            outputStart: Int,
            outputEnd: Int,
            input: Int
        ): Int {
            if (input > inputEnd || input < inputStart) throw IllegalStateException("Input exceeds the input range")
            return (((input - inputStart) * (outputEnd - outputStart)) / (inputEnd - inputStart)) + outputStart
        }

        /**
         * Converts a value from one floating-point range to another floating-point range.
         *
         * This is the floating-point version of [convertIntRange], providing higher precision
         * for mathematical operations. It's commonly used for:
         * - Smooth animations and transitions
         * - Precise coordinate mapping
         * - Color space conversions
         * - Physics calculations
         *
         * The conversion formula is:
         * ```
         * output = ((input - inputStart) * (outputEnd - outputStart)) / (inputEnd - inputStart) + outputStart
         * ```
         *
         * Example usage:
         * ```kotlin
         * // Convert normalized value (0.0-1.0) to angle (0.0-360.0)
         * val angle = MathUtils.convertFloatRange(0f, 1f, 0f, 360f, normalizedValue)
         *
         * // Convert touch pressure (0.0-1.0) to brush size (10.0-50.0)
         * val brushSize = MathUtils.convertFloatRange(0f, 1f, 10f, 50f, pressure)
         * ```
         *
         * @param inputStart Minimum value of the input range
         * @param inputEnd Maximum value of the input range
         * @param outputStart Minimum value of the output range
         * @param outputEnd Maximum value of the output range
         * @param input The value to convert (must be within input range)
         * @return The converted value in the output range
         * @throws IllegalStateException if input is outside the input range
         */
        fun convertFloatRange(
            inputStart: Float,
            inputEnd: Float,
            outputStart: Float,
            outputEnd: Float,
            input: Float
        ): Float {
            if (input > inputEnd || input < inputStart) throw IllegalStateException("Input exceeds the input range")
            return (((input - inputStart) * (outputEnd - outputStart)) / (inputEnd - inputStart)) + outputStart
        }
    }
}

package ir.manan.mananpic.utils

class MathUtils {
    companion object {
        /**
         * takes a range of numbers and converts them onto another
         * for example takes 0 - 35 range and converts it to 0 - 100
         * @param inputStart minimum number of input number range
         * @param inputEnd maximum number of input number range
         * @param outputStart minimum range of output number range
         * @param outputEnd maximum range of output number range
         * @param input current number in input range
         * @return returns the converted range of input to output
         */
        fun converFloatRange(
            inputStart: Int,
            inputEnd: Int,
            outputStart: Int,
            outputEnd: Int,
            input: Int
        ): Int {
            if (input > inputEnd || input < inputStart) throw IllegalStateException("Input exceeds the input range")
            return (((input - inputStart) * (outputEnd - outputStart)) / (inputEnd - inputStart)) + outputStart
        }
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
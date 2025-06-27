package ir.simurgh.photolib.components.paint.painters.coloring.flood

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.get
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Scanline-based implementation of the flood fill algorithm.
 *
 * This implementation uses a scanline approach for efficient flood filling, which works by:
 * 1. Starting from a seed point, finding the left and right boundaries of the current scanline
 * 2. Filling the entire scanline between these boundaries
 * 3. Adding adjacent scan lines (above and below) to the processing queue
 * 4. Repeating until all connected pixels are processed
 *
 * This approach is more efficient than pixel-by-pixel flood fill as it processes
 * entire horizontal lines at once, reducing the number of operations needed.
 */
open class FloodFillScanline : FloodFill {

    /** Queue for storing coordinates (x, y pairs) of scan-lines to be processed */
    protected val queue by lazy {
        LinkedList<Int>()
    }

    /**
     * Performs flood fill using the scanline algorithm.
     *
     * The algorithm fills all pixels connected to the starting point that have colors
     * similar to the target color within the specified threshold tolerance.
     *
     * @param bitmap The bitmap to perform flood fill on
     * @param ex Starting X coordinate for the fill operation
     * @param ey Starting Y coordinate for the fill operation
     * @param replaceColor The color to fill the connected region with
     * @param threshold Color similarity tolerance (0.0-1.0) where 0.0 requires exact match
     */
    override fun fill(bitmap: Bitmap, ex: Int, ey: Int, replaceColor: Int, threshold: Float) {
        // Convert threshold from 0.0-1.0 range to 0-255 range for color comparison
        val tolerance = (threshold * 255).roundToInt()

        val width = bitmap.width
        val height = bitmap.height

        // Get the target color at the starting point
        val targetColor = bitmap[ex, ey]

        // If we're already the right color, no need to fill
        if (colorsEqual(targetColor, replaceColor, tolerance)) return

        // Extract all pixels for efficient processing
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Initialize queue with starting coordinates
        queue.add(ex)
        queue.add(ey)

        // Process all queued scan-lines
        while (queue.isNotEmpty()) {
            val currX = queue.poll()!!
            val currY = queue.poll()!!

            var leftX = currX
            // Find left boundary of current scanline
            while (leftX >= 0 && colorsEqual(
                    pixels[currY * width + leftX],
                    targetColor,
                    tolerance
                )
            ) {
                leftX--
            }
            leftX++ // Adjust to last valid position

            var rightX = currX
            // Find right boundary of current scanline
            while (rightX < width && colorsEqual(
                    pixels[currY * width + rightX],
                    targetColor,
                    tolerance
                )
            ) {
                rightX++
            }
            rightX-- // Adjust to last valid position

            // Fill the entire scanline from left to right boundary
            for (i in leftX..rightX) {
                pixels[currY * width + i] = replaceColor

                // Check scanline above for pixels that need filling
                if (currY > 0 && colorsEqual(
                        pixels[(currY - 1) * width + i],
                        targetColor,
                        tolerance
                    )
                ) {
                    queue.add(i)
                    queue.add(currY - 1)
                }

                // Check scanline below for pixels that need filling
                if (currY < height - 1 && colorsEqual(
                        pixels[(currY + 1) * width + i],
                        targetColor,
                        tolerance
                    )
                ) {
                    queue.add(i)
                    queue.add(currY + 1)
                }
            }
        }

        // Apply the modified pixels back to the bitmap
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * Determines if two colors are equal within the specified tolerance.
     *
     * Compares each color component (ARGB) individually and considers colors equal
     * if the difference in each component is within the tolerance threshold.
     *
     * @param c1 First color to compare
     * @param c2 Second color to compare
     * @param tolerance Maximum allowed difference per color component (0-255)
     * @return true if colors are considered equal within tolerance, false otherwise
     */
    protected open fun colorsEqual(c1: Int, c2: Int, tolerance: Int): Boolean {
        // Exact match optimization for zero tolerance
        if (tolerance == 0) return c1 == c2

        // Extract ARGB components for first color
        val alpha1 = Color.alpha(c1)
        val red1 = Color.red(c1)
        val green1 = Color.green(c1)
        val blue1 = Color.blue(c1)

        // Extract ARGB components for second color
        val alpha2 = Color.alpha(c2)
        val red2 = Color.red(c2)
        val green2 = Color.green(c2)
        val blue2 = Color.blue(c2)

        // Check if all components are within tolerance
        return (abs(alpha1 - alpha2) <= tolerance) &&
                (abs(red1 - red2) <= tolerance) &&
                (abs(green1 - green2) <= tolerance) &&
                (abs(blue1 - blue2) <= tolerance)
    }
}

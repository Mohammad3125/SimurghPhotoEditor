package ir.baboomeh.photolib.components.paint.painters.coloring.flood

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.get
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.roundToInt

class FloodFillScanline : FloodFill {
    private val queue by lazy {
        LinkedList<Int>()
    }

    override fun fill(bitmap: Bitmap, ex: Int, ey: Int, replaceColor: Int, threshold: Float) {

        val tolerance = (threshold * 255).roundToInt()

        val width = bitmap.width
        val height = bitmap.height

        // Get the target color at the starting point
        val targetColor = bitmap[ex, ey]

        // If we're already the right color, return
        if (colorsEqual(targetColor, replaceColor, tolerance)) return

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        queue.add(ex)
        queue.add(ey)

        while (queue.isNotEmpty()) {
            val currX = queue.poll()!!
            val currY = queue.poll()!!

            var leftX = currX
            // Find left boundary
            while (leftX >= 0 && colorsEqual(
                    pixels[currY * width + leftX],
                    targetColor,
                    tolerance
                )
            ) {
                leftX--
            }
            leftX++

            var rightX = currX
            // Find right boundary
            while (rightX < width && colorsEqual(
                    pixels[currY * width + rightX],
                    targetColor,
                    tolerance
                )
            ) {
                rightX++
            }
            rightX--

            // Fill the scanline
            for (i in leftX..rightX) {
                pixels[currY * width + i] = replaceColor

                // Check above
                if (currY > 0 && colorsEqual(
                        pixels[(currY - 1) * width + i],
                        targetColor,
                        tolerance
                    )
                ) {
                    queue.add(i)
                    queue.add(currY - 1)
                }

                // Check below
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

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun colorsEqual(c1: Int, c2: Int, tolerance: Int): Boolean {
        if (tolerance == 0) return c1 == c2

        val alpha1 = Color.alpha(c1)
        val red1 = Color.red(c1)
        val green1 = Color.green(c1)
        val blue1 = Color.blue(c1)

        val alpha2 = Color.alpha(c2)
        val red2 = Color.red(c2)
        val green2 = Color.green(c2)
        val blue2 = Color.blue(c2)

        return (abs(alpha1 - alpha2) <= tolerance) &&
                (abs(red1 - red2) <= tolerance) &&
                (abs(green1 - green2) <= tolerance) &&
                (abs(blue1 - blue2) <= tolerance)
    }
}
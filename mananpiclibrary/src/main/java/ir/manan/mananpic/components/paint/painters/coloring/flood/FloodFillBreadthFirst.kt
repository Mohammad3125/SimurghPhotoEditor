package ir.manan.mananpic.components.paint.painters.coloring.flood

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import androidx.core.graphics.alpha
import java.util.*

class FloodFillBreadthFirst : FloodFill {
    private val queue by lazy {
        LinkedList<Point>()
    }

    private var hsvHolder = FloatArray(3)
    private lateinit var visitArray: Array<IntArray>
    private var firstSelectedColor = 0

    override fun fill(bitmap: Bitmap, ex: Int, ey: Int, replaceColor: Int, threshold: Float) {
        firstSelectedColor = bitmap.getPixel(ex, ey)

        visitArray = Array(bitmap.width) {
            IntArray(bitmap.height) {
                0
            }
        }

        queue.add(Point(ex, ey))

//        Color.colorToHSV(firstSelectedColor, hsvHolder)
//
//        println(
//            "selected color hue ${hsvHolder[0]} saturation ${hsvHolder[1]} brightness ${hsvHolder[2]}"
//        )
//
//        println(
//            "selected color red ${Color.red(firstSelectedColor)}   green ${
//                Color.green(
//                    firstSelectedColor
//                )
//            }    blue ${Color.blue(firstSelectedColor)}    alpha ${
//                Color.alpha(
//                    firstSelectedColor
//                )
//            }"
//        )
//
//        Color.colorToHSV(replaceColor, hsvHolder)
//
//        println(
//            "replace color hue ${hsvHolder[0]} saturation ${hsvHolder[1]} brightness ${hsvHolder[2]}"
//        )
//
//        println(
//            "replace color red ${Color.red(replaceColor)}   green ${Color.green(replaceColor)}    blue ${
//                Color.blue(
//                    replaceColor
//                )
//            }    alpha ${
//                Color.alpha(
//                    replaceColor
//                )
//            }"
//        )


        visitArray[ex][ey] = 1

        while (queue.isNotEmpty()) {

            val p = queue.pop()

            val x1 = p.x
            val y1 = p.y


            bitmap.setPixel(x1, y1, replaceColor)

            var ix = x1 + 1

            if (isValid(
                    bitmap,
                    ix,
                    y1
                ) && visitArray[ix][y1] == 0 && comparePixel(
                    bitmap.getPixel(ix, y1),
                    threshold
                )
            ) {
                queue.add(Point(ix, y1))
                visitArray[ix][y1] = 1
            }

            ix = x1 - 1
            // For Downside Pixel or Cell
            if (isValid(
                    bitmap,
                    ix,
                    y1
                ) && visitArray[ix][y1] == 0 && comparePixel(
                    bitmap.getPixel(ix, y1),
                    threshold
                )
            ) {
                queue.add(Point(ix, y1))
                visitArray[ix][y1] = 1
            }

            var iy: Int = y1 + 1

            // For Right side Pixel or Cell
            if (isValid(bitmap, x1, iy) && visitArray[x1][iy] == 0 && comparePixel(
                    bitmap.getPixel(
                        x1,
                        iy
                    ), threshold
                )
            ) {
                queue.add(Point(x1, iy))
                visitArray[x1][iy] = 1
            }

            iy = y1 - 1
            // For Left side Pixel or Cell
            if (isValid(bitmap, x1, iy) && visitArray[x1][iy] == 0 && comparePixel(
                    bitmap.getPixel(
                        x1,
                        iy
                    ), threshold
                )
            ) {
                queue.add(Point(x1, iy))
                visitArray[x1][iy] = 1
            }
        }

    }

    private fun comparePixel(first: Int, threshold: Float): Boolean {
        if (first == firstSelectedColor) {
            return true
        }

        Color.colorToHSV(first, hsvHolder)

        val firstSaturation = hsvHolder[1]
        val firstBrightness = hsvHolder[2]

        val alpha = first.alpha / 255f

        return if (firstSaturation == 0f && firstBrightness == 0f) {
            (alpha <= threshold)
        } else if (firstSaturation == 0f) {
            (firstBrightness <= threshold)
        } else {
            (firstSaturation <= threshold)
        }

    }

    private fun isValid(bitmap: Bitmap, ex: Int, ey: Int): Boolean {
        return (ex.coerceIn(0, bitmap.width - 1) == ex) && (ey.coerceIn(0, bitmap.height - 1) == ey)
    }
}
package ir.manan.mananpic.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Region
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class BitmapPathConverter {
    private val defaultDispatcher = Dispatchers.Default

    suspend fun bitmapToPath(bitmap: Bitmap, alphaThreshold: Int = 128): Path =
        withContext(defaultDispatcher) {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)

            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val region = Region()
            val tempRect = Rect()

            val deferredResults = (0 until height).map { y ->
                async {
                    val rowRegion = Region()
                    var startX = -1

                    for (x in 0 until width) {
                        val alpha = Color.alpha(pixels[y * width + x])

                        if (alpha > alphaThreshold) {
                            if (startX == -1) startX = x
                        } else if (startX != -1) {
                            tempRect.set(startX, y, x, y + 1)
                            rowRegion.op(tempRect, Region.Op.UNION)
                            startX = -1
                        }
                    }

                    if (startX != -1) {
                        tempRect.set(startX, y, width, y + 1)
                        rowRegion.op(tempRect, Region.Op.UNION)
                    }

                    rowRegion
                }
            }

            deferredResults.awaitAll().forEach { rowRegion ->
                region.op(rowRegion, Region.Op.UNION)
            }

            Path().apply {
                region.getBoundaryPath(this)
            }
        }
}
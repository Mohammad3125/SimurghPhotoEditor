package ir.manan.mananpic.utils

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.core.graphics.alpha
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * A class consists of bitmap utility functions.
 */
class BitmapUtility {
    companion object {
        /**
         * Returns visible parts of an image by removing extra transparent pixels. Bitmap should
         * have alpha channel otherwise it will throw an exception.
         * @param bitmap bitmap to perform visible pixel checking on.
         * @param sensitivity sensitivity of alpha channel. higher number will make boundaries closer to the edges.
         * the sensitivity value is between 0 and 254 (technically between 0 and 255 but 255 would make image all transparent because it will be filtered.)
         * @throws IllegalStateException if bitmap width,height is 0 or bitmap doesn't support alpha channel.
         * @throws IllegalArgumentException if sensitivity value is not between 0 and 244.
         * @return [Rect] representing visible boundaries of image.
         */
        fun getVisiblePixelsRectangle(bitmap: Bitmap, sensitivity: Int = 0): Rect {

            if (bitmap.width == 0 || bitmap.height == 0) throw IllegalStateException("bitmap height or width is 0")
            if (!bitmap.hasAlpha()) throw IllegalStateException("bitmap does not support alpha channel")
            if (sensitivity !in 0..254) throw IllegalArgumentException("sensitivity should be between 0 and 254")

            var visiblePixelX = bitmap.width
            var visiblePixelY = bitmap.height
            var visiblePixelWidth = 0
            var visiblePixelHeight = 0

            var continueCheckingX = true
            var firstPixelOccurrence = false
            var firstVisiblePixelX = false

            outerLoop@ for (i in 0 until bitmap.height) {
                for (b in 0 until bitmap.width) {
                    val pixelColor = bitmap.getPixel(b, i)
                    if (pixelColor.alpha > sensitivity) {

                        // If it's the first pixel and it's not transparent.
                        if (b == 0) {
                            // Set x to 0 because then it will be returned by function (if we don't set it, it's default value will mess the output.)
                            visiblePixelX = 0
                            // Do not check x anymore, only focus on width and y and height.
                            continueCheckingX = false
                        }

                        // Set the first pixel occurrence as y.
                        if (!firstPixelOccurrence) {
                            visiblePixelY = i
                            // Do not let that happen again.
                            firstPixelOccurrence = true
                        }

                        // Height would be determined as long as visible pixel is on the screen.
                        visiblePixelHeight = i

                        // Determine the width, if current width is greater than the last on.
                        if (b > visiblePixelWidth)
                            visiblePixelWidth = b

                        // Set the x once. If there are wider visible pixels available, replace it with current one and if first visible pixel
                        // was the first pixel in image, do not continue checking for x axis.
                        if (b < visiblePixelX && !firstVisiblePixelX && continueCheckingX) {
                            visiblePixelX = b
                            firstVisiblePixelX = true
                        }
                    }
                }
                // Make this flag false for next row.
                firstVisiblePixelX = false
            }

            // If either width of height after removing transparent pixels are 0, throw exception.
            if (visiblePixelWidth == 0) throw IllegalStateException("bitmap width is 0")
            if (visiblePixelHeight == 0) throw IllegalStateException("bitmap height is 0")

            // Finally return the rectangle of only visible part of image.
            return Rect(
                visiblePixelX,
                visiblePixelY,
                visiblePixelWidth + 1,
                visiblePixelHeight + 1
            )
        }

        /**
         * Returns visible parts of an image by removing extra transparent pixels. Bitmap should
         * have alpha channel otherwise it will throw an exception.
         * This method runs concurrently.
         * @param bitmap bitmap to perform visible pixel checking on.
         * @param sensitivity sensitivity of alpha channel. higher number will make boundaries closer to the edges.
         * the sensitivity value is between 0 and 254 (technically between 0 and 255 but 255 would make image all transparent because it will be filtered.)
         * @throws IllegalStateException if bitmap width,height is 0 or bitmap doesn't support alpha channel.
         * @throws IllegalArgumentException if sensitivity value is not between 0 and 244.
         * @return [Rect] representing visible boundaries of image.
         */
        fun getVisiblePixelsRectangleConcurrent(bitmap: Bitmap, sensitivity: Int = 0): Rect {

            if (bitmap.width == 0 || bitmap.height == 0) throw IllegalStateException("bitmap height or width is 0")
            if (!bitmap.hasAlpha()) throw IllegalStateException("bitmap does not support alpha channel")
            if (sensitivity !in 0..254) throw IllegalArgumentException("sensitivity should be between 0 and 254")

            // Get number of available processor.
            val numberThreads = Runtime.getRuntime().availableProcessors()

            // We only need threads because there is only 4 task to be done.
            val executor = Executors.newFixedThreadPool(if (numberThreads > 4) 4 else numberThreads)

            // Create four tasks which approach the center of bitmap from sides.
            // This way we can find boundaries of visible pixel much faster.
            val listOfTask = listOf(Callable {
                for (y in 0 until bitmap.height) {
                    for (x in 0 until bitmap.width) {
                        if (bitmap.getPixel(x, y).alpha > sensitivity) {
                            return@Callable y
                        }
                    }
                }
                0
            }, Callable {
                for (y in bitmap.height - 1 downTo 0) {
                    for (x in bitmap.width - 1 downTo 0) {
                        if (bitmap.getPixel(x, y).alpha > sensitivity) {
                            return@Callable y
                        }
                    }
                }
                0
            }, Callable {
                for (x in 0 until bitmap.width) {
                    for (y in 0 until bitmap.height) {
                        if (bitmap.getPixel(x, y).alpha > sensitivity) {
                            return@Callable x
                        }
                    }
                }
                0
            }, Callable {
                for (x in bitmap.width - 1 downTo 0) {
                    for (y in bitmap.height - 1 downTo 0) {
                        if (bitmap.getPixel(x, y).alpha > sensitivity) {
                            return@Callable x
                        }
                    }
                }
                0
            })

            // Invoke all of task at once.
            val values = executor.invokeAll(listOfTask)
            // Shutdown executor.
            executor.shutdown()

            val visiblePixelX = values[2].get() as Int
            val visiblePixelY = values[0].get() as Int
            val visiblePixelWidth = values[3].get() as Int
            val visiblePixelHeight = values[1].get() as Int

            // If either width of height after removing transparent pixels are 0, throw exception.
            if (visiblePixelWidth == 0) throw IllegalStateException("bitmap width is 0")
            if (visiblePixelHeight == 0) throw IllegalStateException("bitmap height is 0")

            // Finally return the rectangle of only visible part of image.
            return Rect(
                visiblePixelX,
                visiblePixelY,
                visiblePixelWidth + 1,
                visiblePixelHeight + 1
            )
        }

        /**
         * Down sizes the given bitmap to given rectangle boundaries.
         * @param bitmap bitmap to be down sized.
         * @param rect rect to set bitmap size to it.
         * @return down sized bitmap.
         */
        fun downSizeBitmap(bitmap: Bitmap, rect: Rect, recycleInputBitmap: Boolean = true): Bitmap {
            val bitmapToReturn =
                Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())

            // Recycle to prevent memory leaking.
            if (recycleInputBitmap)
                bitmap.recycle()

            return bitmapToReturn
        }

        /**
         * Returns each pixel in bitmap one by one in [onEachPixel] callback.
         * @param bitmap Bitmap to perform operations on it.
         * @param onEachPixel Callback that returns the x and y and value of pixel to perform operations on it.
         * This callback should return a pixel as Color, so any changes on a pixel should be returned from the callback.
         */
        fun transformBitmapPixel(
            bitmap: Bitmap,
            onEachPixel: (x: Int, y: Int, pixel: Int) -> Int
        ): Bitmap {
            for (x in 0 until bitmap.width) {
                for (y in 0 until bitmap.height) {
                    bitmap.setPixel(
                        x,
                        y,
                        onEachPixel(x, y, bitmap.getPixel(x, y))
                    )
                }
            }
            return bitmap
        }

        /**
         * Returns each pixel in bitmap one by one in [onEachPixel] callback (It runs concurrently and uses all available threads).
         * @param bitmap Bitmap to perform operations on it.
         * @param onEachPixel Callback that returns the x and y and value of pixel to perform operations on it.
         * This callback should return a pixel as Color, so any changes on a pixel should be returned from the callback.
         */
        fun transformBitmapPixelConcurrent(
            bitmap: Bitmap,
            onEachPixel: (x: Int, y: Int, pixel: Int) -> Int
        ): Bitmap {
            val numberOfThreads = Runtime.getRuntime().availableProcessors()
            val executor = Executors.newFixedThreadPool(numberOfThreads)

            // Determine how many chunk we would have in bitmap with given number of threads.
            val chunk = bitmap.width / numberOfThreads

            // Create list of task that later will be invoked by executor.
            val listOfTasks = mutableListOf<Callable<Any>>()

            repeat(numberOfThreads) { threadIndex ->

                // Calculate chunks start and end pixel to give to the algorithm to iterate over them.
                val n = threadIndex * chunk
                val startX = if (n == 0) 0 else n - 1

                var endX = (threadIndex + 1) * chunk

                if (threadIndex == numberOfThreads - 1) {
                    endX = bitmap.width
                }

                // Finally create a callable that will be invoked concurrently in executor.
                listOfTasks.add(Callable {
                    for (x in startX until endX) {
                        for (y in 0 until bitmap.height) {
                            bitmap.setPixel(
                                x,
                                y,
                                onEachPixel(x, y, bitmap.getPixel(x, y))
                            )
                        }
                    }

                })
            }

            executor.invokeAll(listOfTasks)

            executor.shutdown()

            return bitmap
        }


        /**
         * Performs image processing operations on bitmap in an selected area, performing operations on an area of pixel
         * can let you process filters like Blur and etc....
         * @param bitmap Bitmap to perform processing on.
         * @param area Represents an area of pixels, so if this value was 3 then total area would be 3 * 3 = 9.
         * @param offsetYAfterIteration Total to shift on y axis of pixels after an area of pixel has been returned, if this value is less than the [area] then two groups of pixels might overlap on each iteration.
         * @param offsetXAfterIteration Total to shift on x axis of pixels after an area of pixel has been returned, if this value is less than the [area] then two groups of pixels might overlap on each iteration.
         * @param onEachArea The callback that returns a [IntArray] representing that area of pixel. User should return an [IntArray] to be replaced in original bitmap.
         */
        fun transformBitmapAreaPixels(
            bitmap: Bitmap, area: Int, offsetXAfterIteration: Int = 1,
            offsetYAfterIteration: Int = 1,
            onEachArea: (area: IntArray) -> IntArray
        ): Bitmap {
            // Allocate a int array for area of pixel to get from bitmap.
            val areaAllocation = IntArray(area * bitmap.width)

            // Iterate over bitmap pixels in each axis with given steps.
            for (offsetX in 0 until bitmap.width step offsetXAfterIteration) {
                // Determine the area, We check if we exceed the bitmap bounds then we limit the area to bitmap bounds.
                val finalXArea =
                    if ((offsetX + area) >= bitmap.width) (bitmap.width) - offsetX else area

                for (offsetY in 0 until bitmap.height step offsetYAfterIteration) {

                    // Determine the area, We check if we exceed the bitmap bounds then we limit the area to bitmap bounds.
                    val finalYArea =
                        if ((offsetY + area) >= bitmap.height) (bitmap.height) - offsetY else area

                    // Get an area of bitmap as pixels.
                    bitmap.getPixels(
                        areaAllocation,
                        0,
                        bitmap.width,
                        offsetX,
                        offsetY,
                        finalXArea,
                        finalYArea
                    )

                    // Since 'getPixels' method returns other pixels that we aren't interested in as zeros, then
                    // filter these zeros and return a pure pixel area to pass to callback.
                    val reducedArea = areaAllocation.filter { number -> number != 0 }.toIntArray()
                    // Finally pass the pixels into callback and return the processed area.
                    val processedArea = onEachArea(reducedArea)

                    // Difference of width and area to later calculate the index of these pixel holders correctly.
                    val diffBitmapWidthAndArea = bitmap.width - area

                    // Fill the allocation with processed pixels returned from callback.
                    for (i in 0 until area) {
                        for (x in 0 until area) {
                            val index = x + ((diffBitmapWidthAndArea + area) * i)

                            val processedAreaIndex =
                                (index - (diffBitmapWidthAndArea * i))

                            if (areaAllocation[index] == 0) continue

                            areaAllocation[index] = processedArea[processedAreaIndex]
                        }
                    }

                    // Finally change the pixels of bitmap.
                    bitmap.setPixels(
                        areaAllocation,
                        0,
                        bitmap.width,
                        offsetX,
                        offsetY,
                        finalXArea,
                        finalYArea
                    )
                }
            }
            return bitmap
        }

        /*  fun transformBitmapAreaPixelsConcurrent(
              bitmap: Bitmap, area: Int, offsetXAfterIteration: Int = 1,
              offsetYAfterIteration: Int = 1,
              onEachArea: (area: IntArray) -> IntArray
          ): Bitmap {
              val numberOfThreads = Runtime.getRuntime().availableProcessors()
              val executor = Executors.newFixedThreadPool(numberOfThreads)

              val chunk = bitmap.width / numberOfThreads

              val listOfTasks = mutableListOf<Callable<Any>>()

              val targetBitmap =
                  Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

              repeat(numberOfThreads) { threadIndex ->
                  listOfTasks.add(Callable {

                      val n = threadIndex * chunk
                      val startX = if (n == 0) 0 else n - 1

                      var endX = (threadIndex + 1) * chunk

                      if (threadIndex == numberOfThreads - 1) {
                          endX = bitmap.width
                      }

                      performAreaProcessing(
                          bitmap,
                          targetBitmap,
                          area,
                          startX,
                          endX,
                          0,
                          bitmap.height,
                          offsetXAfterIteration,
                          offsetYAfterIteration,
                          onEachArea
                      )
                  })
              }
              executor.invokeAll(listOfTasks)

              executor.shutdown()

              return targetBitmap
          }*/

        /* private fun performAreaProcessing(
             bitmap: Bitmap,
             target: Bitmap,
             area: Int,
             startX: Int,
             endX: Int,
             startY: Int,
             endY: Int,
             offsetXAfterIteration: Int,
             offsetYAfterIteration: Int,
             onEachArea: (area: IntArray) -> IntArray
         ) {
             // Allocate a int array for area of pixel to get from bitmap.
             val areaAllocation = IntArray(area * bitmap.width)

             // Iterate over bitmap pixels in each axis with given steps.
             for (offsetX in startX until endX step offsetXAfterIteration) {
                 // Determine the area, We check if we exceed the bitmap bounds then we limit the area to bitmap bounds.
                 val extraPixel =
                     if (offsetX + area >= endX) (offsetX - (endX - area)) else 0

                 val finalXArea =
                     if ((offsetX + area) >= bitmap.width) (bitmap.width - offsetX) - 1 else area

                 for (offsetY in startY until endY step offsetYAfterIteration) {

                     val extraPixelY = if (offsetY + area >= endY) (offsetY - (endY - area)) else 0

                     // Determine the area, We check if we exceed the bitmap bounds then we limit the area to bitmap bounds.
                     val finalYArea =
                         if ((offsetY + area) >= bitmap.height) (bitmap.height - offsetY) - 1 else area

                     // Get an area of bitmap as pixels.
                     bitmap.getPixels(
                         areaAllocation,
                         0,
                         bitmap.width,
                         offsetX,
                         offsetY,
                         finalXArea,
                         finalYArea
                     )

                     // Since 'getPixels' method returns other pixels that we aren't interested in as zeros, then
                     // filter these zeros and return a pure pixel area to pass to callback.
                     val reducedArea =
                         areaAllocation.filter { number -> number != 0 }.toIntArray()

                     // Difference of width and area to later calculate the index of these pixel holders correctly.
                     val diffBitmapWidthAndArea = bitmap.width - area

                     synchronized(this) {
                         // Finally pass the pixels into callback and return the processed area.
                         val processedArea = onEachArea(reducedArea)

                         // Fill the allocation with processed pixels returned from callback.
                         for (i in 0 until area) {
                             for (x in 0 until area) {
                                 val index = x + ((diffBitmapWidthAndArea + area) * i)

                                 if (areaAllocation[index] == 0) continue

                                 val processedAreaIndex =
                                     (index - (diffBitmapWidthAndArea * i))


                                 areaAllocation[index] = processedArea[processedAreaIndex]
                             }
                         }
                     }
                     // Finally change the pixels of bitmap.
                     target.setPixels(
                         areaAllocation,
                         0,
                         bitmap.width,
                         offsetX,
                         offsetY,
                         area - extraPixel,
                         area - extraPixelY
                     )
                 }
             }
         }*/
    }
}
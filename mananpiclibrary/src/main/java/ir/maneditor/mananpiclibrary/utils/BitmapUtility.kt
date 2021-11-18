package ir.maneditor.mananpiclibrary.utils

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
                            // Do not check x anymore, only focus on width and y and height
                            continueCheckingX = false
                        }

                        // Set the first pixel occurrence as y
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

            var visiblePixelX = bitmap.width
            var visiblePixelY = bitmap.height
            var visiblePixelWidth = 0
            var visiblePixelHeight = 0

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
                            visiblePixelY = y
                            return@Callable
                        }
                    }
                }
            }, Callable {
                for (y in bitmap.height - 1 downTo 0) {
                    for (x in bitmap.width - 1 downTo 0) {
                        if (bitmap.getPixel(x, y).alpha > sensitivity) {
                            visiblePixelHeight = y
                            return@Callable
                        }
                    }
                }
            }, Callable {
                for (x in 0 until bitmap.width) {
                    for (y in 0 until bitmap.height) {
                        if (bitmap.getPixel(x, y).alpha > sensitivity) {
                            visiblePixelX = x
                            return@Callable
                        }
                    }
                }
            }, Callable {
                for (x in bitmap.width - 1 downTo 0) {
                    for (y in bitmap.height - 1 downTo 0) {
                        if (bitmap.getPixel(x, y).alpha > sensitivity) {
                            visiblePixelWidth = x
                            return@Callable
                        }
                    }
                }
            })

            // Invoke all of task at once.
            executor.invokeAll(listOfTask)

            // Shutdown executor.
            executor.shutdown()

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
         * @param bitmap bitmap to be down sized
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
    }
}
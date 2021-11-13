package ir.maneditor.mananpiclibrary.components

import android.graphics.*
import android.widget.ImageView
import androidx.core.graphics.withRotation
import androidx.core.view.drawToBitmap

/**
 * Class for applying porter duff methods on two images.
 */
class MananPorterDuff {
    companion object {
        /**
         * Applies porter duff methods on given images and mode on a canvas with size of source image.
         * This method removes any color filter on given images and returns them after applying the porter duff(prevents color filter duplication).
         * Rotation on source image will be set to 0 degrees after applying porter duff.
         * @param sourceImage SRC in porter duff.
         * @param destinationImage DST in porter duff.
         * @param porterDuffMode Mode of porter duff that is going to be applied to both images.
         * @return The masked bitmap.
         */
        fun applyPorterDuff(
            sourceImage: ImageView,
            destinationImage: ImageView,
            porterDuffMode: PorterDuff.Mode
        ): Bitmap {
            // Create a bitmap to perform drawings on.
            val newBitmap =
                Bitmap.createBitmap(
                    sourceImage.width,
                    sourceImage.height,
                    Bitmap.Config.ARGB_8888
                )

            return applyPorterDuff(newBitmap, sourceImage, destinationImage, porterDuffMode)
        }

        /**
         * Determines if 'destination image' is out of bounds of 'source image'
         */
        private fun isOutOfBounds(sourceImage: ImageView, destinationImage: ImageView): Boolean {
            return (destinationImage.x + destinationImage.width < sourceImage.x) ||
                    (destinationImage.x > (sourceImage.x + sourceImage.width)) ||
                    (destinationImage.y + destinationImage.height < sourceImage.y) ||
                    (destinationImage.y > (sourceImage.y + sourceImage.height))
        }

        /**
         * Applies porter duff methods on given images and mode on a canvas with size base bitmap.
         * This method removes any color filter on given images and returns them after applying the porter duff(prevents color filter duplication).
         * Rotation on source image will be set to 0 degrees after applying porter duff.
         * @param baseBitmap Base bitmap that drawings and masks will be drawn on it.
         * @param sourceImage SRC in porter duff.
         * @param destinationImage DST in porter duff.
         * @param porterDuffMode Mode of porter duff that is going to be applied to both images.
         * @return The masked bitmap.
         */
        fun applyPorterDuff(
            baseBitmap: Bitmap,
            sourceImage: ImageView,
            destinationImage: ImageView,
            porterDuffMode: PorterDuff.Mode
        ): Bitmap {
            if (isOutOfBounds(
                    sourceImage,
                    destinationImage
                )
            ) {
                // Recycle the image to be free to garbage collected.
                baseBitmap.recycle()
                throw IllegalStateException("two images do not intersect each other")
            }


            // Create a canvas with size of base bitmap.
            val canvas = Canvas(baseBitmap)

            // Create a paint for bitmaps.
            val paint = Paint()
            // Set color filter of newly created paint to destination bitmap color filter.
            paint.colorFilter = destinationImage.colorFilter

            // Store the color filter to later re-apply it to the image.
            var imagesColorFilterBuffer = destinationImage.colorFilter
            // Remove the color filter to prevent color filter duplication.
            destinationImage.colorFilter = null

            canvas.run {
                // Rotate canvas as much as destination image is rotated.
                withRotation(
                    destinationImage.rotation,
                    (destinationImage.x - sourceImage.x) + (destinationImage.width * 0.5f),
                    (destinationImage.y - sourceImage.y) + (destinationImage.height * 0.5f)
                ) {
                    // Draw bitmap while canvas is rotated.
                    // Left and right coordinated of destination bitmap is calculated
                    // with DestinationImage's x and y plus the source image coordinates.
                    drawBitmap(
                        destinationImage.drawToBitmap(),
                        destinationImage.x - sourceImage.x,
                        destinationImage.y - sourceImage.y,
                        paint
                    )
                }
                // Re-apply color filter.
                destinationImage.colorFilter = imagesColorFilterBuffer

                // Now configure the same paint used for destination bitmap and set
                // it's transfer mode.
                paint.xfermode = PorterDuffXfermode(porterDuffMode)
                // Remove source image's color filter to prevent duplication.
                imagesColorFilterBuffer = sourceImage.colorFilter

                sourceImage.colorFilter = null

                // Rotate the canvas again as much as source image is rotated.
                withRotation(
                    sourceImage.rotation,
                    sourceImage.pivotX,
                    sourceImage.pivotY
                ) {
                    drawBitmap(
                        sourceImage.drawToBitmap(),
                        0f,
                        0f,
                        paint
                    )
                }

                // Return the source color filter.
                sourceImage.colorFilter = imagesColorFilterBuffer
                // Make the cache color filter null.
                imagesColorFilterBuffer = null
            }


            // Reset source image's rotation.
            // If user is going to set newly masked bitmap to be previewed by source imageview, then result
            // would be rotated(because the image view is rotated) so we reset it to prevent that to happen.
            sourceImage.rotation = 0f

            return baseBitmap
        }
    }
}
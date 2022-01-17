package ir.manan.mananpic.components

import android.graphics.*

/**
 * Class for applying porter duff methods on two images.
 */
class MananPorterDuff {
    companion object {
        /**
         * Applies porter-duff methods on two bitmaps with given boundaries.
         * It is beneficial to use original bitmaps to preserve the quality (bitmap directly from [android.graphics.drawable.BitmapDrawable]).
         * This method first calculates how much the original bitmap is scaled compared to it's current boundaries and then
         * uses total scaled amount to scale up the destination image boundaries to have better quality.
         * @param sourceBitmap Bitmap that represents SRC in porter-duff.
         * @param sourceBounds Boundaries of source bitmap on screen.
         * @param sourceRotation Total rotation of source image.
         * @param destinationBitmap Bitmap that represents DST in porter-duff.
         * @param sourceBounds Boundaries of destination bitmap on screen.
         * @param sourceRotation Total rotation of destination image.
         * @param porterDuffMode Defines mode of Porter duff method.
         */

        fun applyPorterDuff(
            sourceBitmap: Bitmap,
            sourceBounds: RectF,
            sourceRotation: Float,
            destinationBitmap: Bitmap,
            destinationBounds: RectF,
            destinationRotation: Float,
            porterDuffMode: PorterDuff.Mode
        ): Bitmap {
            // Create a bitmap same size as source bitmap to perform drawings on.
            val newBitmap =
                Bitmap.createBitmap(
                    sourceBitmap.width,
                    sourceBitmap.height,
                    Bitmap.Config.ARGB_8888
                )

            return applyPorterDuff(
                newBitmap,
                sourceBitmap,
                sourceBounds,
                sourceRotation,
                destinationBitmap,
                destinationBounds,
                destinationRotation,
                porterDuffMode
            )
        }

        /**
         * Determines if 'destination image' is out of bounds of 'source image'.
         * @return True if bounds don't intersect each other.
         */
        private fun isOutOfBounds(srcBounds: RectF, dstBounds: RectF): Boolean {
            return !srcBounds.intersects(
                dstBounds.left,
                dstBounds.top,
                dstBounds.right,
                dstBounds.bottom
            )
        }

        /**
         * Applies porter-duff methods on two bitmaps with given boundaries.
         * It is beneficial to use original bitmaps to preserve the quality (bitmap directly from [android.graphics.drawable.BitmapDrawable]).
         * This method first calculates how much the original bitmap is scaled compared to it's current boundaries and then
         * uses total scaled amount to scale up the destination image boundaries to have better quality.
         * @param baseBitmap Base bitmap that drawing operations are drawn on it.
         * @param sourceBitmap Bitmap that represents SRC in porter-duff.
         * @param sourceBounds Boundaries of source bitmap on screen.
         * @param sourceRotation Total rotation of source image.
         * @param destinationBitmap Bitmap that represents DST in porter-duff.
         * @param sourceBounds Boundaries of destination bitmap on screen.
         * @param sourceRotation Total rotation of destination image.
         * @param porterDuffMode Defines mode of Porter duff method.
         */
        fun applyPorterDuff(
            baseBitmap: Bitmap,
            sourceBitmap: Bitmap,
            sourceBounds: RectF,
            sourceRotation: Float,
            destinationBitmap: Bitmap,
            destinationBounds: RectF,
            destinationRotation: Float,
            porterDuffMode: PorterDuff.Mode
        ): Bitmap {
            if (isOutOfBounds(sourceBounds, destinationBounds)) {
                // Recycle the image to be free to get garbage collected.
                baseBitmap.recycle()
                throw IllegalStateException("two images do not intersect each other")
            }

            // Create a canvas with size of base bitmap.
            val canvas = Canvas(baseBitmap)

            // Create a paint for bitmaps.
            val paint = Paint()

            // Figure out how much the bounds scaled down compared to original width of bitmap.
            // Note that we don't calculate height of bitmap because bitmap has aspect ratio
            // and value basically is the same.
            val totalSourceScale = sourceBitmap.width / sourceBounds.width()

            // Create float array with bounds of images inside it to later use it
            // inside matrix to point values to new coordinates.
            val pointsFloat = floatArrayOf(
                destinationBounds.left - sourceBounds.left,
                destinationBounds.top - sourceBounds.top,
                destinationBounds.right,
                destinationBounds.bottom
            )

            // Create a matrix and apply scale then point new bounds.
            Matrix().apply {
                setScale(totalSourceScale, totalSourceScale)
                mapPoints(pointsFloat)
            }

            canvas.run {

                // Save canvas state to later restore it and continue other drawings.
                save()

                // Rotate the canvas as much as destination image is rotated.
                rotate(
                    destinationRotation,
                    pointsFloat[0],
                    pointsFloat[1]
                )

                // Determine how much to scale the destination bitmap.
                val toScale =
                    (destinationBounds.width() * totalSourceScale) / destinationBitmap.width

                // Scale the canvas to later draw scaled bitmap on it.
                scale(
                    toScale,
                    toScale,
                    pointsFloat[0],
                    pointsFloat[1]
                )

                // Finally draw DST bitmap with respect to scaled x and y.
                drawBitmap(
                    destinationBitmap,
                    pointsFloat[0],
                    pointsFloat[1],
                    paint
                )

                restore()


                // Now configure the same paint used for destination bitmap and set
                // it's transfer mode.
                paint.xfermode = PorterDuffXfermode(porterDuffMode)

                // Rotate the source bitmap(since source bitmap starts drawing from point 0 and 0 then we don't have to provide a pivot point.)
                rotate(sourceRotation)

                // Draw SRC bitmap.
                drawBitmap(
                    sourceBitmap,
                    0f,
                    0f,
                    paint
                )
            }

            return baseBitmap
        }
    }
}
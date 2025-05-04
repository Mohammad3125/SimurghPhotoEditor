package ir.baboomeh.photolib.components.paint.painters.cropper.aspect_ratios

import android.graphics.RectF
import ir.baboomeh.photolib.components.paint.painters.cropper.AspectRatio
import ir.baboomeh.photolib.components.paint.painters.cropper.HandleBar

/**
 * Class representing aspect-ratio-free resizing.
 */
class AspectRatioFree : AspectRatio() {

    override fun resize(rect: RectF, handleBar: HandleBar?, dx: Float, dy: Float): RectF {
        rect.run {
            when (handleBar) {
                HandleBar.BOTTOM_RIGHT -> {
                    right += dx
                    bottom += dy
                }
                HandleBar.TOP_RIGHT -> {
                    right += dx
                    top += dy
                }
                HandleBar.RIGHT -> {
                    right += dx
                }
                HandleBar.TOP -> {
                    top += dy
                }
                HandleBar.BOTTOM -> {
                    bottom += dy
                }
                HandleBar.TOP_LEFT -> {
                    left += dx
                    top += dy
                }
                HandleBar.LEFT -> {
                    left += dx
                }
                HandleBar.BOTTOM_LEFT -> {
                    left += dx
                    bottom += dy
                }

                else -> {}
            }
        }
        return rect
    }

    override fun validate(rect: RectF, dirtyRect: RectF, limitRect: RectF): RectF {
        rect.run {

            var finalLeft = dirtyRect.left
            var finalTop = dirtyRect.top
            var finalRight = dirtyRect.right
            var finalBottom = dirtyRect.bottom

            // If resized rectangle's right side exceeds maximum width don't let it go further.
            if (dirtyRect.right > limitRect.right) finalRight = limitRect.right
            // If left side of rectangle reaches limit of x axis don't let it go further.
            if (dirtyRect.left < limitRect.left) finalLeft = limitRect.left

            // If resized rectangle's bottom side exceeds maximum height don't let it go further.
            if (dirtyRect.bottom > limitRect.bottom) finalBottom = limitRect.bottom
            // If top side of rectangle reaches limit of y axis don't let it go further.
            if (dirtyRect.top < limitRect.top) finalTop = limitRect.top

            return RectF(finalLeft, finalTop, finalRight, finalBottom)
        }
    }

    override fun normalizeAspectRatio(maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
        return Pair(maxWidth, maxHeight)
    }
}
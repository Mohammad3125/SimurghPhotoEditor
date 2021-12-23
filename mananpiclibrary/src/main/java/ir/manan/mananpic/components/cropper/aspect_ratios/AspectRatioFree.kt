package ir.manan.mananpic.components.cropper.aspect_ratios

import android.graphics.RectF
import ir.manan.mananpic.components.cropper.AspectRatio
import ir.manan.mananpic.components.cropper.HandleBar
import kotlin.math.min

/**
 * Class representing aspect-ratio-free resizing.
 */
class AspectRatioFree : AspectRatio() {

    override fun resize(rect: RectF, handleBar: HandleBar?, dx: Float, dy: Float): RectF {
        rect.run {
            when (handleBar) {
                HandleBar.RIGHT, HandleBar.LEFT -> {
                    if (handleBar == HandleBar.RIGHT)
                        right += dx
                    else left += dx
                }
                HandleBar.TOP_LEFT, HandleBar.TOP_RIGHT, HandleBar.TOP -> {
                    top += dy
                    if (handleBar == HandleBar.TOP_LEFT)
                        left += dx
                    else if (handleBar == HandleBar.TOP_RIGHT) right += dx

                }
                HandleBar.BOTTOM_LEFT, HandleBar.BOTTOM_RIGHT, HandleBar.BOTTOM -> {
                    bottom += dy
                    if (handleBar == HandleBar.BOTTOM_LEFT)
                        left += dx
                    else if (handleBar == HandleBar.BOTTOM_RIGHT) right += dx
                }
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

            val minSize = min(limitRect.width(), limitRect.height()) / 4.5f

            val frameWidth = width()
            val frameHeight = height()

            // If resized rectangle's right side exceeds maximum width don't let it go further.
            if (dirtyRect.right > limitRect.right) finalRight = limitRect.right
            // If left side of rectangle reaches limit of x axis don't let it go further.
            if (dirtyRect.left < limitRect.left) finalLeft = limitRect.left

            // If resized rectangle's bottom side exceeds maximum height don't let it go further.
            if (dirtyRect.bottom > limitRect.bottom) finalBottom = limitRect.bottom
            // If top side of rectangle reaches limit of y axis don't let it go further.
            if (dirtyRect.top < limitRect.top) finalTop = limitRect.top

            // This piece of code makes rectangle to don't get resized less than minimum width and height.
            if (frameWidth - (dirtyRect.left - left) < minSize)
                finalLeft = left + (frameWidth - minSize)
            if (frameWidth - (right - dirtyRect.right) < minSize)
                finalRight = right
            if (frameHeight - (dirtyRect.top - top) < minSize)
                finalTop = top + (frameHeight - minSize)
            if (frameHeight - (bottom - dirtyRect.bottom) < minSize)
                finalBottom = bottom


            return RectF(finalLeft, finalTop, finalRight, finalBottom)
        }
    }

    override fun normalizeAspectRatio(maxWidth: Float, maxHeight: Float): Pair<Float, Float> {
        return Pair(maxWidth, maxHeight)
    }
}
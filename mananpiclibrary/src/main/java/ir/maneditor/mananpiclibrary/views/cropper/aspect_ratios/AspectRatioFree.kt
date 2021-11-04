package ir.maneditor.mananpiclibrary.views.cropper.aspect_ratios

import android.graphics.RectF
import ir.maneditor.mananpiclibrary.views.cropper.AspectRatio
import ir.maneditor.mananpiclibrary.views.cropper.HandleBar

/**
 * Class representing aspect-ratio-free resizing.
 */
class AspectRatioFree : AspectRatio() {

    override fun resize(rect: RectF, handleBar: HandleBar?, dx: Float, dy: Float): RectF {
        rect.run {
            when (handleBar) {
                HandleBar.TOP, HandleBar.BOTTOM -> {
                    if (handleBar == HandleBar.BOTTOM)
                        bottom += dy
                    else top += dy
                }
                HandleBar.RIGHT, HandleBar.LEFT -> {
                    if (handleBar == HandleBar.RIGHT)
                        right += dx
                    else left += dx
                }
                HandleBar.TOP_LEFT, HandleBar.TOP_RIGHT -> {
                    top += dy
                    if (handleBar == HandleBar.TOP_LEFT)
                        left += dx
                    else right += dx

                }
                HandleBar.BOTTOM_LEFT, HandleBar.BOTTOM_RIGHT -> {
                    bottom += dy
                    if (handleBar == HandleBar.BOTTOM_LEFT)
                        left += dx
                    else right += dx
                }
            }
        }
        return rect
    }

    override fun validate(
        rect: RectF,
        dirtyRect: RectF,
        minWidth: Float,
        maxWidth: Float,
        minHeight: Float,
        maxHeight: Float
    ): RectF {
        rect.run {
            val frameWidth = width()
            val frameHeight = height()

            var finalLeft = dirtyRect.left
            var finalTop = dirtyRect.top
            var finalRight = dirtyRect.right
            var finalBottom = dirtyRect.bottom

            // If resized rectangle's right side exceeds maximum width don't let it go further.
            if (dirtyRect.right > maxWidth) finalRight = maxWidth
            // If left side of rectangle reaches limit of x axis don't let it go further.
            if (dirtyRect.left < 0f) finalLeft = 0f

            // If resized rectangle's bottom side exceeds maximum height don't let it go further.
            if (dirtyRect.bottom > maxHeight) finalBottom = maxHeight
            // If top side of rectangle reaches limit of y axis don't let it go further.
            if (dirtyRect.top < 0f) finalTop = 0f

            // This piece of code makes rectangle to don't get resized less than minimum width and height.
            if (frameWidth - (dirtyRect.left - left) < minWidth)
                finalLeft = left + (frameWidth - minWidth)
            if (frameWidth - (right - dirtyRect.right) < minWidth)
                finalRight = right
            if (frameHeight - (dirtyRect.top - top) < minHeight)
                finalTop = top + (frameHeight - minHeight)
            if (frameHeight - (bottom - dirtyRect.bottom) < minHeight)
                finalBottom = bottom


            return RectF(finalLeft, finalTop, finalRight, finalBottom)
        }
    }
}
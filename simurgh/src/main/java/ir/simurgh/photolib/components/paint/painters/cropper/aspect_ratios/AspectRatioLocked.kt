package ir.simurgh.photolib.components.paint.painters.cropper.aspect_ratios

import android.graphics.RectF
import ir.simurgh.photolib.components.paint.painters.cropper.HandleBar

/**
 * This class represents an aspect-ratio with locked ratio.
 * @param widthRatio Ratio of width to height.
 * @param heightRatio Ratio of height to width.
 */
open class AspectRatioLocked(private val widthRatio: Float, private val heightRatio: Float) :
    AspectRatio() {

    override fun resize(rect: RectF, handleBar: HandleBar?, dx: Float, dy: Float): RectF {
        rect.run {
            val ratio = getRatio()

            when (handleBar) {
                HandleBar.BOTTOM_RIGHT -> {
                    if (ratio > 1f) {
                        right += dx
                        bottom += (width() / ratio) - height()
                    } else {
                        bottom += dy
                        right += (height() * ratio) - width()
                    }
                }
                HandleBar.BOTTOM_LEFT -> {
                    if (ratio > 1f) {
                        left += dx
                        bottom += (width() / ratio) - height()
                    } else {
                        bottom += dy
                        left -= height() * ratio - width()
                    }
                }
                HandleBar.TOP_RIGHT -> {
                    if (ratio > 1f) {
                        right += dx
                        top -= ((width() / ratio) - height())
                    } else {
                        top += dy
                        right += height() * ratio - width()
                    }
                }
                HandleBar.TOP_LEFT -> {
                    if (ratio > 1f) {
                        left += dx
                        top -= ((width() / ratio) - height())
                    } else {
                        top += dy
                        left -= height() * ratio - width()
                    }
                }
                else -> {}
            }
            return rect
        }
    }

    override fun validate(rect: RectF, dirtyRect: RectF, limitRect: RectF): RectF {
        dirtyRect.run {
            val ratio = getRatio()

            // If right side of rectangle is greater than maximum width do not allow it
            // to go further.
            if (right > limitRect.right) {
                right = limitRect.right
                // If we got to maximum width then change bottom or top to maintain
                // aspect ratio.
                // If top hasn't been changed that means user has been moving cropper with bottom right handle,
                // so change the bottom side of rectangle to maintain aspect ratio.
                if (top == rect.top)
                    bottom = (width() / ratio) + top
                // If bottom hasn't been changed that means user has been moving cropper with top right handle,
                // so change the top side of rectangle to maintain aspect ratio.
                else if (bottom == rect.bottom)
                    top -= (width() / ratio) - height()

            }
            // If left side of rectangle got less than limit that means we have reached the boundary of view.
            if (left < limitRect.left) {
                left = limitRect.left
                // If top hasn't been changed that means user has been moving cropper with bottom left handle,
                // so change the bottom side of rectangle to maintain aspect ratio.
                if (top == rect.top)
                    bottom = (width() / ratio) + top
                // If bottom hasn't been changed that means user has been moving cropper with top left handle,
                // so change the top side of rectangle to maintain aspect ratio.
                else if (bottom == rect.bottom)
                    top -= (width() / ratio) - height()
            }
            // If bottom side of rectangle exceeds the maximum height then don't allow it to go further.
            if (bottom > limitRect.bottom) {
                bottom = limitRect.bottom
                // If left side of rectangle hasn't been changed that means the user has been changing cropper size
                // with bottom right handle.
                if (left == rect.left)
                    right = (height() * ratio) + left
                // If left side of rectangle hasn't been changed that means the user has been changing cropper size
                // with bottom left handle.
                else if (right == rect.right)
                    left -= height() * ratio - width()
            }
            // If top side of rectangle reaches the limit don't let it go further.
            if (top < limitRect.top) {
                top = limitRect.top
                // If left side of rectangle hasn't been changed that means the user has been resizing the cropper
                // with top right handle.
                if (left == rect.left)
                    right = (height() * ratio) + left
                // If right side of rectangle hasn't been changed that means the user has been resizing the cropper
                // with top left handle.
                else if (right == rect.right)
                    left -= height() * ratio - width()
            }

            return dirtyRect
        }

    }

    /**
     * Divides width to height to find ratio.
     * @return ratio of division of width to height.
     */
    open fun getRatio(): Float = widthRatio / heightRatio

    override fun normalizeAspectRatio(
        maxWidth: Float,
        maxHeight: Float
    ): Pair<Float, Float> {
        val ratio = getRatio()

        // If ratio is less than 1 then the height of aspect ratio is bigger than the width.
        return if (ratio < 1f) {
            // Apply aspect ratio.
            var normalizedWidth = (maxHeight * ratio)
            var finalHeight = maxHeight

            // If it exceeds the maximum width.
            if (normalizedWidth > maxWidth) {
                // Then normalize height to fit inside bounds.
                normalizedWidth = maxWidth
                finalHeight = (maxWidth / ratio)
            }

            Pair(normalizedWidth, finalHeight)
        } else {
            var normalizedHeight = (maxWidth / ratio)
            var finalWidth = maxWidth

            // If it exceeds the maximum width.
            if (normalizedHeight > maxHeight) {
                normalizedHeight = maxHeight
                finalWidth = (maxHeight * ratio)
            }

            Pair(finalWidth, normalizedHeight)
        }
    }
}
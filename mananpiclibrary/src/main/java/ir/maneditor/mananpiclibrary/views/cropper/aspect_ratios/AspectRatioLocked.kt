package ir.maneditor.mananpiclibrary.views.cropper.aspect_ratios

import android.graphics.RectF
import ir.maneditor.mananpiclibrary.views.cropper.AspectRatio
import ir.maneditor.mananpiclibrary.views.cropper.HandleBar
import ir.maneditor.mananpiclibrary.views.cropper.HandleBar.*

/**
 * This class represents an aspect-ratio with locked ratio.
 * @param widthRatio Ratio of width to height.
 * @param heightRatio Ratio of height to width.
 */
class AspectRatioLocked(private val widthRatio: Float, private val heightRatio: Float) :
    AspectRatio() {

    override fun resize(rect: RectF, handleBar: HandleBar?, dx: Float, dy: Float): RectF {
        rect.run {
            val ratio = getRatio()

            // We add these two in case user moves finger on two coordinates.
            val differenceForTopLeftAndBottomRight = dx + dy
            val differenceForTopRightAndBottomLeft = dx - dy

            when (handleBar) {
                TOP_LEFT -> {
                    // If width is greater than height
                    if (ratio > 1f) {
                        left += differenceForTopLeftAndBottomRight
                        top -= ((width() / ratio) - height())
                    } else {
                        top += differenceForTopLeftAndBottomRight
                        left -= height() * ratio - width()
                    }
                }
                BOTTOM_LEFT -> {
                    if (ratio > 1f) {
                        left += differenceForTopRightAndBottomLeft
                        bottom = (width() / ratio) + top
                    } else {
                        bottom -= differenceForTopRightAndBottomLeft
                        left -= height() * ratio - width()
                    }
                }
                BOTTOM_RIGHT -> {
                    if (ratio > 1f) {
                        right += differenceForTopLeftAndBottomRight
                        bottom = (width() / ratio) + top
                    } else {
                        bottom += differenceForTopLeftAndBottomRight
                        right = (height() * ratio) + left
                    }
                }
                TOP_RIGHT -> {
                    if (ratio > 1f) {
                        right += differenceForTopRightAndBottomLeft
                        top -= ((width() / ratio) - height())
                    } else {
                        top -= differenceForTopRightAndBottomLeft
                        right += height() * ratio - width()
                    }
                }
                TOP -> {

                }
                BOTTOM -> {

                }
                LEFT -> {

                }
                RIGHT -> {

                }
            }
            return rect
        }
    }

    override fun validate(
        rect: RectF,
        dirtyRect: RectF,
        maxWidth: Float,
        maxHeight: Float
    ): RectF {
        dirtyRect.run {
            val ratio = getRatio()

            // If right side of rectangle is greater than maximum width do not allow it
            // to go further.
            if (right > maxWidth) {
                right = maxWidth
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
            // If left side of rectangle got less than 0 that means we have reached the boundary of view.
            if (left < 0f) {
                left = 0f
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
            if (bottom > maxHeight) {
                bottom = maxHeight
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
            if (top < 0f) {
                top = 0f
                // If left side of rectangle hasn't been changed that means the user has been resizing the cropper
                // with top right handle.
                if (left == rect.left)
                    right = (height() * ratio) + left
                // If right side of rectangle hasn't been changed that means the user has been resizing the cropper
                // with top left handle.
                else if (right == rect.right)
                    left -= height() * ratio - width()
            }

            // Validation for minimum width and height.
//            if (width() < minWidth || height() < minHeight)
//                set(rect)

            return dirtyRect
        }

    }

    /**
     * Divides width to height to find ratio.
     * @return ratio of division of width to height.
     */
    fun getRatio(): Float = widthRatio / heightRatio

    /**
     * Applies aspect ratio to width or height.
     * If final width or height exceeds the maximum amount, it normalizes them to fit inside bounds.
     * @param width Width to be normalized.
     * @param height Height to be normalized.
     * @param maxWidth Maximum width allowed for aspect-ratio.
     * @param maxHeight Maximum height allowed for aspect-ratio.
     * @return A [Pair]. First element is aspect-ratio applied width and second is aspect-ratio applied height.
     */
    fun normalizeAspectRatio(
        width: Int,
        height: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Pair<Int, Int> {
        val ratio = getRatio()
        // If ratio is less than 1 then the height of aspect ratio is bigger than the width.
        return if (ratio < 1f) {
            // Apply aspect ratio.
            var normalizedWidth = (height * ratio).toInt()
            var finalHeight = height

            // If it exceeds the maximum width.
            if (normalizedWidth > maxWidth) {
                // Then normalize height to fit inside bounds.
                finalHeight = (maxWidth / ratio).toInt()
                normalizedWidth = (finalHeight * ratio).toInt()
            }

            Pair(normalizedWidth, finalHeight)
        } else {
            var normalizedHeight = (width / ratio).toInt()
            var finalWidth = width

            if (normalizedHeight > maxHeight) {
                finalWidth = (maxHeight * ratio).toInt()
                normalizedHeight = (finalWidth / ratio).toInt()
            }
            Pair(finalWidth, normalizedHeight)
        }
    }
}
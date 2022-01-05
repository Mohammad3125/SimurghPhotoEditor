package ir.manan.mananpic.components.selection.selectors

import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.abs

abstract class PathBasedSelector : Selector() {

    // Visible part of image bounds.
    protected var leftEdge = 0f
    protected var topEdge = 0f
    protected var rightEdge = 0f
    protected var bottomEdge = 0f

    protected var isPathClose = false

    // Path that adds circles into it and later will be used to
    // clip the drawable content.
    protected val path by lazy {
        Path().apply {
            fillType = Path.FillType.WINDING
        }
    }

    override fun initialize(view: View, bitmap: Bitmap?, bounds: RectF) {
        leftEdge = bounds.left
        topEdge = bounds.top
        rightEdge = bounds.right
        bottomEdge = bounds.bottom
    }

    override fun select(drawable: Drawable): Bitmap? {
        // Only select if path is closed.
        if (isClosed()) {

            // Get how much the current bitmap displayed is scaled comparing to original drawable size.
            val totalScaled = drawable.intrinsicWidth / (rightEdge - leftEdge)

            // Scale the path to that scale value by using Matrix.
            val scaledPoint = Path(path).apply {
                transform(Matrix().apply {
                    setScale(totalScaled, totalScaled, leftEdge, topEdge)
                })
            }

            // Get selected bound of scaled path.
            val selectedBounds = RectF()
            scaledPoint.computeBounds(selectedBounds, true)

            // Get selected bound of normal path (path that is not scaled.)
            val currentPointBounds = RectF()
            path.computeBounds(currentPointBounds, true)

            // Create two variables determining final size of bitmap that is returned.
            var finalBitmapWidth = selectedBounds.width()
            var finalBitmapHeight = selectedBounds.height()

            // Calculate the difference of current path with image's bound.
            val differenceImageBottomAndPathBottom = currentPointBounds.bottom - bottomEdge
            val differenceImageRightAndPathRight = currentPointBounds.right - rightEdge
            val differenceImageLeftAndPathLeft = currentPointBounds.left - leftEdge
            val differenceImageTopAndPathTop = currentPointBounds.top - topEdge

            // This section reduces size of bitmap to visible part of image if path exceeds that bounds of image.

            if (differenceImageBottomAndPathBottom > 0f)
                finalBitmapHeight -= (differenceImageBottomAndPathBottom * totalScaled)

            if (differenceImageRightAndPathRight > 0f)
                finalBitmapWidth -= (differenceImageRightAndPathRight * totalScaled)

            if (differenceImageLeftAndPathLeft < 0f) {
                val diffAbs = (abs(differenceImageLeftAndPathLeft)) * totalScaled
                selectedBounds.left += diffAbs
                finalBitmapWidth -= diffAbs
            }

            if (differenceImageTopAndPathTop < 0f) {
                val diffAbs = (abs(differenceImageTopAndPathTop)) * totalScaled
                selectedBounds.top += diffAbs
                finalBitmapHeight -= diffAbs
            }

            // Finally create a bitmap to draw contents on.
            val createdBitmap =
                Bitmap.createBitmap(
                    finalBitmapWidth.toInt(),
                    finalBitmapHeight.toInt(),
                    Bitmap.Config.ARGB_8888
                )

            Canvas(createdBitmap).run {
                // Translate canvas back to left-top of bitmap.
                translate(-selectedBounds.left, -selectedBounds.top)
                // Clip the content.
                clipPath(scaledPoint)
                // Translate the drawable to left edge and top edge of current image and
                // draw it.
                translate(leftEdge, topEdge)
                drawable.draw(this)
            }

            path.rewind()
            isPathClose = false

            return createdBitmap
        }
        return null
    }

    override fun isClosed(): Boolean {
        return isPathClose
    }
}
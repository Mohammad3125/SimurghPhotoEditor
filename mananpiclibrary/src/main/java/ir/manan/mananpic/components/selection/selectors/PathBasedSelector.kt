package ir.manan.mananpic.components.selection.selectors

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import ir.manan.mananpic.utils.BitmapUtility
import ir.manan.mananpic.utils.MananMatrix
import java.util.*
import kotlin.math.abs

/**
 * Base class for selectors that manipulate a [Path] object to define an area of interest to select.
 */
abstract class PathBasedSelector : Selector() {

    // Visible part of image bounds.
    protected var leftEdge = 0f
    protected var topEdge = 0f
    protected var rightEdge = 0f
    protected var bottomEdge = 0f

    /**
     * Determines if path is closed or not.
     *
     *
     * Selector cannot clip the content if this value is not 'true'.
     */
    protected var isPathClose = false

    /**
     * Transformation matrix of parent selector.
     *
     *
     * Selector might use this to scale,translate,rotate it's content on canvas.
     */
    protected var canvasMatrix = MananMatrix()

    // Path that adds circles into it and later will be used to
    // clip the drawable content.
    protected val path by lazy {
        Path().apply {
            fillType = Path.FillType.WINDING
        }
    }

    // A stack used in undo mechanism.
    protected val paths by lazy {
        Stack<Path>()
    }

    // A path used by other paths in drawings operation to maintain
    // the previous state of a path.
    protected val pathCopy by lazy {
        Path()
    }

    protected var isSelectionInverse = false

    override fun initialize(context: Context, matrix: MananMatrix, bounds: RectF) {
        leftEdge = bounds.left
        topEdge = bounds.top
        rightEdge = bounds.right
        bottomEdge = bounds.bottom
        canvasMatrix = matrix
    }

    override fun select(drawable: Drawable): Bitmap? {
        // Only select if path is closed.
        if (isClosed()) {
            // Get selected bound of normal path (path that is not scaled.)
            val currentPointBounds = RectF()
            val isInverse = path.fillType == Path.FillType.INVERSE_WINDING

            if (isInverse) {
                pathCopy.rewind()
                pathCopy.addRect(leftEdge, topEdge, rightEdge, bottomEdge, Path.Direction.CW)
                pathCopy.computeBounds(currentPointBounds, true)
            } else {
                path.computeBounds(currentPointBounds, true)
            }

            // If rect area of path doesn't intersect the visible part of
            // image, then return null.
            if (!currentPointBounds.intersects(
                    leftEdge,
                    topEdge,
                    rightEdge,
                    bottomEdge
                )
            ) return null

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
            if (isInverse) {
                val scaledCopyPath = Path(pathCopy).apply {
                    transform(Matrix().apply {
                        setScale(totalScaled, totalScaled, leftEdge, topEdge)
                    })
                }
                scaledCopyPath.computeBounds(selectedBounds, true)
            } else {
                scaledPoint.computeBounds(selectedBounds, true)
            }

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

            resetSelection()


            return BitmapUtility.downSizeBitmap(
                createdBitmap,
                BitmapUtility.getVisiblePixelsRectangleConcurrent(createdBitmap)
            )
        }
        return null
    }

    override fun toggleInverse() {
        if (isClosed()) {
            isSelectionInverse = !isSelectionInverse
            invalidate()
        }
    }

    override fun isInverse(): Boolean {
        return isSelectionInverse
    }

    override fun isClosed(): Boolean {
        return isPathClose
    }
}
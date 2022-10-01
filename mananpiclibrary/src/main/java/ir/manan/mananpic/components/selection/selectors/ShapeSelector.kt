package ir.manan.mananpic.components.selection.selectors

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import ir.manan.mananpic.components.shapes.MananShape
import ir.manan.mananpic.utils.BitmapUtility
import ir.manan.mananpic.utils.MananMatrix
import kotlin.math.abs

class ShapeSelector : Selector() {

    var shape: MananShape? = null
        set(value) {
            field = value
            isShapeCreated = false
            shapeOffsetX = 0f
            shapeOffsetY = 0f
            shape?.resize(0f, 0f)
            invalidate()
        }

    // Paint for circles in path.
    private val shapePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

    /**
     * Shape color. Default is [Color.BLACK]
     */
    var shapeColor = Color.BLACK
        set(value) {
            shapePaint.color = value
            shapePaint.alpha = shapeAlpha
            field = value
            invalidate()
        }

    /**
     * Shape alpha. Default values is 128
     * Value should be between 0 and 255. If value exceeds
     * the range then nearest allowable value is replaced.
     */
    var shapeAlpha = 100
        set(value) {
            val finalVal = if (value > 255) 255 else if (value < 0) 0 else value
            shapePaint.alpha = finalVal
            field = finalVal
            invalidate()
        }

    private lateinit var vBounds: RectF

    private val shapeBoundaryRectF = RectF()

    private var isInitialized = false

    private var isShapeCreated = false

    private var firstX = 0f
    private var firstY = 0f

    private var lastX = 0f
    private var lastY = 0f

    private var shapeOffsetX = 0f
    private var shapeOffsetY = 0f

    private val pathCopy = Path()

    override fun shouldParentTransformDrawings(): Boolean {
        return true
    }

    override fun initialize(context: Context, matrix: MananMatrix, bounds: RectF) {
        isInitialized = true

        vBounds = bounds

        shapePaint.color = shapeColor
        shapePaint.alpha = shapeAlpha
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {
        if (!isShapeCreated) {
            firstX = initialX
            firstY = initialY
            shapeOffsetX = initialX
            shapeOffsetY = initialY
        }
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float) {
        if (!isShapeCreated) {
            lastX = ex
            lastY = ey
            shapeBoundaryRectF.set(firstX, firstY, lastX, lastY)
            shape?.resize(shapeBoundaryRectF.width(), shapeBoundaryRectF.height())
        } else {
            shapeOffsetX += dx
            shapeOffsetY += dy
        }
        invalidate()
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        if (!isShapeCreated) {
            this.lastX = lastX
            this.lastY = lastY
            shapeBoundaryRectF.set(firstX, firstY, lastX, lastY)
            shape?.resize(shapeBoundaryRectF.width(), shapeBoundaryRectF.height())
            isShapeCreated = true
        }
    }

    override fun select(drawable: Drawable): Bitmap? {
        val shapePath = getClipPath()
        return if (isClosed() && shapePath != null) {
            val currentPointBounds = RectF()
            // Get selected bound of normal path (path that is not scaled.)
            shapePath.computeBounds(currentPointBounds, true)

            val leftEdge = vBounds.left
            val topEdge = vBounds.top
            val rightEdge = vBounds.right
            val bottomEdge = vBounds.bottom

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
            val scaledPoint = Path(shapePath).apply {
                transform(Matrix().apply {
                    setScale(totalScaled, totalScaled, leftEdge, topEdge)
                })
            }

            // Get selected bound of scaled path.
            val selectedBounds = RectF()
            scaledPoint.computeBounds(selectedBounds, true)

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
        } else {
            null
        }
    }

    override fun draw(canvas: Canvas?) {
        canvas?.let {
            canvas.translate(shapeOffsetX, shapeOffsetY)
            shape?.draw(canvas, shapePaint)
        }
    }

    override fun resetSelection() {
        isShapeCreated = false
        shapeOffsetX = 0f
        shapeOffsetY = 0f
        shape?.resize(0f, 0f)
        invalidate()
    }

    override fun isClosed(): Boolean {
        return (shape != null && isShapeCreated)
    }

    override fun getClipPath(): Path? {
        return shape?.getPath()?.let {
            pathCopy.apply {
                set(it)
                offset(shapeOffsetX, shapeOffsetY)
            }
        }
    }

    override fun undo() {
    }
}
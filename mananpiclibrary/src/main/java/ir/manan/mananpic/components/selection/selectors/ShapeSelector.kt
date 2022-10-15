package ir.manan.mananpic.components.selection.selectors

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import ir.manan.mananpic.components.shapes.MananShape
import ir.manan.mananpic.utils.BitmapUtility
import ir.manan.mananpic.utils.MananMatrix
import kotlin.math.abs

class ShapeSelector : Selector() {

    private val shapesHolder = mutableListOf<ShapeWrapper>()

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

    private var isInitialized = false

    private var isShapeCreated = false

    private var firstX = 0f
    private var firstY = 0f

    private var lastX = 0f
    private var lastY = 0f

    var shapeRotation = 0f
        set(value) {
            field = value
            currentWrapper?.rotation = field
            invalidate()
        }

    private val pathCopy = Path()

    private val mappingMatrix = Matrix()

    private var isShapesEmpty = false

    private var currentWrapper: ShapeWrapper? = null

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
        if (!isShapeCreated && !isShapesEmpty) {
            firstX = initialX
            firstY = initialY

            currentWrapper?.offsetX = initialX
            currentWrapper?.offsetY = initialY
        }
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float) {
        if (!isShapeCreated && !isShapesEmpty) {
            lastX = ex
            lastY = ey

            currentWrapper?.run {
                bounds.set(firstX, firstY, lastX, lastY)

                shape.resize(
                    bounds.width(),
                    bounds.height()
                )
            }

        } else {
            currentWrapper?.let { wrapper ->
                wrapper.offsetX += dx
                wrapper.offsetY += dy
            }
        }
        invalidate()
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        if (!isShapeCreated && !isShapesEmpty) {
            this.lastX = lastX
            this.lastY = lastY

            currentWrapper?.run {
                bounds.set(firstX, firstY, lastX, lastY)
                shape.resize(bounds.width(), bounds.height())
            }

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
            shapesHolder.forEach {
                canvas.save()

                canvas.translate(it.offsetX, it.offsetY)

                canvas.rotate(
                    it.rotation,
                    it.bounds.width() * 0.5f,
                    it.bounds.height() * 0.5f
                )

                it.shape.draw(canvas, shapePaint)

                canvas.restore()
            }
        }
    }

    override fun resetSelection() {
        isShapeCreated = false
        shapeRotation = 0f
        isShapesEmpty = true
        shapesHolder.clear()
        invalidate()
    }

    override fun isClosed(): Boolean {
        return (!isShapesEmpty && isShapeCreated)
    }

    override fun getClipPath(): Path? {

        if (isShapesEmpty) return null

        return pathCopy.apply {
            rewind()

            shapesHolder.forEach { shapeWrapper ->
                addPath(shapeWrapper.shape.getPath(), mappingMatrix.apply {

                    setRotate(
                        shapeWrapper.rotation,
                        shapeWrapper.bounds.width() * 0.5f,
                        shapeWrapper.bounds.height() * 0.5f
                    )

                    postTranslate(shapeWrapper.offsetX, shapeWrapper.offsetY)

                })
            }
        }
    }

    fun addShape(shape: MananShape) {
        currentWrapper = ShapeWrapper(shape, RectF(), 0f, 0f, 0f)
        shapesHolder.add(currentWrapper!!)
        isShapesEmpty = false
        isShapeCreated = false
    }

    private data class ShapeWrapper(
        val shape: MananShape,
        val bounds: RectF,
        var rotation: Float,
        var offsetX: Float,
        var offsetY: Float
    )

    override fun undo() {
    }
}
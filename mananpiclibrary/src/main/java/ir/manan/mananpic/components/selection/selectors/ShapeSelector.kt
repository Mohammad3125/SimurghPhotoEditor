package ir.manan.mananpic.components.selection.selectors

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import ir.manan.mananpic.components.shapes.MananShape
import ir.manan.mananpic.utils.MananMatrix

class ShapeSelector : PathBasedSelector() {

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

    private val vBounds by lazy {
        RectF()
    }

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

    private val mappingMatrix = Matrix()

    private var isShapesEmpty = false

    private var currentWrapper: ShapeWrapper? = null

    override fun shouldParentTransformDrawings(): Boolean {
        return true
    }

    override fun initialize(context: Context, matrix: MananMatrix, bounds: RectF) {
        super.initialize(context, matrix, bounds)
        isInitialized = true

        vBounds.set(bounds)

        shapePaint.color = shapeColor
        shapePaint.alpha = shapeAlpha
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {
        if (!isShapeCreated && !isShapesEmpty) {
            firstX = initialX
            firstY = initialY
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
            currentWrapper?.bounds?.offset(dx, dy)
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
                isShapeCreated = true
            }

        }
    }

    override fun select(drawable: Drawable): Bitmap? {
        getClipPath()?.let { clip ->
            path.rewind()
            path.set(clip)
        }
        return super.select(drawable)
    }

    override fun draw(canvas: Canvas?) {
        canvas?.let { c ->
            path.rewind()

            shapesHolder.forEach { wrapper ->
                wrapper.shape.drawToPath(path, Matrix().apply {
                    setTranslate(wrapper.bounds.left, wrapper.bounds.top)
                    postRotate(wrapper.rotation, wrapper.bounds.centerX(), wrapper.bounds.centerY())
                })
            }

            if (isSelectionInverse) {
                path.fillType = Path.FillType.INVERSE_WINDING
                c.clipPath(path)
                pathCopy.rewind()
                pathCopy.addRect(vBounds, Path.Direction.CCW)
                c.drawPath(pathCopy, shapePaint)
            } else {
                path.fillType = Path.FillType.WINDING
                c.drawPath(path, shapePaint)
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
            fillType = path.fillType
            shapesHolder.forEach { shapeWrapper ->
                addPath(shapeWrapper.shape.getPath(), mappingMatrix.apply {

                    setRotate(
                        shapeWrapper.rotation,
                        shapeWrapper.bounds.width() * 0.5f,
                        shapeWrapper.bounds.height() * 0.5f
                    )

                    postTranslate(shapeWrapper.bounds.left, shapeWrapper.bounds.top)

                })
            }
        }
    }

    override fun onSizeChanged(newBounds: RectF, changeMatrix: Matrix) {
        if (isClosed()) {
            shapesHolder.forEach {
                changeMatrix.mapRect(it.bounds)
                it.shape.resize(it.bounds.width(), it.bounds.height())
            }
        }

        vBounds.set(newBounds)
        leftEdge = vBounds.left
        topEdge = vBounds.top
        rightEdge = vBounds.right
        bottomEdge = vBounds.bottom

        invalidate()
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

        if (isSelectionInverse) {
            isSelectionInverse = false
            if (shapesHolder.isEmpty()) {
                invalidate()
            }
        }

        if (shapesHolder.isNotEmpty()) {
            shapesHolder.removeLast()

            if (shapesHolder.isNotEmpty()) {
                currentWrapper = shapesHolder.last()
            } else {
                isShapesEmpty = true
            }

            invalidate()
        }
    }
}
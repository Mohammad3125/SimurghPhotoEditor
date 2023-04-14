package ir.manan.mananpic.components.paint.painters.transform

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import ir.manan.mananpic.R
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.GestureUtils
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TransformTool : Painter(), Transformable.OnInvalidate {

    private var selectedLayer: PaintLayer? = null

    var boundPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = 10f
        }
        set(value) {
            field = value
            invalidate()
        }

    lateinit var handleDrawable: Drawable

    var touchRange = 0f

    private val mappingMatrix by lazy {
        Matrix()
    }

    override fun invalidate() {
        sendMessage(PainterMessage.INVALIDATE)
    }

    private val basePoints by lazy {
        FloatArray(8)
    }

    private val mappedMeshPoints by lazy {
        FloatArray(8)
    }

    private val mappedBaseSizeChangePoints by lazy {
        FloatArray(8)
    }

    private val cc by lazy {
        FloatArray(8)
    }

    private val pointHolder by lazy {
        FloatArray(2)
    }

    private val targetComponentBounds by lazy {
        RectF()
    }

    private val tempRect by lazy {
        RectF()
    }

    private val finalCanvas by lazy {
        Canvas()
    }

    var isFreeTransform = true

    private var firstSelectedIndex = -1
    private var secondSelectedIndex = -1
    private var thirdSelectedIndex = -1
    private var forthSelectedIndex = -1

    private var firstSizeChangeIndex = -1
    private var secondSizeChangeIndex = -1

    private var isOnlyMoveX = false

    private var lastX = 0f
    private var lastY = 0f

    private lateinit var bounds: RectF

    private lateinit var matrix: MananMatrix

    private val _children = LinkedList<Child>()

    val children: List<Transformable>
        get() {
            return _children.map { it.transformable }
        }


    private var _selectedChild: Child? = null

    val selectedChild: Transformable?
        get() {
            return _selectedChild?.transformable
        }

    private var isToolInitialized = false

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        bounds: RectF
    ) {
        if (!this::handleDrawable.isInitialized) {
            handleDrawable = ResourcesCompat.getDrawable(
                context.resources,
                R.drawable.defualt_transform_tool_handles,
                null
            )!!
        }

        if (touchRange == 0f) {
            touchRange = context.dp(24)
        }
        matrix = transformationMatrix
        this.bounds = bounds

        isToolInitialized = true

        _children.forEach { child ->
            initializeChild(child, shouldCalculateBounds = true)
        }
    }

    private fun initializeChild(
        child: Child,
        isSelectChild: Boolean = false,
        shouldCalculateBounds: Boolean
    ) {
        child.apply {
            transformable.onInvalidateListener = this@TransformTool

            if (isSelectChild && shouldCalculateBounds) {
                transformable.getBounds(targetComponentBounds)
            }

            if (!isSelectChild) {
                transformable.getBounds(targetComponentBounds)

                transformationMatrix.reset()
                polyMatrix.reset()

                if (targetRect == null) {
                    centerMatrix.setRectToRect(
                        targetComponentBounds,
                        bounds,
                        Matrix.ScaleToFit.CENTER
                    )
                } else {
                    changeMatrixToMatchRect(child, targetRect)
                }
            }

            val w = targetComponentBounds.width()
            val h = targetComponentBounds.height()

            val wh = w * 0.5f
            val hh = h * 0.5f

            basePoints[0] = 0f
            basePoints[1] = 0f
            basePoints[2] = w
            basePoints[3] = 0f
            basePoints[4] = 0f
            basePoints[5] = h
            basePoints[6] = w
            basePoints[7] = h

            baseSizeChangeArray[0] = wh
            baseSizeChangeArray[1] = 0f
            baseSizeChangeArray[2] = w
            baseSizeChangeArray[3] = hh
            baseSizeChangeArray[4] = wh
            baseSizeChangeArray[5] = h
            baseSizeChangeArray[6] = 0f
            baseSizeChangeArray[7] = hh

            if (!isSelectChild) {
                basePoints.copyInto(meshPoints)
            }

            mergeMatrices(child)

        }
    }

    private fun changeMatrixToMatchRect(child: Child, rect: RectF) {
        child.apply {
            transformable.getBounds(targetComponentBounds)

            centerMatrix.setRectToRect(
                targetComponentBounds,
                rect,
                Matrix.ScaleToFit.CENTER
            )

            mergeMatrices(child)

            invalidate()
        }
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {
        _selectedChild?.let {
            selectIndexes(it, initialX, initialY)
        }
    }

    private fun selectIndexes(child: Child, ex: Float, ey: Float) {

        val range = touchRange / matrix.getRealScaleX()

        child.baseSizeChangeArray.copyInto(cc)
        mapMeshPoints(child, cc)

        var nearest = range

        firstSelectedIndex = -1
        secondSelectedIndex = -1
        thirdSelectedIndex = -1
        forthSelectedIndex = -1
        firstSizeChangeIndex = -1
        secondSizeChangeIndex = -1

        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[0],
                cc[1],
                range
            )
        ) {
            (abs(ex - cc[0]) + abs(ey - cc[1])).let {
                if (it < nearest) {
                    nearest = it
                    firstSelectedIndex = 0
                    secondSelectedIndex = 1
                    thirdSelectedIndex = 2
                    forthSelectedIndex = 3
                    firstSizeChangeIndex = 0
                    secondSizeChangeIndex = 1
                    isOnlyMoveX = false
                }
            }

        }
        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[2],
                cc[3],
                range
            )
        ) {
            (abs(ex - cc[2]) + abs(ey - cc[3])).let {
                if (it < nearest) {
                    nearest = it
                    firstSelectedIndex = 2
                    secondSelectedIndex = 3
                    thirdSelectedIndex = 6
                    forthSelectedIndex = 7
                    firstSizeChangeIndex = 2
                    secondSizeChangeIndex = 3
                    isOnlyMoveX = true
                }
            }

        }
        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[4],
                cc[5],
                range
            )
        ) {
            (abs(ex - cc[4]) + abs(ey - cc[5])).let {
                if (it < nearest) {
                    nearest = it
                    firstSelectedIndex = 6
                    secondSelectedIndex = 7
                    thirdSelectedIndex = 4
                    forthSelectedIndex = 5
                    firstSizeChangeIndex = 4
                    secondSizeChangeIndex = 5
                    isOnlyMoveX = false
                }
            }

        }
        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[6],
                cc[7],
                range
            )
        ) {
            (abs(ex - cc[6]) + abs(ey - cc[7])).let {
                if (it < nearest) {
                    nearest = it
                    firstSelectedIndex = 4
                    secondSelectedIndex = 5
                    thirdSelectedIndex = 0
                    forthSelectedIndex = 1
                    firstSizeChangeIndex = 6
                    secondSizeChangeIndex = 7
                    isOnlyMoveX = true
                }
            }
        }

        if (firstSizeChangeIndex > -1 && secondSizeChangeIndex > -1) {
            map(child, cc)
            lastX = cc[firstSizeChangeIndex]
            lastY = cc[secondSizeChangeIndex]
        }

        if (!isFreeTransform) {
            return
        }

        basePoints.copyInto(cc)
        mapMeshPoints(child, cc)

        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[0],
                cc[1],
                range
            )
        ) {
            (abs(ex - cc[0]) + abs(ey - cc[1])).let {
                if (it < nearest) {
                    firstSelectedIndex = 0
                    secondSelectedIndex = 1
                }
            }
        }
        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[2],
                cc[3],
                range
            )
        ) {
            (abs(ex - cc[2]) + abs(ey - cc[3])).let {
                if (it < nearest) {
                    firstSelectedIndex = 2
                    secondSelectedIndex = 3
                }
            }
        }
        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[4],
                cc[5],
                range
            )
        ) {
            (abs(ex - cc[4]) + abs(ey - cc[5])).let {
                if (it < nearest) {
                    firstSelectedIndex = 4
                    secondSelectedIndex = 5
                }
            }
        }
        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[6],
                cc[7],
                range
            )
        ) {
            (abs(ex - cc[6]) + abs(ey - cc[7])).let {
                if (it < nearest) {
                    firstSelectedIndex = 6
                    secondSelectedIndex = 7
                }
            }
        }
    }

    override fun onMove(ex: Float, ey: Float, dx: Float, dy: Float) {

        _selectedChild?.apply {
            mapMeshPoints(this, ex, ey)

            if (firstSelectedIndex > -1 && secondSelectedIndex > -1) {
                if (thirdSelectedIndex > -1 && forthSelectedIndex > -1) {
                    if (isFreeTransform) {
                        var diff = pointHolder[0] - lastX
                        meshPoints[firstSelectedIndex] += diff
                        meshPoints[thirdSelectedIndex] += diff
                        diff = pointHolder[1] - lastY
                        meshPoints[secondSelectedIndex] += diff
                        meshPoints[forthSelectedIndex] += diff
                    } else if (isOnlyMoveX) {
                        val diff = pointHolder[0] - lastX
                        meshPoints[firstSelectedIndex] += diff
                        meshPoints[thirdSelectedIndex] += diff
                    } else {
                        val diff = pointHolder[1] - lastY
                        meshPoints[secondSelectedIndex] += diff
                        meshPoints[forthSelectedIndex] += diff
                    }

                    lastX = pointHolder[0]
                    lastY = pointHolder[1]

                } else {
                    meshPoints[firstSelectedIndex] = pointHolder[0]
                    meshPoints[secondSelectedIndex] = pointHolder[1]
                }
            }

            makePolyToPoly()
            invalidate()
        }
    }

    private fun mapFinalPointsForDraw(child: Child) {
        child.apply {
            mappingMatrix.set(transformationMatrix)
            mappingMatrix.preConcat(centerMatrix)

            meshPoints.copyInto(mappedMeshPoints)
            mappingMatrix.mapPoints(mappedMeshPoints)

            mappingMatrix.preConcat(polyMatrix)
            baseSizeChangeArray.copyInto(mappedBaseSizeChangePoints)
            mappingMatrix.mapPoints(mappedBaseSizeChangePoints)
        }
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        if (firstSelectedIndex == -1 && secondSelectedIndex == -1 && firstSizeChangeIndex == -1 && secondSizeChangeIndex == -1) {

            _selectedChild = null

            _children.forEach { child ->

                mapMeshPoints(child, lastX, lastY)

                val x = pointHolder[0]
                val y = pointHolder[1]

                child.apply {

                    val minX =
                        min(min(meshPoints[0], meshPoints[2]), min(meshPoints[4], meshPoints[6]))

                    val minY =
                        min(min(meshPoints[1], meshPoints[3]), min(meshPoints[5], meshPoints[7]))

                    val maxX =
                        max(max(meshPoints[0], meshPoints[2]), max(meshPoints[4], meshPoints[6]))

                    val maxY =
                        max(max(meshPoints[1], meshPoints[3]), max(meshPoints[5], meshPoints[7]))


                    if (x.coerceIn(
                            minX,
                            maxX
                        ) == x && y.coerceIn(
                            minY,
                            maxY
                        ) == y
                    ) {
                        _selectedChild = child
                    }
                }
            }

            _selectedChild?.let {
                selectChild(it, true)
            }

            invalidate()

        }

        firstSelectedIndex = -1
        secondSelectedIndex = -1
        thirdSelectedIndex = -1
        forthSelectedIndex = -1
        firstSizeChangeIndex = -1
        secondSizeChangeIndex = -1

    }

    private fun makePolyToPoly() {
        _selectedChild!!.apply {
            polyMatrix.setPolyToPoly(basePoints, 0, meshPoints, 0, 4)
        }
    }

    private fun mapMeshPoints(child: Child, ex: Float, ey: Float) {
        pointHolder[0] = ex
        pointHolder[1] = ey
        map(child, pointHolder)
    }

    private fun map(child: Child, array: FloatArray) {
        child.apply {
            transformationMatrix.invert(mappingMatrix)
            mappingMatrix.mapPoints(array)
            centerMatrix.invert(mappingMatrix)
            mappingMatrix.mapPoints(array)
        }
    }

    private fun mapMeshPoints(child: Child, array: FloatArray) {
        child.apply {
            polyMatrix.mapPoints(array)
            centerMatrix.mapPoints(array)
            transformationMatrix.mapPoints(array)
        }
    }

    override fun draw(canvas: Canvas) {
        _children.forEach { child ->

            mergeMatrices(child, false)

            drawChild(canvas, child)

            if (child === _selectedChild) {

                selectChild(child)

                canvas.apply {
                    drawLine(
                        mappedMeshPoints[0],
                        mappedMeshPoints[1],
                        mappedMeshPoints[2],
                        mappedMeshPoints[3],
                        boundPaint
                    )

                    drawLine(
                        mappedMeshPoints[4],
                        mappedMeshPoints[5],
                        mappedMeshPoints[6],
                        mappedMeshPoints[7],
                        boundPaint
                    )

                    drawLine(
                        mappedMeshPoints[0],
                        mappedMeshPoints[1],
                        mappedMeshPoints[4],
                        mappedMeshPoints[5],
                        boundPaint
                    )

                    drawLine(
                        mappedMeshPoints[2],
                        mappedMeshPoints[3],
                        mappedMeshPoints[6],
                        mappedMeshPoints[7],
                        boundPaint
                    )

                    if (isFreeTransform) {
                        resizeAndDrawDrawable(
                            mappedMeshPoints[0].toInt(),
                            mappedMeshPoints[1].toInt(),
                            canvas
                        )
                        resizeAndDrawDrawable(
                            mappedMeshPoints[2].toInt(),
                            mappedMeshPoints[3].toInt(),
                            canvas
                        )
                        resizeAndDrawDrawable(
                            mappedMeshPoints[4].toInt(),
                            mappedMeshPoints[5].toInt(),
                            canvas
                        )
                        resizeAndDrawDrawable(
                            mappedMeshPoints[6].toInt(),
                            mappedMeshPoints[7].toInt(),
                            canvas
                        )
                    }


                    resizeAndDrawDrawable(
                        mappedBaseSizeChangePoints[0].toInt(),
                        mappedBaseSizeChangePoints[1].toInt(),
                        canvas
                    )
                    resizeAndDrawDrawable(
                        mappedBaseSizeChangePoints[2].toInt(),
                        mappedBaseSizeChangePoints[3].toInt(),
                        canvas
                    )
                    resizeAndDrawDrawable(
                        mappedBaseSizeChangePoints[4].toInt(),
                        mappedBaseSizeChangePoints[5].toInt(),
                        canvas
                    )
                    resizeAndDrawDrawable(
                        mappedBaseSizeChangePoints[6].toInt(),
                        mappedBaseSizeChangePoints[7].toInt(),
                        canvas
                    )
                }
            }
        }
    }

    private fun resizeAndDrawDrawable(x: Int, y: Int, canvas: Canvas) {
        val hw = handleDrawable.intrinsicWidth / 2
        val hh = handleDrawable.intrinsicHeight / 2
        handleDrawable.setBounds(
            x - hw,
            y - hh,
            hw + x,
            hh + y
        )

        handleDrawable.draw(canvas)
    }

    private fun drawChild(canvas: Canvas, child: Child) {
        canvas.apply {
            save()

            concat(mappingMatrix)

            child.transformable.draw(this)

            restore()

        }
    }

    override fun resetPaint() {
    }

    override fun onSizeChanged(newBounds: RectF, changeMatrix: Matrix) {
    }

    override fun onLayerChanged(layer: PaintLayer?) {
        selectedLayer = layer
    }

    override fun doesHandleHistory(): Boolean {
        return true
    }

    override fun doesTakeGestures(): Boolean {
        return _selectedChild != null
    }

    override fun onTransformed(transformMatrix: MananMatrix) {
        _selectedChild?.let {
            it.transformationMatrix.postConcat(transformMatrix)
            mergeMatrices(it)
            sendMessage(PainterMessage.INVALIDATE)
        }
    }
    private fun calculateMaximumRect(child: Child, rect: RectF, array: FloatArray) {
        child.apply {
            val minX =
                min(min(array[0], array[2]), min(array[4], array[6]))

            val minY =
                min(min(array[1], array[3]), min(array[5], array[7]))

            val maxX =
                max(max(array[0], array[2]), max(array[4], array[6]))

            val maxY =
                max(max(array[1], array[3]), max(array[5], array[7]))

            rect.set(minX, minY, maxX, maxY)
        }
    }

    private fun mergeMatrices(child: Child, shouldMap: Boolean = true) {
        if (shouldMap) {
            mapFinalPointsForDraw(child)
        }
        child.apply {
            mappingMatrix.set(transformationMatrix)
            mappingMatrix.preConcat(centerMatrix)
            mappingMatrix.preConcat(polyMatrix)
        }
    }

    fun applyComponentOnLayer() {

        selectedLayer?.let { layer ->
            finalCanvas.setBitmap(layer.bitmap)

            _children.forEach { child ->
                selectChild(child)
                drawChild(finalCanvas, child)
            }

            sendMessage(PainterMessage.SAVE_HISTORY)
            invalidate()

        }
    }

    override fun indicateBoundsChange() {
        _selectedChild?.let { child ->
            tempRect.set(targetComponentBounds)

            selectChild(child, true)

            val tw = targetComponentBounds.width()
            val th = targetComponentBounds.height()

            val lw = tempRect.width()
            val lh = tempRect.height()

            child.apply {

                calculateMaximumRect(child, tempRect, meshPoints)

                mappingMatrix.apply {
                    setScale(tw / lw, th / lh, tempRect.centerX(), tempRect.centerY())
                    mapPoints(meshPoints)
                }

            }
            makePolyToPoly()
            mergeMatrices(child)
            invalidate()
        }
    }

    override fun undo() {
        resetPaint()
    }

    override fun redo() {
        resetPaint()
    }

    fun addChild(transformable: Transformable) {
        addChild(transformable, null)
    }

    fun addChild(transformable: Transformable, targetRect: RectF?) {
        _selectedChild = Child(
            transformable, Matrix(), Matrix(), Matrix(), FloatArray(8),
            FloatArray(8), targetRect
        )

        _children.add(_selectedChild!!)

        if (isToolInitialized) {
            initializeChild(_selectedChild!!, shouldCalculateBounds = true)
            invalidate()
        }
    }

    fun bringSelectedChildUp() {
        getSelectedChildIndexAndCompare(_children.lastIndex) { child, selectedChildIndex ->
            swap(selectedChildIndex + 1, selectedChildIndex, child)
        }
    }

    fun bringSelectedChildToFront() {
        getSelectedChildIndexAndCompare(_children.lastIndex) { child, selectedChildIndex ->
            bringFromIndexToIndex(selectedChildIndex, _children.lastIndex)
        }
    }

    fun bringSelectedChildDown() {
        getSelectedChildIndexAndCompare(0) { child, selectedChildIndex ->
            swap(selectedChildIndex - 1, selectedChildIndex, child)
        }
    }

    fun bringSelectedChildToBack() {
        getSelectedChildIndexAndCompare(0) { child, selectedChildIndex ->
            bringFromIndexToIndex(selectedChildIndex, 0)
        }
    }

    private fun getSelectedChildIndexAndCompare(
        compareIndex: Int,
        operation: (child: Child, selectedChildIndex: Int) -> Unit
    ) {
        _selectedChild?.let { child ->
            val selectedChildIndex = _children.indexOf(child)

            if (selectedChildIndex != compareIndex) {
                operation(child, selectedChildIndex)
                invalidate()
            }
        }
    }

    private fun swap(firstIndex: Int, secondIndex: Int, child: Child) {
        val temp = _children.get(firstIndex)
        _children[firstIndex] = child
        _children[secondIndex] = temp
    }

    private fun bringFromIndexToIndex(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) {
            return
        }

        val temp = _children[fromIndex]
        _children.removeAt(fromIndex)

        if (fromIndex < toIndex) {
            _children.addLast(temp)
        } else {
            _children.addFirst(temp)
        }
    }

    fun removeSelectedChild() {
        _selectedChild?.let {
            _children.remove(it)
            invalidate()
        }
    }

    fun removeChildAt(index: Int) {
        _children.removeAt(index)
        invalidate()
    }

    private fun selectChild(child: Child, shouldCalculateBounds: Boolean = false) {
        initializeChild(child, true, shouldCalculateBounds)
    }
    private data class Child(
        val transformable: Transformable,
        val transformationMatrix: Matrix,
        val centerMatrix: Matrix,
        val polyMatrix: Matrix,
        val baseSizeChangeArray: FloatArray,
        val meshPoints: FloatArray,
        val targetRect: RectF?
    )

}
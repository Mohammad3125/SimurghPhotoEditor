package ir.manan.mananpic.components.paint.painters.transform

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import ir.manan.mananpic.R
import ir.manan.mananpic.components.MananFrame.Guidelines
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.components.paint.painters.transform.TransformTool.TransformableAlignment.BOTTOM
import ir.manan.mananpic.components.paint.painters.transform.TransformTool.TransformableAlignment.CENTER
import ir.manan.mananpic.components.paint.painters.transform.TransformTool.TransformableAlignment.HORIZONTAL
import ir.manan.mananpic.components.paint.painters.transform.TransformTool.TransformableAlignment.LEFT
import ir.manan.mananpic.components.paint.painters.transform.TransformTool.TransformableAlignment.RIGHT
import ir.manan.mananpic.components.paint.painters.transform.TransformTool.TransformableAlignment.TOP
import ir.manan.mananpic.components.paint.painters.transform.TransformTool.TransformableAlignment.VERTICAL
import ir.manan.mananpic.components.paint.paintview.MananPaintView
import ir.manan.mananpic.components.paint.paintview.PaintLayer
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.GestureUtils
import java.util.LinkedList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
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
        MananMatrix()
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

    val smartGuidePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 5f
            color = Color.MAGENTA
        }
    }

    /**
     * Holds lines for smart guidelines.
     */
    private val smartGuidelineHolder = arrayListOf<Float>()
    private val smartGuidelineDashedLine = arrayListOf<Boolean>()

    private var smartGuidelineFlags: Int = 0

    private var smartRotationDegreeHolder: FloatArray? = null

    private var smartRotationLineHolder = FloatArray(4)

    var acceptableDistanceForSmartGuideline = 0f

    var rangeForSmartRotationGuideline = 2f
        set(value) {
            if (value < 0f || value > 360) throw IllegalStateException("this value should not be less than 0 or greater than 360")
            field = value
        }

    private val smartGuideLineDashedPathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        bounds: RectF
    ) {

        if (acceptableDistanceForSmartGuideline == 0f) {
            acceptableDistanceForSmartGuideline = context.dp(1)
        }

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
                    transformationMatrix.setRectToRect(
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

            transformationMatrix.setRectToRect(
                targetComponentBounds,
                rect,
                Matrix.ScaleToFit.CENTER
            )

            mergeMatrices(child)

            invalidate()
        }
    }

    override fun onMoveBegin(touchData: MananPaintView.TouchData) {
        _selectedChild?.let {
            selectIndexes(it, touchData.ex, touchData.ey)
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

    override fun onMove(touchData: MananPaintView.TouchData) {

        _selectedChild?.apply {
            mapMeshPoints(this, touchData.ex, touchData.ey)

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

            meshPoints.copyInto(mappedMeshPoints)
            mappingMatrix.mapPoints(mappedMeshPoints)

            mappingMatrix.preConcat(polyMatrix)
            baseSizeChangeArray.copyInto(mappedBaseSizeChangePoints)
            mappingMatrix.mapPoints(mappedBaseSizeChangePoints)
        }
    }

    override fun onMoveEnded(touchData: MananPaintView.TouchData) {
        if (firstSelectedIndex == -1 && secondSelectedIndex == -1 && firstSizeChangeIndex == -1 && secondSizeChangeIndex == -1) {

            _selectedChild = null

            _children.forEach { child ->

                mapMeshPoints(child, touchData.ex, touchData.ey)

                val x = pointHolder[0]
                val y = pointHolder[1]

                child.apply {

                    calculateMaximumRect(child, tempRect, meshPoints)

                    if (x.coerceIn(
                            tempRect.left,
                            tempRect.right
                        ) == x && y.coerceIn(
                            tempRect.top,
                            tempRect.bottom
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

        findSmartGuideLines()
        findRotationSmartGuidelines()

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
        }
    }

    private fun mapMeshPoints(child: Child, array: FloatArray) {
        child.apply {
            polyMatrix.mapPoints(array)
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

                canvas.drawLines(smartRotationLineHolder, smartGuidePaint)
            }


            for (i in smartGuidelineHolder.indices step 4) {

                if (smartGuidelineDashedLine[i]) {
                    smartGuidePaint.pathEffect = smartGuideLineDashedPathEffect
                }

                canvas.drawLine(
                    smartGuidelineHolder[i],
                    smartGuidelineHolder[i + 1],
                    smartGuidelineHolder[i + 2],
                    smartGuidelineHolder[i + 3],
                    smartGuidePaint
                )

                smartGuidePaint.pathEffect = null
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

    override fun onTransformed(transformMatrix: Matrix) {
        _selectedChild?.let {
            it.transformationMatrix.postConcat(transformMatrix)
            findSmartGuideLines()
            findRotationSmartGuidelines()
            mergeMatrices(it)
            sendMessage(PainterMessage.INVALIDATE)
        }
    }

    /**
     * Finds possible guide lines on selected component and other components and populates the line holder if there is
     * any line that could help user.
     * This method detects guide lines on sides of selected component.
     * Sides that are calculated for guide lines include:
     * - Left-Left
     * - Left,Right
     * - Right,Left
     * - Right,Right
     * - Top,Top
     * - Top,Bottom
     * - Bottom,Top
     * - Bottom,Bottom
     * - CenterX
     * - CenterY
     */
    private fun findSmartGuideLines() {

        smartGuidelineDashedLine.clear()

        smartGuidelineHolder.clear()

        if (smartGuidelineFlags == 0) {
            return
        }

        _selectedChild?.let { child ->
            val finalDistanceValue =
                acceptableDistanceForSmartGuideline / child.transformationMatrix.getRealScaleX()

            // Get flags to determine if we should use corresponding guideline or not.
            val isLeftLeftEnabled = smartGuidelineFlags.and(Guidelines.LEFT_LEFT) != 0
            val isLeftRightEnabled = smartGuidelineFlags.and(Guidelines.LEFT_RIGHT) != 0
            val isRightLeftEnabled = smartGuidelineFlags.and(Guidelines.RIGHT_LEFT) != 0
            val isRightRightEnabled =
                smartGuidelineFlags.and(Guidelines.RIGHT_RIGHT) != 0
            val isTopTopEnabled = smartGuidelineFlags.and(Guidelines.TOP_TOP) != 0
            val isTopBottomEnabled = smartGuidelineFlags.and(Guidelines.TOP_BOTTOM) != 0
            val isBottomTopEnabled = smartGuidelineFlags.and(Guidelines.BOTTOM_TOP) != 0
            val isBottomBottomEnabled =
                smartGuidelineFlags.and(Guidelines.BOTTOM_BOTTOM) != 0
            val isCenterXEnabled = smartGuidelineFlags.and(Guidelines.CENTER_X) != 0
            val isCenterYEnabled = smartGuidelineFlags.and(Guidelines.CENTER_Y) != 0

            mergeMatrices(child, true)
            calculateMaximumRect(child, tempRect, mappedMeshPoints)

            // Remove selected component from list of children (because we don't need to find smart guideline for
            // selected component which is a undefined behaviour) and then map each bounds of children to get exact
            // location of points and then add page's bounds to get smart guidelines for page too.
            _children.minus(child).map { c ->
                val r = RectF()
                mergeMatrices(c, true)
                calculateMaximumRect(c, r, mappedMeshPoints)
                r
            }.plus(bounds).forEach { childBounds ->

                // Stores total value that selected component should shift in each axis
                var totalToShiftX = 0f
                var totalToShiftY = 0f

                // Calculate distance between two centers in x axis.
                val centerXDiff = childBounds.centerX() - tempRect.centerX()
                val centerXDiffAbs = abs(centerXDiff)

                // Calculate distance between two centers in y axis.
                val centerYDiff = childBounds.centerY() - tempRect.centerY()
                val centerYDiffAbs = abs(centerYDiff)

                // If absolute value of difference two center x was in range of acceptable distance,
                // then store total difference to later shift the component.
                if (centerXDiffAbs <= finalDistanceValue && isCenterXEnabled) {
                    totalToShiftX = centerXDiff
                }
                if (centerYDiffAbs <= finalDistanceValue && isCenterYEnabled) {
                    totalToShiftY = centerYDiff
                }

                // Calculate distance between two lefts.
                val leftToLeft = childBounds.left - tempRect.left
                val leftToLeftAbs = abs(leftToLeft)

                // Calculate distance between two other component left and selected component right.
                val leftToRight = childBounds.left - tempRect.right
                val leftToRightAbs = abs(leftToRight)

                // Calculate distance between two rights.
                val rightToRight = childBounds.right - tempRect.right
                val rightToRightAbs = abs(rightToRight)

                // Calculate distance between other component right and selected component left.
                val rightToLeft = childBounds.right - tempRect.left
                val rightToLeftAbs = abs(rightToLeft)

                // If left to left of two components was less than left two right and
                // if the lesser value was in acceptable range then set total shift amount
                // in x axis to that value.
                // If we are currently centering in x direction then any of these
                // side should not be calculated or be smart guided.
                if (totalToShiftX != centerXDiff) {
                    if (leftToLeftAbs < leftToRightAbs) {
                        if (leftToLeftAbs <= finalDistanceValue && isLeftLeftEnabled) {
                            totalToShiftX = leftToLeft
                        }
                    } else if (leftToRightAbs < leftToLeftAbs) {
                        if (leftToRightAbs <= finalDistanceValue && isLeftRightEnabled) {
                            totalToShiftX = leftToRight
                        }
                    }
                    // If right to right of two components was less than right to left of them,
                    // Then check if we haven't set the total shift amount so far, if either we didn't
                    // set any value to shift so far or current difference is less than current
                    // total shift amount, then set total shift amount to the right to right difference.
                    if (rightToRightAbs < rightToLeftAbs) {
                        if (rightToRightAbs <= finalDistanceValue && isRightRightEnabled) {
                            if (totalToShiftX == 0f) {
                                totalToShiftX = rightToRight
                            } else if (rightToRightAbs < abs(totalToShiftX)) {
                                totalToShiftX = rightToRight
                            }
                        }
                    } else if (rightToLeftAbs < rightToRightAbs) {
                        if (rightToLeftAbs <= finalDistanceValue && isRightLeftEnabled) {
                            if (totalToShiftX == 0f) {
                                totalToShiftX = rightToLeft
                            } else if (rightToLeftAbs < abs(totalToShiftX)) {
                                totalToShiftX = rightToLeft
                            }
                        }
                    }
                }

                val topToTop = childBounds.top - tempRect.top
                val topToTopAbs = abs(topToTop)
                val topToBottom = childBounds.top - tempRect.bottom
                val topToBottomAbs = abs(topToBottom)

                val bottomToBottom = childBounds.bottom - tempRect.bottom
                val bottomToBottomAbs = abs(bottomToBottom)
                val bottomToTop = childBounds.bottom - tempRect.top
                val bottomToTopAbs = abs(bottomToTop)

                if (totalToShiftY != centerYDiff) {
                    if (topToTopAbs < topToBottomAbs) {
                        if (topToTopAbs <= finalDistanceValue && isTopTopEnabled) {
                            totalToShiftY = topToTop
                        }
                    } else if (topToBottomAbs < topToTopAbs && isTopBottomEnabled) {
                        if (topToBottomAbs <= finalDistanceValue) {
                            totalToShiftY = topToBottom
                        }
                    }

                    if (bottomToBottomAbs < bottomToTopAbs) {
                        if (bottomToBottomAbs <= finalDistanceValue && isBottomBottomEnabled) {
                            if (totalToShiftY == 0f) {
                                totalToShiftY = bottomToBottom
                            } else if (bottomToBottomAbs < abs(totalToShiftY)) {
                                totalToShiftY = bottomToBottom
                            }
                        }
                    } else if (bottomToTopAbs < bottomToBottomAbs) {
                        if (bottomToTopAbs <= finalDistanceValue && isBottomTopEnabled) {
                            if (totalToShiftY == 0f) {
                                totalToShiftY = bottomToTop
                            } else if (bottomToTopAbs < abs(totalToShiftY)) {
                                totalToShiftY = bottomToTop
                            }
                        }
                    }
                }

                child.transformationMatrix.postTranslate(totalToShiftX, totalToShiftY)
                mergeMatrices(child)
                calculateMaximumRect(child, tempRect, mappedMeshPoints)

                // Calculate the minimum and maximum amount of two axes
                // because we want to draw a line from leftmost to rightmost
                // and topmost to bottommost component.
                val minTop = min(tempRect.top, childBounds.top)
                val maxBottom = max(tempRect.bottom, childBounds.bottom)

                val minLeft = min(tempRect.left, childBounds.left)
                val maxRight = max(tempRect.right, childBounds.right)

                smartGuidelineHolder.run {

                    val isNotPage = childBounds !== bounds

                    // Draw a line on left side of selected component if two lefts are the same
                    // or right of other component is same to left of selected component
                    if (totalToShiftX == leftToLeft || totalToShiftX == rightToLeft) {
                        add(tempRect.left)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(minTop)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(tempRect.left)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(maxBottom)
                        smartGuidelineDashedLine.add(isNotPage)
                    }
                    // Draw a line on right side of selected component if left side of other
                    // component is right side of selected component or two rights are the same.
                    if (totalToShiftX == leftToRight || totalToShiftX == rightToRight) {
                        add(tempRect.right)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(minTop)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(tempRect.right)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(maxBottom)
                        smartGuidelineDashedLine.add(isNotPage)
                    }

                    // Draw a line on other component top if it's top is same as
                    // selected component top or bottom of selected component is same as
                    // top of other component.
                    if (totalToShiftY == topToTop || totalToShiftY == topToBottom) {
                        add(minLeft)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(childBounds.top)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(maxRight)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(childBounds.top)
                        smartGuidelineDashedLine.add(isNotPage)
                    }
                    // Draw a line on other component bottom if bottom of it is same as
                    // selected component's top or two bottoms are the same.
                    if (totalToShiftY == bottomToTop || totalToShiftY == bottomToBottom) {
                        add(minLeft)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(childBounds.bottom)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(maxRight)
                        smartGuidelineDashedLine.add(isNotPage)
                        add(childBounds.bottom)
                        smartGuidelineDashedLine.add(isNotPage)
                    }

                    // Finally draw a line from center of each component to another.
                    if (totalToShiftX == centerXDiff || totalToShiftY == centerYDiff) {
                        if (isNotPage) {
                            add(tempRect.centerX())
                            smartGuidelineDashedLine.add(isNotPage)
                            add(tempRect.centerY())
                            smartGuidelineDashedLine.add(isNotPage)
                            add(childBounds.centerX())
                            smartGuidelineDashedLine.add(isNotPage)
                            add(childBounds.centerY())
                            smartGuidelineDashedLine.add(isNotPage)
                        } else {
                            if (totalToShiftX == centerXDiff) {
                                add(tempRect.centerX())
                                smartGuidelineDashedLine.add(isNotPage)
                                add(bounds.top)
                                smartGuidelineDashedLine.add(isNotPage)
                                add(tempRect.centerX())
                                smartGuidelineDashedLine.add(isNotPage)
                                add(bounds.bottom)
                                smartGuidelineDashedLine.add(isNotPage)
                            }

                            if (totalToShiftY == centerYDiff) {
                                add(bounds.left)
                                smartGuidelineDashedLine.add(isNotPage)
                                add(tempRect.centerY())
                                smartGuidelineDashedLine.add(isNotPage)
                                add(bounds.right)
                                smartGuidelineDashedLine.add(isNotPage)
                                add(tempRect.centerY())
                                smartGuidelineDashedLine.add(isNotPage)
                            }
                        }
                    }

                }
            }
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
            transformable, MananMatrix(), MananMatrix(), FloatArray(8),
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

    fun removeAllChildren() {
        _children.clear()
        invalidate()
    }

    private fun selectChild(child: Child, shouldCalculateBounds: Boolean = false) {
        initializeChild(child, true, shouldCalculateBounds)
    }

    /**
     * Sets flags of smart guideline to customize needed smart guidelines,
     * for example if user sets [Guidelines.CENTER_X] and [Guidelines.BOTTOM_BOTTOM], only these
     * guidelines would be detected.
     * If [Guidelines.ALL] is set then all flags would bet set to 1 indicating they are all enabled.
     * ### NOTE: Flags should OR each other to create desired output:
     *      setFlags(LEFT_LEFT.or(RIGHT_LEFT).or(CENTER_X)))
     *      setFlags(LEFT_LEFT | RIGHT_LEFT | CENTER_X)
     * @see Guidelines
     */
    fun setSmartGuidelineFlags(flags: Int) {
        // If flag has the ALL in it then store the maximum int value in flag holder to indicate
        // that all of flags has been set, otherwise set it to provided flags.
        smartGuidelineFlags = if (flags.and(Guidelines.ALL) != 0) Int.MAX_VALUE else flags
    }

    fun clearSmartGuidelines() {
        smartGuidelineDashedLine.clear()
        smartGuidelineHolder.clear()
        invalidate()
    }

    fun clearSmartGuidelineFlags() {
        smartGuidelineFlags = 0
    }

    fun eraseSmartGuidelines() {
        smartGuidelineDashedLine.clear()
        smartGuidelineHolder.clear()
        invalidate()
    }

    fun eraseRotationSmartGuidelines() {
        clearSmartRotationArray()
        invalidate()
    }

    private fun clearSmartRotationArray() {
        smartRotationLineHolder[0] = 0f
        smartRotationLineHolder[1] = 0f
        smartRotationLineHolder[2] = 0f
        smartRotationLineHolder[3] = 0f
    }

    /**
     * Returns smart guidelines flags.
     * @see setSmartGuidelineFlags
     * @see clearSmartGuidelineFlags
     */
    fun getSmartGuidelineFlags(): Int = smartGuidelineFlags


    /**
     * Finds smart guidelines for rotation if [smartRotationDegreeHolder] does have target rotations.
     * @return True if it found smart guideline, false otherwise.
     */
    private fun findRotationSmartGuidelines(): Boolean {
        _selectedChild?.let { child ->

            clearSmartRotationArray()

            smartRotationDegreeHolder?.forEach { snapDegree ->

                mapFinalPointsForDraw(child)

                val imageRotation =
                    child.transformationMatrix.run {
                        GestureUtils.mapTo360(
                            -atan2(
                                getSkewX(true),
                                (getScaleX())
                            ) * (180f / PI)
                        ).toFloat()
                    }

                if (imageRotation in (snapDegree - rangeForSmartRotationGuideline)..(snapDegree + rangeForSmartRotationGuideline)
                ) {
                    calculateMaximumRect(child, tempRect, mappedMeshPoints)

                    child.transformationMatrix.postRotate(
                        snapDegree - imageRotation,
                        tempRect.centerX(),
                        tempRect.centerY()
                    )

                    val centerXBound = tempRect.centerX()

                    smartRotationLineHolder[0] = (centerXBound)
                    smartRotationLineHolder[1] = (-10000f)
                    smartRotationLineHolder[2] = (centerXBound)
                    smartRotationLineHolder[3] = (10000f)

                    mappingMatrix.setRotate(imageRotation, tempRect.centerX(), tempRect.centerY())
                    mappingMatrix.mapPoints(smartRotationLineHolder)

                    return true
                }
            }
        }
        return false
    }

    /**
     * Add degrees that user wants to snap to it if rotation reaches it.
     * These values should be between 0 and 359 (360 is same as 0 degree so use 0 instead of 360).
     * @param degrees Array of specific degrees that rotation snaps to.
     * @throws IllegalStateException if provided array is empty or any element in array is not between 0-360 degrees.
     */
    fun setRotationSmartGuideline(degrees: FloatArray) {
        if (degrees.any { degree -> (degree < 0 || degree > 359) }) throw IllegalStateException(
            "array elements should be between 0-359 degrees"
        )
        if (degrees.isEmpty()) throw IllegalStateException("array should contain at least 1 element")

        smartRotationDegreeHolder = if (degrees.any { it == 0f } && !degrees.any { it == 360f }) {
            FloatArray(degrees.size + 1).also { array ->
                degrees.copyInto(array)
                array[array.lastIndex] = 360f
            }
        } else {
            degrees
        }
    }

    /**
     * Clears any degrees that smart guideline detector detects.
     * This way smart guideline wouldn't snap to any specific degree.
     */
    fun clearRotationSmartGuideline() {
        smartRotationDegreeHolder = null
    }

    /**
     * Returns the rotation degree holder. Smart guideline detector snaps to these
     * degrees if there is any.
     */
    fun getRotationSmartGuidelineDegreeHolder() = smartRotationDegreeHolder

    fun applyMatrix(matrix: Matrix) {
        onTransformed(matrix)
    }

    fun setMatrix(matrix: Matrix) {
        _selectedChild?.apply {
            transformationMatrix.set(matrix)
            findSmartGuideLines()
            findRotationSmartGuidelines()
            mergeMatrices(this)
            sendMessage(PainterMessage.INVALIDATE)
            invalidate()
        }
    }

    fun getChildMatrix(): Matrix? =
        _selectedChild?.transformationMatrix


    fun getSelectedChildBounds(rect: RectF): Boolean {
        _selectedChild?.let { child ->
            mapFinalPointsForDraw(child)
            calculateMaximumRect(child, rect, mappedMeshPoints)
            return true
        }
        return false
    }

    fun setSelectedChildAlignment(alignment: TransformableAlignment) {
        _selectedChild?.let {
            getSelectedChildBounds(tempRect)

            when (alignment) {
                TOP -> mappingMatrix.setTranslate(0f, bounds.top - tempRect.top)
                BOTTOM -> mappingMatrix.setTranslate(0f, bounds.bottom - tempRect.bottom)
                LEFT -> mappingMatrix.setTranslate(bounds.left - tempRect.left, 0f)
                RIGHT -> mappingMatrix.setTranslate(bounds.right - tempRect.right, 0F)
                VERTICAL -> mappingMatrix.setTranslate(0f, bounds.centerY() - tempRect.centerY())
                HORIZONTAL -> mappingMatrix.setTranslate(bounds.centerX() - tempRect.centerX(), 0f)
                CENTER -> mappingMatrix.setTranslate(
                    bounds.centerX() - tempRect.centerX(),
                    bounds.centerY() - tempRect.centerY()
                )
            }

            applyMatrix(mappingMatrix)

        }
    }

    private data class Child(
        val transformable: Transformable,
        val transformationMatrix: MananMatrix,
        val polyMatrix: MananMatrix,
        val baseSizeChangeArray: FloatArray,
        val meshPoints: FloatArray,
        val targetRect: RectF?
    )

    enum class TransformableAlignment {
        TOP, LEFT, RIGHT, BOTTOM, VERTICAL, HORIZONTAL, CENTER
    }
}
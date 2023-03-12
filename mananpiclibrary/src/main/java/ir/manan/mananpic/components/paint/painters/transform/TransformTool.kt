package ir.manan.mananpic.components.paint.painters.transform

import android.content.Context
import android.graphics.*
import ir.manan.mananpic.components.paint.PaintLayer
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.gesture.GestureUtils

class TransformTool() : Painter(), Transformable.OnInvalidate {

    private var selectedLayer: PaintLayer? = null

    var boundPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = 15f
        }
        set(value) {
            field = value
            invalidate()
        }

    var circlesPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.RED
            strokeWidth = 15f
        }
        set(value) {
            field = value
            invalidate()
        }

    var circlesRadius = 0f
        set(value) {
            field = value
            invalidate()
        }

    var targetComponent: Transformable? = null
        set(value) {
            field = value
            targetComponent?.onInvalidateListener = this
            if (this::bounds.isInitialized) {
                initializeComponent(bounds)
                invalidate()
            }
        }

    private val transformationMatrix by lazy {
        Matrix()
    }

    private val centerMatrix by lazy {
        Matrix()
    }

    private val polyMatrix by lazy {
        Matrix()
    }

    private val mappingMatrix by lazy {
        Matrix()
    }

    override fun invalidate() {
        sendMessage(PainterMessage.INVALIDATE)
    }

    private val basePoints by lazy {
        FloatArray(8)
    }

    private val meshPoints by lazy {
        FloatArray(8)
    }

    private val mappedMeshPoints by lazy {
        FloatArray(8)
    }

    private val mappedBaseSizeChangePoints by lazy {
        FloatArray(8)
    }

    private val baseSizeChangePoint by lazy {
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

    override fun initialize(context: Context, matrix: MananMatrix, bounds: RectF) {
        circlesRadius = context.dp(10)
        this.bounds = bounds
        initializeComponent(bounds)
    }

    private fun initializeComponent(bounds: RectF) {
        targetComponent?.apply {
            getBounds(targetComponentBounds)

            transformationMatrix.reset()
            polyMatrix.reset()

            centerMatrix.setRectToRect(targetComponentBounds, bounds, Matrix.ScaleToFit.CENTER)

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

            baseSizeChangePoint[0] = wh
            baseSizeChangePoint[1] = 0f
            baseSizeChangePoint[2] = w
            baseSizeChangePoint[3] = hh
            baseSizeChangePoint[4] = wh
            baseSizeChangePoint[5] = h
            baseSizeChangePoint[6] = 0f
            baseSizeChangePoint[7] = hh

            basePoints.copyInto(meshPoints)

            mergeMatrices()
        }
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {
        selectIndexes(initialX, initialY)
    }

    override fun onMove(ex: Float, ey: Float, dx: Float, dy: Float) {
        mapMeshPoints(ex, ey)

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
        mergeMatrices()
        invalidate()
    }

    private fun mapFinalPointsForDraw() {

        mappingMatrix.set(transformationMatrix)
        mappingMatrix.preConcat(centerMatrix)

        meshPoints.copyInto(mappedMeshPoints)
        mappingMatrix.mapPoints(mappedMeshPoints)

        mappingMatrix.preConcat(polyMatrix)
        baseSizeChangePoint.copyInto(mappedBaseSizeChangePoints)
        mappingMatrix.mapPoints(mappedBaseSizeChangePoints)
    }

    private fun selectIndexes(ex: Float, ey: Float) {

        val range = 120f

        baseSizeChangePoint.copyInto(cc)
        mapMeshPoints(cc)

        if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[0],
                cc[1],
                range
            )
        ) {
            firstSelectedIndex = 0
            secondSelectedIndex = 1
            thirdSelectedIndex = 2
            forthSelectedIndex = 3
            firstSizeChangeIndex = 0
            secondSizeChangeIndex = 1
            map(cc)
            lastX = cc[0]
            lastY = cc[1]
            isOnlyMoveX = false
        } else if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[2],
                cc[3],
                range
            )
        ) {
            firstSelectedIndex = 2
            secondSelectedIndex = 3
            thirdSelectedIndex = 6
            forthSelectedIndex = 7
            firstSizeChangeIndex = 2
            secondSizeChangeIndex = 3
            map(cc)
            lastX = cc[2]
            lastY = cc[3]
            isOnlyMoveX = true
        } else if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[4],
                cc[5],
                range
            )
        ) {
            firstSelectedIndex = 6
            secondSelectedIndex = 7
            thirdSelectedIndex = 4
            forthSelectedIndex = 5
            firstSizeChangeIndex = 4
            secondSizeChangeIndex = 5
            map(cc)
            lastX = cc[4]
            lastY = cc[5]
            isOnlyMoveX = false
        } else if (GestureUtils.isNearTargetPoint(
                ex,
                ey,
                cc[6],
                cc[7],
                range
            )
        ) {
            firstSelectedIndex = 4
            secondSelectedIndex = 5
            thirdSelectedIndex = 0
            forthSelectedIndex = 1
            firstSizeChangeIndex = 6
            secondSizeChangeIndex = 7
            map(cc)
            lastX = cc[6]
            lastY = cc[7]
            isOnlyMoveX = true
        } else {
            firstSelectedIndex = -1
            secondSelectedIndex = -1
            thirdSelectedIndex = -1
            forthSelectedIndex = -1
            firstSizeChangeIndex = -1
            secondSizeChangeIndex = -1

            if (!isFreeTransform) {
                return
            }

            basePoints.copyInto(cc)
            mapMeshPoints(cc)

            if (GestureUtils.isNearTargetPoint(
                    ex,
                    ey,
                    cc[0],
                    cc[1],
                    range
                )
            ) {
                firstSelectedIndex = 0
                secondSelectedIndex = 1
            } else if (GestureUtils.isNearTargetPoint(
                    ex,
                    ey,
                    cc[2],
                    cc[3],
                    range
                )
            ) {
                firstSelectedIndex = 2
                secondSelectedIndex = 3
            } else if (GestureUtils.isNearTargetPoint(
                    ex,
                    ey,
                    cc[4],
                    cc[5],
                    range
                )
            ) {
                firstSelectedIndex = 4
                secondSelectedIndex = 5
            } else if (GestureUtils.isNearTargetPoint(
                    ex,
                    ey,
                    cc[6],
                    cc[7],
                    range
                )
            ) {
                firstSelectedIndex = 6
                secondSelectedIndex = 7
            } else {
                firstSelectedIndex = -1
                secondSelectedIndex = -1
            }
        }
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
    }

    private fun makePolyToPoly() {
        polyMatrix.setPolyToPoly(basePoints, 0, meshPoints, 0, 4)
    }

    private fun mapMeshPoints(ex: Float, ey: Float) {
        pointHolder[0] = ex
        pointHolder[1] = ey

        transformationMatrix.invert(mappingMatrix)
        mappingMatrix.mapPoints(pointHolder)
        centerMatrix.invert(mappingMatrix)
        mappingMatrix.mapPoints(pointHolder)
    }

    private fun map(array: FloatArray) {
        transformationMatrix.invert(mappingMatrix)
        mappingMatrix.mapPoints(array)
        centerMatrix.invert(mappingMatrix)
        mappingMatrix.mapPoints(array)
    }

    private fun mapMeshPoints(array: FloatArray) {
        polyMatrix.mapPoints(array)
        centerMatrix.mapPoints(array)
        transformationMatrix.mapPoints(array)
    }

    override fun draw(canvas: Canvas) {
        targetComponent?.apply {
            canvas.apply {

                save()

                concat(mappingMatrix)

                draw(this)

                restore()

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
                    drawCircle(
                        mappedMeshPoints[0], mappedMeshPoints[1],
                        circlesRadius, circlesPaint
                    )

                    drawCircle(
                        mappedMeshPoints[2], mappedMeshPoints[3],
                        circlesRadius, circlesPaint
                    )


                    drawCircle(
                        mappedMeshPoints[4], mappedMeshPoints[5],
                        circlesRadius, circlesPaint
                    )


                    drawCircle(
                        mappedMeshPoints[6], mappedMeshPoints[7],
                        circlesRadius, circlesPaint
                    )
                }

                drawCircle(
                    mappedBaseSizeChangePoints[0], mappedBaseSizeChangePoints[1],
                    circlesRadius, circlesPaint
                )

                drawCircle(
                    mappedBaseSizeChangePoints[2], mappedBaseSizeChangePoints[3],
                    circlesRadius, circlesPaint
                )

                drawCircle(
                    mappedBaseSizeChangePoints[4], mappedBaseSizeChangePoints[5],
                    circlesRadius, circlesPaint
                )

                drawCircle(
                    mappedBaseSizeChangePoints[6], mappedBaseSizeChangePoints[7],
                    circlesRadius, circlesPaint
                )

            }
        }

    }

    override fun resetPaint() {
        initializeComponent(bounds)
        invalidate()
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
        return true
    }

    override fun onTransformed(transformMatrix: MananMatrix) {
        transformationMatrix.postConcat(transformMatrix)
        mergeMatrices()
        sendMessage(PainterMessage.INVALIDATE)
    }

    private fun mergeMatrices() {
        mapFinalPointsForDraw()
        mappingMatrix.set(transformationMatrix)
        mappingMatrix.preConcat(centerMatrix)
        mappingMatrix.preConcat(polyMatrix)
    }

    fun applyComponentOnLayer() {
        selectedLayer?.let { layer ->
            targetComponent?.let { transformable ->
                mergeMatrices()
                finalCanvas.apply {
                    setBitmap(layer.bitmap)
                    save()
                    concat(mappingMatrix)
                    transformable.draw(finalCanvas)
                    restore()
                }
                sendMessage(PainterMessage.SAVE_HISTORY)
                invalidate()
            }
        }
    }

    override fun undo() {
        resetPaint()
    }

    override fun redo() {
        resetPaint()
    }

}
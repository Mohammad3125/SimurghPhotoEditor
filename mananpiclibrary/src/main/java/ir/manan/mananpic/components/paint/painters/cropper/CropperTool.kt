package ir.manan.mananpic.components.paint.painters.cropper

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Region
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import ir.manan.mananpic.components.cropper.AspectRatio
import ir.manan.mananpic.components.cropper.HandleBar
import ir.manan.mananpic.components.cropper.aspect_ratios.AspectRatioFree
import ir.manan.mananpic.components.cropper.aspect_ratios.AspectRatioLocked
import ir.manan.mananpic.components.paint.Painter
import ir.manan.mananpic.components.paint.paintview.MananPaintView
import ir.manan.mananpic.components.paint.paintview.PaintLayer
import ir.manan.mananpic.utils.MananMatrix
import ir.manan.mananpic.utils.dp
import ir.manan.mananpic.utils.evaluators.MatrixEvaluator
import ir.manan.mananpic.utils.evaluators.RectFloatEvaluator
import kotlin.math.max
import kotlin.math.min

class CropperTool : Painter() {

    private var selectedLayer: PaintLayer? = null

    private val limitRect = RectF()

    // Paint used for drawing the frame.
    private val framePaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE
        }
    }

    var frameColor = Color.DKGRAY
        set(value) {
            framePaint.color = value
            field = value
        }
    var frameStrokeWidth = 0f
        set(value) {
            framePaint.strokeWidth = value
            field = value
        }

    // Paint used for drawing guidelines.
    private val frameGuidelinePaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE
        }
    }

    var guidelineStrokeWidth = 0f
        set(value) {
            frameGuidelinePaint.strokeWidth = value
            field = value
        }
    var guidelineColor = Color.DKGRAY
        set(value) {
            frameGuidelinePaint.color = value
            field = value
        }

    // Determines if guideline should be drawn or not.
    var isDrawGuidelineEnabled = true


    // Paint used for drawing the shadows around frame.
    private val frameShadowsPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    var backgroundShadowColor = Color.BLACK
        set(value) {
            frameShadowsPaint.color = value
            field = value
        }
    var backgroundShadowAlpha = 255 / 3
        set(value) {
            frameShadowsPaint.alpha = value
            field = value
        }

    // Paint used for drawing handle bars.
    private val handleBarPaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE

        }
    }

    var handleBarStrokeWidth = 0f
        set(value) {
            handleBarPaint.strokeWidth = value
            field = value
        }
    var handleBarColor = Color.DKGRAY
        set(value) {
            handleBarPaint.color = value
            field = value
        }
    var handleBarCornerType = Paint.Cap.ROUND
        set(value) {
            handleBarPaint.strokeCap = value
            // If corners are round turn on anti-aliasing.
            handleBarPaint.isAntiAlias = value == Paint.Cap.ROUND
            field = value
        }

    /**
     * Determines color of handle bars when they are selected.
     * By default this value is same as [handleBarColor].
     */
    var selectedHandleBarColor = handleBarColor


    // Rectangle that represents the crop frame.
    private val frameRect by lazy {
        RectF()
    }


    val cropperDimensions: RectF
        get() = frameRect

    private val allocRectF by lazy {
        RectF()
    }

    private val pointHolder = FloatArray(2)

    // Handle bar dimensions for drawing.
    private var frameHandleBar = FloatArray(48)

    // Dimension of guidelines.
    private var guideLineDimension = FloatArray(16)

    // Map of points on frame that represents each handle bar like LEFT, TOP-LEFT and etc...
    private lateinit var mapOfHandleBars: MutableMap<Pair<PointF, PointF>, HandleBar>

    // Later in code determines which handle bar has been pressed.
    private var handleBar: HandleBar? = null

    // Variable to save aspect ratio of cropper.
    private var aspectRatio: AspectRatio = AspectRatioFree()

    private var excessTouchArea = 0f
    private var excessTouchAreaHalf = 0f

    private lateinit var context: Context

    private lateinit var canvasMatrix: MananMatrix
    private lateinit var fitInsideMatrix: MananMatrix

    private val inverseMatrix = MananMatrix()

    // Used to animate the matrix in MatrixEvaluator
    private val endMatrix = MananMatrix()
    private val startMatrix = MananMatrix()

    private val tempMatrix by lazy {
        Matrix()
    }
    private val tempRect by lazy {
        RectF()
    }

    private val startRect by lazy {
        RectF()
    }

    private val endRect by lazy {
        RectF()
    }

    private var leftEdge = 0f
    private var topEdge = 0f
    private var rightEdge = 0f
    private var bottomEdge = 0f

    private lateinit var bounds: RectF

    private val basePoints by lazy {
        FloatArray(8)
    }

    private val cc by lazy {
        FloatArray(8)
    }

    private val cropCanvas by lazy {
        Canvas()
    }


    var animationDuration: Long = 500
        set(value) {
            field = value
            animator.duration = field
        }

    var animationInterpolator: TimeInterpolator = FastOutSlowInInterpolator()
        set(value) {
            field = value
            animator.interpolator = field
        }

    private val animator by lazy {
        ValueAnimator.ofObject(MatrixEvaluator(), startMatrix, endMatrix).apply {
            interpolator = animationInterpolator
            duration = animationDuration
            addUpdateListener {
                canvasMatrix.set(it.animatedValue as MananMatrix)
                frameRect.set(rectEvaluator.evaluate(it.animatedFraction, startRect, endRect))
                setDrawingDimensions()
                sendMessage(PainterMessage.INVALIDATE)
            }
        }
    }

    private val rectEvaluator by lazy {
        RectFloatEvaluator()
    }

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        bounds: RectF
    ) {
        this.context = context
        canvasMatrix = transformationMatrix
        this.fitInsideMatrix = fitInsideMatrix
        this.bounds = bounds

        val r = RectF(bounds)

        fitInsideMatrix.mapRect(r)

        frameShadowsPaint.color = backgroundShadowColor
        frameShadowsPaint.alpha = backgroundShadowAlpha

        framePaint.color = frameColor

        frameGuidelinePaint.color = guidelineColor
        frameGuidelinePaint.strokeWidth = guidelineStrokeWidth

        handleBarPaint.strokeCap = handleBarCornerType
        handleBarPaint.isAntiAlias = handleBarCornerType == Paint.Cap.ROUND

        leftEdge = r.left
        topEdge = r.top
        rightEdge = r.right
        bottomEdge = r.bottom

        // Initialize limit rect that later will be used to limit resizing.
        limitRect.set(r)

        normalizeCropper(r.width(), r.height(), frameRect)
        fitCropperInsideLayer(setRect = true, animate = false)
        setDrawingDimensions()

        context.apply {
            excessTouchArea = dp(40)
            excessTouchAreaHalf = excessTouchArea * 0.5f

            if (handleBarStrokeWidth == 0f) {
                handleBarStrokeWidth = dp(3)
            }
            if (guidelineStrokeWidth == 0f) {
                guidelineStrokeWidth = dp(1)
            }
            if (frameStrokeWidth == 0f) {
                frameStrokeWidth = dp(2)
            }

        }
    }

    private fun normalizeCropper(finalWidth: Float, finalHeight: Float, targetRect: RectF) {
        // Initialize drawing objects after the width and height has been determined.
        val pair = aspectRatio.normalizeAspectRatio(
            finalWidth, finalHeight
        )

        val t = limitRect.centerY() - (pair.second * 0.5f)

        targetRect.set(
            leftEdge,
            t,
            pair.first + leftEdge,
            pair.second + t
        )
    }

    override fun onMoveBegin(touchData: MananPaintView.TouchData) {
        mapPoints(touchData.ex, touchData.ey).let {
            // Figure out which handle bar is in range of the event.
            handleBar = figureOutWhichHandleIsInRangeOfEvent(
                PointF(
                    it[0], it[1]
                )
            )
        }
    }

    private fun mapPoints(ex: Float, ey: Float): FloatArray {
        pointHolder[0] = ex
        pointHolder[1] = ey
        inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)
        inverseMatrix.mapPoints(pointHolder)
        return pointHolder
    }

    private fun mapArray(array: FloatArray) {
        canvasMatrix.invert(inverseMatrix)
        inverseMatrix.mapPoints(array)
    }

    private fun mapVectorPoints(array: FloatArray) {
        canvasMatrix.mapVectors(array)
    }

    override fun onMove(touchData: MananPaintView.TouchData) {
        if (animator.isRunning) {
            return
        }
        // Create a new rectangle to change it's dimensions indirectly to later be able to validate it's size.
        if (handleBar != null) {


            val changedRect = mapPoints(touchData.ex, touchData.ey).let {
                aspectRatio.resize(allocRectF.apply {
                    set(frameRect)
                }, handleBar, it[0], it[1])
            }


            // Change color of handle bar indicating that user is changing size of cropper.
            handleBarPaint.color = selectedHandleBarColor

            mapRectToMatrix(changedRect)

            getOffsetValues(tempRect).let {

                if (it[0] != 0f || it[1] != 0f) {
                    return
                }

                tempMatrix.setTranslate(it[0], it[1])
                tempMatrix.mapRect(changedRect)
                frameRect.set(changedRect)
            }

        } else {
            canvasMatrix.postTranslate(touchData.dx, touchData.dy)
        }
        // Reset the shadows,handle bar dimensions, handle bar map and etc based on new frame size.
        setDrawingDimensions()
        sendMessage(PainterMessage.INVALIDATE)
    }

    private fun getOffsetValues(rect: RectF): FloatArray {
        // Validate that the rectangle is inside the view's bounds.
        val finalX = when {
            rect.right > limitRect.right -> limitRect.right - rect.right
            rect.left < limitRect.left -> limitRect.left - rect.left
            else -> 0f
        }

        val finalY = when {
            rect.bottom > limitRect.bottom -> limitRect.bottom - rect.bottom
            rect.top < limitRect.top -> limitRect.top - rect.top
            else -> 0f
        }

        pointHolder[0] = finalX
        pointHolder[1] = finalY

        return pointHolder
    }

    private fun mapRectToMatrix(changedRect: RectF) {
        setBoundsVariablesFromRect(changedRect, basePoints)

        basePoints.copyInto(cc)
        mapArray(cc)

        calculateMaximumBounds(cc, tempRect)
    }

    private fun calculateMaximumBounds(cc: FloatArray, tempRect: RectF) {
        val minX = min(min(cc[0], cc[2]), min(cc[4], cc[6]))
        val maxX = max(max(cc[0], cc[2]), max(cc[4], cc[6]))
        val minY = min(min(cc[1], cc[3]), min(cc[5], cc[7]))
        val maxY = max(max(cc[1], cc[3]), max(cc[5], cc[7]))
        tempRect.set(minX, minY, maxX, maxY)
    }

    override fun onMoveEnded(touchData: MananPaintView.TouchData) {
        fitCropperInsideLayer()
    }

    override fun onTransformEnded() {
        fitCropperInsideLayer()
    }

    private fun fitCropperInsideLayer(
        setStartRect: Boolean = true,
        animate: Boolean = true,
        setMatrix: Boolean = false,
        setRect: Boolean = false
    ) {

        if (animator.isRunning && animate) {
            return
        }

        mapRectToMatrix(frameRect)

        val s = 1f / calculateRectScaleDifference(tempRect)

        startMatrix.set(canvasMatrix)
        endMatrix.set(canvasMatrix)
        endRect.set(frameRect)

        if (setStartRect) {
            startRect.set(frameRect)
        }

        if (s > 1f) {
            endMatrix.postScale(s, s)
            canvasMatrix.postScale(s, s)
        } else {
            val invS = calculateRectScaleDifference(frameRect)

            endMatrix.postScale(invS, invS)
            canvasMatrix.postScale(invS, invS)

            inverseMatrix.setScale(invS, invS)
            inverseMatrix.mapRect(endRect)

            val offsetX = -(endRect.centerX() - limitRect.centerX())
            val offsetY = -(endRect.centerY() - limitRect.centerY())

            endRect.offset(offsetX, offsetY)

            endMatrix.postTranslate(offsetX, offsetY)
            canvasMatrix.postTranslate(offsetX, offsetY)
        }

        mapRectToMatrix(endRect)

        getOffsetValues(tempRect).let {
            mapVectorPoints(it)
            endMatrix.postTranslate(-it[0], -it[1])
        }

        if (setMatrix) {
            canvasMatrix.set(endMatrix)
        }
        if (setRect) {
            frameRect.set(endRect)
        }

        if (animate) {
            animator.start()
        }
    }

    private fun calculateRectScaleDifference(rect: RectF): Float {
        return min(limitRect.width() / rect.width(), limitRect.height() / rect.height())
    }


    override fun draw(canvas: Canvas) {
        canvas.run {
            save()

            inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)
            inverseMatrix.invert(inverseMatrix)

            concat(inverseMatrix)

            save()

            canvas.clipRect(frameRect, Region.Op.DIFFERENCE)

            drawPaint(frameShadowsPaint)

            restore()

            drawLines(frameHandleBar, framePaint)

            // Draw guidelines.
            if (isDrawGuidelineEnabled) {
                drawLines(guideLineDimension, frameGuidelinePaint)
            }

            restore()


        }
    }

    override fun resetPaint() {
        startMatrix.set(canvasMatrix)
        endMatrix.reset()
        startRect.set(frameRect)
        normalizeCropper(limitRect.width(), limitRect.height(), endRect)
        animator.start()
    }


    fun resetTransformationMatrix() {
        startMatrix.set(canvasMatrix)
        endMatrix.reset()
        startRect.set(frameRect)
        endRect.set(frameRect)
        animator.start()
    }

    private fun setDrawingDimensions() {
        createHandleBarsDimensions(frameRect)

        mapOfHandleBars = createHandleBarPointMap(frameRect)

        createGuideLines(frameRect)
    }

    /**
     * This method creates guidelines in given rectangle.
     * @param frame The frame that guidelines will be drawn inside it.
     */
    private fun createGuideLines(frame: RectF) {
        return frame.run {
            val offsetFromCenterX = (width() * 0.165f)
            val frameCenterX = centerX()
            val offsetFromCenterY = (height() * 0.165f)
            val frameCenterY = centerY()

            guideLineDimension[0] = frameCenterX - offsetFromCenterX
            guideLineDimension[1] = top
            guideLineDimension[2] = frameCenterX - offsetFromCenterX
            guideLineDimension[3] = bottom
            guideLineDimension[4] = frameCenterX + offsetFromCenterX
            guideLineDimension[5] = top
            guideLineDimension[6] = frameCenterX + offsetFromCenterX
            guideLineDimension[7] = bottom
            guideLineDimension[8] = left
            guideLineDimension[9] = frameCenterY - offsetFromCenterY
            guideLineDimension[10] = right
            guideLineDimension[11] = frameCenterY - offsetFromCenterY
            guideLineDimension[12] = left
            guideLineDimension[13] = frameCenterY + offsetFromCenterY
            guideLineDimension[14] = right
            guideLineDimension[15] = frameCenterY + offsetFromCenterY
        }
    }

    /**
     * Figures which handle bar is responsible for the current point in screen.
     * If there are no handle bars in area of touch this method return null.
     * @param point Represents the points that's been touched.
     * @return Returns the handle bar responsible for given point (nullable).
     */
    private fun figureOutWhichHandleIsInRangeOfEvent(point: PointF): HandleBar? {
        // Iterate over handle bar points and figure where the touch is located and which handle bar is touched.
        for (pair in mapOfHandleBars.keys) if ((point.x in pair.first.x..pair.second.x) && (point.y in pair.first.y..pair.second.y)) return mapOfHandleBars[pair]
        return null
    }

    /**
     * Calculates the positions that handle bars should locate.
     * @param frame The rectangle the represents the overlay window.
     * @return Returns a [FloatArray] representing the location of lines that should be drawn on screen.
     */
    private fun createHandleBarsDimensions(frame: RectF) {
        frame.run {

            val frameCenterX = centerX()
            val frameCenterY = centerY()
            val handleBarSizeX = width() / 10
            val handleBarSizeY = height() / 10

            val offset = context.dp(1)
            val leftOffset = left + offset
            val topOffset = top + offset
            val rightOffset = right - offset
            val bottomOffset = bottom - offset

            frameHandleBar[0] = leftOffset
            frameHandleBar[1] = frameCenterY + handleBarSizeY
            frameHandleBar[2] = leftOffset
            frameHandleBar[3] = frameCenterY - handleBarSizeY
            frameHandleBar[4] = frameCenterX - handleBarSizeX
            frameHandleBar[5] = bottomOffset
            frameHandleBar[6] = frameCenterX + handleBarSizeX
            frameHandleBar[7] = bottomOffset
            frameHandleBar[8] = rightOffset
            frameHandleBar[9] = frameCenterY - handleBarSizeY
            frameHandleBar[10] = rightOffset
            frameHandleBar[11] = frameCenterY + handleBarSizeY
            frameHandleBar[12] = frameCenterX - handleBarSizeX
            frameHandleBar[13] = topOffset
            frameHandleBar[14] = frameCenterX + handleBarSizeX
            frameHandleBar[15] = topOffset
            frameHandleBar[16] = leftOffset
            frameHandleBar[17] = topOffset
            frameHandleBar[18] = leftOffset + handleBarSizeX
            frameHandleBar[19] = topOffset
            frameHandleBar[20] = leftOffset
            frameHandleBar[21] = topOffset
            frameHandleBar[22] = leftOffset
            frameHandleBar[23] = topOffset + handleBarSizeY
            frameHandleBar[24] = rightOffset - handleBarSizeX
            frameHandleBar[25] = topOffset
            frameHandleBar[26] = rightOffset
            frameHandleBar[27] = topOffset
            frameHandleBar[28] = rightOffset
            frameHandleBar[29] = topOffset
            frameHandleBar[30] = rightOffset
            frameHandleBar[31] = topOffset + handleBarSizeY
            frameHandleBar[32] = leftOffset
            frameHandleBar[33] = bottomOffset - handleBarSizeY
            frameHandleBar[34] = leftOffset
            frameHandleBar[35] = bottomOffset
            frameHandleBar[36] = leftOffset
            frameHandleBar[37] = bottomOffset
            frameHandleBar[38] = leftOffset + handleBarSizeX
            frameHandleBar[39] = bottomOffset
            frameHandleBar[40] = rightOffset - handleBarSizeX
            frameHandleBar[41] = bottomOffset
            frameHandleBar[42] = rightOffset
            frameHandleBar[43] = bottomOffset
            frameHandleBar[44] = rightOffset
            frameHandleBar[45] = bottomOffset
            frameHandleBar[46] = rightOffset
            frameHandleBar[47] = bottomOffset - handleBarSizeY
        }
    }

    /**
     * This method figures the touch area of each handle bar.
     * @param frame The rectangle the represents the overlay window.
     * @return Returns a map of handle bar area range and [HandleBar] itself.
     */
    private fun createHandleBarPointMap(
        frame: RectF
    ): MutableMap<Pair<PointF, PointF>, HandleBar> {

        return frame.run {
            // Store areas that handle are located + excess area.
            mutableMapOf<Pair<PointF, PointF>, HandleBar>(
                Pair(
                    Pair(
                        PointF(left - excessTouchArea, top - excessTouchArea),
                        PointF(left + excessTouchArea, top + excessTouchArea)
                    ), HandleBar.TOP_LEFT
                ),
                Pair(
                    Pair(
                        PointF(centerX() - excessTouchAreaHalf, top - excessTouchArea),
                        PointF(centerX() + excessTouchAreaHalf, top + excessTouchArea)
                    ), HandleBar.TOP
                ),
                Pair(
                    Pair(
                        PointF(
                            right - excessTouchArea, centerY() - excessTouchAreaHalf
                        ), PointF(right + excessTouchArea, centerY() + excessTouchAreaHalf)
                    ), HandleBar.RIGHT
                ),
                Pair(
                    Pair(
                        PointF(right - excessTouchArea, top - excessTouchArea),
                        PointF(right + excessTouchArea, top + excessTouchArea)
                    ), HandleBar.TOP_RIGHT
                ),
                Pair(
                    Pair(
                        PointF(left - excessTouchArea, centerY() - excessTouchAreaHalf),
                        PointF(left + excessTouchArea, centerY() + excessTouchAreaHalf)
                    ), HandleBar.LEFT
                ),
                Pair(
                    Pair(
                        PointF(left - excessTouchArea, bottom - excessTouchArea),
                        PointF(left + excessTouchArea, bottom + excessTouchArea)
                    ), HandleBar.BOTTOM_LEFT
                ),
                Pair(
                    Pair(
                        PointF(
                            centerX() - excessTouchAreaHalf, bottom - excessTouchArea
                        ), PointF(centerX() + excessTouchAreaHalf, bottom + excessTouchArea)
                    ), HandleBar.BOTTOM
                ),
                Pair(
                    Pair(
                        PointF(
                            right - excessTouchArea, bottom - excessTouchArea
                        ), PointF(right + excessTouchArea, bottom + excessTouchArea)
                    ), HandleBar.BOTTOM_RIGHT
                ),
            )
        }
    }

    fun setAspectRatio(newAspectRatio: AspectRatio) {
        if (newAspectRatio is AspectRatioLocked && aspectRatio is AspectRatioLocked) if ((aspectRatio as AspectRatioLocked).getRatio() == newAspectRatio.getRatio()) return

        if (newAspectRatio is AspectRatioFree && aspectRatio is AspectRatioFree) return

        aspectRatio = newAspectRatio

        startRect.set(frameRect)

        normalizeCropper(limitRect.width(), limitRect.height(), frameRect)

        if (this::context.isInitialized) {
            fitCropperInsideLayer(false)
        } else {
            setDrawingDimensions()
        }

    }

    fun crop(): Bitmap? {
        selectedLayer?.let { layer ->

            fitInsideMatrix.invert(startMatrix)

            tempRect.set(frameRect)
            startMatrix.mapRect(tempRect)

            val croppedBitmap =
                Bitmap.createBitmap(
                    tempRect.width().toInt(),
                    tempRect.height().toInt(),
                    layer.bitmap.config ?: Bitmap.Config.ARGB_8888
                )

            cropCanvas.setBitmap(croppedBitmap)

            cropCanvas.save()

            cropCanvas.translate(-tempRect.left, -tempRect.top)

            cropCanvas.concat(startMatrix)

            cropCanvas.clipRect(frameRect)

            cropCanvas.save()

            inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)

            cropCanvas.concat(inverseMatrix)

            cropCanvas.drawBitmap(layer.bitmap, 0f, 0f, framePaint)

            cropCanvas.restoreToCount(1)

            return croppedBitmap
        }
        return null
    }

    override fun doesNeedTouchSlope(): Boolean {
        return false
    }

    override fun onSizeChanged(newBounds: RectF, changeMatrix: Matrix) {
        limitRect.set(newBounds)
        fitCropperInsideLayer(animate = false, setRect = true, setMatrix = true)
        setDrawingDimensions()
        sendMessage(PainterMessage.INVALIDATE)
    }

    private fun setBoundsVariablesFromRect(rect: RectF, dstArray: FloatArray) {
        dstArray[0] = rect.left
        dstArray[1] = rect.top
        dstArray[2] = rect.right
        dstArray[3] = rect.top
        dstArray[4] = rect.left
        dstArray[5] = rect.bottom
        dstArray[6] = rect.right
        dstArray[7] = rect.bottom
    }

    override fun onLayerChanged(layer: PaintLayer?) {
        selectedLayer = layer
    }
}
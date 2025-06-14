package ir.baboomeh.photolib.components.paint.painters.cropper

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import androidx.core.animation.doOnEnd
import androidx.core.graphics.createBitmap
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import ir.baboomeh.photolib.components.paint.Painter
import ir.baboomeh.photolib.components.paint.painters.cropper.aspect_ratios.AspectRatio
import ir.baboomeh.photolib.components.paint.painters.cropper.aspect_ratios.AspectRatioFree
import ir.baboomeh.photolib.components.paint.painters.cropper.aspect_ratios.AspectRatioLocked
import ir.baboomeh.photolib.components.paint.paintview.PaintLayer
import ir.baboomeh.photolib.utils.MananMatrix
import ir.baboomeh.photolib.utils.dp
import ir.baboomeh.photolib.utils.evaluators.MatrixEvaluator
import ir.baboomeh.photolib.utils.evaluators.RectFloatEvaluator
import ir.baboomeh.photolib.utils.gesture.TouchData
import ir.baboomeh.photolib.utils.perimeter
import kotlin.math.max
import kotlin.math.min

open class CropperTool(context: Context) : Painter() {

    protected var selectedLayer: PaintLayer? = null

    protected val limitRect = RectF()

    // Paint used for drawing the frame.
    protected val framePaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE
        }
    }

    open var frameColor = Color.DKGRAY
        set(value) {
            framePaint.color = value
            field = value
        }
    open var frameStrokeWidth = context.dp(2)
        set(value) {
            framePaint.strokeWidth = value
            field = value
        }

    // Paint used for drawing guidelines.
    protected val frameGuidelinePaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE
        }
    }

    open var guidelineStrokeWidth = context.dp(1)
        set(value) {
            frameGuidelinePaint.strokeWidth = value
            field = value
        }
    open var guidelineColor = Color.DKGRAY
        set(value) {
            frameGuidelinePaint.color = value
            field = value
        }

    // Determines if guideline should be drawn or not.
    var isDrawGuidelineEnabled = true


    // Paint used for drawing the shadows around frame.
    protected val frameShadowsPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    open var backgroundShadowColor = Color.BLACK
        set(value) {
            frameShadowsPaint.color = value
            field = value
        }
    open var backgroundShadowAlpha = 85
        set(value) {
            frameShadowsPaint.alpha = value
            field = value
        }

    // Paint used for drawing handle bars.
    protected val handleBarPaint by lazy {
        Paint().apply {
            style = Paint.Style.STROKE

        }
    }

    open var handleBarStrokeWidth = context.dp(3)
        set(value) {
            handleBarPaint.strokeWidth = value
            field = value
        }
    open var handleBarColor = Color.DKGRAY
        set(value) {
            handleBarPaint.color = value
            field = value
        }
    open var handleBarCornerType = Paint.Cap.ROUND
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
    open var selectedHandleBarColor = handleBarColor

    // Rectangle that represents the crop frame.
    protected val frameRect by lazy {
        RectF()
    }

    open val cropperDimensions: Rect
        get() {
            inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)
            inverseMatrix.invert(inverseMatrix)
            tempRectF.set(frameRect)
            inverseMatrix.mapRect(tempRectF)
            tempRectF.round(tempRect)
            return tempRect
        }

    protected val allocRectF by lazy {
        RectF()
    }

    protected val pointHolder = FloatArray(2)

    // Handle bar dimensions for drawing.
    protected var frameHandleBar = FloatArray(48)

    // Dimension of guidelines.
    protected var guideLineDimension = FloatArray(16)

    // Map of points on frame that represents each handle bar like LEFT, TOP-LEFT and etc...
    protected lateinit var mapOfHandleBars: MutableMap<Pair<PointF, PointF>, HandleBar>

    // Later in code determines which handle bar has been pressed.
    protected var handleBar: HandleBar? = null

    // Variable to save aspect ratio of cropper.
    protected var aspectRatio: AspectRatio = AspectRatioFree()

    protected var excessTouchArea = context.dp(40)
    protected var excessTouchAreaHalf = excessTouchArea * 0.5f

    protected lateinit var context: Context

    protected lateinit var canvasMatrix: MananMatrix
    protected lateinit var fitInsideMatrix: MananMatrix

    protected val inverseMatrix = MananMatrix()

    // Used to animate the matrix in MatrixEvaluator
    protected val endMatrix = MananMatrix()
    protected val startMatrix = MananMatrix()

    protected val tempMatrix by lazy {
        Matrix()
    }

    protected val tempRectF by lazy {
        RectF()
    }

    protected val tempRect by lazy {
        Rect()
    }

    protected val startRect by lazy {
        RectF()
    }

    protected val endRect by lazy {
        RectF()
    }

    protected val basePoints by lazy {
        FloatArray(8)
    }

    protected val cc by lazy {
        FloatArray(8)
    }

    protected val cropCanvas by lazy {
        Canvas()
    }

    protected val startingRect by lazy {
        RectF()
    }

    open var animationDuration: Long = 500
        set(value) {
            field = value
            animator.duration = field
        }

    open var animationInterpolator: TimeInterpolator = FastOutSlowInInterpolator()
        set(value) {
            field = value
            animator.interpolator = field
        }

    protected val animator by lazy {
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

    protected val rectEvaluator by lazy {
        RectFloatEvaluator()
    }

    override fun initialize(
        context: Context,
        transformationMatrix: MananMatrix,
        fitInsideMatrix: MananMatrix,
        layerBounds: Rect,
        clipBounds: Rect
    ) {
        this.context = context
        canvasMatrix = transformationMatrix
        this.fitInsideMatrix = fitInsideMatrix

        tempRectF.set(layerBounds)

        fitInsideMatrix.mapRect(tempRectF)

        frameShadowsPaint.color = backgroundShadowColor
        frameShadowsPaint.alpha = backgroundShadowAlpha

        framePaint.color = frameColor

        frameGuidelinePaint.color = guidelineColor
        frameGuidelinePaint.strokeWidth = guidelineStrokeWidth

        handleBarPaint.strokeCap = handleBarCornerType
        handleBarPaint.isAntiAlias = handleBarCornerType == Paint.Cap.ROUND

        // Initialize limit rect that later will be used to limit resizing.
        limitRect.set(tempRectF)

        normalizeCropper(tempRectF.width(), tempRectF.height(), frameRect)
        fitCropperInsideLayer(setRect = true, animate = false)
        setDrawingDimensions()

        super.initialize(context, transformationMatrix, fitInsideMatrix, layerBounds, clipBounds)
    }

    protected open fun normalizeCropper(finalWidth: Float, finalHeight: Float, targetRect: RectF) {
        // Initialize drawing objects after the width and height has been determined.
        val pair = aspectRatio.normalizeAspectRatio(
            finalWidth, finalHeight
        )

        val t = limitRect.centerY() - (pair.second * 0.5f)
        val l = limitRect.centerX() - (pair.first * 0.5f)

        targetRect.set(
            l,
            t,
            pair.first + l,
            pair.second + t
        )
    }

    override fun onMoveBegin(touchData: TouchData) {
        mapPoints(touchData.ex, touchData.ey).let {
            // Figure out which handle bar is in range of the event.
            handleBar = figureOutWhichHandleIsInRangeOfEvent(
                PointF(
                    it[0], it[1]
                )
            )
        }

        startingRect.set(frameRect)

    }

    protected open fun mapPoints(ex: Float, ey: Float): FloatArray {
        pointHolder[0] = ex
        pointHolder[1] = ey
        inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)
        inverseMatrix.mapPoints(pointHolder)
        return pointHolder
    }

    protected open fun mapArray(array: FloatArray) {
        canvasMatrix.invert(inverseMatrix)
        inverseMatrix.mapPoints(array)
    }

    protected open fun mapVectorPoints(array: FloatArray) {
        canvasMatrix.mapVectors(array)
    }

    protected open fun mapInverseVector(touchData: TouchData) {
        inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)
        pointHolder[0] = touchData.dx
        pointHolder[1] = touchData.dy
        inverseMatrix.mapVectors(pointHolder)
        touchData.dx = pointHolder[0]
        touchData.dy = pointHolder[1]
    }

    override fun onMove(touchData: TouchData) {
        if (animator.isRunning) {
            return
        }
        // Create a new rectangle to change it's dimensions indirectly to later be able to validate it's size.
        if (handleBar != null) {

            mapInverseVector(touchData)

            val changedRect = aspectRatio.resize(allocRectF.apply {
                set(frameRect)
            }, handleBar, touchData.dx, touchData.dy)


            val frameRectPerimeter = frameRect.perimeter()

            if (frameRectPerimeter < (startingRect.perimeter() / 3f) && allocRectF.perimeter() < frameRectPerimeter) {
                return
            }

            // Change color of handle bar indicating that user is changing size of cropper.
            handleBarPaint.color = selectedHandleBarColor

            mapRectToMatrix(changedRect)

            getOffsetValues(tempRectF).let {

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

    protected open fun getOffsetValues(rect: RectF): FloatArray {
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

    protected open fun mapRectToMatrix(changedRect: RectF) {
        setBoundsVariablesFromRect(changedRect, basePoints)

        basePoints.copyInto(cc)
        mapArray(cc)

        calculateMaximumBounds(cc, tempRectF)
    }

    protected open fun calculateMaximumBounds(cc: FloatArray, tempRect: RectF) {
        val minX = min(min(cc[0], cc[2]), min(cc[4], cc[6]))
        val maxX = max(max(cc[0], cc[2]), max(cc[4], cc[6]))
        val minY = min(min(cc[1], cc[3]), min(cc[5], cc[7]))
        val maxY = max(max(cc[1], cc[3]), max(cc[5], cc[7]))
        tempRect.set(minX, minY, maxX, maxY)
    }

    override fun onMoveEnded(touchData: TouchData) {
        fitCropperInsideLayer()
    }

    override fun onTransformEnded() {
        fitCropperInsideLayer()
    }

    protected open fun fitCropperInsideLayer(
        setStartRect: Boolean = true,
        animate: Boolean = true,
        setMatrix: Boolean = false,
        setRect: Boolean = false
    ) {

        if (animator.isRunning && animate) {
            return
        }

        mapRectToMatrix(frameRect)

        val s = 1f / calculateRectScaleDifference(tempRectF)

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

        getOffsetValues(tempRectF).let {
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

    protected open fun calculateRectScaleDifference(rect: RectF): Float {
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

    protected open fun setDrawingDimensions() {
        createHandleBarsDimensions(frameRect)

        mapOfHandleBars = createHandleBarPointMap(frameRect)

        createGuideLines(frameRect)
    }

    /**
     * This method creates guidelines in given rectangle.
     * @param frame The frame that guidelines will be drawn inside it.
     */
    protected open fun createGuideLines(frame: RectF) {
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
    protected open fun figureOutWhichHandleIsInRangeOfEvent(point: PointF): HandleBar? {
        // Iterate over handle bar points and figure where the touch is located and which handle bar is touched.
        for (pair in mapOfHandleBars.keys) if ((point.x in pair.first.x..pair.second.x) && (point.y in pair.first.y..pair.second.y)) return mapOfHandleBars[pair]
        return null
    }

    /**
     * Calculates the positions that handle bars should locate.
     * @param frame The rectangle the represents the overlay window.
     * @return Returns a [FloatArray] representing the location of lines that should be drawn on screen.
     */
    protected open fun createHandleBarsDimensions(frame: RectF) {
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
    protected open fun createHandleBarPointMap(
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

    open fun setAspectRatio(newAspectRatio: AspectRatio, force: Boolean = false) {
        if (!force && newAspectRatio is AspectRatioLocked && aspectRatio is AspectRatioLocked && (aspectRatio as AspectRatioLocked).getRatio() == newAspectRatio.getRatio()) {
            return
        }

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

    open fun crop(): Bitmap? {
        selectedLayer?.let { layer ->

            fitInsideMatrix.invert(startMatrix)

            tempRectF.set(frameRect)
            startMatrix.mapRect(tempRectF)

            val croppedBitmap = createBitmap(
                tempRectF.width().toInt(),
                tempRectF.height().toInt(),
                layer.bitmap.config ?: Bitmap.Config.ARGB_8888
            )

            cropCanvas.run {
                setBitmap(croppedBitmap)

                save()

                translate(-tempRectF.left, -tempRectF.top)

                concat(startMatrix)

                clipRect(frameRect)

                save()

                inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)

                concat(inverseMatrix)

                drawBitmap(layer.bitmap, 0f, 0f, framePaint)

                restoreToCount(1)
            }
            return croppedBitmap
        }
        return null
    }

    open fun clip() {
        selectedLayer?.let { layer ->
            cropCanvas.run {
                val layerBitmapCopy =
                    layer.bitmap.copy(layer.bitmap.config ?: Bitmap.Config.ARGB_8888, true)

                layer.bitmap.eraseColor(Color.TRANSPARENT)

                setBitmap(layer.bitmap)
                inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)
                inverseMatrix.invert(inverseMatrix)
                save()
                concat(inverseMatrix)
                drawRect(frameRect, framePaint.apply {
                    style = Paint.Style.FILL
                })
                restore()

                drawBitmap(layerBitmapCopy, 0f, 0f, framePaint.apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                })

                framePaint.style = Paint.Style.STROKE
                framePaint.xfermode = null

                sendMessage(PainterMessage.INVALIDATE)
                sendMessage(PainterMessage.SAVE_HISTORY)
            }
        }
    }

    open fun setFrame(
        rect: Rect,
        fit: Boolean = false,
        animate: Boolean = true,
        onEnd: () -> Unit = {}
    ) {
        inverseMatrix.setConcat(canvasMatrix, fitInsideMatrix)
        tempRectF.set(rect)
        inverseMatrix.mapRect(tempRectF)
        frameRect.set(tempRectF)

        when {
            fit && animate -> {
                fitCropperInsideLayer(animate = true, setRect = false)
                animator.doOnEnd {
                    onEnd()
                    animator.listeners.clear()
                }
            }

            fit && !animate -> {
                fitCropperInsideLayer(animate = false, setRect = true)
                setDrawingDimensions()
                onEnd()
                sendMessage(PainterMessage.INVALIDATE)
            }

            else -> {
                setDrawingDimensions()
                onEnd()
                sendMessage(PainterMessage.INVALIDATE)
            }
        }
    }

    override fun doesHandleHistory(): Boolean {
        return true
    }

    override fun doesNeedTouchSlope(): Boolean {
        return false
    }

    override fun onSizeChanged(newBounds: RectF, clipBounds: Rect, changeMatrix: Matrix) {
        /*
            Previous method:
            canvasMatrix.postConcat(changeMatrix)
            fitCropperInsideLayer(animate = false, setRect = true, setMatrix = true)
         */

        tempRectF.set(clipBounds)
        fitInsideMatrix.mapRect(tempRectF)
        limitRect.set(tempRectF)
        changeMatrix.mapRect(frameRect)
        setDrawingDimensions()
        sendMessage(PainterMessage.INVALIDATE)
    }

    protected open fun setBoundsVariablesFromRect(rect: RectF, dstArray: FloatArray) {
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
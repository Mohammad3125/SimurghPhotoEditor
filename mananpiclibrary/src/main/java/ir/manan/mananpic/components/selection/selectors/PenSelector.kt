package ir.manan.mananpic.components.selection.selectors

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import ir.manan.mananpic.utils.dp
import kotlin.math.min

class PenSelector : PathBasedSelector() {

    // Paint for drawing cross in center of circle for better indicating selected pixels.
    private val centerCrossPaint by lazy {
        Paint().apply {
            style = Paint.Style.FILL
        }
    }

    // Stroke width of center cross.
    var centerCrossStrokeWidth = 0f
        set(value) {
            centerCrossPaint.strokeWidth = value
            field = value
        }

    // Color of center cross.
    var centerCrossColor = Color.WHITE
        set(value) {
            centerCrossPaint.color = value
            field = value
        }

    private val enlargedBitmapPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG)
    }

    private val shadowCirclePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    private val circleShadowColors by lazy {
        intArrayOf(Color.BLACK, Color.TRANSPARENT)
    }

    private val enlargedBitmapMatrix by lazy {
        Matrix()
    }

    // Determines the size of cross lines (not to be confused with stroke width).
    var centerCrossLineSize = 0f

    // These two variables determine the location of first touch to later
    // use to close a path.
    private var firstX = 0f
    private var firstY = 0f

    // Determines if magnifier is enabled.
    var isZoomEnabled = false

    // Radius of magnifier circle
    private var circleRadius = 0f

    // Total offset of magnifier circle from center of touch.
    // We offset the circle to better view the location that user
    // is currently touching, otherwise magnifier circle would go
    // under user finger.
    private var circleOffsetFromCenter = 0f

    // This offset is for times when the magnifier circle
    // exceeds the view y position.
    private var offsetY: Float = 0f

    // Determines current location of touch (defined in global to be accessible to other methods.)
    private var currentX = 0f
    private var currentY = 0f

    /**
     * This range will later determine the range of acceptance
     * for current touch location to close the path. Default value is 8dp.
     */
    var touchRange = 0f

    // Flag used to indicate whether show the magnifier circle of not.
    private var showCircle = false

    private lateinit var context: Context

    // Height of view (is different to 'bottomEdge' because scale type of image view is Matrix)
    private var height = 0

    // Counts total number of points on screen.
    // This variable will later be used to only select bitmap if our points are more than 2 otherwise
    // we cannot make a side or shape with it to be able to select.
    private var pointCounter = 0

    // Reference to view to later invalidate the view in appropriate situations.
    private lateinit var view: View

    // Bitmap that will be enlarged by matrix and drawn on circle.
    private var bitmapToShowInsideCircle: Bitmap? = null

    // Path effect for corner of path.
    private lateinit var cornerPathEffect: CornerPathEffect

    // Animator for when a path is closed. This animator basically shifts the
    // phase of path effect to create a cool animation.
    private val pathEffectAnimator = ValueAnimator().apply {
        duration = 500
        interpolator = LinearInterpolator()
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        setFloatValues(0f, 20f)
        addUpdateListener {
            pointsPaint.pathEffect =
                ComposePathEffect(
                    DashPathEffect(floatArrayOf(10f, 10f), it.animatedValue as Float),
                    cornerPathEffect
                )

            view.invalidate()
        }
    }

    private val pointsPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }
    }


    override fun initialize(view: View, bitmap: Bitmap?, bounds: RectF) {
        super.initialize(view, bitmap, bounds)
        this.context = view.context
        this.view = view
        height = view.height

        bitmapToShowInsideCircle = bitmap

        // Get display metrics.
        val displayMetrics = context.resources.displayMetrics

        // Take the minimum size of display and divide it by 5 to get the circle radius
        circleRadius = min(displayMetrics.widthPixels, displayMetrics.heightPixels).toFloat() / 5f
        // Offset value of
        circleOffsetFromCenter = circleRadius * 1.5f

        centerCrossPaint.run {
            color = centerCrossColor
            strokeWidth = centerCrossStrokeWidth
        }

        context.run {

            centerCrossLineSize = dp(4)

            centerCrossStrokeWidth = dp(1)

            cornerPathEffect = CornerPathEffect(dp(2))

            touchRange = dp(8)

            pointsPaint.strokeWidth = dp(2)
        }
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {
        if (!isPathClose && bitmapToShowInsideCircle != null)
            initializeMagnifier(initialX, initialY)
    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float) {
        // If path is closed then offset (move around) path if user moves his/her finger.
        if (isPathClose) {
            path.offset(dx, dy)
            view.invalidate()
        }
        // Otherwise show the magnifier.
        else if (isZoomEnabled && bitmapToShowInsideCircle != null) {
            initializeMagnifier(ex, ey)
        }
    }

    private fun initializeMagnifier(ex: Float, ey: Float) {
        currentX = ex
        currentY = ey

        // Limit point to do not go further than view's dimensions.
        if (currentX > rightEdge) currentX = rightEdge
        if (currentX < leftEdge) currentX = leftEdge
        if (currentY > bottomEdge) currentY = bottomEdge
        if (currentY < topEdge) currentY = topEdge

        // Offset the Circle in case circle exceeds the height of view y coordinate.
        offsetY = if (currentY - circleOffsetFromCenter * 1.5f <= 0f) {
            (height - currentY)
        } else 0f

        // Translate to left/top of visible part of image.
        enlargedBitmapMatrix.setTranslate(leftEdge, topEdge)

        // Scale the bitmap two times with pivot point of current
        // touch points + offsets.
        enlargedBitmapMatrix.postScale(
            2f,
            2f,
            currentX,
            currentY + circleOffsetFromCenter - offsetY
        )

        // Initialize the shader.
        enlargedBitmapPaint.shader =
            BitmapShader(
                bitmapToShowInsideCircle!!,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP
            ).apply {
                setLocalMatrix(enlargedBitmapMatrix)
            }

        // Initialize the RadialGradient for shadow behind the enlarged bitmap circle.
        shadowCirclePaint.shader = RadialGradient(
            currentX,
            currentY - circleOffsetFromCenter + offsetY,
            circleRadius + (circleRadius * 0.2f),
            circleShadowColors,
            null,
            Shader.TileMode.CLAMP
        )
        // Show the circle.
        showCircle = true
        view.invalidate()
    }

    override fun onMoveEnded(lastX: Float, lastY: Float) {
        // If path is closed.
        if (!isPathClose) {
            // If path is empty store the first touch location.
            if (path.isEmpty) {
                firstX = lastX
                firstY = lastY
                path.moveTo(lastX, lastY)
                pointCounter++
            } else {
                // If current touch position is close to first line and we have more that 2 line (to make at least a triangle)
                // close the line.
                if (isNearFirstLine(lastX, lastY) && pointCounter > 2) {
                    path.close()
                    pathEffectAnimator.start()
                    isPathClose = true
                } else {
                    // Else just add the point to the path.
                    path.lineTo(lastX, lastY)
                    pointCounter++
                }
            }
        }

        // Don't show the circle anymore.
        showCircle = false
        // Invalidate to hide the circle.
        view.invalidate()

    }

    private fun isNearFirstLine(initialX: Float, initialY: Float): Boolean {
        return (initialX in (firstX - touchRange)..(firstX + touchRange) && initialY in (firstY - touchRange)..(firstY + touchRange))
    }

    override fun resetSelection() {
        path.rewind()
        isPathClose = false

        if (pathEffectAnimator.isRunning || pathEffectAnimator.isStarted) {
            pointsPaint.pathEffect = null
            pathEffectAnimator.cancel()
        }

        view.invalidate()
    }


    override fun draw(canvas: Canvas?) {
        canvas?.run {

            drawPath(path, pointsPaint)

            if (isZoomEnabled && bitmapToShowInsideCircle != null && showCircle) {

                val drawingPositionX = currentX
                val drawingPositionY = currentY - circleOffsetFromCenter + offsetY

                // Draw a shadow circle with RadialGradient.
                drawCircle(
                    drawingPositionX,
                    drawingPositionY,
                    circleRadius + (circleRadius * 0.2f),
                    shadowCirclePaint
                )

                // Draw a white circle for times when image has transparent pixels,
                // otherwise the shadow circle will be fully visible.
                drawCircle(
                    drawingPositionX,
                    drawingPositionY,
                    circleRadius,
                    centerCrossPaint
                )

                // Draw enlarged bitmap inside circle by using BitmapShader.
                drawCircle(
                    drawingPositionX,
                    drawingPositionY,
                    circleRadius,
                    enlargedBitmapPaint
                )

                // Draw horizontal cross in center of circle.
                drawLine(
                    drawingPositionX - centerCrossLineSize,
                    drawingPositionY,
                    drawingPositionX + centerCrossLineSize,
                    drawingPositionY,
                    centerCrossPaint
                )

                // Draw vertical cross in center of circle.
                drawLine(
                    drawingPositionX,
                    currentY - centerCrossLineSize - circleOffsetFromCenter + offsetY,
                    drawingPositionX,
                    currentY + centerCrossLineSize - circleOffsetFromCenter + offsetY,
                    centerCrossPaint
                )

            }
        }
    }
}
package ir.manan.mananpic.components.selection.selectors

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.animation.LinearInterpolator
import ir.manan.mananpic.utils.dp

class PenSelector : PathBasedSelector() {

    // These two variables determine the location of first touch to later
    // use to close a path.
    private var firstX = 0f
    private var firstY = 0f

    /**
     * This range will later determine the range of acceptance
     * for current touch location to close the path. Default value is 8dp.
     */
    var touchRange = 0f


    private lateinit var context: Context

    // Counts total number of points on screen.
    // This variable will later be used to only select bitmap if our points are more than 2 otherwise
    // we cannot make a side or shape with it to be able to select.
    private var pointCounter = 0

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

            invalidateListener?.invalidateDrawings()
        }
    }

    private val pointsPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }
    }


    override fun initialize(context: Context, bounds: RectF) {
        super.initialize(context, bounds)
        this.context = context

        context.run {

            cornerPathEffect = CornerPathEffect(dp(2))

            touchRange = dp(8)

            pointsPaint.strokeWidth = dp(2)
        }
    }

    override fun onMoveBegin(initialX: Float, initialY: Float) {

    }

    override fun onMove(dx: Float, dy: Float, ex: Float, ey: Float) {
        // If path is closed then offset (move around) path if user moves his/her finger.
        if (isPathClose) {
            path.offset(dx, dy)
            invalidateListener?.invalidateDrawings()
        }
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

        // Invalidate to hide the circle.
        invalidateListener?.invalidateDrawings()

    }

    private fun isNearFirstLine(initialX: Float, initialY: Float): Boolean {
        return (initialX in (firstX - touchRange)..(firstX + touchRange) && initialY in (firstY - touchRange)..(firstY + touchRange))
    }

    override fun resetSelection() {
        path.rewind()
        isPathClose = false
        pointCounter = 0

        if (pathEffectAnimator.isRunning || pathEffectAnimator.isStarted) {
            pointsPaint.pathEffect = null
            pathEffectAnimator.cancel()
        }

        invalidateListener?.invalidateDrawings()
    }


    override fun draw(canvas: Canvas?) {
        canvas?.run {
            drawPath(path, pointsPaint)
        }
    }
}